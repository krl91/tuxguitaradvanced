package app.tuxguitar.audio.engine.analysis;

/** Computes level metrics and applies caller-provided provisional thresholds. */
public final class LevelAnalyzer {

	private final double tooLowRmsDbfs;
	private final double tooHighRmsDbfs;
	private final double clippedSampleLevel;
	private final double clippingRatioThreshold;

	public LevelAnalyzer(double tooLowRmsDbfs, double tooHighRmsDbfs, double clippedSampleLevel, double clippingRatioThreshold) {
		if (!Double.isFinite(tooLowRmsDbfs) || !Double.isFinite(tooHighRmsDbfs)
				|| tooLowRmsDbfs >= tooHighRmsDbfs || tooHighRmsDbfs > 0.0) {
			throw new IllegalArgumentException("require finite tooLowRmsDbfs < tooHighRmsDbfs <= 0");
		}
		if (!Double.isFinite(clippedSampleLevel) || clippedSampleLevel <= 0.0 || clippedSampleLevel > 1.0) {
			throw new IllegalArgumentException("clippedSampleLevel must be in (0, 1]");
		}
		if (!Double.isFinite(clippingRatioThreshold) || clippingRatioThreshold <= 0.0 || clippingRatioThreshold > 1.0) {
			throw new IllegalArgumentException("clippingRatioThreshold must be in (0, 1]");
		}
		this.tooLowRmsDbfs = tooLowRmsDbfs;
		this.tooHighRmsDbfs = tooHighRmsDbfs;
		this.clippedSampleLevel = clippedSampleLevel;
		this.clippingRatioThreshold = clippingRatioThreshold;
	}

	/** Below this RMS level a frame is TOO_LOW (the "minimum usable level" setting). */
	public double getTooLowRmsDbfs() {
		return this.tooLowRmsDbfs;
	}

	/** Above this RMS level a frame is TOO_HIGH (the "high level warning" setting). */
	public double getTooHighRmsDbfs() {
		return this.tooHighRmsDbfs;
	}

	public LevelMeasurement analyze(float[] samples) {
		return analyze(samples, 0, samples.length);
	}

	public LevelMeasurement analyze(float[] samples, int offset, int length) {
		if (samples == null || offset < 0 || length <= 0 || offset > samples.length - length) {
			throw new IllegalArgumentException("a non-empty valid sample range is required");
		}
		double sumSquares = 0.0;
		double peak = 0.0;
		int clipped = 0;
		int exactZeros = 0;
		for (int i = offset; i < offset + length; i++) {
			double absolute = Math.abs(samples[i]);
			sumSquares += absolute * absolute;
			peak = Math.max(peak, absolute);
			if (absolute >= this.clippedSampleLevel) {
				clipped++;
			}
			if (samples[i] == 0f) {
				exactZeros++;
			}
		}

		double rms = Math.sqrt(sumSquares / length);
		double rmsDbfs = toDbfs(rms);
		double peakDbfs = toDbfs(peak);
		double clippedRatio = clipped / (double) length;
		double exactZeroRatio = exactZeros / (double) length;
		AnalysisFrame.LevelState state;
		if (clippedRatio >= this.clippingRatioThreshold) {
			state = AnalysisFrame.LevelState.CLIPPING;
		} else if (rmsDbfs < this.tooLowRmsDbfs) {
			state = AnalysisFrame.LevelState.TOO_LOW;
		} else if (rmsDbfs > this.tooHighRmsDbfs) {
			state = AnalysisFrame.LevelState.TOO_HIGH;
		} else {
			state = AnalysisFrame.LevelState.SUITABLE;
		}
		return new LevelMeasurement(rmsDbfs, peakDbfs, clippedRatio, exactZeroRatio, state);
	}

	private static double toDbfs(double linear) {
		return linear == 0.0 ? Double.NEGATIVE_INFINITY : 20.0 * Math.log10(linear);
	}
}
