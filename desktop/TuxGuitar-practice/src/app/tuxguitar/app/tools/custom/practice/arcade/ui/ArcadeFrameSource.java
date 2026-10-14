package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
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
	private final long countInNanos;
	private long startNanos;
	private boolean paused;
	private long frozenNanos;
	private long startWall = Long.MIN_VALUE;
	private boolean countInOver;
	private long referenceReal = Long.MIN_VALUE;
	private final FlameSetting flames;
	private final boolean flameMotion;
	private final ArcadeScoring scoring;
	private final FlameLevel flameLevel = new FlameLevel();
	private long flameClock;
	private long flameLast = Long.MIN_VALUE;
	private boolean levelHeld;

	/** How much the clock must have moved for the music to be taken as begun. */
	private static final long MOVED_NANOS = 30_000_000L;

	/** How far the clock of the player may be from the picture's own time for the music to be taken as begun. */
	private static final long AGREE_NANOS = 400_000_000L;

	/** How long after the end of the count-in the picture waits for a clock that does not move before it goes to the clock anyway. */
	private static final long GRACE_NANOS = 1_500_000_000L;

	/**
	 * @param firstTick the absolute tick of the player at the start of the timeline (the one the session was built with)
	 * @param run the latest view of the run, or null before the first snapshot
	 */
	public ArcadeFrameSource(ArcadeScene scene, FollowTimeline timeline, long firstTick, TempoClock clock, Supplier<ArcadeRun.View> run,
			IntSupplier speedPercent, QualityController quality) {
		this(scene, timeline, firstTick, clock, run, speedPercent, quality, 0L, 0L);
	}

	/**
	 * @param countInNanos how long the metronome counts in before the music: the player's clock holds still during it, so the picture runs its own time up to
	 *        the start (0 for none) and the notes come from the far end while the learner hears the clicks
	 * @param startNanos the instant of the timeline where the music begins
	 */
	public ArcadeFrameSource(ArcadeScene scene, FollowTimeline timeline, long firstTick, TempoClock clock, Supplier<ArcadeRun.View> run,
			IntSupplier speedPercent, QualityController quality, long countInNanos, long startNanos) {
		this(scene, timeline, firstTick, clock, run, speedPercent, quality, countInNanos, startNanos, FlameSetting.OFF, false, ArcadeScoring.standard());
	}

	/**
	 * With the stage flames (spec 009): their level is that of this run alone (a new source is made for each run), taken from the share of the maximum the
	 * scoring gives; it is picture state and nothing of the judging reads it.
	 *
	 * @param flames the setting, read when the run started
	 * @param flameMotion false when the system asks to reduce motion: the flames then do not sway
	 */
	public ArcadeFrameSource(ArcadeScene scene, FollowTimeline timeline, long firstTick, TempoClock clock, Supplier<ArcadeRun.View> run,
			IntSupplier speedPercent, QualityController quality, long countInNanos, long startNanos, FlameSetting flames, boolean flameMotion, ArcadeScoring scoring) {
		this.flames = (flames != null ? flames : FlameSetting.OFF);
		this.flameMotion = flameMotion;
		this.scoring = scoring;
		this.countInNanos = countInNanos;
		this.startNanos = startNanos;
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

	/**
	 * The instant of the picture: the timeline of the clock, or, from the start of the run until the clock of the player agrees with it, the picture's own time,
	 * which begins one count-in before the music. The clock of the player is not to be trusted before the music begins: it holds the position of the start
	 * during the count-in, and after a first run it still holds the end of the song until the new run moves it (owner, 2026-10-13: the second run began with the
	 * first chord on the neck). It is followed only when it is where the picture's own time says the music is.
	 */
	private long pictureNanosAt(long wallNanos) {
		long real = timelineNanosAt(wallNanos);
		if (this.paused) {
			return this.frozenNanos;
		}
		if (this.countInNanos <= 0L || this.countInOver) {
			return real;
		}
		if (this.startWall == Long.MIN_VALUE) {
			this.startWall = wallNanos;
		}
		long elapsed = wallNanos - this.startWall;
		long own = this.startNanos - this.countInNanos + elapsed;
		boolean near = real != NOT_READY_NANOS && Math.abs(real - own) <= AGREE_NANOS && elapsed >= this.countInNanos - AGREE_NANOS;
		if (!near) {
			this.referenceReal = Long.MIN_VALUE;
		} else if (this.referenceReal == Long.MIN_VALUE) {
			this.referenceReal = real;
		}
		// the clock holds the start still during the count-in, which is near the picture's own time for the last moments of it: it is followed only once it
		// moves, so that the picture does not jump from its own time to the start of the music (owner, 2026-10-13: the first chord jumped onto the neck)
		boolean moving = near && real - this.referenceReal > MOVED_NANOS;
		if (moving || elapsed > this.countInNanos + GRACE_NANOS) {
			this.countInOver = true;
			return real;
		}
		return own;
	}

	/**
	 * The run is paused or goes on (UI thread, with the wall time of the change): the picture holds still while it is paused, and a run that goes on counts in
	 * again, as the player does, from where the music was: the notes come from the far end while the metronome clicks, and the clock of the player is followed
	 * once it moves (owner, 2026-10-13: after a pause during the count-in the first chord was on the neck).
	 */
	public void setPaused(boolean paused, long wallNanos) {
		if (paused && !this.paused) {
			this.frozenNanos = pictureNanosAt(wallNanos);
			advanceFlameClock(wallNanos);
			this.flameLevel.hold(wallNanos);
			this.levelHeld = true;
			this.paused = true;
		} else if (!paused && this.paused) {
			this.paused = false;
			this.flameLast = wallNanos;
			if (this.countInNanos > 0L) {
				long real = timelineNanosAt(wallNanos);
				this.startNanos = real != NOT_READY_NANOS ? real : this.frozenNanos;
				this.startWall = wallNanos;
				this.countInOver = false;
				this.referenceReal = Long.MIN_VALUE;
			}
		}
	}

	public Scene frame(long wallNanos, Viewport viewport) {
		long picture = pictureNanosAt(wallNanos);
		ArcadeRun.View view = this.run.get();
		return this.scene.build(picture, viewport, view, this.speedPercent.getAsInt(), this.quality.tier(), flamesAt(wallNanos, view));
	}

	/** The flame clock goes on while the run is not paused, in a count-in too, and never depends on the instant of the picture, which jumps back at a resume. */
	private void advanceFlameClock(long wallNanos) {
		if (this.flameLast != Long.MIN_VALUE && !this.paused) {
			this.flameClock += Math.max(0L, wallNanos - this.flameLast);
		}
		this.flameLast = wallNanos;
	}

	private FlameState flamesAt(long wallNanos, ArcadeRun.View view) {
		if (this.flames == FlameSetting.OFF) {
			return null;
		}
		advanceFlameClock(wallNanos);
		// after a resume the level stays held until the picture is back on the clock of the player: the count-in is not play
		if (this.levelHeld && !this.paused && (this.countInNanos <= 0L || this.countInOver)) {
			this.flameLevel.resume(wallNanos);
			this.levelHeld = false;
		}
		int judged = view == null ? 0 : view.judged();
		int maximum = view == null ? 0 : this.scoring.maximum(judged);
		this.flameLevel.update(maximum > 0 ? view.score() / (double) maximum : 0.0, judged, wallNanos);
		return new FlameState(this.flames, this.flameLevel.level(), this.flameClock, this.flameMotion);
	}

	/** The monotonic flame clock, for the tests: it never goes back and stands still while the run is paused. */
	long flameClockNanos() {
		return this.flameClock;
	}

	public QualityTier tier() {
		return this.quality.tier();
	}

	/** The cost of the frame just painted, from its first instruction to its last. */
	public void painted(long costNanos) {
		this.quality.frame(costNanos);
	}
}
