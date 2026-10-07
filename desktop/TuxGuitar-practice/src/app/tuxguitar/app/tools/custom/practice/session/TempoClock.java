package app.tuxguitar.app.tools.custom.practice.session;

/**
 * The clock of the tempo-following mode (spec 002 T093): the position of the score on the wall clock. The clock is the player's, because the
 * backing the learner hears comes from the player's events (research R6c, validated by the owner on 2026-10-07).
 */
public interface TempoClock {

	/** The wall clock, on the scale of {@code System.nanoTime()} (the scale of {@code CaptureClock.wallNanosOf}). */
	long nowNanos();

	/** The position of the player in ticks (fractional) at the wall time; NaN while the clock cannot say (no player, not started). */
	double tickAt(long wallNanos);

	/** The wall time at which the player reaches the tick; {@link Long#MIN_VALUE} when the clock cannot say. */
	long wallNanosAt(double tick);

	/** True when the player is not advancing (stopped or paused). */
	boolean isStalled(long wallNanos);
}
