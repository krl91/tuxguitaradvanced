package app.tuxguitar.audio.engine.frontend;

/**
 * Spectral subtraction of stationary background noise (spec 002, FR-064): 512-sample frames with a
 * 50% overlap and a Hann window (so overlap-adding the processed frames rebuilds the signal), the
 * learnt noise magnitude spectrum subtracted with an over-subtraction factor and a spectral floor,
 * the phase kept. It learns the noise spectrum only while told the input is quiet. It delays the
 * signal by one hop (256 samples, about 5 ms at 48 kHz), which counts in the latency budget.
 */
public final class SpectralSubtractor implements FrontEndStage {

	public static final int FRAME = 512;
	public static final int HOP = FRAME / 2;
	/** Over-subtraction factor. */
	public static final double ALPHA = 1.5;
	/** Spectral floor: never subtract below this share of the original magnitude. */
	public static final double BETA = 0.05;
	/** Share of the new quiet spectrum mixed into the learnt noise spectrum. */
	private static final double LEARN_RATE = 0.1;

	private final double[] window = new double[FRAME];
	private final double[] noise = new double[FRAME / 2 + 1];
	private final double[] re = new double[FRAME];
	private final double[] im = new double[FRAME];
	private final double[] previous = new double[HOP];
	private final double[] overlap = new double[HOP];
	private final int[] bitReversed = new int[FRAME];
	private final double[] cos = new double[FRAME / 2];
	private final double[] sin = new double[FRAME / 2];
	private boolean learnedOnce;
	private volatile boolean quiet;

	public SpectralSubtractor() {
		int bits = Integer.numberOfTrailingZeros(FRAME);
		for( int i = 0 ; i < FRAME ; i ++ ) {
			this.window[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / FRAME);
			this.bitReversed[i] = Integer.reverse(i) >>> (32 - bits);
		}
		for( int i = 0 ; i < FRAME / 2 ; i ++ ) {
			this.cos[i] = Math.cos(-2.0 * Math.PI * i / FRAME);
			this.sin[i] = Math.sin(-2.0 * Math.PI * i / FRAME);
		}
	}

	/** Tells whether the input is quiet right now (the noise gate is closed): the noise spectrum is learnt then. */
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}

	@Override
	public void process(float[] hop, int length) {
		if( length != HOP ) {
			throw new IllegalArgumentException("hops of " + HOP + " samples are required, got " + length);
		}
		for( int i = 0 ; i < HOP ; i ++ ) {
			this.re[i] = this.previous[i] * this.window[i];
			this.im[i] = 0.0;
			this.re[HOP + i] = hop[i] * this.window[HOP + i];
			this.im[HOP + i] = 0.0;
			this.previous[i] = hop[i];
		}
		fft(false);
		boolean learn = this.quiet;
		for( int k = 0 ; k <= FRAME / 2 ; k ++ ) {
			double magnitude = Math.hypot(this.re[k], this.im[k]);
			if( learn ) {
				this.noise[k] = (this.learnedOnce ? (1.0 - LEARN_RATE) * this.noise[k] + LEARN_RATE * magnitude : magnitude);
			}
			double kept = Math.max(magnitude - ALPHA * this.noise[k], BETA * magnitude);
			double scale = (magnitude > 0.0 ? kept / magnitude : 0.0);
			this.re[k] *= scale;
			this.im[k] *= scale;
			if( k > 0 && k < FRAME / 2 ) {
				this.re[FRAME - k] = this.re[k];
				this.im[FRAME - k] = -this.im[k];
			}
		}
		if( learn ) {
			this.learnedOnce = true;
		}
		fft(true);
		for( int i = 0 ; i < HOP ; i ++ ) {
			hop[i] = (float) (this.overlap[i] + this.re[i]);
			this.overlap[i] = this.re[HOP + i];
		}
	}

	private void fft(boolean inverse) {
		for( int i = 0 ; i < FRAME ; i ++ ) {
			int j = this.bitReversed[i];
			if( j > i ) {
				double t = this.re[i];
				this.re[i] = this.re[j];
				this.re[j] = t;
				t = this.im[i];
				this.im[i] = this.im[j];
				this.im[j] = t;
			}
		}
		for( int length = 2 ; length <= FRAME ; length <<= 1 ) {
			int half = length >> 1;
			int step = FRAME / length;
			for( int start = 0 ; start < FRAME ; start += length ) {
				for( int k = 0 ; k < half ; k ++ ) {
					double wr = this.cos[k * step];
					double wi = (inverse ? -this.sin[k * step] : this.sin[k * step]);
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
		if( inverse ) {
			for( int i = 0 ; i < FRAME ; i ++ ) {
				this.re[i] /= FRAME;
				this.im[i] /= FRAME;
			}
		}
	}

	@Override
	public void reset() {
		java.util.Arrays.fill(this.previous, 0.0);
		java.util.Arrays.fill(this.overlap, 0.0);
		java.util.Arrays.fill(this.noise, 0.0);
		this.learnedOnce = false;
	}

	@Override
	public int latencyHops() {
		return 1;
	}
}
