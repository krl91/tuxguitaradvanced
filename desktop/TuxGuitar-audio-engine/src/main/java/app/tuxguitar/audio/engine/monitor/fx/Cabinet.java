package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Effect;

/**
 * The speaker cabinet (spec 022 FR-004, research R4): a curve, not a recorded impulse response. Two high-passes at 80 Hz, a resonance
 * at 120 Hz, a presence bump at 2.5 kHz and two low-passes at 5 kHz. Switching it on or off crossfades over 10 ms; off from the start, it
 * is an exact pass-through.
 */
public final class Cabinet implements Effect {

	private final Biquad[] filters = { new Biquad(), new Biquad(), new Biquad(), new Biquad(), new Biquad(), new Biquad() };
	private float[] wet;
	private boolean enabled = true;
	private double mix = 1.0;
	private double mixStep;
	private boolean started;

	@Override
	public void prepare(float sampleRate, int maxBlock) {
		for( Biquad filter : this.filters ) {
			filter.prepare(sampleRate, maxBlock);
		}
		this.filters[0].setHighPass(80, Math.sqrt(0.5));
		this.filters[1].setHighPass(80, Math.sqrt(0.5));
		this.filters[2].setPeak(120, 1.2, 3);
		this.filters[3].setPeak(2500, 1.5, 2);
		this.filters[4].setLowPass(5000, Math.sqrt(0.5));
		this.filters[5].setLowPass(5000, Math.sqrt(0.5));
		this.wet = new float[maxBlock];
		this.mixStep = 1.0 / (0.010 * sampleRate);
		this.mix = this.enabled ? 1.0 : 0.0;
	}

	/** Switched on or off with a 10 ms crossfade; before any sound it is set at once. */
	public void setEnabled(boolean value) {
		this.enabled = value;
		if( !this.started ) {
			this.mix = value ? 1.0 : 0.0;
		}
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		this.started = true;
		double target = this.enabled ? 1.0 : 0.0;
		if( this.mix == 0.0 && target == 0.0 ) {
			if( in != out ) {
				System.arraycopy(in, 0, out, 0, n);
			}
			return;
		}
		for( int i = 0 ; i < n ; i++ ) {
			double y = in[i];
			for( Biquad filter : this.filters ) {
				y = filter.tick(y);
			}
			this.wet[i] = (float) y;
		}
		for( int i = 0 ; i < n ; i++ ) {
			if( this.mix < target ) {
				this.mix = Math.min(target, this.mix + this.mixStep);
			} else if( this.mix > target ) {
				this.mix = Math.max(target, this.mix - this.mixStep);
			}
			out[i] = (float) (this.mix * this.wet[i] + (1 - this.mix) * in[i]);
		}
	}

	@Override
	public void reset() {
		this.started = false;
		for( Biquad filter : this.filters ) {
			filter.reset();
		}
	}
}
