package app.tuxguitar.audio.engine.analysis;

import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.PitchDetectionResult;

/** Pitch-estimator adapter around the provisional FastYin MVP baseline. */
public final class TarsosFastYinEstimator implements PitchEstimator {

	public static final float DEFAULT_SAMPLE_RATE = 48000f;
	public static final int DEFAULT_WINDOW_SIZE = 2048;
	public static final int DEFAULT_HOP_SIZE = 256;

	private final int windowSize;
	private final int hopSize;
	private final FastYin detector;

	public TarsosFastYinEstimator() {
		this(DEFAULT_SAMPLE_RATE, DEFAULT_WINDOW_SIZE, DEFAULT_HOP_SIZE);
	}

	public TarsosFastYinEstimator(float sampleRate, int windowSize, int hopSize) {
		if (!Float.isFinite(sampleRate) || sampleRate <= 0f) {
			throw new IllegalArgumentException("sampleRate must be positive and finite");
		}
		if (windowSize < 2) {
			throw new IllegalArgumentException("windowSize must be at least 2");
		}
		if (hopSize <= 0 || hopSize > windowSize) {
			throw new IllegalArgumentException("hopSize must be between 1 and windowSize");
		}
		this.windowSize = windowSize;
		this.hopSize = hopSize;
		this.detector = new FastYin(sampleRate, windowSize);
	}

	@Override
	public PitchEstimate estimate(float[] window) {
		if (window == null || window.length != this.windowSize) {
			throw new IllegalArgumentException("window length must be " + this.windowSize);
		}

		PitchDetectionResult result = this.detector.getPitch(window);
		return new PitchEstimate(
				result.isPitched() ? result.getPitch() : Double.NaN,
				result.getProbability(),
				result.isPitched());
	}

	public int getWindowSize() {
		return this.windowSize;
	}

	/** Hop size is scheduling metadata consumed by the analysis service. */
	public int getHopSize() {
		return this.hopSize;
	}
}
