package app.tuxguitar.audio.engine.analysis;

import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.audio.engine.technique.TechniqueFeatures;

/**
 * Analysis result for one hop of the selected input. Immutable.
 */
public final class AnalysisFrame {

	public enum LevelState {
		TOO_LOW,
		SUITABLE,
		TOO_HIGH,
		CLIPPING
	}

	private final long timestampNanos;
	private final double rmsDbfs;
	private final double peakDbfs;
	private final double clippedRatio;
	private final LevelState levelState;
	private final double f0Hz;
	private final double confidence;
	private final boolean onset;
	private final SpectrumFrame spectrum;
	private final TargetEvidence targetEvidence;
	private final TechniqueFeatures techniqueFeatures;

	/**
	 * @param timestampNanos capture time of the hop end
	 * @param rmsDbfs RMS level in dBFS
	 * @param peakDbfs peak level in dBFS
	 * @param clippedRatio share of clipped samples
	 * @param levelState live level classification
	 * @param f0Hz estimated fundamental in Hz, or NaN when none
	 * @param confidence 0–1 from the estimator
	 * @param onset true when a new articulation is detected in this hop
	 */
	public AnalysisFrame(long timestampNanos, double rmsDbfs, double peakDbfs, double clippedRatio, LevelState levelState, double f0Hz, double confidence, boolean onset) {
		this(timestampNanos, rmsDbfs, peakDbfs, clippedRatio, levelState, f0Hz, confidence, onset, null);
	}

	/** @param spectrum the polyphonic view of this hop, or null when the spectrum is not enabled */
	public AnalysisFrame(long timestampNanos, double rmsDbfs, double peakDbfs, double clippedRatio, LevelState levelState, double f0Hz, double confidence, boolean onset, SpectrumFrame spectrum) {
		this(timestampNanos, rmsDbfs, peakDbfs, clippedRatio, levelState, f0Hz, confidence, onset, spectrum, null, null);
	}

	/**
	 * @param targetEvidence the expected-pitch verifier's view of this hop, or null when not enabled (spec 002)
	 * @param techniqueFeatures the technique measurements of this hop, or null when not enabled (spec 002)
	 */
	public AnalysisFrame(long timestampNanos, double rmsDbfs, double peakDbfs, double clippedRatio, LevelState levelState, double f0Hz, double confidence, boolean onset, SpectrumFrame spectrum,
			TargetEvidence targetEvidence, TechniqueFeatures techniqueFeatures) {
		this.timestampNanos = timestampNanos;
		this.rmsDbfs = rmsDbfs;
		this.peakDbfs = peakDbfs;
		this.clippedRatio = clippedRatio;
		this.levelState = levelState;
		this.f0Hz = f0Hz;
		this.confidence = confidence;
		this.onset = onset;
		this.spectrum = spectrum;
		this.targetEvidence = targetEvidence;
		this.techniqueFeatures = techniqueFeatures;
	}

	public long getTimestampNanos() {
		return this.timestampNanos;
	}

	public double getRmsDbfs() {
		return this.rmsDbfs;
	}

	public double getPeakDbfs() {
		return this.peakDbfs;
	}

	public double getClippedRatio() {
		return this.clippedRatio;
	}

	public LevelState getLevelState() {
		return this.levelState;
	}

	/** NaN when no fundamental was estimated. */
	public double getF0Hz() {
		return this.f0Hz;
	}

	public double getConfidence() {
		return this.confidence;
	}

	public boolean isOnset() {
		return this.onset;
	}

	/** Polyphonic view of this hop (plan stage D); null unless the spectrum is enabled. */
	public SpectrumFrame getSpectrum() {
		return this.spectrum;
	}

	/** Expected-pitch evidence of this hop; null unless enabled (spec 002). */
	public TargetEvidence getTargetEvidence() {
		return this.targetEvidence;
	}

	/** Technique measurements of this hop; null unless enabled (spec 002). */
	public TechniqueFeatures getTechniqueFeatures() {
		return this.techniqueFeatures;
	}
}
