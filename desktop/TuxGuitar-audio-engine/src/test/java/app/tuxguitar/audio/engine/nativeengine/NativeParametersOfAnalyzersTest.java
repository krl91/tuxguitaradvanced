package app.tuxguitar.audio.engine.nativeengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The native parameters read from the analysers the live analysis was given (amendment of 2026-11-03): the same values as from the settings they were
 * built from, the fast detector's windows included, so the per-hop native analysis follows exactly the analysers of the Java one.
 */
public class NativeParametersOfAnalyzersTest {

	@Test
	public void theAnalysersOfTheSettingsGiveTheParametersOfTheSettings() {
		RecognitionSettings settings = RecognitionSettings.defaults();
		float[] expected = NativeParameters.of(settings);
		float[] actual = NativeParameters.of(AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings));
		assertSameAnalysis(expected, actual);
	}

	@Test
	public void theFastDetectorGivesTheFastWindows() {
		RecognitionSettings settings = RecognitionSettings.defaults();
		ArticulationDetector base = AudioEngine.articulationDetector(settings);
		float[] expected = NativeParameters.withFastOnset(NativeParameters.of(settings));
		float[] actual = NativeParameters.of(AudioEngine.levelAnalyzer(settings), ArticulationDetector.fast(base.getMinLevelDb(), base.getGateDb()));
		assertSameAnalysis(expected, actual);
	}

	@Test
	public void customValuesAreCarried() {
		float[] p = NativeParameters.of(new LevelAnalyzer(-55.0, -3.0, 0.95, 0.2), new ArticulationDetector(2.0, 4.0, -45.0, 10, 6.0));
		Assertions.assertEquals(-55f, p[NativeParameters.LEVEL_TOO_LOW]);
		Assertions.assertEquals(-3f, p[NativeParameters.LEVEL_TOO_HIGH]);
		Assertions.assertEquals(0.95f, p[NativeParameters.LEVEL_CLIP_SAMPLE]);
		Assertions.assertEquals(0.2f, p[NativeParameters.LEVEL_CLIP_RATIO]);
		Assertions.assertEquals(2f, p[NativeParameters.ONSET_FLUX_FACTOR]);
		Assertions.assertEquals(4f, p[NativeParameters.ONSET_FLUX_OFFSET]);
		Assertions.assertEquals(-45f, p[NativeParameters.ONSET_MIN_LEVEL]);
		Assertions.assertEquals(10f, p[NativeParameters.ONSET_REFRACTORY]);
		Assertions.assertEquals(6f, p[NativeParameters.ONSET_GATE]);
	}

	/** The values the analysis stages read (level, onset, YIN); the front end and the strum meter are not part of the per-hop analysis. */
	private static void assertSameAnalysis(float[] expected, float[] actual) {
		Assertions.assertEquals(NativeParameters.LENGTH, actual.length);
		for (int i = NativeParameters.LEVEL_TOO_LOW; i <= NativeParameters.ONSET_POST_LAST; i++) {
			Assertions.assertEquals(expected[i], actual[i], "index " + i);
		}
		Assertions.assertEquals(expected[NativeParameters.YIN_THRESHOLD], actual[NativeParameters.YIN_THRESHOLD]);
	}
}
