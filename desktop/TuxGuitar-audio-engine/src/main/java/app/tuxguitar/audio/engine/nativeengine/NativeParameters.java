package app.tuxguitar.audio.engine.nativeengine;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.frontend.InputGain;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The settings of the native engine's analysis as a {@code float[]} (spec 023, contract §1 {@code open} and {@code configure}): the values the Java
 * analysis reads from the {@link RecognitionSettings} (the level analyser and the articulation detector of {@link AudioEngine}, the strum clip meter),
 * the live front-end values, and the pitch estimator's threshold, each at its index. The crate's {@code src/analysis/mod.rs} reads the same indices.
 */
public final class NativeParameters {

	public static final int LEVEL_TOO_LOW = 0;
	public static final int LEVEL_TOO_HIGH = 1;
	public static final int LEVEL_CLIP_SAMPLE = 2;
	public static final int LEVEL_CLIP_RATIO = 3;
	public static final int ONSET_FLUX_FACTOR = 4;
	public static final int ONSET_FLUX_OFFSET = 5;
	public static final int ONSET_MIN_LEVEL = 6;
	public static final int ONSET_REFRACTORY = 7;
	public static final int ONSET_GATE = 8;
	public static final int ONSET_PRE_FIRST = 9;
	public static final int ONSET_PRE_LAST = 10;
	public static final int ONSET_POST_FIRST = 11;
	public static final int ONSET_POST_LAST = 12;
	public static final int FRONT_GAIN = 13;
	public static final int FRONT_NOISE_REDUCTION = 14;
	public static final int FRONT_SUBTRACTION = 15;
	public static final int STRUM_ARM_FLOOR = 16;
	public static final int STRUM_CLIP_SAMPLE = 17;
	public static final int STRUM_WEAK = 18;
	public static final int STRUM_LOUD = 19;
	public static final int YIN_THRESHOLD = 20;
	public static final int LENGTH = 21;

	/** The absolute threshold of the YIN pitch estimator (de Cheveigné and Kawahara 2002), the value of the Java engine. */
	public static final double DEFAULT_YIN_THRESHOLD = 0.20;

	/** The windows of the articulation detector built by {@link ArticulationDetector#ArticulationDetector(double, double, double, int, double)}. */
	private static final int NORMAL_PRE_FIRST = 19;
	private static final int NORMAL_PRE_LAST = 2;
	private static final int NORMAL_POST_FIRST = 4;
	/** The windows of {@link ArticulationDetector#fast(double, double)}. */
	private static final int FAST_PRE_FIRST = 8;
	private static final int FAST_PRE_LAST = 2;
	private static final int FAST_POST_FIRST = 3;

	private NativeParameters() {
	}

	/** The values of these settings, with a neutral gain, the stored noise-reduction choice and no spectral subtraction (the front end at start). */
	public static float[] of(RecognitionSettings settings) {
		return of(settings, InputGain.NEUTRAL, settings.get(RecognitionParameters.INPUT_NOISE_REDUCTION), false);
	}

	/** The values of these settings with the live front-end values (the gain is in memory only, never a setting). */
	public static float[] of(RecognitionSettings settings, double gain, boolean noiseReduction, boolean subtraction) {
		float[] p = new float[LENGTH];
		p[LEVEL_TOO_LOW] = (float) settings.get(RecognitionParameters.INPUT_LEVEL_MIN);
		p[LEVEL_TOO_HIGH] = (float) settings.get(RecognitionParameters.INPUT_LEVEL_MAX);
		p[LEVEL_CLIP_SAMPLE] = (float) settings.get(RecognitionParameters.LEVEL_CLIP_SAMPLE);
		p[LEVEL_CLIP_RATIO] = (float) settings.get(RecognitionParameters.LEVEL_CLIP_SHARE);
		p[ONSET_FLUX_FACTOR] = (float) ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR;
		p[ONSET_FLUX_OFFSET] = (float) ArticulationDetector.DEFAULT_FLUX_OFFSET;
		p[ONSET_MIN_LEVEL] = (float) settings.get(RecognitionParameters.ATTACK_MIN_LEVEL);
		p[ONSET_REFRACTORY] = ArticulationDetector.DEFAULT_REFRACTORY_HOPS;
		p[ONSET_GATE] = (float) settings.get(RecognitionParameters.NOTE_ATTACK);
		p[ONSET_PRE_FIRST] = NORMAL_PRE_FIRST;
		p[ONSET_PRE_LAST] = NORMAL_PRE_LAST;
		p[ONSET_POST_FIRST] = NORMAL_POST_FIRST;
		p[ONSET_POST_LAST] = ArticulationDetector.DECISION_DELAY_HOPS;
		p[FRONT_GAIN] = (float) gain;
		p[FRONT_NOISE_REDUCTION] = noiseReduction ? 1f : 0f;
		p[FRONT_SUBTRACTION] = subtraction ? 1f : 0f;
		StrumClipMeter.Parameters strum = StrumClipMeter.Parameters.of(settings);
		p[STRUM_ARM_FLOOR] = (float) strum.armFloorDbfs;
		p[STRUM_CLIP_SAMPLE] = (float) strum.clipSample;
		p[STRUM_WEAK] = (float) strum.weakDbfs;
		p[STRUM_LOUD] = (float) strum.loudDbfs;
		p[YIN_THRESHOLD] = (float) DEFAULT_YIN_THRESHOLD;
		return p;
	}

	/** The values the analysis stages read, taken from the analysers the live analysis was given; the front end neutral, the strum meter's defaults. */
	public static float[] of(LevelAnalyzer levels, ArticulationDetector articulation) {
		float[] p = of(RecognitionSettings.defaults());
		p[LEVEL_TOO_LOW] = (float) levels.getTooLowRmsDbfs();
		p[LEVEL_TOO_HIGH] = (float) levels.getTooHighRmsDbfs();
		p[LEVEL_CLIP_SAMPLE] = (float) levels.getClippedSampleLevel();
		p[LEVEL_CLIP_RATIO] = (float) levels.getClippingRatioThreshold();
		p[ONSET_FLUX_FACTOR] = (float) articulation.getFluxMedianFactor();
		p[ONSET_FLUX_OFFSET] = (float) articulation.getFluxOffset();
		p[ONSET_MIN_LEVEL] = (float) articulation.getMinLevelDb();
		p[ONSET_REFRACTORY] = articulation.getRefractoryHops();
		p[ONSET_GATE] = (float) articulation.getGateDb();
		p[ONSET_PRE_FIRST] = articulation.getPreFirst();
		p[ONSET_PRE_LAST] = articulation.getPreLast();
		p[ONSET_POST_FIRST] = articulation.getPostFirst();
		p[ONSET_POST_LAST] = articulation.getDecisionDelayHops();
		p[FRONT_GAIN] = (float) InputGain.NEUTRAL;
		p[FRONT_NOISE_REDUCTION] = 0f;
		p[FRONT_SUBTRACTION] = 0f;
		return p;
	}

	/** A copy of {@code parameters} with the windows of the fast-run articulation detector (the parity tests of the fast mode). */
	public static float[] withFastOnset(float[] parameters) {
		float[] p = parameters.clone();
		p[ONSET_REFRACTORY] = ArticulationDetector.FAST_REFRACTORY_HOPS;
		p[ONSET_PRE_FIRST] = FAST_PRE_FIRST;
		p[ONSET_PRE_LAST] = FAST_PRE_LAST;
		p[ONSET_POST_FIRST] = FAST_POST_FIRST;
		p[ONSET_POST_LAST] = ArticulationDetector.FAST_DECISION_DELAY_HOPS;
		return p;
	}
}
