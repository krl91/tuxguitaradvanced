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
public final class ArcadeSession {

	private final FollowStarter.Plan plan;
	private final ArcadeRun arcadeRun;
	private final ArcadeScene scene;
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
		List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
		List<PracticeEvent> practice = plan.sequence().getEvents();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), plan.timeline()));
		}
		NeckModel neck = NeckModel.of(tuning, 24);
		ArcadeScene scene = new ArcadeScene(events, neck, StringColours.forStrings(tuning.getStringCount()), CameraTrack.build(events, neck, CameraTrack.Settings.standard()),
				labels, monochrome);
		return new ArcadeSession(plan, new ArcadeRun(plan.sequence(), ArcadeScoring.standard()), scene);
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
		return new ArcadeFrameSource(this.scene, this.plan.timeline(), FollowStarter.FIRST_TICK, clock, this::view, speedPercent, quality);
	}

	/** The result of the run so far, or null before the first snapshot. */
	public ArcadeResult result() {
		FollowSession.Snapshot snapshot = this.latest.get();
		return snapshot == null ? null : this.arcadeRun.result(snapshot);
	}

	/** True when the session judged the whole passage. */
	public boolean isComplete() {
		FollowSession.Snapshot snapshot = this.latest.get();
		return snapshot != null && snapshot.state() == FollowSession.State.COMPLETE;
	}
}
