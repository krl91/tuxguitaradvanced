package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * Where an event is in its life on the screen (spec 003 visual-design.md, "Chord verdict"): a pure function of the time left before its onset. The
 * onset is the written instant; nothing here moves it.
 */
public enum RenderState {
	/** Outside the look-ahead: nothing is drawn. */
	HIDDEN,
	/** Far: a bar and a compact cartouche, or a small marker. */
	DISTANT,
	/** Near: the cartouche is held above its minimum size and the ghost appears on the neck. */
	READABLE,
	/** Close: the target frets are in view and the event approaches the hit line. */
	ARMED,
	/** The event is on the hit line. */
	IMPACT,
	/** Just judged: the cartouche stays briefly while the verdict appears. */
	FEEDBACK,
	/** Gone: it fades without blocking later events. */
	EXPIRED;

	/** The share of the look-ahead from which an event is {@link #READABLE}. */
	public static final double READABLE_FROM = 0.35;
	/** The share of the look-ahead from which an event is {@link #ARMED}. */
	public static final double ARMED_FROM = 0.70;
	/** How long after its onset an event is still {@link #IMPACT}. */
	public static final long IMPACT_NANOS = 80_000_000L;

	/** The progress of an event on its way: 0 at the horizon, exactly 1 at its onset and after. */
	public static double progress(long onsetNanos, long nowNanos, long lookAheadNanos) {
		double progress = 1.0 - (double) (onsetNanos - nowNanos) / (double) lookAheadNanos;
		return Math.max(0.0, Math.min(1.0, progress));
	}

	public static RenderState of(long onsetNanos, long nowNanos, long lookAheadNanos, long holdAfterNanos) {
		long until = onsetNanos - nowNanos;
		if (until > lookAheadNanos) {
			return HIDDEN;
		}
		if (until > 0) {
			double progress = progress(onsetNanos, nowNanos, lookAheadNanos);
			if (progress < READABLE_FROM) {
				return DISTANT;
			}
			return progress < ARMED_FROM ? READABLE : ARMED;
		}
		long since = -until;
		if (since <= IMPACT_NANOS) {
			return IMPACT;
		}
		return since <= holdAfterNanos ? FEEDBACK : EXPIRED;
	}
}
