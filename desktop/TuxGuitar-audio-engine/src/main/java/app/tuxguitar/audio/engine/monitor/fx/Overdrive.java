package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/**
 * The overdrive (spec 022 FR-005, research R4): a mid bump (720 Hz, +4 dB) and the {@code tone} low-pass (700 Hz at 0 to above 20 kHz at 10) before a
 * {@code tanh} soft clipping run at 2× oversampling. {@code drive} raises the gain into the clipping by 4 dB a step; {@code level} is the output, 5 being
 * unity. Gains ramp over a block and the filters glide.
 */
public final class Overdrive implements Pedal {

	private final Biquad bump = new Biquad();
	private final Biquad tone = new Biquad();
	private Oversampler oversampler;
	private float[] high;
	private double drive = 1;
	private double targetDrive = 1;
	private double level = 1;
	private double targetLevel = 1;
	private boolean first = true;

	@Override
	public void prepare(float rate, int maxBlock) {
		this.bump.prepare(rate, maxBlock);
		this.tone.prepare(rate, maxBlock);
		this.bump.setPeak(720, 0.7, 4);
		this.tone.setLowPass(4000, Math.sqrt(0.5));
		this.oversampler = new Oversampler(2, maxBlock);
		this.high = new float[2 * maxBlock];
	}

	@Override
	public void set(PedalSettings settings) {
		this.targetDrive = Math.pow(10, settings.value("drive") * 4 / 20);
		this.targetLevel = Pedals.level(settings.value("level"));
		this.tone.setLowPass(700 * Math.pow(2, settings.value("tone") / 2), Math.sqrt(0.5));
		if( this.first ) {
			this.first = false;
			this.drive = this.targetDrive;
			this.level = this.targetLevel;
		}
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double fromDrive = this.drive;
		double fromLevel = this.level;
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			out[i] = (float) (this.tone.tick(this.bump.tick(in[i])) * (fromDrive + (this.targetDrive - fromDrive) * t));
		}
		this.drive = this.targetDrive;
		int count = this.oversampler.up(out, n, this.high);
		for( int i = 0 ; i < count ; i++ ) {
			this.high[i] = (float) Math.tanh(this.high[i]);
		}
		this.oversampler.down(this.high, count, out);
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			out[i] = (float) (out[i] * (fromLevel + (this.targetLevel - fromLevel) * t));
		}
		this.level = this.targetLevel;
	}

	@Override
	public void reset() {
		this.bump.reset();
		this.tone.reset();
		this.oversampler.reset();
	}
}
