package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/**
 * The chorus (spec 022 FR-005, research R4): two delays of 7.5 to 19.5 ms swept by a sine a quarter of a turn apart, read with linear interpolation.
 * {@code rate} is the sweep's frequency, {@code depth} its width (±0.6 ms a step around 13.5 ms) and {@code mix} the share of the delayed voices (0 is
 * the dry signal exactly). Depth and mix ramp over a block; the sweep's phase never jumps. After a reset the line is empty: until it holds the
 * longest delay the voices are replaced by the dry signal, then they come in over 10 ms, so that a chorus switched on in the middle of a note does not
 * click when its delayed signal arrives.
 */
public final class Chorus implements Pedal {

	private static final double CENTRE_SECONDS = 0.0135;
	private static final double SWING_SECONDS_PER_STEP = 0.0006;

	private float sampleRate = 48000f;
	private float[] line;
	private int write;
	private double phase;
	private double phaseStep;
	private double swing;
	private double targetSwing;
	private double mix;
	private double targetMix;
	private double delaySamples;
	private boolean first = true;
	private int filled;
	private int longest;
	private double warm;
	private double warmStep;

	@Override
	public void prepare(float rate, int maxBlock) {
		this.sampleRate = rate;
		this.line = new float[(int) Math.ceil(0.025 * rate) + maxBlock + 4];
		this.delaySamples = CENTRE_SECONDS * rate;
		this.longest = (int) Math.ceil(0.020 * rate) + 2;
		this.warmStep = 1.0 / (0.010 * rate);
	}

	@Override
	public void set(PedalSettings settings) {
		this.phaseStep = 2 * Math.PI * settings.value("rate") / this.sampleRate;
		this.targetSwing = settings.value("depth") * SWING_SECONDS_PER_STEP * this.sampleRate;
		this.targetMix = settings.value("mix") / 100.0;
		if( this.first ) {
			this.first = false;
			this.swing = this.targetSwing;
			this.mix = this.targetMix;
		}
	}

	/** The first voice's delay at the end of the last block, in milliseconds. */
	double delayMillis() {
		return this.delaySamples * 1000.0 / this.sampleRate;
	}

	private double read(double delay) {
		double position = this.write - delay;
		int length = this.line.length;
		while( position < 0 ) {
			position += length;
		}
		int index = (int) position;
		double fraction = position - index;
		float a = this.line[index % length];
		float b = this.line[(index + 1) % length];
		return a + (b - a) * fraction;
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double fromSwing = this.swing;
		double fromMix = this.mix;
		double centre = CENTRE_SECONDS * this.sampleRate;
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			double swingNow = fromSwing + (this.targetSwing - fromSwing) * t;
			double mixNow = fromMix + (this.targetMix - fromMix) * t;
			double x = in[i];
			this.line[this.write] = (float) x;
			double first = centre + swingNow * Math.sin(this.phase);
			double second = centre + swingNow * Math.cos(this.phase);
			double wet = 0.5 * (read(first) + read(second));
			if( this.warm < 1.0 ) {
				if( this.filled < this.longest ) {
					this.filled++;
				} else {
					this.warm = Math.min(1.0, this.warm + this.warmStep);
				}
				wet = x + (wet - x) * this.warm;
			}
			this.delaySamples = first;
			this.write = (this.write + 1) % this.line.length;
			this.phase += this.phaseStep;
			if( this.phase > 2 * Math.PI ) {
				this.phase -= 2 * Math.PI;
			}
			out[i] = (float) (x * (1 - mixNow) + wet * mixNow);
		}
		this.swing = this.targetSwing;
		this.mix = this.targetMix;
	}

	@Override
	public void reset() {
		java.util.Arrays.fill(this.line, 0f);
		this.phase = 0;
		this.filled = 0;
		this.warm = 0;
	}
}
