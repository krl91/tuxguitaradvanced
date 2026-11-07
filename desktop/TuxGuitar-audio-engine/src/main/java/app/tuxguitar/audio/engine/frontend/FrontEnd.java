package app.tuxguitar.audio.engine.frontend;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

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

	/** The stages' values (spec 026, sections {@code FRONT_END} and {@code NOISE_GATE}); the defaults are the former constants. Immutable. */
	public static final class Parameters {
		public final double highPassHz;
		public final double noiseRiseDbPerSecond;
		public final double noiseFallRate;
		public final int noiseTrustedHops;
		public final double gateOpenDb;
		public final double gateCloseDb;
		public final double gateReductionDb;
		public final int gateHoldHops;
		public final double subtractionAlpha;
		public final double subtractionBeta;
		public final double subtractionLearnRate;

		public Parameters(double highPassHz, double noiseRiseDbPerSecond, double noiseFallRate, int noiseTrustedHops, double gateOpenDb,
				double gateCloseDb, double gateReductionDb, int gateHoldHops, double subtractionAlpha, double subtractionBeta,
				double subtractionLearnRate) {
			this.highPassHz = highPassHz;
			this.noiseRiseDbPerSecond = noiseRiseDbPerSecond;
			this.noiseFallRate = noiseFallRate;
			this.noiseTrustedHops = noiseTrustedHops;
			this.gateOpenDb = gateOpenDb;
			this.gateCloseDb = gateCloseDb;
			this.gateReductionDb = gateReductionDb;
			this.gateHoldHops = gateHoldHops;
			this.subtractionAlpha = subtractionAlpha;
			this.subtractionBeta = subtractionBeta;
			this.subtractionLearnRate = subtractionLearnRate;
		}

		public static Parameters defaults() {
			return new Parameters(HighPass.CORNER_HZ, NoiseEstimator.DEFAULT_RISE_DB_PER_SECOND, NoiseEstimator.DEFAULT_FALL_RATE,
					NoiseEstimator.TRUSTED_AFTER_HOPS, NoiseGate.OPEN_MARGIN_DB, NoiseGate.CLOSE_MARGIN_DB, NoiseGate.REDUCTION_DB, NoiseGate.HOLD_HOPS,
					SpectralSubtractor.ALPHA, SpectralSubtractor.BETA, SpectralSubtractor.DEFAULT_LEARN_RATE);
		}

		public static Parameters of(RecognitionSettings settings) {
			return new Parameters(settings.get(RecognitionParameters.FRONT_END_HIGH_PASS), settings.get(RecognitionParameters.FRONT_END_NOISE_RISE),
					settings.get(RecognitionParameters.FRONT_END_NOISE_FALL), (int) Math.round(settings.get(RecognitionParameters.FRONT_END_NOISE_TRUSTED)),
					settings.get(RecognitionParameters.NOISE_GATE_OPEN), settings.get(RecognitionParameters.NOISE_GATE_CLOSE),
					settings.get(RecognitionParameters.NOISE_GATE_REDUCTION), (int) Math.round(settings.get(RecognitionParameters.NOISE_GATE_HOLD)),
					settings.get(RecognitionParameters.NOISE_GATE_SUBTRACTION), settings.get(RecognitionParameters.NOISE_GATE_SUBTRACTION_FLOOR),
					settings.get(RecognitionParameters.NOISE_GATE_SUBTRACTION_LEARN));
		}
	}

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
		configure(sampleRate, hopSize, Parameters.defaults());
	}

	/** As {@link #configure(int, int)}, with the stages' values of the settings (spec 026). */
	public void configure(int sampleRate, int hopSize, Parameters parameters) {
		this.hopSize = hopSize;
		this.highPass = new HighPass(sampleRate, parameters.highPassHz);
		this.estimator = new NoiseEstimator(sampleRate, hopSize, parameters.noiseRiseDbPerSecond, parameters.noiseFallRate, parameters.noiseTrustedHops);
		this.gate = new NoiseGate(this.estimator, parameters.gateOpenDb, parameters.gateCloseDb, parameters.gateReductionDb, parameters.gateHoldHops);
		this.subtractor.setValues(parameters.subtractionAlpha, parameters.subtractionBeta, parameters.subtractionLearnRate);
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
