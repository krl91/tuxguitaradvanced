package app.tuxguitar.audio.engine.frontend;

/**
 * Tracks the level of the background noise (spec 002, FR-064): it drops quickly to a quieter hop and
 * rises only slowly, so a long sustained note does not become "the noise floor". It can be seeded
 * with the silent part of the level check. Levels are linear RMS of a hop.
 */
public final class NoiseEstimator {

	/** Source of the current estimate, for diagnostics. */
	public enum Source {
		NONE,
		LEVEL_CHECK,
		QUIET_TRACKING
	}

	/** The floor never goes below this (about −96 dBFS), so a digital silence does not collapse it to zero. */
	private static final double MINIMUM_RMS = 1.5e-5;
	/** How fast the floor may rise toward a louder hop: 0.5 dB per second. */
	private static final double RISE_DB_PER_SECOND = 0.5;
	/** How fast it falls to a quieter hop (share of the gap closed per hop). */
	private static final double FALL_RATE = 0.2;
	/** Hops needed before the estimate is trusted (about one second at 48 kHz, 256-sample hops). */
	public static final int TRUSTED_AFTER_HOPS = 188;

	private final double riseFactorPerHop;
	private double floorRms;
	private int hops;
	private Source source = Source.NONE;

	public NoiseEstimator(int sampleRate, int hopSize) {
		double secondsPerHop = hopSize / (double) sampleRate;
		this.riseFactorPerHop = Math.pow(10.0, RISE_DB_PER_SECOND * secondsPerHop / 20.0);
		reset();
	}

	/** Updates the estimate with the RMS of the newest hop. */
	public void update(double hopRms) {
		double level = Math.max(MINIMUM_RMS, hopRms);
		if( this.hops == 0 || this.floorRms <= 0.0 ) {
			this.floorRms = level;
		} else if( level < this.floorRms ) {
			this.floorRms += FALL_RATE * (level - this.floorRms);
		} else {
			this.floorRms = Math.min(level, this.floorRms * this.riseFactorPerHop);
		}
		this.hops ++;
		if( this.source != Source.LEVEL_CHECK ) {
			this.source = Source.QUIET_TRACKING;
		}
	}

	/** Starts from the level measured while the learner was silent (the level check). */
	public void seed(double silentRms) {
		this.floorRms = Math.max(MINIMUM_RMS, silentRms);
		this.hops = Math.max(this.hops, 1);
		this.source = Source.LEVEL_CHECK;
	}

	public double getFloorRms() {
		return this.floorRms;
	}

	public double getFloorDbfs() {
		return 20.0 * Math.log10(Math.max(MINIMUM_RMS, this.floorRms));
	}

	public boolean isTrusted() {
		return this.source == Source.LEVEL_CHECK || this.hops >= TRUSTED_AFTER_HOPS;
	}

	public Source getSource() {
		return this.source;
	}

	public final void reset() {
		this.floorRms = 0.0;
		this.hops = 0;
		this.source = Source.NONE;
	}

	/** RMS of the first {@code length} samples. */
	public static double rms(float[] samples, int length) {
		double sum = 0.0;
		for( int i = 0 ; i < length ; i ++ ) {
			sum += (double) samples[i] * samples[i];
		}
		return Math.sqrt(sum / Math.max(1, length));
	}
}
