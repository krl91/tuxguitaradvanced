package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/**
 * The compressor (spec 022 FR-005, research R4): a feed-forward RMS detector (5 ms), a soft knee of 6 dB and make-up gain. {@code sustain} sets the
 * amount: at 0 nothing is compressed, at 10 the threshold is −50 dBFS with a ratio of 10:1, and half the reduction at the threshold is made up, so quiet
 * notes come out louder and last longer. The gain follows the target at the {@code attack} time going down and in 50 ms going up; the make-up is part of
 * that gain, so a change of sustain glides instead of jumping, and it starts from unity (0 dB) rather than from a make-up that
 * would burst out before the detector has heard the note. {@code level} is the output, 5 being unity.
 */
public final class Compressor implements Pedal {

	private static final double KNEE_DB = 6;
	private static final double RELEASE_SECONDS = 0.050;

	private float sampleRate = 48000f;
	private double rmsCoefficient;
	private double meanSquare;
	private double thresholdDb = -10;
	private double ratio = 1;
	private double makeupDb;
	private double attackCoefficient;
	private double releaseCoefficient;
	private double gainDb;
	private double level = 1;
	private double targetLevel = 1;
	private boolean first = true;

	@Override
	public void prepare(float rate, int maxBlock) {
		this.sampleRate = rate;
		this.rmsCoefficient = 1.0 - Math.exp(-1.0 / (0.005 * rate));
		this.releaseCoefficient = 1.0 - Math.exp(-1.0 / (RELEASE_SECONDS * rate));
		this.attackCoefficient = 1.0 - Math.exp(-1.0 / (0.010 * rate));
	}

	@Override
	public void set(PedalSettings settings) {
		double sustain = settings.value("sustain");
		this.ratio = 1 + 0.9 * sustain;
		this.thresholdDb = -10 - 4 * sustain;
		this.makeupDb = -this.thresholdDb * (1 - 1 / this.ratio) * 0.5;
		this.attackCoefficient = 1.0 - Math.exp(-1.0 / (settings.value("attack") / 1000.0 * this.sampleRate));
		this.targetLevel = Pedals.level(settings.value("level"));
		if( this.first ) {
			this.first = false;
			this.level = this.targetLevel;
		}
	}

	private double reductionDb(double levelDb) {
		double over = levelDb - this.thresholdDb;
		double slope = 1 - 1 / this.ratio;
		if( over <= -KNEE_DB / 2 ) {
			return 0;
		}
		if( over >= KNEE_DB / 2 ) {
			return -over * slope;
		}
		double inKnee = over + KNEE_DB / 2;
		return -slope * inKnee * inKnee / (2 * KNEE_DB);
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double from = this.level;
		for( int i = 0 ; i < n ; i++ ) {
			double x = in[i];
			this.meanSquare = Pedals.flush(this.meanSquare + (x * x - this.meanSquare) * this.rmsCoefficient);
			double levelDb = 10 * Math.log10(this.meanSquare + 1e-12);
			double target = reductionDb(levelDb) + this.makeupDb;
			double coefficient = target < this.gainDb ? this.attackCoefficient : this.releaseCoefficient;
			this.gainDb += (target - this.gainDb) * coefficient;
			double t = (i + 1) / (double) n;
			out[i] = (float) (x * Math.pow(10, this.gainDb / 20) * (from + (this.targetLevel - from) * t));
		}
		this.level = this.targetLevel;
	}

	@Override
	public void reset() {
		this.meanSquare = 0;
		this.gainDb = 0;
	}
}
