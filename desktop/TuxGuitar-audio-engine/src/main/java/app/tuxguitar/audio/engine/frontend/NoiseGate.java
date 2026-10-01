package app.tuxguitar.audio.engine.frontend;

/**
 * Noise gate driven by the estimated background noise (spec 002, FR-064). It decides once per hop,
 * from the hop's RMS: opens at once when the level exceeds the floor by {@link #OPEN_MARGIN_DB},
 * stays open until the level has stayed under floor + {@link #CLOSE_MARGIN_DB} for the hold time, then
 * lowers the hop by {@link #REDUCTION_DB} (ramped within the hop, no click). Because it opens as soon
 * as a hop is loud, the start of a pluck is never attenuated, and a decaying note is kept while it
 * stays above the noise. No latency.
 */
public final class NoiseGate implements FrontEndStage {

	public static final double OPEN_MARGIN_DB = 9.0;
	public static final double CLOSE_MARGIN_DB = 5.0;
	public static final double REDUCTION_DB = 20.0;
	/** About 100 ms with 256-sample hops at 48 kHz. */
	public static final int HOLD_HOPS = 19;

	private final NoiseEstimator estimator;
	private final double reduction = Math.pow(10.0, -REDUCTION_DB / 20.0);
	private boolean open;
	private int hold;
	private double lastGain = 1.0;

	public NoiseGate(NoiseEstimator estimator) {
		this.estimator = estimator;
	}

	@Override
	public void process(float[] hop, int length) {
		double level = NoiseEstimator.rms(hop, length);
		this.estimator.update(level);
		double floor = this.estimator.getFloorRms();
		double openThreshold = floor * Math.pow(10.0, OPEN_MARGIN_DB / 20.0);
		double closeThreshold = floor * Math.pow(10.0, CLOSE_MARGIN_DB / 20.0);
		if( level > openThreshold ) {
			this.open = true;
			this.hold = HOLD_HOPS;
		} else if( this.open ) {
			if( level < closeThreshold ) {
				this.hold --;
				if( this.hold <= 0 ) {
					this.open = false;
				}
			} else {
				this.hold = HOLD_HOPS;
			}
		}
		double target = (this.open || !this.estimator.isTrusted() ? 1.0 : this.reduction);
		if( target < this.lastGain ) {
			// closing: ramp down within the hop, so there is no click
			for( int i = 0 ; i < length ; i ++ ) {
				double gain = this.lastGain + (target - this.lastGain) * (i + 1) / length;
				hop[i] = (float) (hop[i] * gain);
			}
		} else if( target < 1.0 ) {
			// still closed
			for( int i = 0 ; i < length ; i ++ ) {
				hop[i] = (float) (hop[i] * target);
			}
		}
		// opening (or open): the hop goes through untouched, so the start of a pluck is never attenuated
		this.lastGain = target;
	}

	/** True while the gate lets the signal through; the noise estimate and subtraction learn when it is closed. */
	public boolean isOpen() {
		return this.open;
	}

	@Override
	public void reset() {
		this.open = false;
		this.hold = 0;
		this.lastGain = 1.0;
	}

	@Override
	public int latencyHops() {
		return 0;
	}
}
