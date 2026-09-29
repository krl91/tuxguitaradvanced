package app.tuxguitar.audio.engine.analysis;

import java.util.Collections;
import java.util.List;

/**
 * Polyphonic view of one analysis hop (research R21): per-semitone band levels, harmonic-sum
 * salience, the ranked pitch candidates and the floor-removed spectrum they come from (for
 * score-conditioned questions through {@link ChordEvidence}). Immutable.
 */
public final class SpectrumFrame {

	private final PolyphonicAnalyzer.Parameters parameters;
	private final double binHz;
	private final int minBandMidi;
	private final float[] bandDb;
	private final int minSalienceMidi;
	private final float[] salience;
	private final List<PitchCandidate> candidates;
	private final float[] peaks;

	SpectrumFrame(PolyphonicAnalyzer.Parameters parameters, double binHz, int minBandMidi, float[] bandDb,
			int minSalienceMidi, float[] salience, List<PitchCandidate> candidates, float[] peaks) {
		this.parameters = parameters;
		this.binHz = binHz;
		this.minBandMidi = minBandMidi;
		this.bandDb = bandDb;
		this.minSalienceMidi = minSalienceMidi;
		this.salience = salience;
		this.candidates = Collections.unmodifiableList(candidates);
		this.peaks = peaks;
	}

	/**
	 * Level in dBFS of the strongest spectral peak within ±50 cents of {@code midi}'s centre
	 * (a full-scale sine is 0 dB), or {@link Double#NEGATIVE_INFINITY} outside the band range.
	 */
	public double getBandDb(int midi) {
		int index = midi - this.minBandMidi;
		return (index >= 0 && index < this.bandDb.length ? this.bandDb[index] : Double.NEGATIVE_INFINITY);
	}

	/** Harmonic-sum salience of {@code midi} before any suppression; 0 outside the candidate range. */
	public double getSalience(int midi) {
		int index = midi - this.minSalienceMidi;
		return (index >= 0 && index < this.salience.length ? this.salience[index] : 0.0);
	}

	/** Open-ended candidates, strongest first; empty when nothing rises above the level floor. Indicative only. */
	public List<PitchCandidate> getCandidates() {
		return this.candidates;
	}

	PolyphonicAnalyzer.Parameters getParameters() {
		return this.parameters;
	}

	double getBinHz() {
		return this.binHz;
	}

	/** Copies the floor-removed magnitude spectrum into {@code target} (length ≥ {@link #getPeakLength()}). */
	void copyPeaks(double[] target) {
		for (int k = 0; k < this.peaks.length; k++) {
			target[k] = this.peaks[k];
		}
	}

	int getPeakLength() {
		return this.peaks.length;
	}
}
