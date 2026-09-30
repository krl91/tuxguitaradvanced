package app.tuxguitar.audio.engine.analysis;

/**
 * Harmonic-comb measurements on a floor-removed magnitude spectrum, shared by the candidate
 * search ({@link PolyphonicAnalyzer}) and score-conditioned evidence ({@link ChordEvidence}) so
 * both use the same rules. Stateless apart from its preallocated scratch array; not thread-safe.
 */
final class HarmonicComb {

	private static final double[] MIDI_FREQUENCIES = new double[128];
	static {
		for (int midi = 0; midi < MIDI_FREQUENCIES.length; midi++) {
			MIDI_FREQUENCIES[midi] = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
		}
	}

	private final PolyphonicAnalyzer.Parameters parameters;
	private final double binHz;
	private final double toleranceRatio;
	private final double[] harmonicAmplitudes;

	HarmonicComb(PolyphonicAnalyzer.Parameters parameters, double binHz) {
		this.parameters = parameters;
		this.binHz = binHz;
		this.toleranceRatio = Math.pow(2.0, parameters.harmonicToleranceCents / 1200.0);
		this.harmonicAmplitudes = new double[parameters.harmonics + 2];
	}

	static double frequency(int midi) {
		return MIDI_FREQUENCIES[midi];
	}

	double toleranceRatio() {
		return this.toleranceRatio;
	}

	/** Harmonic-sum salience (Klapuri weights), skipping partials that are multiples of {@code skipMultiplesOf} (0: none). */
	double salience(double[] magnitudes, int length, int midi, int skipMultiplesOf) {
		double f0 = frequency(midi);
		double sum = 0.0;
		for (int h = 1; h <= this.parameters.harmonics; h++) {
			double f = f0 * h;
			if (f > this.parameters.maxHarmonicHz || f >= this.binHz * (length - 2)) {
				break;
			}
			if (skipMultiplesOf > 0 && h % skipMultiplesOf == 0) {
				continue;
			}
			sum += weight(f0, h) * peakAround(magnitudes, length, f, this.toleranceRatio);
		}
		return sum;
	}

	/**
	 * Weighted mean amplitude of the pitch's partials (below the partial limit); when
	 * {@code others} is not null, only the partials that no other pitch's partial shares within
	 * the tolerance ("unique partials"). Returns {mean, partial count}.
	 */
	double[] meanPartialAmplitude(double[] magnitudes, int length, int midi, int[] others) {
		double f0 = frequency(midi);
		double sum = 0.0;
		double weights = 0.0;
		int count = 0;
		for (int h = 1; h <= this.parameters.harmonics; h++) {
			double f = f0 * h;
			if (f > this.parameters.maxHarmonicHz || f >= this.binHz * (length - 2)) {
				break;
			}
			if (others != null && sharedWithOthers(f, midi, others)) {
				continue;
			}
			double weight = weight(f0, h);
			sum += weight * peakAround(magnitudes, length, f, this.toleranceRatio);
			weights += weight;
			count++;
		}
		return new double[] { (weights > 0.0 ? sum / weights : 0.0), count };
	}

	/**
	 * Peak amplitude of each of the pitch's partials below the partial limit; when
	 * {@code others} is not null, only its unique partials.
	 */
	double[] partialPeaks(double[] magnitudes, int length, int midi, int[] others) {
		double f0 = frequency(midi);
		double[] peaks = new double[this.parameters.harmonics];
		int count = 0;
		for (int h = 1; h <= this.parameters.harmonics; h++) {
			double f = f0 * h;
			if (f > this.parameters.maxHarmonicHz || f >= this.binHz * (length - 2)) {
				break;
			}
			if (others != null && sharedWithOthers(f, midi, others)) {
				continue;
			}
			peaks[count++] = peakAround(magnitudes, length, f, this.toleranceRatio);
		}
		return java.util.Arrays.copyOf(peaks, count);
	}

	private boolean sharedWithOthers(double frequency, int midi, int[] others) {
		for (int other : others) {
			if (other == midi) {
				continue;
			}
			double f0 = frequency(other);
			long h = Math.round(frequency / f0);
			// the other pitch's partials count up to the same limit as this one's (its harmonics beyond it still sound)
			if (h >= 1 && Math.abs(1200.0 * Math.log(frequency / (h * f0)) / Math.log(2.0)) <= this.parameters.harmonicToleranceCents) {
				return true;
			}
		}
		return false;
	}

	/** Removes the pitch's harmonics, each at most down to the mean of its neighbours (spectral smoothness). */
	void suppress(double[] magnitudes, int length, int midi) {
		double f0 = frequency(midi);
		int count = 0;
		for (int h = 1; h <= this.parameters.harmonics && f0 * h <= this.parameters.maxHarmonicHz && f0 * h < this.binHz * (length - 2); h++) {
			this.harmonicAmplitudes[h] = peakAround(magnitudes, length, f0 * h, this.toleranceRatio);
			count = h;
		}
		for (int h = 1; h <= count; h++) {
			double amplitude = this.harmonicAmplitudes[h];
			if (amplitude <= 0.0) {
				continue;
			}
			double removable = amplitude;
			if (h > 1) {
				double neighbours = (h < count ? (this.harmonicAmplitudes[h - 1] + this.harmonicAmplitudes[h + 1]) / 2.0 : this.harmonicAmplitudes[h - 1]);
				removable = Math.min(amplitude, neighbours);
			}
			double factor = Math.max(0.0, 1.0 - removable / amplitude);
			// widened by the Hann main lobe (±2 bins) so the partial's leakage goes too
			int high = highBin(length, f0 * h, this.toleranceRatio, 2);
			for (int k = lowBin(length, f0 * h, this.toleranceRatio, 2); k <= high; k++) {
				magnitudes[k] *= factor;
			}
		}
	}

	/**
	 * A pitch whose partials all lie on the comb of a lower pitch (e.g. a string's 5th harmonic)
	 * scores well by itself; returns the lowest subharmonic 1/k (k = 2…8, within the tolerance,
	 * not excluded) whose own partials — those not multiples of k — reach {@code ratio} of the
	 * pitch's salience, else the pitch itself.
	 */
	int lowestExplainingSubharmonic(double[] magnitudes, int length, int midi, double midiSalience, double ratio, boolean[] excluded, int minMidi) {
		int lowest = midi;
		for (int k = 2; k <= 8; k++) {
			double semitones = 12.0 * Math.log(k) / Math.log(2.0);
			int candidate = midi - (int) Math.round(semitones);
			double errorCents = Math.abs((midi - candidate - semitones) * 100.0);
			if (candidate < minMidi || errorCents > this.parameters.harmonicToleranceCents || excluded[candidate]) {
				continue;
			}
			if (salience(magnitudes, length, candidate, k) >= ratio * midiSalience && candidate < lowest) {
				lowest = candidate;
			}
		}
		return lowest;
	}

	/** True when the pitch lies, within the tolerance, on harmonic 2…{@code harmonics} of the lower pitch. */
	boolean isHarmonicOf(int midi, int lowerMidi) {
		if (lowerMidi >= midi) {
			return false;
		}
		double ratio = frequency(midi) / frequency(lowerMidi);
		long h = Math.round(ratio);
		return (h >= 2 && h <= this.parameters.harmonics && Math.abs(1200.0 * Math.log(ratio / h) / Math.log(2.0)) <= this.parameters.harmonicToleranceCents);
	}

	/** Strongest magnitude within a frequency ratio around the frequency (at least the nearest bin). */
	double peakAround(double[] magnitudes, int length, double frequency, double ratio) {
		double peak = 0.0;
		int high = highBin(length, frequency, ratio, 0);
		for (int k = lowBin(length, frequency, ratio, 0); k <= high; k++) {
			peak = Math.max(peak, magnitudes[k]);
		}
		return peak;
	}

	private int lowBin(int length, double frequency, double ratio, int extraBins) {
		int nearest = (int) Math.round(frequency / this.binHz);
		return clampBin(length, Math.min(nearest, (int) Math.ceil(frequency / ratio / this.binHz)) - extraBins);
	}

	private int highBin(int length, double frequency, double ratio, int extraBins) {
		int nearest = (int) Math.round(frequency / this.binHz);
		return clampBin(length, Math.max(nearest, (int) Math.floor(frequency * ratio / this.binHz)) + extraBins);
	}

	private static int clampBin(int length, int bin) {
		return Math.max(1, Math.min(length - 1, bin));
	}

	private static double weight(double f0, int h) {
		return (f0 + 27.0) / (h * f0 + 320.0);
	}
}
