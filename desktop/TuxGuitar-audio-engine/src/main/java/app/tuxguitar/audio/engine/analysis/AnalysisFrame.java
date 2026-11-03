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

	/** Value of {@link #getCaptureSampleEnd()} for a frame that does not come from the live capture. */
	public static final long NO_CAPTURE_SAMPLE = -1L;

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
	private final AttackEvidence attackEvidence;
	private final long captureSampleEnd;
	private final ChordEvidence chordEvidence;

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
		this(timestampNanos, rmsDbfs, peakDbfs, clippedRatio, levelState, f0Hz, confidence, onset, spectrum, targetEvidence, techniqueFeatures, null);
	}

	/** @param attackEvidence what the hop added to the sound, explained by pitches, or null when not enabled (spec 004) */
	public AnalysisFrame(long timestampNanos, double rmsDbfs, double peakDbfs, double clippedRatio, LevelState levelState, double f0Hz, double confidence, boolean onset, SpectrumFrame spectrum,
			TargetEvidence targetEvidence, TechniqueFeatures techniqueFeatures, AttackEvidence attackEvidence) {
		this(timestampNanos, rmsDbfs, peakDbfs, clippedRatio, levelState, f0Hz, confidence, onset, spectrum, targetEvidence, techniqueFeatures, attackEvidence, NO_CAPTURE_SAMPLE);
	}

	/** @param captureSampleEnd the index in the capture stream of the last sample of this hop (see {@code CaptureClock}), or {@link #NO_CAPTURE_SAMPLE} */
	public AnalysisFrame(long timestampNanos, double rmsDbfs, double peakDbfs, double clippedRatio, LevelState levelState, double f0Hz, double confidence, boolean onset, SpectrumFrame spectrum,
			TargetEvidence targetEvidence, TechniqueFeatures techniqueFeatures, AttackEvidence attackEvidence, long captureSampleEnd) {
		this(timestampNanos, rmsDbfs, peakDbfs, clippedRatio, levelState, f0Hz, confidence, onset, spectrum, targetEvidence, techniqueFeatures, attackEvidence,
				captureSampleEnd, null);
	}

	/** @param chordEvidence the evidence for the expected chord computed with this hop's spectrum, or null when none was asked (spec 023) */
	public AnalysisFrame(long timestampNanos, double rmsDbfs, double peakDbfs, double clippedRatio, LevelState levelState, double f0Hz, double confidence, boolean onset, SpectrumFrame spectrum,
			TargetEvidence targetEvidence, TechniqueFeatures techniqueFeatures, AttackEvidence attackEvidence, long captureSampleEnd, ChordEvidence chordEvidence) {
		this.chordEvidence = chordEvidence;
		this.captureSampleEnd = captureSampleEnd;
		this.attackEvidence = attackEvidence;
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

	/** What this hop added to the sound, explained by pitches; null unless attack candidates are set (spec 004). */
	/** The index in the capture stream of the last sample of this hop, to be dated by the capture clock; {@link #NO_CAPTURE_SAMPLE} when unknown (offline analysis, tests). */
	public long getCaptureSampleEnd() {
		return this.captureSampleEnd;
	}

	/** The evidence for the expected chord in this hop's spectrum; null unless an engine computed it (the native engine while a chord is expected, spec 023). */
	public ChordEvidence getChordEvidence() {
		return this.chordEvidence;
	}

	public AttackEvidence getAttackEvidence() {
		return this.attackEvidence;
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
