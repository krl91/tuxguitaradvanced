package app.tuxguitar.audio.engine.monitor;

import app.tuxguitar.audio.engine.analysis.RealFft;

/**
 * Watches the processed output for a howl (spec 022 FR-012, SC-005, research R5): every 10 ms, the spectrum of the last 2048 samples (the existing
 * {@link RealFft}); a howl is one narrow peak more than 20 dB above the median of the spectrum, on the same frequency for 300 ms, whose level keeps
 * rising (by 15 dB) or stays above −12 dBFS. It watches the output once the half-second fade in is over. Output thread only; it never allocates after it is made.
 */
final class FeedbackGuard {

	private static final int SIZE = 2048;
	private static final double ABOVE_MEDIAN_DB = 20;
	private static final double NARROW_DB = 10;
	private static final int NARROW_BINS = 4;
	private static final double LOUD_DB = -12;
	private static final double HOLD_SECONDS = 0.300;
	/**
	 * The rise over the 300 ms that tells a growing howl: the recorded guitars through the ready-made sounds rise at most 11.9 dB (an open string
	 * blooming after its attack), the test's howl 18 dB; a slower howl is caught when it stays above −12 dBFS.
	 */
	private static final double RISE_DB = 15;
	private static final double FALL_TOLERANCE_DB = 1;
	private static final double LOW_HZ = 60;
	private static final double HIGH_HZ = 12000;

	private final RealFft fft = new RealFft(SIZE);
	private final float[] ring = new float[SIZE];
	private final double[] frame = new double[SIZE];
	private final double[] window = new double[SIZE];
	private final double[] magnitudes = new double[SIZE / 2 + 1];
	private final double[] sorted;
	private final int hop;
	private final int low;
	private final int high;
	private final int holdAnalyses;
	private int position;
	private int sinceAnalysis;
	private int filled;
	private int bin = -1;
	private int analyses;
	private double startDb;
	private double lastDb;
	private boolean rising;
	private boolean loud;
	private boolean found;

	FeedbackGuard(float sampleRate, int maxBlock) {
		this.hop = Math.max(1, Math.round(0.010f * sampleRate));
		double binHz = sampleRate / SIZE;
		this.low = Math.max(NARROW_BINS + 1, (int) Math.floor(LOW_HZ / binHz));
		this.high = Math.min(SIZE / 2 - NARROW_BINS - 1, (int) Math.ceil(HIGH_HZ / binHz));
		this.sorted = new double[this.high - this.low + 1];
		this.holdAnalyses = (int) Math.ceil(HOLD_SECONDS * sampleRate / this.hop);
		double sum = 0;
		for (int i = 0; i < SIZE; i++) {
			this.window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (SIZE - 1));
			sum += this.window[i];
		}
		// a full-scale sine reads 0 dB at its peak bin
		double scale = 2.0 / sum;
		for (int i = 0; i < SIZE; i++) {
			this.window[i] *= scale;
		}
	}

	/** Takes a block of the output; true once a howl is found (and until {@link #reset}). */
	boolean process(float[] block, int n) {
		for (int i = 0; i < n; i++) {
			this.ring[this.position] = block[i];
			this.position = (this.position + 1) % SIZE;
		}
		this.filled = Math.min(SIZE, this.filled + n);
		this.sinceAnalysis += n;
		if (!this.found && this.filled == SIZE && this.sinceAnalysis >= this.hop) {
			this.sinceAnalysis = 0;
			analyse();
		}
		return this.found;
	}

	void reset() {
		this.filled = 0;
		this.sinceAnalysis = 0;
		this.bin = -1;
		this.found = false;
	}

	private void analyse() {
		for (int i = 0; i < SIZE; i++) {
			this.frame[i] = this.ring[(this.position + i) % SIZE] * this.window[i];
		}
		this.fft.magnitudes(this.frame, this.magnitudes, 1.0);
		int peak = this.low;
		for (int k = this.low; k <= this.high; k++) {
			this.sorted[k - this.low] = this.magnitudes[k];
			if (this.magnitudes[k] > this.magnitudes[peak]) {
				peak = k;
			}
		}
		double peakDb = db(this.magnitudes[peak]);
		double medianDb = db(median());
		boolean narrow = peakDb - db(this.magnitudes[peak - NARROW_BINS]) >= NARROW_DB && peakDb - db(this.magnitudes[peak + NARROW_BINS]) >= NARROW_DB;
		if (!narrow || peakDb - medianDb <= ABOVE_MEDIAN_DB) {
			this.bin = -1;
			return;
		}
		if (this.bin < 0 || Math.abs(peak - this.bin) > 1) {
			this.bin = peak;
			this.analyses = 1;
			this.startDb = peakDb;
			this.lastDb = peakDb;
			this.rising = true;
			this.loud = peakDb > LOUD_DB;
			return;
		}
		this.bin = peak;
		this.analyses++;
		this.rising &= peakDb >= this.lastDb - FALL_TOLERANCE_DB;
		this.loud &= peakDb > LOUD_DB;
		this.lastDb = peakDb;
		if (this.analyses >= this.holdAnalyses && ((this.rising && peakDb - this.startDb >= RISE_DB) || this.loud)) {
			this.found = true;
		}
	}

	/** The median of the spectrum between 60 Hz and 12 kHz (a selection in place, no allocation). */
	private double median() {
		int count = this.sorted.length;
		int k = count / 2;
		int left = 0;
		int right = count - 1;
		while (left < right) {
			double pivot = this.sorted[(left + right) >>> 1];
			int i = left;
			int j = right;
			while (i <= j) {
				while (this.sorted[i] < pivot) {
					i++;
				}
				while (this.sorted[j] > pivot) {
					j--;
				}
				if (i <= j) {
					double swap = this.sorted[i];
					this.sorted[i] = this.sorted[j];
					this.sorted[j] = swap;
					i++;
					j--;
				}
			}
			if (k <= j) {
				right = j;
			} else if (k >= i) {
				left = i;
			} else {
				break;
			}
		}
		return this.sorted[k];
	}

	private static double db(double magnitude) {
		return 20 * Math.log10(Math.max(1e-12, magnitude));
	}
}
