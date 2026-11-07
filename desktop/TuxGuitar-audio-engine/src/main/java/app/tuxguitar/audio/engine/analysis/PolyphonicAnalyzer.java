package app.tuxguitar.audio.engine.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Deterministic polyphonic spectrum analysis (research R21, plan stage D): a Hann-windowed FFT
 * over the latest {@link Parameters#fftSize} samples, per-semitone band levels, harmonic-sum
 * salience and ranked pitch candidates.
 *
 * <p>Salience is measured on a floor-removed spectrum (content above twice the local mean) with
 * Klapuri's (2006) harmonic weights {@code (f + 27) / (h·f + 320)}. Candidates come from
 * iterative estimation: the most salient pitch is picked — or its lowest subharmonic whose own
 * partials are salient enough, since a pitch lying on a lower string's harmonic comb also scores
 * well — then its harmonics are removed under a spectral-smoothness limit (a harmonic is removed
 * only down to the mean of its neighbours, so a partial shared with another note leaves a
 * residual), and the next pitch is picked. Open-ended candidates are indicative only;
 * score-conditioned questions go through {@link ChordEvidence}. Every value is a provisional
 * engineering parameter, not a learner setting.</p>
 *
 * <p>Single-threaded: owned by the analysis thread. No allocation in the per-hop path except the
 * returned {@link SpectrumFrame}.</p>
 */
public final class PolyphonicAnalyzer {

	public static final class Parameters {
		public final int fftSize;
		public final int minBandMidi;
		public final int maxBandMidi;
		public final int minCandidateMidi;
		public final int maxCandidateMidi;
		public final int harmonics;
		public final double maxHarmonicHz;
		public final double harmonicToleranceCents;
		public final int maxCandidates;
		public final double minRelativeSalience;
		public final double floorDb;
		public final double subharmonicRatio;
		/** Half width of the local floor window: below the lowest partial spacing (A#1 ≈ 58 Hz). */
		public final double floorHalfWidthHz;
		/** Content must exceed this multiple of the local mean to count as a partial (+6 dB). */
		public final double floorFactor;
		/** The chord evidence: a partial is present from this share of the strongest ({@link ChordEvidence#PARTIAL_PRESENCE_RATIO} by default). */
		public final double partialPresenceRatio;
		/** The chord evidence: the chord a whole tone away must explain the partials this many times better ({@link ChordEvidence#NEIGHBOUR_MARGIN}). */
		public final double neighbourMargin;

		public Parameters(int fftSize, int minBandMidi, int maxBandMidi, int minCandidateMidi, int maxCandidateMidi,
				int harmonics, double maxHarmonicHz, double harmonicToleranceCents, int maxCandidates,
				double minRelativeSalience, double floorDb, double subharmonicRatio) {
			this(fftSize, minBandMidi, maxBandMidi, minCandidateMidi, maxCandidateMidi, harmonics, maxHarmonicHz, harmonicToleranceCents, maxCandidates,
					minRelativeSalience, floorDb, subharmonicRatio, DEFAULT_FLOOR_HALF_WIDTH_HZ, DEFAULT_FLOOR_FACTOR);
		}

		public Parameters(int fftSize, int minBandMidi, int maxBandMidi, int minCandidateMidi, int maxCandidateMidi,
				int harmonics, double maxHarmonicHz, double harmonicToleranceCents, int maxCandidates,
				double minRelativeSalience, double floorDb, double subharmonicRatio, double floorHalfWidthHz, double floorFactor) {
			this(fftSize, minBandMidi, maxBandMidi, minCandidateMidi, maxCandidateMidi, harmonics, maxHarmonicHz, harmonicToleranceCents, maxCandidates,
					minRelativeSalience, floorDb, subharmonicRatio, floorHalfWidthHz, floorFactor, ChordEvidence.PARTIAL_PRESENCE_RATIO,
					ChordEvidence.NEIGHBOUR_MARGIN);
		}

		/**
		 * With the local floor's half width and factor and the chord evidence's two values (spec 026, {@code polyphonic.floor-width},
		 * {@code polyphonic.floor-factor}, {@code chord-evidence.partial-presence}, {@code chord-evidence.neighbour-margin}).
		 */
		public Parameters(int fftSize, int minBandMidi, int maxBandMidi, int minCandidateMidi, int maxCandidateMidi,
				int harmonics, double maxHarmonicHz, double harmonicToleranceCents, int maxCandidates,
				double minRelativeSalience, double floorDb, double subharmonicRatio, double floorHalfWidthHz, double floorFactor,
				double partialPresenceRatio, double neighbourMargin) {
			if (partialPresenceRatio <= 0 || neighbourMargin <= 0 || floorHalfWidthHz <= 0 || floorFactor <= 0 || minBandMidi > minCandidateMidi || maxCandidateMidi > maxBandMidi || minCandidateMidi > maxCandidateMidi
					|| minBandMidi < 0 || maxBandMidi > 127 || harmonics < 1 || maxCandidates < 1
					|| harmonicToleranceCents <= 0 || harmonicToleranceCents > 50 || maxHarmonicHz <= 0) {
				throw new IllegalArgumentException("invalid polyphonic analysis parameters");
			}
			this.fftSize = fftSize;
			this.minBandMidi = minBandMidi;
			this.maxBandMidi = maxBandMidi;
			this.minCandidateMidi = minCandidateMidi;
			this.maxCandidateMidi = maxCandidateMidi;
			this.harmonics = harmonics;
			this.maxHarmonicHz = maxHarmonicHz;
			this.harmonicToleranceCents = harmonicToleranceCents;
			this.maxCandidates = maxCandidates;
			this.minRelativeSalience = minRelativeSalience;
			this.floorDb = floorDb;
			this.subharmonicRatio = subharmonicRatio;
			this.floorHalfWidthHz = floorHalfWidthHz;
			this.floorFactor = floorFactor;
			this.partialPresenceRatio = partialPresenceRatio;
			this.neighbourMargin = neighbourMargin;
		}

		/**
		 * Provisional defaults: 8192 samples (≈ 171 ms at 48 kHz); bands C1–E8; candidates A1–E6
		 * (below A#1, the lowest open string of TuxGuitar's built-in 6-string presets, up to fret
		 * 24 of the high E string); up to 12 partials below 5 kHz within ±25 cents (wider windows
		 * catch other notes' partials high up, and string inharmonicity makes high partials
		 * unreliable); up to 6 candidates down to 10 % of the strongest; floor −70 dBFS; a pick is
		 * replaced by its lowest subharmonic (1/2 … 1/8) whose own partials reach 30 % of it.
		 */
		public static Parameters defaults() {
			return new Parameters(8192, 24, 112, 33, 88, 12, 5000.0, 25.0, 6, 0.10, -70.0, 0.30);
		}

		/** The defaults with the values of the settings (spec 026, sections {@code POLYPHONIC_1} and {@code POLYPHONIC_2}); the FFT size and the bands stay. */
		public static Parameters of(RecognitionSettings settings) {
			Parameters d = defaults();
			return new Parameters(d.fftSize, d.minBandMidi, d.maxBandMidi, (int) settings.get(RecognitionParameters.POLYPHONIC_CANDIDATE_LOW),
					(int) settings.get(RecognitionParameters.POLYPHONIC_CANDIDATE_HIGH), (int) settings.get(RecognitionParameters.POLYPHONIC_HARMONICS),
					settings.get(RecognitionParameters.POLYPHONIC_MAX_HARMONIC), settings.get(RecognitionParameters.POLYPHONIC_TOLERANCE),
					(int) settings.get(RecognitionParameters.POLYPHONIC_CANDIDATES), settings.get(RecognitionParameters.POLYPHONIC_MIN_RELATIVE),
					settings.get(RecognitionParameters.POLYPHONIC_FLOOR), settings.get(RecognitionParameters.POLYPHONIC_SUBHARMONIC),
					settings.get(RecognitionParameters.POLYPHONIC_FLOOR_WIDTH), settings.get(RecognitionParameters.POLYPHONIC_FLOOR_FACTOR),
					settings.get(RecognitionParameters.CHORD_EVIDENCE_PARTIAL_PRESENCE), settings.get(RecognitionParameters.CHORD_EVIDENCE_NEIGHBOUR_MARGIN));
		}
	}

	/** The former half width of the local floor window, the default of {@link Parameters#floorHalfWidthHz}. */
	static final double DEFAULT_FLOOR_HALF_WIDTH_HZ = 25.0;
	/** The former floor factor, the default of {@link Parameters#floorFactor}. */
	static final double DEFAULT_FLOOR_FACTOR = 2.0;

	private final Parameters parameters;
	private final RealFft fft;
	private final double[] hann;
	private final float[] history;
	private final double[] input;
	private final double[] spectrum;
	private final double[] peaks;
	private final double[] working;
	private final double[] prefix;
	private final boolean[] picked;
	private final boolean[] excluded;
	private final double bandRatio;
	private HarmonicComb comb;
	private int peakLength;
	private int historyPosition;
	private double binHz;

	public PolyphonicAnalyzer(Parameters parameters, int sampleRate) {
		this.parameters = parameters;
		this.fft = new RealFft(parameters.fftSize);
		this.hann = new double[parameters.fftSize];
		for (int i = 0; i < this.hann.length; i++) {
			this.hann[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (this.hann.length - 1));
		}
		this.history = new float[parameters.fftSize];
		this.input = new double[parameters.fftSize];
		this.spectrum = new double[parameters.fftSize / 2 + 1];
		this.peaks = new double[parameters.fftSize / 2 + 1];
		this.working = new double[parameters.fftSize / 2 + 1];
		this.prefix = new double[parameters.fftSize / 2 + 2];
		this.picked = new boolean[parameters.maxCandidateMidi - parameters.minCandidateMidi + 1];
		this.excluded = new boolean[128];
		this.bandRatio = Math.pow(2.0, 50.0 / 1200.0);
		setSampleRate(sampleRate);
	}

	public Parameters getParameters() {
		return this.parameters;
	}

	/** Clears the history and applies a new sample rate. */
	public void setSampleRate(int sampleRate) {
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("invalid sample rate");
		}
		this.binHz = (double) sampleRate / this.parameters.fftSize;
		this.comb = new HarmonicComb(this.parameters, this.binHz);
		// the frame keeps the bins that partials (and their tolerance windows) can reach
		this.peakLength = Math.min(this.spectrum.length, (int) Math.ceil(this.parameters.maxHarmonicHz * 1.1 / this.binHz) + 3);
		reset();
	}

	public void reset() {
		Arrays.fill(this.history, 0f);
		this.historyPosition = 0;
	}

	/** Appends the latest samples (oldest first). */
	public void push(float[] samples, int offset, int length) {
		for (int i = 0; i < length; i++) {
			this.history[this.historyPosition] = samples[offset + i];
			this.historyPosition = (this.historyPosition + 1) % this.history.length;
		}
	}

	/** Analyses the latest {@code fftSize} samples pushed so far (zeros before the first ones). */
	public SpectrumFrame analyze() {
		int size = this.parameters.fftSize;
		for (int i = 0; i < size; i++) {
			this.input[i] = this.history[(this.historyPosition + i) % size] * this.hann[i];
		}
		// a full-scale sine peaks at 1.0: Hann coherent gain is 1/2
		this.fft.magnitudes(this.input, this.spectrum, 4.0 / size);

		float[] bandDb = new float[this.parameters.maxBandMidi - this.parameters.minBandMidi + 1];
		for (int midi = this.parameters.minBandMidi; midi <= this.parameters.maxBandMidi; midi++) {
			bandDb[midi - this.parameters.minBandMidi] = (float) toDb(this.comb.peakAround(this.spectrum, this.spectrum.length, HarmonicComb.frequency(midi), this.bandRatio));
		}

		removeFloor(this.spectrum, this.peaks);
		float[] salience = new float[this.picked.length];
		for (int midi = this.parameters.minCandidateMidi; midi <= this.parameters.maxCandidateMidi; midi++) {
			salience[midi - this.parameters.minCandidateMidi] = (float) this.comb.salience(this.peaks, this.peakLength, midi, 0);
		}

		System.arraycopy(this.peaks, 0, this.working, 0, this.peakLength);
		Arrays.fill(this.picked, false);
		Arrays.fill(this.excluded, false);
		List<PitchCandidate> candidates = new ArrayList<PitchCandidate>();
		double floor = Math.pow(10.0, this.parameters.floorDb / 20.0);
		double strongest = 0.0;
		while (candidates.size() < this.parameters.maxCandidates) {
			int best = -1;
			double bestSalience = 0.0;
			for (int midi = this.parameters.minCandidateMidi; midi <= this.parameters.maxCandidateMidi; midi++) {
				if (!this.picked[midi - this.parameters.minCandidateMidi]) {
					double value = this.comb.salience(this.working, this.peakLength, midi, 0);
					if (value > bestSalience) {
						bestSalience = value;
						best = midi;
					}
				}
			}
			if (best < 0 || bestSalience < floor || (strongest > 0.0 && bestSalience < strongest * this.parameters.minRelativeSalience)) {
				break;
			}
			best = this.comb.lowestExplainingSubharmonic(this.working, this.peakLength, best, bestSalience,
					this.parameters.subharmonicRatio, this.excluded, this.parameters.minCandidateMidi);
			bestSalience = Math.max(bestSalience, this.comb.salience(this.working, this.peakLength, best, 0));
			if (strongest == 0.0) {
				strongest = bestSalience;
			}
			double level = this.comb.peakAround(this.spectrum, this.spectrum.length, HarmonicComb.frequency(best), this.comb.toleranceRatio());
			candidates.add(new PitchCandidate(best, bestSalience, bestSalience / strongest, toDb(level)));
			this.picked[best - this.parameters.minCandidateMidi] = true;
			this.excluded[best] = true;
			this.comb.suppress(this.working, this.peakLength, best);
		}
		float[] peaks = new float[this.peakLength];
		for (int k = 0; k < this.peakLength; k++) {
			peaks[k] = (float) this.peaks[k];
		}
		return new SpectrumFrame(this.parameters, this.binHz, this.parameters.minBandMidi, bandDb,
				this.parameters.minCandidateMidi, salience, candidates, peaks);
	}

	/**
	 * Keeps only what rises above {@link Parameters#floorFactor} times the local mean (over
	 * ±{@link Parameters#floorHalfWidthHz}), so that summing partials does not accumulate the broadband
	 * floor between them.
	 */
	private void removeFloor(double[] magnitudes, double[] result) {
		this.prefix[0] = 0.0;
		for (int k = 0; k < magnitudes.length; k++) {
			this.prefix[k + 1] = this.prefix[k] + magnitudes[k];
		}
		int halfWidth = Math.max(2, (int) Math.round(this.parameters.floorHalfWidthHz / this.binHz));
		for (int k = 0; k < magnitudes.length; k++) {
			int low = Math.max(0, k - halfWidth);
			int high = Math.min(magnitudes.length - 1, k + halfWidth);
			double mean = (this.prefix[high + 1] - this.prefix[low]) / (high - low + 1);
			result[k] = Math.max(0.0, magnitudes[k] - this.parameters.floorFactor * mean);
		}
	}

	static double toDb(double amplitude) {
		return (amplitude > 0.0 ? 20.0 * Math.log10(amplitude) : Double.NEGATIVE_INFINITY);
	}
}
