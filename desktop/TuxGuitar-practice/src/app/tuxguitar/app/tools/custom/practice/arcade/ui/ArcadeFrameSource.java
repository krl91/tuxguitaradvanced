package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.app.tools.custom.practice.session.TempoClock;

/**
 * Builds the frame to paint from the player's clock at the moment of the paint (spec 003 FR-055, T041): the instant on the timeline is the player's tick
 * now, turned into the nanoseconds of the timeline, so nothing is accumulated from one frame to the next and a pause, a seek or a dropped frame cannot
 * make the picture drift. It also feeds the cost of each paint to the quality controller. It never touches the judging, and it is used on the UI thread
 * only.
 */
public final class ArcadeFrameSource {

	/** The time used while the clock cannot say where the player is: far before everything, so only the neck and the HUD are drawn. */
	public static final long NOT_READY_NANOS = Long.MIN_VALUE / 4L;

	private final ArcadeScene scene;
	private final FollowTimeline timeline;
	private final long firstTick;
	private final TempoClock clock;
	private final Supplier<ArcadeRun.View> run;
	private final IntSupplier speedPercent;
	private final QualityController quality;

	/**
	 * @param firstTick the absolute tick of the player at the start of the timeline (the one the session was built with)
	 * @param run the latest view of the run, or null before the first snapshot
	 */
	public ArcadeFrameSource(ArcadeScene scene, FollowTimeline timeline, long firstTick, TempoClock clock, Supplier<ArcadeRun.View> run,
			IntSupplier speedPercent, QualityController quality) {
		this.scene = scene;
		this.timeline = timeline;
		this.firstTick = firstTick;
		this.clock = clock;
		this.run = run;
		this.speedPercent = speedPercent;
		this.quality = quality;
	}

	/** The instant on the timeline for a wall clock reading. */
	public long timelineNanosAt(long wallNanos) {
		if (!this.clock.isReady()) {
			return NOT_READY_NANOS;
		}
		double tick = this.clock.tickAt(wallNanos);
		if (Double.isNaN(tick)) {
			return NOT_READY_NANOS;
		}
		return this.timeline.nanosAtTick(Math.round(tick - this.firstTick));
	}

	public Scene frame(long wallNanos, Viewport viewport) {
		return this.scene.build(timelineNanosAt(wallNanos), viewport, this.run.get(), this.speedPercent.getAsInt(), this.quality.tier());
	}

	public QualityTier tier() {
		return this.quality.tier();
	}

	/** The cost of the frame just painted, from its first instruction to its last. */
	public void painted(long costNanos) {
		this.quality.frame(costNanos);
	}
}
