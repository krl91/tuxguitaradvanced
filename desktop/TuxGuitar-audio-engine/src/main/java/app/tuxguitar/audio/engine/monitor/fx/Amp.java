package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.AmpSettings;
import app.tuxguitar.audio.engine.monitor.Effect;

/**
 * The amp (spec 022 FR-004, research R4), the project's own: a high-pass that tightens the low end, the input gain, a soft clipping stage
 * run oversampled (one stage for clean and crunch, two for high gain, with a small offset for even harmonics), a DC blocker, a three-band
 * tone stack (bass shelf, middle peak, treble shelf), a low-pass that tames the fizz, and the master volume.
 *
 * Knob changes ramp over a block. A change of character dips the level for 10 ms around the switch instead of jumping.
 */
public final class Amp implements Effect {

	private static final double CHANGE_SECONDS = 0.010;

	private float sampleRate = 48000f;
	private int maxBlock = 128;
	private final Biquad tighten = new Biquad();
	private final Biquad dcBlock = new Biquad();
	private final Biquad bass = new Biquad();
	private final Biquad middle = new Biquad();
	private final Biquad treble = new Biquad();
	private final Biquad fizz = new Biquad();
	private Oversampler[] oversamplers;
	private float[] high;
	private int oversampling = 4;
	private AmpSettings settings = AmpSettings.defaults();
	private AmpSettings.Character character = AmpSettings.Character.CLEAN;
	private AmpSettings.Character pendingCharacter;
	private double inputGain = 1;
	private double targetInputGain = 1;
	private double outputGain = 1;
	private double targetOutputGain = 1;
	private double change = 1.0;
	private double changeStep;
	private boolean first = true;

	@Override
	public void prepare(float rate, int block) {
		this.sampleRate = rate;
		this.maxBlock = block;
		for( Biquad biquad : new Biquad[] { this.tighten, this.dcBlock, this.bass, this.middle, this.treble, this.fizz } ) {
			biquad.prepare(rate, block);
		}
		this.dcBlock.setHighPass(10, Math.sqrt(0.5));
		this.oversamplers = new Oversampler[] { new Oversampler(1, block), new Oversampler(2, block), new Oversampler(4, block) };
		this.high = new float[block * 4];
		this.changeStep = 1.0 / (CHANGE_SECONDS / 2 * rate);
		apply(this.settings, true);
	}

	/** 1, 2 or 4 (4 by default; the overload lightening lowers it). */
	public void setOversampling(int factor) {
		this.oversampling = factor >= 4 ? 4 : factor >= 2 ? 2 : 1;
	}

	public void set(AmpSettings value) {
		apply(value, this.first);
	}

	private void apply(AmpSettings value, boolean immediately) {
		this.settings = value;
		if( immediately ) {
			this.first = false;
			this.character = value.character();
			this.pendingCharacter = null;
			this.change = 1.0;
		} else if( value.character() != this.character ) {
			this.pendingCharacter = value.character();
		}
		configure(this.pendingCharacter != null ? this.character : value.character(), value, immediately);
	}

	private static double db(double value) {
		return Math.pow(10, value / 20);
	}

	private void configure(AmpSettings.Character active, AmpSettings value, boolean immediately) {
		double gain = value.gain();
		double inputDb;
		double outputDb;
		double tightenHz;
		double fizzHz;
		if( active == AmpSettings.Character.CLEAN ) {
			inputDb = 1.2 * gain;
			outputDb = -inputDb;
			tightenHz = 70;
			fizzHz = 12000;
		} else if( active == AmpSettings.Character.CRUNCH ) {
			inputDb = 6 + 2.4 * gain;
			outputDb = -(6 + 1.2 * gain);
			tightenHz = 110;
			fizzHz = 9000;
		} else {
			inputDb = 12 + 3 * gain;
			outputDb = -14;
			tightenHz = 140;
			fizzHz = 7000;
		}
		double volume = value.volume();
		double volumeGain = volume <= 0 ? 0 : db((volume - 6) * 3);
		this.targetInputGain = db(inputDb);
		this.targetOutputGain = db(outputDb) * volumeGain;
		this.tighten.setHighPass(tightenHz, Math.sqrt(0.5));
		this.fizz.setLowPass(fizzHz, Math.sqrt(0.5));
		this.bass.setLowShelf(200, (value.bass() - 5) * 2.4);
		this.middle.setPeak(700, 0.8, (value.middle() - 5) * 2.4);
		this.treble.setHighShelf(1800, (value.treble() - 5) * 2.4);
		if( immediately ) {
			this.inputGain = this.targetInputGain;
			this.outputGain = this.targetOutputGain;
		}
	}

	private static double shape(AmpSettings.Character character, double x) {
		if( character == AmpSettings.Character.CLEAN ) {
			return Math.tanh(x);
		}
		if( character == AmpSettings.Character.CRUNCH ) {
			return Math.tanh(x + 0.2) - 0.197375320224904;
		}
		double first = Math.tanh(x + 0.1) - 0.0996679946249558;
		return Math.tanh(6 * first);
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double fromInput = this.inputGain;
		double fromOutput = this.outputGain;
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			out[i] = (float) (this.tighten.tick(in[i]) * (fromInput + (this.targetInputGain - fromInput) * t));
		}
		this.inputGain = this.targetInputGain;
		Oversampler oversampler = this.oversamplers[this.oversampling == 4 ? 2 : this.oversampling == 2 ? 1 : 0];
		int count = oversampler.up(out, n, this.high);
		AmpSettings.Character active = this.character;
		for( int i = 0 ; i < count ; i++ ) {
			this.high[i] = (float) shape(active, this.high[i]);
		}
		oversampler.down(this.high, count, out);
		for( int i = 0 ; i < n ; i++ ) {
			double y = this.dcBlock.tick(out[i]);
			y = this.bass.tick(y);
			y = this.middle.tick(y);
			y = this.treble.tick(y);
			y = this.fizz.tick(y);
			double t = (i + 1) / (double) n;
			y *= fromOutput + (this.targetOutputGain - fromOutput) * t;
			y *= dip();
			out[i] = (float) y;
		}
		this.outputGain = this.targetOutputGain;
	}

	/** The level around a change of character: down to silence in 5 ms, the switch, back up in 5 ms. */
	private double dip() {
		if( this.pendingCharacter != null ) {
			this.change -= this.changeStep;
			if( this.change <= 0 ) {
				this.change = 0;
				this.character = this.pendingCharacter;
				this.pendingCharacter = null;
				configure(this.character, this.settings, false);
			}
		} else if( this.change < 1.0 ) {
			this.change = Math.min(1.0, this.change + this.changeStep);
		}
		return 0.5 - 0.5 * Math.cos(Math.PI * this.change);
	}

	@Override
	public void reset() {
		for( Biquad biquad : new Biquad[] { this.tighten, this.dcBlock, this.bass, this.middle, this.treble, this.fizz } ) {
			biquad.reset();
		}
		for( Oversampler oversampler : this.oversamplers ) {
			oversampler.reset();
		}
	}
}
