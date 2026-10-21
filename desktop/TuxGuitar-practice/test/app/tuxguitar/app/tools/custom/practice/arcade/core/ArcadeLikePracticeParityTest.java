package app.tuxguitar.app.tools.custom.practice.arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Spec 014 (T015, FR-011, SC-003): at the position "like Practice" the Arcade start hands the run exactly what the practice start would: the same settings, the
 * same parameters derived from them, the same window. The run itself (verdicts, score, result) is built from those and is covered by the existing Arcade and
 * Follow suites, which this feature leaves untouched; the figures on a recording are asserted by the gated {@code ArcadeLeniencyReplayTest}. What is not covered
 * here: an Arcade run driven through the practice window, which no test builds.
 */
public class ArcadeLikePracticeParityTest {

	private static void assertSameParameters(PracticeParameters expected, PracticeParameters actual) {
		assertEquals(expected.getSettings(), actual.getSettings());
		assertEquals(expected.stabilityNanos(), actual.stabilityNanos());
		assertEquals(expected.articulationLatencyNanos(), actual.articulationLatencyNanos());
	}

	@Test
	public void likePracticeHandsTheRunTheSettingsTheParametersAndTheWindowOfThePracticeStart() {
		RecognitionSettings[] effective = { RecognitionSettings.defaults(),
				RecognitionSettings.defaults().with(RecognitionParameters.NOTE_STABILITY, 200.0).with(RecognitionParameters.NOTE_CONFIDENCE, 0.9),
				RecognitionSettings.defaults().with(RecognitionParameters.CHORD_EVIDENCE, 0.5).with(RecognitionParameters.TEMPO_WINDOW, 60.0) };
		for (RecognitionSettings settings : effective) {
			RecognitionSettings arcade = ArcadeLeniency.apply(ArcadeLeniencyPosition.LIKE_PRACTICE, settings);
			assertEquals(settings, arcade);
			assertSameParameters(PracticeParameters.of(settings), PracticeParameters.of(arcade));
			for (int window = 40; window <= 200; window += 20) {
				assertEquals(window, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.LIKE_PRACTICE, window));
			}
		}
	}

	@Test
	public void theOtherPositionsDoChangeWhatTheRunIsGivenSoTheParityIsNotVacuous() {
		RecognitionSettings base = RecognitionSettings.defaults();
		RecognitionSettings fun = ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, base);
		assertEquals(false, base.equals(fun), "the fun position relaxes something");
		assertEquals(false, PracticeParameters.of(base).stabilityNanos() == PracticeParameters.of(fun).stabilityNanos(), "and it reaches the parameters of the run");
		assertEquals(125, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 100));
	}
}
