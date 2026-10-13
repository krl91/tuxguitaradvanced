package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.IsometricProjection;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneLabels;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.TempoClock;

/**
 * Everything the view of one Arcade run needs besides the run itself (spec 003 T052): the score of the run, the scene made from the events and the timeline
 * of the prepared plan and the neck of the chosen track, and the latest snapshot of the session. The snapshots arrive on the UI thread (the sink of the run);
 * the view of the run is taken from the latest one at each paint, on the UI thread. Nothing here is kept after the run: it is dropped with the window (FR-026).
 */
public final class ArcadeSession implements ArcadeController.Outcome {

	private final FollowStarter.Plan plan;
	private final ArcadeRun arcadeRun;
	private final ArcadeScene scene;
	private long lookAheadNanos;
	private final AtomicReference<FollowSession.Snapshot> latest = new AtomicReference<FollowSession.Snapshot>();

	private ArcadeSession(FollowStarter.Plan plan, ArcadeRun arcadeRun, ArcadeScene scene) {
		this.plan = plan;
		this.arcadeRun = arcadeRun;
		this.scene = scene;
	}

	/**
	 * @param plan the prepared run (its sequence and its timeline are those of the events shown and judged)
	 * @param tuning the strings of the chosen track: the neck has as many strings as the track
	 */
	public static ArcadeSession of(FollowStarter.Plan plan, TrackTuning tuning, SceneLabels labels, boolean monochrome) {
		return of(plan, tuning, labels, monochrome, 1.0);
	}

	/** @param markerSize the size of the discs of the incoming notes, 1 being the normal one */
	public static ArcadeSession of(FollowStarter.Plan plan, TrackTuning tuning, SceneLabels labels, boolean monochrome, double markerSize) {
		return of(plan, tuning, labels, monochrome, markerSize, CameraTrack.Settings.standard().lookAheadNanos);
	}

	/** The shortest floating measure: a measure shorter than this (a very fast tempo) is shown for this long anyway. */
	public static final long MIN_LOOK_AHEAD_NANOS = 1_000_000_000L;

	/**
	 * The length of a measure of the run (FR-076): the time between the start of a measure and the start of the next one, at the tempo of the run, from the
	 * timeline and the length of the measure in ticks. Never shorter than {@link #MIN_LOOK_AHEAD_NANOS}.
	 */
	public static long measureNanos(app.tuxguitar.app.tools.custom.practice.session.FollowTimeline timeline, long fromTicks, long measureTicks) {
		return Math.max(MIN_LOOK_AHEAD_NANOS, timeline.nanosAtTick(fromTicks + measureTicks) - timeline.nanosAtTick(fromTicks));
	}

	/**
	 * @param lookAheadNanos how long before its onset an event is in the picture: the length of one measure, a floating measure that holds the same sum of
	 *        beats whatever the notes (FR-076)
	 */
	public static ArcadeSession of(FollowStarter.Plan plan, TrackTuning tuning, SceneLabels labels, boolean monochrome, double markerSize, long lookAheadNanos) {
		return of(plan, tuning, labels, monochrome, markerSize, lookAheadNanos, IsometricProjection.DEFAULT_FAR_SCALE, IsometricProjection.DEFAULT_HORIZON);
	}

	/** @param farScale the scale at the horizon and @param horizon its height as a share of the picture: the tilt of the plane, the learner's setting */
	public static ArcadeSession of(FollowStarter.Plan plan, TrackTuning tuning, SceneLabels labels, boolean monochrome, double markerSize, long lookAheadNanos,
			double farScale, double horizon) {
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		List<PracticeEvent> practice = plan.sequence().getEvents();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), i + 1 < practice.size() ? practice.get(i + 1) : null, plan.timeline()));
		}
		NeckModel neck = NeckModel.of(tuning, 24);
		ArcadeScene scene = new ArcadeScene(events, neck, StringColours.forStrings(tuning.getStringCount()), CameraTrack.build(events, neck, CameraTrack.Settings.standard().withLookAhead(lookAheadNanos)),
				labels, monochrome).withMarkerSize(markerSize).withPerspective(farScale, horizon);
		ArcadeSession session = new ArcadeSession(plan, new ArcadeRun(plan.sequence(), ArcadeScoring.standard()), scene);
		session.lookAheadNanos = lookAheadNanos;
		return session;
	}

	public FollowStarter.Plan plan() {
		return this.plan;
	}

	/** The sink of the run: the latest snapshot of the session (UI thread). */
	public void accept(FollowSession.Snapshot snapshot) {
		this.latest.set(snapshot);
	}

	public FollowSession.Snapshot latest() {
		return this.latest.get();
	}

	/** The view of the run for the picture: null before the first snapshot. UI thread only. */
	public ArcadeRun.View view() {
		FollowSession.Snapshot snapshot = this.latest.get();
		return snapshot == null ? null : this.arcadeRun.update(snapshot);
	}

	/** The source of the frames of the canvas, on the clock of the player the plan was made with. */
	public ArcadeFrameSource frameSource(TempoClock clock, QualityController quality, IntSupplier speedPercent) {
		// the metronome counts one measure in before the music, and the look-ahead is one measure: the first notes are at the far end when it starts
		long startNanos = this.plan.timeline().nanosAtTick(this.plan.startTick() - FollowStarter.FIRST_TICK);
		return new ArcadeFrameSource(this.scene, this.plan.timeline(), FollowStarter.FIRST_TICK, clock, this::view, speedPercent, quality, this.lookAheadNanos, startNanos);
	}

	/** The result of the run so far, or null before the first snapshot. */
	@Override
	public ArcadeResult result() {
		FollowSession.Snapshot snapshot = this.latest.get();
		return snapshot == null ? null : this.arcadeRun.result(snapshot);
	}

	/** True when the session judged the whole passage. */
	@Override
	public boolean isComplete() {
		FollowSession.Snapshot snapshot = this.latest.get();
		return snapshot != null && snapshot.state() == FollowSession.State.COMPLETE;
	}
}
