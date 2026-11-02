package app.tuxguitar.audio.engine.monitor.fx;

import app.tuxguitar.audio.engine.monitor.Pedal;
import app.tuxguitar.audio.engine.monitor.PedalSettings;

/**
 * The reverb (spec 022 FR-005, research R4): the public Schroeder–Moorer structure known as "Freeverb", rewritten. Eight comb filters in parallel, each
 * with a one-pole low-pass in its loop, then four all-pass filters in series; the lengths are the structure's published ones, scaled to the sample rate.
 * {@code size} sets the combs' feedback (0.70 to 0.98, a longer tail), {@code damping} the low-pass in their loops (darker as the tail decays) and
 * {@code mix} the share of the reverb (0 is the dry signal exactly). Every knob ramps over a block.
 */
public final class Reverb implements Pedal {

	private static final int[] COMBS = { 1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617 };
	private static final int[] ALL_PASSES = { 556, 441, 341, 225 };
	private static final double INPUT_GAIN = 0.015;
	private static final double WET_GAIN = 3.0;

	private float[][] combs;
	private int[] combIndex;
	private double[] combLowPass;
	private float[][] allPasses;
	private int[] allPassIndex;
	private double feedback;
	private double targetFeedback;
	private double damping;
	private double targetDamping;
	private double mix;
	private double targetMix;
	private boolean first = true;
	private double opening = 1.0;
	private double openingStep;

	@Override
	public void prepare(float rate, int maxBlock) {
		double scale = rate / 44100.0;
		this.combs = new float[COMBS.length][];
		for( int i = 0 ; i < COMBS.length ; i++ ) {
			this.combs[i] = new float[Math.max(1, (int) Math.round(COMBS[i] * scale))];
		}
		this.combIndex = new int[COMBS.length];
		this.combLowPass = new double[COMBS.length];
		this.allPasses = new float[ALL_PASSES.length][];
		for( int i = 0 ; i < ALL_PASSES.length ; i++ ) {
			this.allPasses[i] = new float[Math.max(1, (int) Math.round(ALL_PASSES[i] * scale))];
		}
		this.allPassIndex = new int[ALL_PASSES.length];
		this.openingStep = 1.0 / (0.010 * rate);
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
		this.targetFeedback = 0.70 + 0.028 * settings.value("size");
		this.targetDamping = 0.04 * settings.value("damping");
		this.targetMix = settings.value("mix") / 100.0;
		if( this.first ) {
			this.first = false;
			this.feedback = this.targetFeedback;
			this.damping = this.targetDamping;
			this.mix = this.targetMix;
		}
	}

	@Override
	public void process(float[] in, float[] out, int n) {
		double fromFeedback = this.feedback;
		double fromDamping = this.damping;
		double fromMix = this.mix;
		for( int i = 0 ; i < n ; i++ ) {
			double t = (i + 1) / (double) n;
			double feedbackNow = fromFeedback + (this.targetFeedback - fromFeedback) * t;
			double dampingNow = fromDamping + (this.targetDamping - fromDamping) * t;
			double mixNow = fromMix + (this.targetMix - fromMix) * t;
			double x = in[i];
			double input = x * INPUT_GAIN * opening();
			double sum = 0;
			for( int c = 0 ; c < this.combs.length ; c++ ) {
				float[] comb = this.combs[c];
				int index = this.combIndex[c];
				double delayed = comb[index];
				this.combLowPass[c] = Pedals.flush(delayed * (1 - dampingNow) + this.combLowPass[c] * dampingNow);
				comb[index] = (float) Pedals.flush(input + this.combLowPass[c] * feedbackNow);
				this.combIndex[c] = index + 1 == comb.length ? 0 : index + 1;
				sum += delayed;
			}
			for( int a = 0 ; a < this.allPasses.length ; a++ ) {
				float[] allPass = this.allPasses[a];
				int index = this.allPassIndex[a];
				double delayed = allPass[index];
				allPass[index] = (float) Pedals.flush(sum + delayed * 0.5);
				sum = delayed - sum;
				this.allPassIndex[a] = index + 1 == allPass.length ? 0 : index + 1;
			}
			out[i] = (float) (x * (1 - mixNow) + sum * WET_GAIN * mixNow);
		}
		this.feedback = this.targetFeedback;
		this.damping = this.targetDamping;
		this.mix = this.targetMix;
	}

	@Override
	public void reset() {
		for( int c = 0 ; c < this.combs.length ; c++ ) {
			java.util.Arrays.fill(this.combs[c], 0f);
			this.combLowPass[c] = 0;
		}
		for( float[] allPass : this.allPasses ) {
			java.util.Arrays.fill(allPass, 0f);
		}
		this.opening = 0;
	}
}
