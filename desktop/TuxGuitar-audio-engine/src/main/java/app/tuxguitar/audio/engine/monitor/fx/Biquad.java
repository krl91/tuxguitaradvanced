package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Effect;

/**
 * A second-order filter in the forms of the public "Audio EQ Cookbook" (low-pass, high-pass, peak, low and high shelf), direct form I in
 * double precision (spec 022, research R4). A new setting glides over the next block instead of jumping: the frequency and the Q move
 * geometrically and the gain in decibels, the coefficients worked out again every few samples, so the filter's speed changes as gradually
 * as its sound (gliding the coefficients themselves made a low-pass jump from 200 Hz to nearly 8 kHz in a few samples). The very first
 * setting applies at once. Tiny values are flushed to zero so silence never leaves denormals.
 */
public final class Biquad implements Effect {

	private enum Kind { LOW_PASS, HIGH_PASS, PEAK, LOW_SHELF, HIGH_SHELF }

	/** The samples between two designs while a setting glides. */
	private static final int STEP = 8;

	private float sampleRate = 48000f;
	private int rampLength = 128;
	private double b0 = 1, b1, b2, a1, a2;
	private double x1, x2, y1, y2;
	private Kind kind;
	private double frequency, q, gainDb;
	private double fromFrequency, fromQ, fromGainDb;
	private double toFrequency, toQ, toGainDb;
	private int remaining;

	@Override
	public void prepare(float rate, int maxBlock) {
		this.sampleRate = rate;
		this.rampLength = Math.max(1, maxBlock);
		if( this.kind != null ) {
			design(this.kind, this.frequency, this.q, this.gainDb);
		}
	}

	public void setLowPass(double frequency, double q) {
		target(Kind.LOW_PASS, frequency, q, 0);
	}

	public void setHighPass(double frequency, double q) {
		target(Kind.HIGH_PASS, frequency, q, 0);
	}

	public void setPeak(double frequency, double q, double gainDb) {
		target(Kind.PEAK, frequency, q, gainDb);
	}

	public void setLowShelf(double frequency, double gainDb) {
		target(Kind.LOW_SHELF, frequency, Math.sqrt(0.5), gainDb);
	}

	public void setHighShelf(double frequency, double gainDb) {
		target(Kind.HIGH_SHELF, frequency, Math.sqrt(0.5), gainDb);
	}

	private void target(Kind newKind, double newFrequency, double newQ, double newGainDb) {
		double limitedQ = Math.max(0.05, newQ);
		if( this.kind != newKind ) {
			// the first setting, or another kind of filter: no glide between two different shapes
			this.kind = newKind;
			this.frequency = newFrequency;
			this.q = limitedQ;
			this.gainDb = newGainDb;
			this.remaining = 0;
			design(newKind, newFrequency, limitedQ, newGainDb);
			return;
		}
		this.fromFrequency = this.frequency;
		this.fromQ = this.q;
		this.fromGainDb = this.gainDb;
		this.toFrequency = newFrequency;
		this.toQ = limitedQ;
		this.toGainDb = newGainDb;
		this.remaining = this.rampLength;
	}

	private double omega(double value) {
		double limited = Math.max(10, Math.min(0.49 * this.sampleRate, value));
		return 2 * Math.PI * limited / this.sampleRate;
	}

	private void design(Kind shape, double f, double quality, double gain) {
		double w = omega(f);
		double cos = Math.cos(w);
		double sin = Math.sin(w);
		double a = Math.pow(10, gain / 40);
		switch( shape ) {
		case LOW_PASS: {
			double alpha = sin / (2 * quality);
			coefficients((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
			break;
		}
		case HIGH_PASS: {
			double alpha = sin / (2 * quality);
			coefficients((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
			break;
		}
		case PEAK: {
			double alpha = sin / (2 * quality);
			coefficients(1 + alpha * a, -2 * cos, 1 - alpha * a, 1 + alpha / a, -2 * cos, 1 - alpha / a);
			break;
		}
		case LOW_SHELF: {
			double root = 2 * Math.sqrt(a) * sin / 2 * Math.sqrt(2);
			coefficients(a * ((a + 1) - (a - 1) * cos + root), 2 * a * ((a - 1) - (a + 1) * cos), a * ((a + 1) - (a - 1) * cos - root),
					(a + 1) + (a - 1) * cos + root, -2 * ((a - 1) + (a + 1) * cos), (a + 1) + (a - 1) * cos - root);
			break;
		}
		default: {
			double root = 2 * Math.sqrt(a) * sin / 2 * Math.sqrt(2);
			coefficients(a * ((a + 1) + (a - 1) * cos + root), -2 * a * ((a - 1) + (a + 1) * cos), a * ((a + 1) + (a - 1) * cos - root),
					(a + 1) - (a - 1) * cos + root, 2 * ((a - 1) - (a + 1) * cos), (a + 1) - (a - 1) * cos - root);
			break;
		}
		}
	}

	private void coefficients(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
		this.b0 = nb0 / na0;
		this.b1 = nb1 / na0;
		this.b2 = nb2 / na0;
		this.a1 = na1 / na0;
		this.a2 = na2 / na0;
	}

	private void glide() {
		this.remaining--;
		if( this.remaining == 0 ) {
			this.frequency = this.toFrequency;
			this.q = this.toQ;
			this.gainDb = this.toGainDb;
		} else if( this.remaining % STEP == 0 ) {
			double t = 1 - this.remaining / (double) this.rampLength;
			this.frequency = this.fromFrequency * Math.pow(this.toFrequency / this.fromFrequency, t);
			this.q = this.fromQ * Math.pow(this.toQ / this.fromQ, t);
			this.gainDb = this.fromGainDb + (this.toGainDb - this.fromGainDb) * t;
		} else {
			return;
		}
		design(this.kind, this.frequency, this.q, this.gainDb);
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		for( int i = 0 ; i < n ; i++ ) {
			out[i] = (float) tick(in[i]);
		}
	}

	/** One sample, for callers that work sample by sample (the amp's stages). */
	public double tick(double x) {
		if( this.remaining > 0 ) {
			glide();
		}
		double y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
		if( Math.abs(y) < 1e-20 ) {
			y = 0;
		}
		this.x2 = this.x1;
		this.x1 = Math.abs(x) < 1e-20 ? 0 : x;
		this.y2 = this.y1;
		this.y1 = y;
		return y;
	}

	@Override
	public void reset() {
		this.x1 = 0;
		this.x2 = 0;
		this.y1 = 0;
		this.y2 = 0;
		if( this.remaining > 0 ) {
			this.remaining = 0;
			this.frequency = this.toFrequency;
			this.q = this.toQ;
			this.gainDb = this.toGainDb;
			design(this.kind, this.frequency, this.q, this.gainDb);
		}
	}
}
