package app.tuxguitar.audio.engine.monitor.fx;

/**
 * Runs a clipping stage at 2 or 4 times the sample rate so that its harmonics do not fold back as aliasing (spec 022, research R4). Each
 * factor of 2 is a half-band FIR (a Kaiser-windowed sinc, every other tap zero) used to interpolate on the way up and to filter before
 * dropping every other sample on the way down. Factor 1 is a plain copy. Every buffer is made at construction.
 */
public final class Oversampler {

	private static final int TAPS = 63;
	private static final double KAISER_BETA = 8.0;
	private static final double[] NONZERO_TAPS;
	private static final int[] NONZERO_INDEX;

	static {
		int middle = (TAPS - 1) / 2;
		double[] taps = new double[TAPS];
		double sum = 0;
		for( int n = 0 ; n < TAPS ; n++ ) {
			double x = (n - middle) / 2.0;
			double sinc = x == 0 ? 1.0 : Math.sin(Math.PI * x) / (Math.PI * x);
			double r = (n - middle) / (double) middle;
			taps[n] = 0.5 * sinc * bessel(KAISER_BETA * Math.sqrt(Math.max(0, 1 - r * r))) / bessel(KAISER_BETA);
			sum += taps[n];
		}
		int count = 0;
		for( int n = 0 ; n < TAPS ; n++ ) {
			taps[n] /= sum;
			if( Math.abs(taps[n]) > 1e-12 ) {
				count++;
			}
		}
		NONZERO_TAPS = new double[count];
		NONZERO_INDEX = new int[count];
		int k = 0;
		for( int n = 0 ; n < TAPS ; n++ ) {
			if( Math.abs(taps[n]) > 1e-12 ) {
				NONZERO_TAPS[k] = taps[n];
				NONZERO_INDEX[k] = n;
				k++;
			}
		}
	}

	private static double bessel(double x) {
		double sum = 1;
		double term = 1;
		for( int k = 1 ; k < 40 ; k++ ) {
			term *= (x / (2 * k)) * (x / (2 * k));
			sum += term;
		}
		return sum;
	}

	/** One half-band stage with its own history. */
	private static final class HalfBand {
		private final double[] history = new double[2 * TAPS];
		private int position;

		private void push(double x) {
			this.position = (this.position + 1) % TAPS;
			this.history[this.position] = x;
			this.history[this.position + TAPS] = x;
		}

		private double filter() {
			// history[position + TAPS - n] is the sample pushed n steps ago
			double sum = 0;
			int base = this.position + TAPS;
			for( int k = 0 ; k < NONZERO_TAPS.length ; k++ ) {
				sum += NONZERO_TAPS[k] * this.history[base - NONZERO_INDEX[k]];
			}
			return Math.abs(sum) < 1e-25 ? 0 : sum;
		}

		private void up(float[] in, int n, float[] out) {
			for( int i = 0 ; i < n ; i++ ) {
				push(2.0 * in[i]);
				out[2 * i] = (float) filter();
				push(0);
				out[2 * i + 1] = (float) filter();
			}
		}

		private void down(float[] in, int n, float[] out) {
			for( int i = 0 ; i < n / 2 ; i++ ) {
				push(in[2 * i]);
				push(in[2 * i + 1]);
				out[i] = (float) filter();
			}
		}

		private void reset() {
			java.util.Arrays.fill(this.history, 0);
			this.position = 0;
		}
	}

	private final int factor;
	private final HalfBand upFirst = new HalfBand();
	private final HalfBand upSecond = new HalfBand();
	private final HalfBand downFirst = new HalfBand();
	private final HalfBand downSecond = new HalfBand();
	private final float[] middle;

	/** @param factor 1, 2 or 4 */
	public Oversampler(int factor, int maxBlock) {
		if( factor != 1 && factor != 2 && factor != 4 ) {
			throw new IllegalArgumentException("factor " + factor);
		}
		this.factor = factor;
		this.middle = new float[maxBlock * 2];
	}

	public int factor() {
		return this.factor;
	}

	/** @return the frames written to {@code out}: {@code n} times the factor */
	public int up(float[] in, int n, float[] out) {
		if( this.factor == 1 ) {
			System.arraycopy(in, 0, out, 0, n);
		} else if( this.factor == 2 ) {
			this.upFirst.up(in, n, out);
		} else {
			this.upFirst.up(in, n, this.middle);
			this.upSecond.up(this.middle, 2 * n, out);
		}
		return n * this.factor;
	}

	/** @param count the oversampled frames, a multiple of the factor; writes {@code count / factor} frames */
	public void down(float[] in, int count, float[] out) {
		if( this.factor == 1 ) {
			System.arraycopy(in, 0, out, 0, count);
		} else if( this.factor == 2 ) {
			this.downFirst.down(in, count, out);
		} else {
			this.downSecond.down(in, count, this.middle);
			this.downFirst.down(this.middle, count / 2, out);
		}
	}

	public void reset() {
		this.upFirst.reset();
		this.upSecond.reset();
		this.downFirst.reset();
		this.downSecond.reset();
	}
}
