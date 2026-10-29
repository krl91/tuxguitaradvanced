package app.tuxguitar.app.tools.custom.practice.session;

/**
 * How the windows of an event are made from its target and its neighbours (spec 016): {@link TimingTolerance} for Arcade (and Follow before spec 016),
 * {@link PlayTimeWindow} for Follow. All times in nanoseconds.
 */
public interface WindowPolicy {

	/**
	 * @param targetNanos the target time of the event
	 * @param beatNanos the local beat in nanoseconds (0 for a broken tempo)
	 * @param gapBeforeNanos the distance to the previous event of another time, {@link Long#MAX_VALUE} when there is none
	 * @param gapAfterNanos the distance to the next event of another time, {@link Long#MAX_VALUE} when there is none
	 */
	TimingTolerance.Window windowFor(long targetNanos, long beatNanos, long gapBeforeNanos, long gapAfterNanos);
}
