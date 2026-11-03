package app.tuxguitar.audio.engine.nativeengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The settings handed to the native engine (spec 023, contract §1 {@code configure}): every value read by the front end, the level analyser, the
 * articulation detector, the strum clip meter and the pitch estimator lands at its documented index; the array is always {@link NativeParameters#LENGTH}
 * long.
 */
public class NativeParametersTest {

	private static final double FLOAT = 1e-6;

	@Test
	public void theDefaultsLandAtTheirIndices() {
		float[] p = NativeParameters.of(RecognitionSettings.defaults());
		Assertions.assertEquals(NativeParameters.LENGTH, p.length);
		Assertions.assertEquals(-40.0, p[NativeParameters.LEVEL_TOO_LOW], FLOAT);
		Assertions.assertEquals(-6.0, p[NativeParameters.LEVEL_TOO_HIGH], FLOAT);
		Assertions.assertEquals(0.99, p[NativeParameters.LEVEL_CLIP_SAMPLE], FLOAT);
		Assertions.assertEquals(0.10, p[NativeParameters.LEVEL_CLIP_RATIO], FLOAT);
		Assertions.assertEquals(ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, p[NativeParameters.ONSET_FLUX_FACTOR], FLOAT);
		Assertions.assertEquals(ArticulationDetector.DEFAULT_FLUX_OFFSET, p[NativeParameters.ONSET_FLUX_OFFSET], FLOAT);
		Assertions.assertEquals(-50.0, p[NativeParameters.ONSET_MIN_LEVEL], FLOAT);
		Assertions.assertEquals(ArticulationDetector.DEFAULT_REFRACTORY_HOPS, p[NativeParameters.ONSET_REFRACTORY], FLOAT);
		Assertions.assertEquals(4.5, p[NativeParameters.ONSET_GATE], FLOAT);
		Assertions.assertEquals(19, p[NativeParameters.ONSET_PRE_FIRST], FLOAT);
		Assertions.assertEquals(2, p[NativeParameters.ONSET_PRE_LAST], FLOAT);
		Assertions.assertEquals(4, p[NativeParameters.ONSET_POST_FIRST], FLOAT);
		Assertions.assertEquals(ArticulationDetector.DECISION_DELAY_HOPS, p[NativeParameters.ONSET_POST_LAST], FLOAT);
		Assertions.assertEquals(1.0, p[NativeParameters.FRONT_GAIN], FLOAT);
		Assertions.assertEquals(0.0, p[NativeParameters.FRONT_NOISE_REDUCTION], FLOAT);
		Assertions.assertEquals(0.0, p[NativeParameters.FRONT_SUBTRACTION], FLOAT);
		StrumClipMeter.Parameters strum = StrumClipMeter.Parameters.of(RecognitionSettings.defaults());
		Assertions.assertEquals(strum.armFloorDbfs, p[NativeParameters.STRUM_ARM_FLOOR], FLOAT);
		Assertions.assertEquals(strum.clipSample, p[NativeParameters.STRUM_CLIP_SAMPLE], FLOAT);
		Assertions.assertEquals(strum.weakDbfs, p[NativeParameters.STRUM_WEAK], FLOAT);
		Assertions.assertEquals(strum.loudDbfs, p[NativeParameters.STRUM_LOUD], FLOAT);
		Assertions.assertEquals(0.20, p[NativeParameters.YIN_THRESHOLD], FLOAT);
	}

	@Test
	public void aChangedProfileMovesItsValues() {
		RecognitionSettings changed = RecognitionSettings.defaults().with(RecognitionParameters.INPUT_LEVEL_MIN, -30).with(RecognitionParameters.INPUT_LEVEL_MAX, -3)
				.with(RecognitionParameters.LEVEL_CLIP_SAMPLE, 0.95).with(RecognitionParameters.LEVEL_CLIP_SHARE, 0.2).with(RecognitionParameters.ATTACK_MIN_LEVEL, -60)
				.with(RecognitionParameters.NOTE_ATTACK, 7.0).with(RecognitionParameters.INPUT_NOISE_REDUCTION, Boolean.TRUE);
		float[] p = NativeParameters.of(changed);
		Assertions.assertEquals(NativeParameters.LENGTH, p.length);
		Assertions.assertEquals(-30.0, p[NativeParameters.LEVEL_TOO_LOW], FLOAT);
		Assertions.assertEquals(-3.0, p[NativeParameters.LEVEL_TOO_HIGH], FLOAT);
		Assertions.assertEquals(0.95, p[NativeParameters.LEVEL_CLIP_SAMPLE], FLOAT);
		Assertions.assertEquals(0.2, p[NativeParameters.LEVEL_CLIP_RATIO], FLOAT);
		Assertions.assertEquals(-60.0, p[NativeParameters.ONSET_MIN_LEVEL], FLOAT);
		Assertions.assertEquals(7.0, p[NativeParameters.ONSET_GATE], FLOAT);
		Assertions.assertEquals(1.0, p[NativeParameters.FRONT_NOISE_REDUCTION], FLOAT);
		Assertions.assertEquals(-30.0, p[NativeParameters.STRUM_ARM_FLOOR], FLOAT);
		Assertions.assertEquals(0.95, p[NativeParameters.STRUM_CLIP_SAMPLE], FLOAT);
		Assertions.assertEquals(-30.0, p[NativeParameters.STRUM_WEAK], FLOAT);
		Assertions.assertEquals(-3.0, p[NativeParameters.STRUM_LOUD], FLOAT);
	}

	@Test
	public void theLiveFrontEndValuesAreGivenBesideTheSettings() {
		float[] p = NativeParameters.of(RecognitionSettings.defaults(), 2.5, true, true);
		Assertions.assertEquals(NativeParameters.LENGTH, p.length);
		Assertions.assertEquals(2.5, p[NativeParameters.FRONT_GAIN], FLOAT);
		Assertions.assertEquals(1.0, p[NativeParameters.FRONT_NOISE_REDUCTION], FLOAT);
		Assertions.assertEquals(1.0, p[NativeParameters.FRONT_SUBTRACTION], FLOAT);
	}

	@Test
	public void theFastDetectorHasItsOwnWindow() {
		float[] p = NativeParameters.withFastOnset(NativeParameters.of(RecognitionSettings.defaults()));
		Assertions.assertEquals(ArticulationDetector.FAST_REFRACTORY_HOPS, p[NativeParameters.ONSET_REFRACTORY], FLOAT);
		Assertions.assertEquals(8, p[NativeParameters.ONSET_PRE_FIRST], FLOAT);
		Assertions.assertEquals(2, p[NativeParameters.ONSET_PRE_LAST], FLOAT);
		Assertions.assertEquals(3, p[NativeParameters.ONSET_POST_FIRST], FLOAT);
		Assertions.assertEquals(ArticulationDetector.FAST_DECISION_DELAY_HOPS, p[NativeParameters.ONSET_POST_LAST], FLOAT);
		Assertions.assertEquals(-50.0, p[NativeParameters.ONSET_MIN_LEVEL], FLOAT);
	}

	@Test
	public void theIndicesAreDistinctAndCoverTheArray() {
		int[] indices = { NativeParameters.LEVEL_TOO_LOW, NativeParameters.LEVEL_TOO_HIGH, NativeParameters.LEVEL_CLIP_SAMPLE, NativeParameters.LEVEL_CLIP_RATIO,
				NativeParameters.ONSET_FLUX_FACTOR, NativeParameters.ONSET_FLUX_OFFSET, NativeParameters.ONSET_MIN_LEVEL, NativeParameters.ONSET_REFRACTORY,
				NativeParameters.ONSET_GATE, NativeParameters.ONSET_PRE_FIRST, NativeParameters.ONSET_PRE_LAST, NativeParameters.ONSET_POST_FIRST,
				NativeParameters.ONSET_POST_LAST, NativeParameters.FRONT_GAIN, NativeParameters.FRONT_NOISE_REDUCTION, NativeParameters.FRONT_SUBTRACTION,
				NativeParameters.STRUM_ARM_FLOOR, NativeParameters.STRUM_CLIP_SAMPLE, NativeParameters.STRUM_WEAK, NativeParameters.STRUM_LOUD,
				NativeParameters.YIN_THRESHOLD };
		boolean[] seen = new boolean[NativeParameters.LENGTH];
		for (int index : indices) {
			Assertions.assertFalse(seen[index], "index " + index + " used twice");
			seen[index] = true;
		}
		Assertions.assertEquals(NativeParameters.LENGTH, indices.length);
	}
}
