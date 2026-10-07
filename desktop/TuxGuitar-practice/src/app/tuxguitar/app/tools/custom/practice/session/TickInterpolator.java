package app.tuxguitar.app.tools.custom.practice.session;

/**
 * Turns the position of the player's sequencer, which moves by steps, into a continuous position on the wall clock (spec 002 T093, research R6c).
 * <p>
 * The sequencer integrates {@code System.nanoTime()} with a 10 ms timer and a rounding at each step: its position changes every 10 to 18 ms and its
 * rate is off by up to -0.14 % from the nominal tempo (measured). The interpolator keeps the moments at which the position was seen to change (the
 * middle of the two polls that surround each change), learns the rate by a least squares fit over the last 600 ms of changes (the nominal rate
 * until it has enough of them), and gives the position at the wall time from the latest change, never extrapolated by more than 30 ms. A seek (the
 * position goes back, or jumps by much more than the time that passed allows) starts it again, and a player that stops (no change for 60 ms, much longer
 * than any step) makes the position hold instead of running away. The position is meant to be asked for the present or the next few ms, as the frames
 * of the practice do; asked far in the past it carries the error of the rate over that time.
 * <p>
 * Pure and single threaded: the caller polls it from one thread.
 */
public final class TickInterpolator {

	private static final long FIT_WINDOW_NANOS = 600_000_000L;
	private static final int MIN_POINTS = 8;
	private static final long MIN_SPAN_NANOS = 150_000_000L;
	/** A step lasts 10 to 18 ms and a poll comes every few ms: a position that has not moved for this long is stopped. */
	private static final long STALL_NANOS = 60_000_000L;
	/** The most the position is extrapolated beyond the latest change: longer than the longest step plus a poll. */
	private static final long EXTRAPOLATION_NANOS = 30_000_000L;
	private static final long SEEK_MARGIN_TICKS = 960;
	private static final int CAPACITY = 512;

	private final double nominalTicksPerNano;
	private final long[] pointWall = new long[CAPACITY];
	private final long[] pointTick = new long[CAPACITY];
	private int head;
	private int size;
	private long lastWall = Long.MIN_VALUE;
	private long lastTick;
	private double ticksPerNano;

	public TickInterpolator(double nominalTicksPerSecond) {
		this.nominalTicksPerNano = nominalTicksPerSecond / 1e9;
		this.ticksPerNano = this.nominalTicksPerNano;
	}

	/** One poll of the player: the wall clock now and the position the sequencer reports. */
	public void observe(long wallNanos, long tick) {
		if (this.lastWall == Long.MIN_VALUE) {
			this.lastWall = wallNanos;
			this.lastTick = tick;
			return;
		}
		if (tick != this.lastTick) {
			long elapsed = wallNanos - this.lastWall;
			boolean seek = tick < this.lastTick || (tick - this.lastTick) > this.ticksPerNano * elapsed * 4.0 + SEEK_MARGIN_TICKS;
			if (seek) {
				this.size = 0;
				this.head = 0;
				this.ticksPerNano = this.nominalTicksPerNano;
				addPoint(wallNanos, tick);
			} else {
				// the change happened between the two polls
				addPoint(this.lastWall + elapsed / 2, tick);
			}
			this.lastTick = tick;
			fitRate();
		}
		this.lastWall = wallNanos;
	}

	private void addPoint(long wall, long tick) {
		if (this.size == CAPACITY) {
			this.head = (this.head + 1) % CAPACITY;
			this.size--;
		}
		int at = (this.head + this.size) % CAPACITY;
		this.pointWall[at] = wall;
		this.pointTick[at] = tick;
		this.size++;
	}

	private void fitRate() {
		long newest = this.pointWall[(this.head + this.size - 1) % CAPACITY];
		int first = this.size;
		for (int i = this.size - 1 ; i >= 0 ; i--) {
			if (newest - this.pointWall[(this.head + i) % CAPACITY] > FIT_WINDOW_NANOS) {
				break;
			}
			first = i;
		}
		int count = this.size - first;
		if (count < MIN_POINTS) {
			return;
		}
		long originWall = this.pointWall[(this.head + first) % CAPACITY];
		long originTick = this.pointTick[(this.head + first) % CAPACITY];
		if (newest - originWall < MIN_SPAN_NANOS) {
			return;
		}
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double sxy = 0.0;
		for (int i = first ; i < this.size ; i++) {
			double x = (this.pointWall[(this.head + i) % CAPACITY] - originWall) / 1e9;
			double y = (double) (this.pointTick[(this.head + i) % CAPACITY] - originTick);
			sx += x;
			sy += y;
			sxx += x * x;
			sxy += x * y;
		}
		double denominator = count * sxx - sx * sx;
		if (denominator > 0.0) {
			double slope = (count * sxy - sx * sy) / denominator;
			if (slope > 0.0) {
				this.ticksPerNano = slope / 1e9;
			}
		}
	}

	/** True once the position has been seen to move: before that the interpolator has no anchor. */
	public boolean isReady() {
		return this.size > 0;
	}

	/** The rate the interpolator uses, in ticks a second. */
	public double ticksPerSecond() {
		return this.ticksPerNano * 1e9;
	}

	/** True when the position has not changed for much longer than a step: the player is stopped or paused. */
	public boolean isStalled(long wallNanos) {
		return this.size > 0 && wallNanos - this.pointWall[(this.head + this.size - 1) % CAPACITY] > STALL_NANOS;
	}

	/** The position in ticks (fractional) at the wall time, or NaN while not ready. A stopped player holds its position. */
	public double tickAt(long wallNanos) {
		if (this.size == 0) {
			return Double.NaN;
		}
		int newest = (this.head + this.size - 1) % CAPACITY;
		long since = wallNanos - this.pointWall[newest];
		return this.pointTick[newest] + this.ticksPerNano * Math.min(since, EXTRAPOLATION_NANOS);
	}

	/** The wall time at which the position is the given tick (the inverse of {@link #tickAt}), or {@link Long#MIN_VALUE} while not ready. */
	public long wallNanosAt(double tick) {
		if (this.size == 0) {
			return Long.MIN_VALUE;
		}
		int newest = (this.head + this.size - 1) % CAPACITY;
		return this.pointWall[newest] + Math.round((tick - this.pointTick[newest]) / this.ticksPerNano);
	}
}
