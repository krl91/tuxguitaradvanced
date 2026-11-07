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

	/** The parameters of the analyser that made this frame. */
	public PolyphonicAnalyzer.Parameters getParameters() {
		return this.parameters;
	}

	/** The width of one bin of the peaks, in Hz. */
	public double getBinHz() {
		return this.binHz;
	}

	/** The lowest pitch of the band levels. */
	public int getMinBandMidi() {
		return this.minBandMidi;
	}

	/** The number of band levels. */
	public int getBandCount() {
		return this.bandDb.length;
	}

	/** The lowest pitch of the saliences. */
	public int getMinSalienceMidi() {
		return this.minSalienceMidi;
	}

	/** The number of saliences. */
	public int getSalienceCount() {
		return this.salience.length;
	}

	/** One bin of the floor-removed magnitude spectrum. */
	public float getPeak(int k) {
		return this.peaks[k];
	}

	/**
	 * A frame made elsewhere than by a {@link PolyphonicAnalyzer} of this process (the native engine, spec 023), with the default parameters; the
	 * arrays are kept, not copied.
	 */
	public static SpectrumFrame of(double binHz, int minBandMidi, float[] bandDb, int minSalienceMidi, float[] salience, List<PitchCandidate> candidates,
			float[] peaks) {
		return of(PolyphonicAnalyzer.Parameters.defaults(), binHz, minBandMidi, bandDb, minSalienceMidi, salience, candidates, peaks);
	}

	/** As the other, with the parameters the frame was analysed with (spec 026). */
	public static SpectrumFrame of(PolyphonicAnalyzer.Parameters parameters, double binHz, int minBandMidi, float[] bandDb, int minSalienceMidi,
			float[] salience, List<PitchCandidate> candidates, float[] peaks) {
		return new SpectrumFrame(parameters, binHz, minBandMidi, bandDb, minSalienceMidi, salience, candidates, peaks);
	}

	/** Copies the floor-removed magnitude spectrum into {@code target} (length ≥ {@link #getPeakLength()}). */
	void copyPeaks(double[] target) {
		for (int k = 0; k < this.peaks.length; k++) {
			target[k] = this.peaks[k];
		}
	}

	public int getPeakLength() {
		return this.peaks.length;
	}
}
