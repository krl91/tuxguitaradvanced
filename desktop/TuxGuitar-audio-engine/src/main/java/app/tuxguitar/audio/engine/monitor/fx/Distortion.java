package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/**
 * The distortion (spec 022 FR-005, research R4): a high-pass at 100 Hz and a pre-emphasis (+6 dB above 700 Hz), then an asymmetric hard clipping (+0.5,
 * −0.35) run at 4× oversampling, a DC blocker and the {@code tone} low-pass (1.5 kHz at 0 to above 20 kHz at 10). {@code gain} raises the level into the
 * clipping by 5 dB a step; {@code level} is the output, 5 being unity. Gains ramp over a block and the filters glide.
 */
public final class Distortion implements Pedal {

	private static final double TOP = 0.5;
	private static final double BOTTOM = -0.35;

	private final Biquad tighten = new Biquad();
	private final Biquad emphasis = new Biquad();
	private final Biquad dcBlock = new Biquad();
	private final Biquad tone = new Biquad();
	private Oversampler oversampler;
	private float[] high;
	private double gain = 1;
	private double targetGain = 1;
	private double level = 1;
	private double targetLevel = 1;
	private boolean first = true;

	@Override
	public void prepare(float rate, int maxBlock) {
		for( Biquad biquad : new Biquad[] { this.tighten, this.emphasis, this.dcBlock, this.tone } ) {
			biquad.prepare(rate, maxBlock);
		}
		this.tighten.setHighPass(100, Math.sqrt(0.5));
		this.emphasis.setHighShelf(700, 6);
		this.dcBlock.setHighPass(10, Math.sqrt(0.5));
		this.tone.setLowPass(6000, Math.sqrt(0.5));
		this.oversampler = new Oversampler(4, maxBlock);
		this.high = new float[4 * maxBlock];
	}

	@Override
	public void set(PedalSettings settings) {
		this.targetGain = Math.pow(10, settings.value("gain") * 5 / 20);
		this.targetLevel = Pedals.level(settings.value("level"));
		this.tone.setLowPass(1500 * Math.pow(2, settings.value("tone") * 0.4), Math.sqrt(0.5));
		if( this.first ) {
			this.first = false;
			this.gain = this.targetGain;
			this.level = this.targetLevel;
		}
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double fromGain = this.gain;
		double fromLevel = this.level;
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			out[i] = (float) (this.emphasis.tick(this.tighten.tick(in[i])) * (fromGain + (this.targetGain - fromGain) * t));
		}
		this.gain = this.targetGain;
		int count = this.oversampler.up(out, n, this.high);
		for( int i = 0 ; i < count ; i++ ) {
			this.high[i] = (float) Math.max(BOTTOM, Math.min(TOP, this.high[i]));
		}
		this.oversampler.down(this.high, count, out);
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			out[i] = (float) (this.tone.tick(this.dcBlock.tick(out[i])) * (fromLevel + (this.targetLevel - fromLevel) * t));
		}
		this.level = this.targetLevel;
	}

	@Override
	public void reset() {
		for( Biquad biquad : new Biquad[] { this.tighten, this.emphasis, this.dcBlock, this.tone } ) {
			biquad.reset();
		}
		this.oversampler.reset();
	}
}
