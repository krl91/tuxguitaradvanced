package app.tuxguitar.audio.engine.analysis;

/**
 * In-place radix-2 FFT with preallocated tables, used for magnitude spectra. Deterministic; no
 * allocation after construction. Not thread-safe: one instance per analysis thread.
 */
final class RealFft {

	private final int size;
	private final int[] bitReversed;
	private final double[] cos;
	private final double[] sin;
	private final double[] re;
	private final double[] im;

	RealFft(int size) {
		if (size < 2 || Integer.bitCount(size) != 1) {
			throw new IllegalArgumentException("FFT size must be a power of two: " + size);
		}
		this.size = size;
		this.bitReversed = new int[size];
		int bits = Integer.numberOfTrailingZeros(size);
		for (int i = 0; i < size; i++) {
			this.bitReversed[i] = Integer.reverse(i) >>> (32 - bits);
		}
		this.cos = new double[size / 2];
		this.sin = new double[size / 2];
		for (int i = 0; i < size / 2; i++) {
			this.cos[i] = Math.cos(-2.0 * Math.PI * i / size);
			this.sin[i] = Math.sin(-2.0 * Math.PI * i / size);
		}
		this.re = new double[size];
		this.im = new double[size];
	}

	int size() {
		return this.size;
	}

	/**
	 * Writes the magnitudes of bins 0…size/2 of {@code input} into {@code magnitudes}
	 * (length ≥ size/2 + 1), scaled by {@code scale}.
	 */
	void magnitudes(double[] input, double[] magnitudes, double scale) {
		for (int i = 0; i < this.size; i++) {
			this.re[this.bitReversed[i]] = input[i];
			this.im[this.bitReversed[i]] = 0.0;
		}
		for (int length = 2; length <= this.size; length <<= 1) {
			int half = length >> 1;
			int step = this.size / length;
			for (int start = 0; start < this.size; start += length) {
				for (int k = 0; k < half; k++) {
					double wr = this.cos[k * step];
					double wi = this.sin[k * step];
					int a = start + k;
					int b = a + half;
					double tr = wr * this.re[b] - wi * this.im[b];
					double ti = wr * this.im[b] + wi * this.re[b];
					this.re[b] = this.re[a] - tr;
					this.im[b] = this.im[a] - ti;
					this.re[a] += tr;
					this.im[a] += ti;
				}
			}
		}
		for (int k = 0; k <= this.size / 2; k++) {
			magnitudes[k] = scale * Math.sqrt(this.re[k] * this.re[k] + this.im[k] * this.im[k]);
		}
	}
}
