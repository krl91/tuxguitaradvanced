package app.tuxguitar.audio.engine.frontend;

/**
 * The input front end applied to every hop before analysis (spec 002, FR-060…FR-065): the input
 * gain first (so the level meter, the level check and the recognition all see the boosted signal),
 * then, when noise reduction is on, the rumble filter, the noise gate and optionally the spectral
 * subtraction. With a neutral gain and noise reduction off, a hop is left bit-for-bit as it came.
 *
 * <p>{@link #process} and {@link #configure} are called by the analysis thread only; the setters may
 * be called from any thread.</p>
 */
public final class FrontEnd {

	private final InputGain gain = new InputGain();
	private volatile boolean noiseReduction;
	private volatile boolean subtraction;
	private volatile boolean resetRequested;
	private int hopSize = SpectralSubtractor.HOP;
	private HighPass highPass = new HighPass(48000);
	private NoiseEstimator estimator = new NoiseEstimator(48000, SpectralSubtractor.HOP);
	private NoiseGate gate = new NoiseGate(this.estimator);
	private final SpectralSubtractor subtractor = new SpectralSubtractor();

	/** Adapts the stages to the sample rate and hop size actually negotiated (analysis thread). */
	public void configure(int sampleRate, int hopSize) {
		this.hopSize = hopSize;
		this.highPass = new HighPass(sampleRate);
		this.estimator = new NoiseEstimator(sampleRate, hopSize);
		this.gate = new NoiseGate(this.estimator);
		this.subtractor.reset();
	}

	public InputGain getGain() {
		return this.gain;
	}

	public boolean isNoiseReduction() {
		return this.noiseReduction;
	}

	public void setNoiseReduction(boolean enabled) {
		if( enabled != this.noiseReduction ) {
			this.resetRequested = true;
		}
		this.noiseReduction = enabled;
	}

	/** Whether the spectral subtraction runs inside the noise reduction (off by default until the benchmark justifies it). */
	public boolean isSubtraction() {
		return this.subtraction;
	}

	public void setSubtraction(boolean enabled) {
		if( enabled != this.subtraction ) {
			this.resetRequested = true;
		}
		this.subtraction = enabled;
	}

	/** True when the front end changes nothing (neutral gain, no noise reduction). */
	public boolean isNeutral() {
		return this.gain.isNeutral() && !this.noiseReduction;
	}

	public void process(float[] hop, int length) {
		this.gain.process(hop, length);
		if( !this.noiseReduction ) {
			return;
		}
		if( this.resetRequested ) {
			this.resetRequested = false;
			reset();
		}
		this.highPass.process(hop, length);
		this.gate.process(hop, length);
		if( this.subtraction && length == SpectralSubtractor.HOP && this.hopSize == SpectralSubtractor.HOP ) {
			this.subtractor.setQuiet(!this.gate.isOpen());
			this.subtractor.process(hop, length);
		}
	}

	/** Forgets all history (a gap in the input, the stages being switched on). */
	public void reset() {
		this.highPass.reset();
		this.gate.reset();
		this.estimator.reset();
		this.subtractor.reset();
	}

	/** Delay added by the stages currently in use, in analysis hops (counted in the latency budget, FR-065). */
	public int latencyHops() {
		return (this.noiseReduction && this.subtraction ? this.subtractor.latencyHops() : 0);
	}
}
