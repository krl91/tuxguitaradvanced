package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.PedalSettings;
import app.tuxguitar.audio.engine.monitor.Pedal;

/**
 * The noise gate (spec 022 FR-011, research R4). A 5 ms RMS detector opens it above the threshold (within a millisecond for a note) and,
 * after a 10 ms hold, lets it close 3 dB under the threshold. Closing lowers the gain in decibels at a steady rate, 60 dB over the release
 * time, down to -80 dB, so it never clicks.
 */
public final class NoiseGate implements Pedal {

	private static final double FLOOR_DB = -80;
	private static final double HYSTERESIS_DB = 3;
	private static final double HOLD_SECONDS = 0.010;
	private static final double OPEN_SECONDS = 0.00025;

	private float sampleRate = 48000f;
	private double thresholdDb = -60;
	private double releaseMillis = 150;
	private double meanSquare;
	private double rmsCoefficient;
	private double gainDb = FLOOR_DB;
	private boolean open;
	private int holdLeft;
	private int holdSamples;
	private double openStep;
	private double closeStep;

	@Override
	public void prepare(float rate, int maxBlock) {
		this.sampleRate = rate;
		this.rmsCoefficient = 1.0 - Math.exp(-1.0 / (0.005 * rate));
		this.holdSamples = (int) Math.round(HOLD_SECONDS * rate);
		this.openStep = -FLOOR_DB / (OPEN_SECONDS * rate);
		updateClose();
	}

	@Override
	public void set(PedalSettings settings) {
		this.thresholdDb = settings.value("threshold");
		this.releaseMillis = settings.value("release");
		updateClose();
	}

	private void updateClose() {
		this.closeStep = 60.0 / (this.releaseMillis / 1000.0 * this.sampleRate);
	}

	/** The gain applied now, linear (for the tests). */
	double gain() {
		return Math.pow(10, this.gainDb / 20);
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double openLevel = Math.pow(10, this.thresholdDb / 10);
		double closeLevel = Math.pow(10, (this.thresholdDb - HYSTERESIS_DB) / 10);
		for( int i = 0 ; i < n ; i++ ) {
			double x = in[i];
			this.meanSquare += (x * x - this.meanSquare) * this.rmsCoefficient;
			if( this.meanSquare < 1e-30 ) {
				this.meanSquare = 0;
			}
			if( this.meanSquare > openLevel ) {
				this.open = true;
				this.holdLeft = this.holdSamples;
			} else if( this.open && this.meanSquare < closeLevel ) {
				if( this.holdLeft > 0 ) {
					this.holdLeft--;
				} else {
					this.open = false;
				}
			}
			if( this.open ) {
				this.gainDb = Math.min(0, this.gainDb + this.openStep);
			} else {
				this.gainDb = Math.max(FLOOR_DB, this.gainDb - this.closeStep);
			}
			out[i] = (float) (x * Math.pow(10, this.gainDb / 20));
		}
	}

	@Override
	public void reset() {
		this.meanSquare = 0;
		this.gainDb = FLOOR_DB;
		this.open = false;
		this.holdLeft = 0;
	}
}
