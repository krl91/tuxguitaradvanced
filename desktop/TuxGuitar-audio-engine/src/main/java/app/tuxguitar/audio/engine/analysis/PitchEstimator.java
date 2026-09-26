package app.tuxguitar.audio.engine.analysis;

/**
 * Estimates the fundamental of one analysis window. Implementations wrap a concrete
 * detector (for the MVP, TarsosDSP FastYin) and must not expose its types.
 */
public interface PitchEstimator {

	/**
	 * @param window mono samples in [-1, 1]; its length is the window size the implementation
	 *        was configured with. The array is not retained.
	 */
	PitchEstimate estimate(float[] window);
}
