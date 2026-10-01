package app.tuxguitar.audio.engine.frontend;

/**
 * A software gain on the input (spec 002, FR-060). Neutral (1.0) leaves samples untouched. The value
 * lives in memory only: it is reset when the input changes and at start, never saved (FR-063).
 */
public final class InputGain implements FrontEndStage {

	/** −6 dB: a slight reduction is allowed (provisional range, to be refined from the trials of S-B2). */
	public static final double MIN_MULTIPLIER = Math.pow(10.0, -6.0 / 20.0);
	/** +18 dB: a boost beyond this only amplifies noise on the validated setups (provisional). */
	public static final double MAX_MULTIPLIER = Math.pow(10.0, 18.0 / 20.0);
	public static final double NEUTRAL = 1.0;

	private volatile double multiplier = NEUTRAL;

	public double getMultiplier() {
		return this.multiplier;
	}

	/** Sets the multiplier, limited to the allowed range; returns the value actually set. */
	public double setMultiplier(double multiplier) {
		double bounded = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
		if( Double.isNaN(multiplier) ) {
			bounded = NEUTRAL;
		}
		this.multiplier = bounded;
		return bounded;
	}

	public void resetToNeutral() {
		this.multiplier = NEUTRAL;
	}

	public boolean isNeutral() {
		return this.multiplier == NEUTRAL;
	}

	/** The multiplier expressed in decibels. */
	public static double toDb(double multiplier) {
		return 20.0 * Math.log10(multiplier);
	}

	public static double fromDb(double db) {
		return Math.pow(10.0, db / 20.0);
	}

	@Override
	public void process(float[] hop, int length) {
		double gain = this.multiplier;
		if( gain == NEUTRAL ) {
			return;
		}
		for( int i = 0 ; i < length ; i ++ ) {
			hop[i] = (float) (hop[i] * gain);
		}
	}

	@Override
	public void reset() {
		// stateless: the gain value itself is reset by resetToNeutral when the input changes
	}

	@Override
	public int latencyHops() {
		return 0;
	}
}
