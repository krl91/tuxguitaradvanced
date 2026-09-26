package app.tuxguitar.audio.engine.analysis;

/**
 * Raw result of one pitch estimation, before note interpretation. Immutable.
 */
public final class PitchEstimate {

	private final double f0Hz;
	private final double probability;
	private final boolean pitched;

	/**
	 * @param f0Hz estimated fundamental in Hz
	 * @param probability estimator confidence, 0–1
	 * @param pitched whether the estimator considers the window pitched
	 */
	public PitchEstimate(double f0Hz, double probability, boolean pitched) {
		this.f0Hz = f0Hz;
		this.probability = probability;
		this.pitched = pitched;
	}

	public double getF0Hz() {
		return this.f0Hz;
	}

	public double getProbability() {
		return this.probability;
	}

	public boolean isPitched() {
		return this.pitched;
	}
}
