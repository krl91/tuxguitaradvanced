package app.tuxguitar.app.tools.custom.practice.testsupport;

import java.util.function.DoubleUnaryOperator;

import app.tuxguitar.app.tools.custom.practice.session.TempoClock;

/**
 * A {@link TempoClock} that moves only when the test says so (spec 002 T128): no thread, no sleep, no real time. The wall clock is a number the test
 * advances; the player's position follows a function from the ticks since the start to the nanoseconds (the tempo changes of a song: for
 * example {@code FollowTimeline.nanosAtTick}), so the clock has the tempo, the changes of tempo, the repeats and the alternative endings of the song
 * whose timeline gives the function (the expanded ticks of the player). It can be started, paused, resumed, sought, stopped and made not ready.
 */
public final class FakeTempoClock implements TempoClock {

	private final DoubleUnaryOperator nanosAtTick;
	private final long baseTick;
	private final long lengthTicks;

	private long now;
	private boolean started;
	private boolean paused;
	private boolean ready = true;
	/** The wall time at which the position was {@link #anchorTick}, and that position, in the player's ticks. */
	private long anchorWall;
	private double anchorTick;

	/**
	 * @param nanosAtTick the nanoseconds from the first tick of the song to a tick counted from it; it must grow with the tick
	 * @param baseTick the player's tick of the first tick of the song (960 in TuxGuitar)
	 * @param lengthTicks the length of the song in ticks (the position never goes past it)
	 */
	public FakeTempoClock(DoubleUnaryOperator nanosAtTick, long baseTick, long lengthTicks) {
		this.nanosAtTick = nanosAtTick;
		this.baseTick = baseTick;
		this.lengthTicks = lengthTicks;
	}

	// ----------------------------------------------------------------------------------------------- the test's controls

	/** The wall clock, which only the test moves. */
	public void setNow(long wallNanos) {
		advance(wallNanos - this.now);
	}

	public void advance(long nanos) {
		if (nanos < 0) {
			throw new IllegalArgumentException("a clock does not go back: " + nanos);
		}
		this.now += nanos;
	}

	/** The player starts at the tick (in the player's ticks) at the current wall time. */
	public void start(double playerTick) {
		this.started = true;
		this.paused = false;
		this.anchorWall = this.now;
		this.anchorTick = playerTick;
	}

	public void pause() {
		if (this.started && !this.paused) {
			this.anchorTick = positionNow();
			this.anchorWall = this.now;
			this.paused = true;
		}
	}

	public void resume() {
		if (this.started && this.paused) {
			this.anchorWall = this.now;
			this.paused = false;
		}
	}

	/** The player jumps to the tick (a seek). */
	public void seek(double playerTick) {
		this.anchorTick = playerTick;
		this.anchorWall = this.now;
	}

	/** The player stopped: the clock says nothing any more. */
	public void stopClock() {
		this.started = false;
		this.paused = false;
	}

	public void setReady(boolean ready) {
		this.ready = ready;
	}

	public boolean isStarted() {
		return this.started;
	}

	/** The position of the player now, in the player's ticks. */
	public double positionNow() {
		return this.started ? tickAt(this.now) : Double.NaN;
	}

	// ----------------------------------------------------------------------------------------------- TempoClock

	@Override
	public long nowNanos() {
		return this.now;
	}

	@Override
	public double tickAt(long wallNanos) {
		if (!this.started) {
			return Double.NaN;
		}
		if (this.paused) {
			return this.anchorTick;
		}
		double anchorNanos = this.nanosAtTick.applyAsDouble(this.anchorTick - this.baseTick);
		double target = anchorNanos + (wallNanos - this.anchorWall);
		return Math.min(this.baseTick + this.lengthTicks, this.baseTick + ticksAt(target));
	}

	@Override
	public long wallNanosAt(double tick) {
		if (!this.started) {
			return Long.MIN_VALUE;
		}
		double anchorNanos = this.nanosAtTick.applyAsDouble(this.anchorTick - this.baseTick);
		double nanos = this.nanosAtTick.applyAsDouble(tick - this.baseTick);
		return this.anchorWall + Math.round(nanos - anchorNanos);
	}

	@Override
	public boolean isReady() {
		return this.ready && this.started;
	}

	@Override
	public boolean isStalled(long wallNanos) {
		return !this.started || this.paused;
	}

	/** The ticks from the first tick of the song at the nanoseconds (the inverse of the function, by bisection). */
	private double ticksAt(double nanos) {
		double low = 0.0;
		double high = this.lengthTicks;
		if (nanos <= this.nanosAtTick.applyAsDouble(low)) {
			return low;
		}
		if (nanos >= this.nanosAtTick.applyAsDouble(high)) {
			return high;
		}
		for (int i = 0; i < 60; i++) {
			double middle = (low + high) / 2.0;
			if (this.nanosAtTick.applyAsDouble(middle) < nanos) {
				low = middle;
			} else {
				high = middle;
			}
		}
		return (low + high) / 2.0;
	}
}
