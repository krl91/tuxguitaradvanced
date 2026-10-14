package app.tuxguitar.app.tools.custom.practice.arcade.ui;

/**
 * The level of the stage flames, 0 (embers) to 1 (full), of one run (spec 009 FR-002 to FR-004, FR-010; research R4, R11, R12). It follows a target that is
 * made of the real share of the maximum, rising quickly and falling slowly, so that the picture never leaps. It is state of the picture only: nothing of
 * the judging reads it. UI thread only; a new object is made for each run, so it starts at 0 and the count of judged events is that of the run.
 */
public final class FlameLevel {

	/** The judged events (right, wrong or missed) a run needs before the flames follow the share: the first note would make the share leap from 0 to 1. */
	static final int MIN_JUDGED = 4;

	/** 0 to 1 in half a second. */
	static final double RISE_PER_SECOND = 2.0;

	/** 1 to 0 in three seconds. */
	static final double FALL_PER_SECOND = 1.0 / 3.0;

	/** A long gap between two pictures (a stall) moves the level as if it were this long, never more. */
	static final long MAX_STEP_NANOS = 50_000_000L;

	private double level;
	private long lastNanos;
	private boolean started;
	private boolean held;

	/** The level now. */
	public double level() {
		return this.level;
	}

	/**
	 * @param shareOfTheMaximum the real ratio score / maximum (0 when nothing was judged), not the rounded percent
	 * @param judgedEvents the events of this run judged so far
	 * @param wallNanos the wall clock of the picture
	 */
	public void update(double shareOfTheMaximum, int judgedEvents, long wallNanos) {
		if (!this.started) {
			this.started = true;
			this.lastNanos = wallNanos;
			return;
		}
		long elapsed = Math.max(0L, Math.min(wallNanos - this.lastNanos, MAX_STEP_NANOS));
		this.lastNanos = wallNanos;
		if (this.held) {
			return;
		}
		double seconds = elapsed / 1e9;
		double target = (judgedEvents < MIN_JUDGED ? 0.0 : Math.max(0.0, Math.min(1.0, (shareOfTheMaximum - 0.5) / 0.5)));
		if (target > this.level) {
			this.level = Math.min(target, this.level + RISE_PER_SECOND * seconds);
		} else {
			this.level = Math.max(target, this.level - FALL_PER_SECOND * seconds);
		}
	}

	/** Keeps the level as it is, in a pause and in the count-in of a resume, until {@link #resume(long)}. */
	public void hold(long wallNanos) {
		this.held = true;
		this.lastNanos = wallNanos;
		this.started = true;
	}

	/** Goes on from the same level; the time spent held does not count. */
	public void resume(long wallNanos) {
		this.held = false;
		this.lastNanos = wallNanos;
	}
}
