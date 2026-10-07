package app.tuxguitar.app.tools.custom.practice.session;

import java.util.function.LongSupplier;

import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerException;
import app.tuxguitar.player.base.MidiSequencer;

/**
 * The tempo clock of the player (spec 002 T093): the position of the player's sequencer, polled, interpolated with the wall clock
 * ({@link TickInterpolator}). It never assumes a device: it reads what the player does, whatever the output.
 * <p>
 * The source is the position of the <b>sequencer</b> and not {@code MidiPlayer.getTickPosition()}: the latter is a copy refreshed by another 10 ms timer,
 * which adds a second staircase on top of the sequencer's own (research R6c).
 * <p>
 * Call {@link #poll()} often (the practice frames come every 5 ms); polling and reading are meant for the same thread.
 */
public final class PlayerTempoClock implements TempoClock {

	/** Ticks a second at 120 beats a minute (960 ticks a quarter note): the rate used until the player has shown its own. */
	public static final double NOMINAL_TICKS_PER_SECOND = 1920.0;

	private final LongSupplier wall;
	private final LongSupplier tick;
	private final TickInterpolator interpolator;

	public PlayerTempoClock(LongSupplier wall, LongSupplier tick, double nominalTicksPerSecond) {
		this.wall = wall;
		this.tick = tick;
		this.interpolator = new TickInterpolator(nominalTicksPerSecond);
	}

	/** The clock of the application's player. Nothing is read from the player until {@link #poll()}. */
	public static PlayerTempoClock of(final MidiPlayer player) {
		return new PlayerTempoClock(System::nanoTime, () -> {
			MidiSequencer sequencer = player.getSequencer();
			try {
				return (sequencer != null ? sequencer.getTickPosition() : -1L);
			} catch (MidiPlayerException exception) {
				// the sequencer cannot say where it is (closed, being replaced): there is nothing to read this time
				return -1L;
			}
		}, NOMINAL_TICKS_PER_SECOND);
	}

	/** Reads the player now. A position below 0 means there is no player to read. */
	public void poll() {
		long now = this.wall.getAsLong();
		long position = this.tick.getAsLong();
		if (position >= 0) {
			this.interpolator.observe(now, position);
		}
	}

	@Override
	public long nowNanos() {
		return this.wall.getAsLong();
	}

	@Override
	public double tickAt(long wallNanos) {
		return this.interpolator.tickAt(wallNanos);
	}

	@Override
	public long wallNanosAt(double tickPosition) {
		return this.interpolator.wallNanosAt(tickPosition);
	}

	@Override
	public boolean isReady() {
		return this.interpolator.isReady();
	}

	@Override
	public boolean isStalled(long wallNanos) {
		return this.interpolator.isStalled(wallNanos);
	}

	/** The rate the clock uses, in ticks a second. */
	public double ticksPerSecond() {
		return this.interpolator.ticksPerSecond();
	}
}
