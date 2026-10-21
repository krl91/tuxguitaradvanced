package app.tuxguitar.app.tools.custom.practice.arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Spec 014 (T008, FR-001 to FR-005, FR-011): the permissiveness of Arcade. It relaxes recognition settings, one way only (the result is the more lenient of the
 * player's effective value and the position's), within the range of each parameter, never touches the level thresholds of the input, gives the same settings at
 * the position "like Practice", keeps what a device profile already made more lenient, and widens the timing window up to the existing maximum.
 */
public class ArcadeLeniencyTest {

	/** For each relaxed parameter, whether a higher value is the more lenient one. */
	private static final Map<RecognitionParameter.Number, Boolean> HIGHER_IS_MORE_LENIENT = new LinkedHashMap<RecognitionParameter.Number, Boolean>();

	static {
		HIGHER_IS_MORE_LENIENT.put(RecognitionParameters.NOTE_ACCEPT, true);
		HIGHER_IS_MORE_LENIENT.put(RecognitionParameters.NOTE_CONFIDENCE, false);
		HIGHER_IS_MORE_LENIENT.put(RecognitionParameters.NOTE_STABILITY, false);
		HIGHER_IS_MORE_LENIENT.put(RecognitionParameters.CHORD_EVIDENCE, false);
		HIGHER_IS_MORE_LENIENT.put(RecognitionParameters.CHORD_MIN_PARTIALS, false);
		HIGHER_IS_MORE_LENIENT.put(RecognitionParameters.CHORD_CONFIRM, false);
	}

	private static boolean atLeastAsLenient(RecognitionParameter.Number parameter, double candidate, double reference) {
		return HIGHER_IS_MORE_LENIENT.get(parameter) ? candidate >= reference : candidate <= reference;
	}

	@Test
	public void likePracticeGivesTheSameSettingsAndTheSameWindow() {
		RecognitionSettings base = RecognitionSettings.defaults();
		assertEquals(base, ArcadeLeniency.apply(ArcadeLeniencyPosition.LIKE_PRACTICE, base));
		for (int chosen : new int[] { 40, 100, 150, 200 }) {
			assertEquals(chosen, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.LIKE_PRACTICE, chosen));
		}
	}

	@Test
	public void everyRelaxedParameterIsAtLeastAsLenientAndInsideItsRangeAtEachPosition() {
		RecognitionSettings base = RecognitionSettings.defaults();
		for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
			RecognitionSettings relaxed = ArcadeLeniency.apply(position, base);
			for (RecognitionParameter.Number parameter : HIGHER_IS_MORE_LENIENT.keySet()) {
				double value = relaxed.get(parameter);
				assertTrue(atLeastAsLenient(parameter, value, base.get(parameter)), position + " " + parameter.getKey() + " " + value);
				assertTrue(value >= parameter.getMinimum() && value <= parameter.getMaximum(), position + " " + parameter.getKey() + " is inside its range: " + value);
			}
		}
	}

	@Test
	public void theFunPositionIsMorePermissiveThanTheDefaultSettingsAndTheVeryPermissiveOneMoreStill() {
		RecognitionSettings base = RecognitionSettings.defaults();
		RecognitionSettings fun = ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, base);
		RecognitionSettings very = ArcadeLeniency.apply(ArcadeLeniencyPosition.VERY_PERMISSIVE, base);
		boolean funChangesSomething = false;
		for (RecognitionParameter.Number parameter : HIGHER_IS_MORE_LENIENT.keySet()) {
			funChangesSomething |= fun.get(parameter) != base.get(parameter);
			assertTrue(atLeastAsLenient(parameter, very.get(parameter), fun.get(parameter)), "very permissive is at least as lenient as fun for " + parameter.getKey());
		}
		assertTrue(funChangesSomething, "the default position relaxes something");
		assertTrue(fun.get(RecognitionParameters.NOTE_STABILITY) < base.get(RecognitionParameters.NOTE_STABILITY), "a shorter stability time is what makes the verdict quicker");
	}

	@Test
	public void itIsOneWayAPlayerOrAProfileAlreadyMoreLenientIsNeverMadeStricter() {
		RecognitionSettings lenient = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_ACCEPT, 45.0).with(RecognitionParameters.NOTE_STABILITY, 50.0)
				.with(RecognitionParameters.NOTE_CONFIDENCE, 0.5).with(RecognitionParameters.CHORD_EVIDENCE, 0.2).with(RecognitionParameters.CHORD_MIN_PARTIALS, 1.0)
				.with(RecognitionParameters.CHORD_CONFIRM, 20.0);
		for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
			assertEquals(lenient, ArcadeLeniency.apply(position, lenient), position + " leaves settings that are already the most lenient");
		}
		RecognitionSettings strict = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_ACCEPT, 15.0).with(RecognitionParameters.NOTE_STABILITY, 250.0);
		RecognitionSettings relaxed = ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, strict);
		assertTrue(relaxed.get(RecognitionParameters.NOTE_ACCEPT) >= 15.0 && relaxed.get(RecognitionParameters.NOTE_STABILITY) <= 250.0, "strict values are relaxed, never tightened");
	}

	@Test
	public void theLevelThresholdsOfTheInputAndEveryOtherSettingAreNeverTouched() {
		RecognitionSettings base = RecognitionSettings.defaults();
		for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
			RecognitionSettings relaxed = ArcadeLeniency.apply(position, base);
			for (RecognitionParameter parameter : RecognitionParameters.ALL) {
				if (parameter instanceof RecognitionParameter.Number && HIGHER_IS_MORE_LENIENT.containsKey(parameter)) {
					continue;
				}
				assertEquals(base.getValue(parameter), relaxed.getValue(parameter), position + " must not change " + parameter.getKey());
			}
		}
		assertEquals(base.get(RecognitionParameters.ATTACK_MIN_LEVEL), ArcadeLeniency.apply(ArcadeLeniencyPosition.VERY_PERMISSIVE, base).get(RecognitionParameters.ATTACK_MIN_LEVEL));
		assertEquals(base.get(RecognitionParameters.INPUT_LEVEL_MIN), ArcadeLeniency.apply(ArcadeLeniencyPosition.VERY_PERMISSIVE, base).get(RecognitionParameters.INPUT_LEVEL_MIN));
	}

	@Test
	public void appliedOnSettingsThatCarryADeviceProfileItKeepsWhatTheProfileMadeMoreLenient() {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, 45.0);
		values.put(RecognitionParameters.NOTE_STABILITY, 52.0);
		RecognitionProfile profile = new RecognitionProfile(DeviceKey.of("Micro", null, 0), 48000, 1L, values, new RecognitionRate(9, 10, 0, 0), new RecognitionRate(5, 10, 0, 0),
				new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
		RecognitionSettings effective = profile.applyTo(RecognitionSettings.defaults());
		RecognitionSettings relaxed = ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, effective);
		assertEquals(45.0, relaxed.get(RecognitionParameters.NOTE_ACCEPT), 1e-9, "the profile's more lenient acceptance is kept");
		assertEquals(52.0, relaxed.get(RecognitionParameters.NOTE_STABILITY), 1e-9, "the profile's quicker stability is kept");
	}

	@Test
	public void theWindowIsWidenedByTheFactorOfThePositionAndCappedAtTheExistingMaximum() {
		assertEquals(125, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 100));
		assertEquals(150, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.VERY_PERMISSIVE, 100));
		assertEquals(50, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 40));
		assertEquals(200, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 200), "a player already at the maximum gets no more");
		assertEquals(200, ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.VERY_PERMISSIVE, 160), "capped at 200 ms, the range of spec 012");
		for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
			for (int chosen = 40; chosen <= 200; chosen += 10) {
				int widened = ArcadeLeniency.windowMillis(position, chosen);
				assertTrue(widened >= chosen && widened <= 200, position + " " + chosen + " -> " + widened);
			}
		}
	}

	@Test
	public void theResultIsDeterministic() {
		RecognitionSettings base = RecognitionSettings.defaults();
		assertEquals(ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, base), ArcadeLeniency.apply(ArcadeLeniencyPosition.FUN, base));
		assertEquals(ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 87), ArcadeLeniency.windowMillis(ArcadeLeniencyPosition.FUN, 87));
	}
}
