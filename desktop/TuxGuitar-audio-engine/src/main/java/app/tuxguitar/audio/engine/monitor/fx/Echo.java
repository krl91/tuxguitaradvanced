package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/**
 * The echo (spec 022 FR-005, research R4): a line of 1.5 s made once, read with linear interpolation at {@code time}, through a one-pole low-pass set by
 * {@code tone} (1 kHz at 0 to above 20 kHz at 10), fed back by {@code repeats} (0.09 a step, so each repeat is lower and darker), the tail going on after
 * the input stops. {@code mix} is the share of the repeats (0 is the dry signal exactly). A new time crossfades from the old reading point to the new one
 * over 10 ms rather than sliding, which would bend the pitch; repeats and mix ramp over a block.
 */
public final class Echo implements Pedal {

	private static final double LONGEST_SECONDS = 1.5;
	private static final double FADE_SECONDS = 0.010;

	private float sampleRate = 48000f;
	private float[] line;
	private int write;
	private double time;
	private double nextTime;
	private double pendingTime = -1;
	private double fade = 1.0;
	private double fadeStep;
	private double lowPass;
	private double lowPassCoefficient = 1.0;
	private double feedback;
	private double targetFeedback;
	private double mix;
	private double targetMix;
	private boolean first = true;
	private double opening = 1.0;
	private double openingStep;

	@Override
	public void prepare(float rate, int maxBlock) {
		this.sampleRate = rate;
		this.line = new float[(int) Math.ceil(LONGEST_SECONDS * rate) + maxBlock + 4];
		this.fadeStep = 1.0 / (FADE_SECONDS * rate);
		this.openingStep = this.fadeStep;
	}

	/** After a reset the input enters the line over 10 ms, so that its first repeat does not start with a click. */
	private double opening() {
		if( this.opening < 1.0 ) {
			this.opening = Math.min(1.0, this.opening + this.openingStep);
		}
		return this.opening;
	}

	@Override
	public void set(PedalSettings settings) {
		double samples = settings.value("time") / 1000.0 * this.sampleRate;
		this.targetFeedback = 0.09 * settings.value("repeats");
		this.targetMix = settings.value("mix") / 100.0;
		this.lowPassCoefficient = Pedals.onePole(1000 * Math.pow(2, settings.value("tone") * 0.45), this.sampleRate);
		if( this.first ) {
			this.first = false;
			this.time = samples;
			this.nextTime = samples;
			this.feedback = this.targetFeedback;
			this.mix = this.targetMix;
		} else if( this.fade >= 1.0 ) {
			if( samples != this.time ) {
				this.nextTime = samples;
				this.fade = 0.0;
			}
			this.pendingTime = -1;
		} else {
			this.pendingTime = samples;
		}
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
		double fromFeedback = this.feedback;
		double fromMix = this.mix;
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			double delayed;
			if( this.fade < 1.0 ) {
				this.fade = Math.min(1.0, this.fade + this.fadeStep);
				delayed = read(this.time) * (1 - this.fade) + read(this.nextTime) * this.fade;
				if( this.fade >= 1.0 ) {
					this.time = this.nextTime;
					if( this.pendingTime >= 0 && this.pendingTime != this.time ) {
						this.nextTime = this.pendingTime;
						this.fade = 0.0;
					}
					this.pendingTime = -1;
				}
			} else {
				delayed = read(this.time);
			}
			this.lowPass = Pedals.flush(this.lowPass + (delayed - this.lowPass) * this.lowPassCoefficient);
			double x = in[i];
			double feedbackNow = fromFeedback + (this.targetFeedback - fromFeedback) * t;
			this.line[this.write] = (float) (x * opening() + feedbackNow * this.lowPass);
			this.write = (this.write + 1) % this.line.length;
			double mixNow = fromMix + (this.targetMix - fromMix) * t;
			out[i] = (float) (x * (1 - mixNow) + this.lowPass * mixNow);
		}
		this.feedback = this.targetFeedback;
		this.mix = this.targetMix;
	}

	@Override
	public void reset() {
		java.util.Arrays.fill(this.line, 0f);
		this.lowPass = 0;
		this.opening = 0;
	}
}
