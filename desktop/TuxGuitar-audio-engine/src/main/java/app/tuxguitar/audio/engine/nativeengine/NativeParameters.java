package app.tuxguitar.audio.engine.nativeengine;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.AttackAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.frontend.FrontEnd;
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

	/**
	 * The length of the tuning array (spec 026, contracts/native-parameters.md): every other value a native stage reads, at the indices
	 * {@code T_…} that each engine family adds, the same names in the crate's {@code analysis/mod.rs}. The native library reports its own
	 * length ({@code tuningLayout()}); a library with another one is not used.
	 */
	public static final int T_TARGET_SEARCH = 0;
	public static final int T_TARGET_PERIODS = 1;
	public static final int T_POLY_CANDIDATE_LOW = 2;
	public static final int T_POLY_CANDIDATE_HIGH = 3;
	public static final int T_POLY_HARMONICS = 4;
	public static final int T_POLY_MAX_HARMONIC = 5;
	public static final int T_POLY_TOLERANCE = 6;
	public static final int T_POLY_CANDIDATES = 7;
	public static final int T_POLY_MIN_RELATIVE = 8;
	public static final int T_POLY_FLOOR = 9;
	public static final int T_POLY_SUBHARMONIC = 10;
	public static final int T_POLY_FLOOR_WIDTH = 11;
	public static final int T_POLY_FLOOR_FACTOR = 12;
	public static final int T_ATTACK_LAG = 13;
	public static final int T_ATTACK_MAX_FREQUENCY = 14;
	public static final int T_ATTACK_PARTIALS = 15;
	public static final int T_ATTACK_BETA = 16;
	public static final int T_ATTACK_SIGMA = 17;
	public static final int T_CHORD_PARTIAL_PRESENCE = 18;
	public static final int T_CHORD_NEIGHBOUR_MARGIN = 19;
	/** The strum meter's window, in milliseconds. */
	public static final int T_STRUM_WINDOW = 20;
	public static final int T_STRUM_QUIET = 21;
	public static final int T_STRUM_RISE = 22;
	public static final int T_STRUM_CLIP_SHARE = 23;
	public static final int T_FRONT_HIGH_PASS = 24;
	public static final int T_FRONT_NOISE_RISE = 25;
	public static final int T_FRONT_NOISE_FALL = 26;
	public static final int T_FRONT_NOISE_TRUSTED = 27;
	public static final int T_GATE_OPEN = 28;
	public static final int T_GATE_CLOSE = 29;
	public static final int T_GATE_REDUCTION = 30;
	public static final int T_GATE_HOLD = 31;
	public static final int T_SUBTRACTION = 32;
	public static final int T_SUBTRACTION_FLOOR = 33;
	public static final int T_SUBTRACTION_LEARN = 34;
	public static final int TUNING_LENGTH = 35;

	/** The absolute threshold of the YIN pitch estimator (de Cheveigné and Kawahara 2002), the value of the Java engine. */
	public static final double DEFAULT_YIN_THRESHOLD = 0.20;

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
		p[ONSET_FLUX_FACTOR] = (float) settings.get(RecognitionParameters.ONSET_FLUX_FACTOR);
		p[ONSET_FLUX_OFFSET] = (float) settings.get(RecognitionParameters.ONSET_FLUX_OFFSET);
		p[ONSET_MIN_LEVEL] = (float) settings.get(RecognitionParameters.ATTACK_MIN_LEVEL);
		p[ONSET_REFRACTORY] = (float) settings.get(RecognitionParameters.ONSET_REFRACTORY);
		p[ONSET_GATE] = (float) settings.get(RecognitionParameters.NOTE_ATTACK);
		p[ONSET_PRE_FIRST] = (float) settings.get(RecognitionParameters.ONSET_PRE_FIRST);
		p[ONSET_PRE_LAST] = (float) settings.get(RecognitionParameters.ONSET_PRE_LAST);
		p[ONSET_POST_FIRST] = (float) settings.get(RecognitionParameters.ONSET_POST_FIRST);
		p[ONSET_POST_LAST] = (float) settings.get(RecognitionParameters.ONSET_POST_LAST);
		p[FRONT_GAIN] = (float) gain;
		p[FRONT_NOISE_REDUCTION] = noiseReduction ? 1f : 0f;
		p[FRONT_SUBTRACTION] = subtraction ? 1f : 0f;
		StrumClipMeter.Parameters strum = StrumClipMeter.Parameters.of(settings);
		p[STRUM_ARM_FLOOR] = (float) strum.armFloorDbfs;
		p[STRUM_CLIP_SAMPLE] = (float) strum.clipSample;
		p[STRUM_WEAK] = (float) strum.weakDbfs;
		p[STRUM_LOUD] = (float) strum.loudDbfs;
		p[YIN_THRESHOLD] = (float) settings.get(RecognitionParameters.PITCH_YIN_THRESHOLD);
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

	/** The tuning array of {@code settings}, in the order of the {@code T_…} indices. */
	public static double[] tuning(RecognitionSettings settings) {
		double[] t = new double[TUNING_LENGTH];
		t[T_TARGET_SEARCH] = settings.get(RecognitionParameters.PITCH_TARGET_SEARCH);
		t[T_TARGET_PERIODS] = settings.get(RecognitionParameters.PITCH_TARGET_PERIODS);
		PolyphonicAnalyzer.Parameters p = PolyphonicAnalyzer.Parameters.of(settings);
		t[T_POLY_CANDIDATE_LOW] = p.minCandidateMidi;
		t[T_POLY_CANDIDATE_HIGH] = p.maxCandidateMidi;
		t[T_POLY_HARMONICS] = p.harmonics;
		t[T_POLY_MAX_HARMONIC] = p.maxHarmonicHz;
		t[T_POLY_TOLERANCE] = p.harmonicToleranceCents;
		t[T_POLY_CANDIDATES] = p.maxCandidates;
		t[T_POLY_MIN_RELATIVE] = p.minRelativeSalience;
		t[T_POLY_FLOOR] = p.floorDb;
		t[T_POLY_SUBHARMONIC] = p.subharmonicRatio;
		t[T_POLY_FLOOR_WIDTH] = p.floorHalfWidthHz;
		t[T_POLY_FLOOR_FACTOR] = p.floorFactor;
		AttackAnalyzer.Parameters a = AttackAnalyzer.Parameters.of(settings);
		t[T_ATTACK_LAG] = a.lagHops;
		t[T_ATTACK_MAX_FREQUENCY] = a.maxHz;
		t[T_ATTACK_PARTIALS] = a.partials;
		t[T_ATTACK_BETA] = a.beta;
		t[T_ATTACK_SIGMA] = a.sigmaBins;
		t[T_CHORD_PARTIAL_PRESENCE] = p.partialPresenceRatio;
		t[T_CHORD_NEIGHBOUR_MARGIN] = p.neighbourMargin;
		t[T_STRUM_WINDOW] = settings.get(RecognitionParameters.CHORD_EVIDENCE_STRUM_WINDOW);
		t[T_STRUM_QUIET] = settings.get(RecognitionParameters.CHORD_EVIDENCE_STRUM_QUIET);
		t[T_STRUM_RISE] = settings.get(RecognitionParameters.CHORD_EVIDENCE_STRUM_RISE);
		t[T_STRUM_CLIP_SHARE] = settings.get(RecognitionParameters.CHORD_EVIDENCE_STRUM_CLIP_SHARE);
		FrontEnd.Parameters f = FrontEnd.Parameters.of(settings);
		t[T_FRONT_HIGH_PASS] = f.highPassHz;
		t[T_FRONT_NOISE_RISE] = f.noiseRiseDbPerSecond;
		t[T_FRONT_NOISE_FALL] = f.noiseFallRate;
		t[T_FRONT_NOISE_TRUSTED] = f.noiseTrustedHops;
		t[T_GATE_OPEN] = f.gateOpenDb;
		t[T_GATE_CLOSE] = f.gateCloseDb;
		t[T_GATE_REDUCTION] = f.gateReductionDb;
		t[T_GATE_HOLD] = f.gateHoldHops;
		t[T_SUBTRACTION] = f.subtractionAlpha;
		t[T_SUBTRACTION_FLOOR] = f.subtractionBeta;
		t[T_SUBTRACTION_LEARN] = f.subtractionLearnRate;
		return t;
	}

	/** A copy of {@code parameters} with the recommended values of the fast-run articulation detector (the parity tests of the fast mode). */
	public static float[] withFastOnset(float[] parameters) {
		return withFastOnset(parameters, RecognitionSettings.defaults());
	}

	/** A copy of {@code parameters} with the fast-run onset values of {@code settings} (spec 026, section {@code ONSET_FAST}). */
	public static float[] withFastOnset(float[] parameters, RecognitionSettings settings) {
		float[] p = parameters.clone();
		p[ONSET_FLUX_FACTOR] = (float) settings.get(RecognitionParameters.ONSET_FAST_FLUX_FACTOR);
		p[ONSET_FLUX_OFFSET] = (float) settings.get(RecognitionParameters.ONSET_FAST_FLUX_OFFSET);
		p[ONSET_REFRACTORY] = (float) settings.get(RecognitionParameters.ONSET_FAST_REFRACTORY);
		p[ONSET_PRE_FIRST] = (float) settings.get(RecognitionParameters.ONSET_FAST_PRE_FIRST);
		p[ONSET_PRE_LAST] = (float) settings.get(RecognitionParameters.ONSET_FAST_PRE_LAST);
		p[ONSET_POST_FIRST] = (float) settings.get(RecognitionParameters.ONSET_FAST_POST_FIRST);
		p[ONSET_POST_LAST] = (float) settings.get(RecognitionParameters.ONSET_FAST_POST_LAST);
		return p;
	}

	/** The polyphonic analyser's parameters a tuning array carries: those the native engine analysed with (spec 026). */
	public static PolyphonicAnalyzer.Parameters polyphonic(double[] tuning) {
		PolyphonicAnalyzer.Parameters d = PolyphonicAnalyzer.Parameters.defaults();
		return new PolyphonicAnalyzer.Parameters(d.fftSize, d.minBandMidi, d.maxBandMidi, (int) Math.round(tuning[T_POLY_CANDIDATE_LOW]),
				(int) Math.round(tuning[T_POLY_CANDIDATE_HIGH]), (int) Math.round(tuning[T_POLY_HARMONICS]), tuning[T_POLY_MAX_HARMONIC],
				tuning[T_POLY_TOLERANCE], (int) Math.round(tuning[T_POLY_CANDIDATES]), tuning[T_POLY_MIN_RELATIVE], tuning[T_POLY_FLOOR],
				tuning[T_POLY_SUBHARMONIC], tuning[T_POLY_FLOOR_WIDTH], tuning[T_POLY_FLOOR_FACTOR], tuning[T_CHORD_PARTIAL_PRESENCE],
				tuning[T_CHORD_NEIGHBOUR_MARGIN]);
	}
}
