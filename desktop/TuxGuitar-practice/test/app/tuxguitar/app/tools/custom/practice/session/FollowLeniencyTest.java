package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
 * Spec 016 (T030, contracts/follow-leniency.md): the pitch permissiveness of Follow. One way (the more lenient of the effective value and the position's), Strict
 * gives the effective settings themselves, the semitone is never accepted (at most 45 cents), no level threshold is a row, a device profile applies first and the
 * position on top, an unknown saved name is Permissive. The values themselves are measured later (T032) and pinned there.
 */
public class FollowLeniencyTest {

	/** The rows of the contract: for each, whether a higher value is the more lenient one. */
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
	public void strictGivesTheEffectiveSettingsThemselves() {
		RecognitionSettings effective = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_STABILITY, 200.0);
		assertSame(effective, FollowLeniency.apply(FollowLeniencyPosition.STRICT, effective));
	}

	@Test
	public void everyRowIsAtLeastAsLenientInsideItsRangeAndTheSemitoneIsNeverAccepted() {
		RecognitionSettings base = RecognitionSettings.defaults();
		for (FollowLeniencyPosition position : FollowLeniencyPosition.values()) {
			RecognitionSettings relaxed = FollowLeniency.apply(position, base);
			for (RecognitionParameter.Number parameter : HIGHER_IS_MORE_LENIENT.keySet()) {
				double value = relaxed.get(parameter);
				assertTrue(atLeastAsLenient(parameter, value, base.get(parameter)), position + " " + parameter.getKey() + " " + value);
				assertTrue(value >= parameter.getMinimum() && value <= parameter.getMaximum(), position + " " + parameter.getKey() + " inside its range: " + value);
			}
			assertTrue(relaxed.get(RecognitionParameters.NOTE_ACCEPT) <= 45.0, position + ": below the 50 cents halfway to the next semitone");
		}
		RecognitionSettings permissive = FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, base);
		RecognitionSettings very = FollowLeniency.apply(FollowLeniencyPosition.VERY_PERMISSIVE, base);
		for (RecognitionParameter.Number parameter : HIGHER_IS_MORE_LENIENT.keySet()) {
			assertTrue(atLeastAsLenient(parameter, very.get(parameter), permissive.get(parameter)), "very permissive is at least as lenient for " + parameter.getKey());
		}
	}

	@Test
	public void itIsOneWayAndAProfileAppliesFirstThePositionOnTop() {
		RecognitionSettings lenient = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_ACCEPT, 45.0).with(RecognitionParameters.NOTE_STABILITY, 50.0)
				.with(RecognitionParameters.NOTE_CONFIDENCE, 0.5).with(RecognitionParameters.CHORD_EVIDENCE, 0.2).with(RecognitionParameters.CHORD_MIN_PARTIALS, 1.0)
				.with(RecognitionParameters.CHORD_CONFIRM, 20.0);
		for (FollowLeniencyPosition position : FollowLeniencyPosition.values()) {
			assertEquals(lenient, FollowLeniency.apply(position, lenient), position + " leaves settings that are already the most lenient");
		}
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, 45.0);
		values.put(RecognitionParameters.NOTE_STABILITY, 52.0);
		RecognitionProfile profile = new RecognitionProfile(DeviceKey.of("Micro", null, 0), 48000, 1L, values, new RecognitionRate(9, 10, 0, 0), new RecognitionRate(5, 10, 0, 0),
				new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
		RecognitionSettings relaxed = FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, profile.applyTo(RecognitionSettings.defaults()));
		assertEquals(45.0, relaxed.get(RecognitionParameters.NOTE_ACCEPT), 1e-9, "the profile's more lenient acceptance is kept");
		assertEquals(52.0, relaxed.get(RecognitionParameters.NOTE_STABILITY), 1e-9, "the profile's quicker stability is kept");
	}

	@Test
	public void noLevelThresholdNorAnyOtherSettingIsTouched() {
		RecognitionSettings base = RecognitionSettings.defaults();
		for (FollowLeniencyPosition position : FollowLeniencyPosition.values()) {
			RecognitionSettings relaxed = FollowLeniency.apply(position, base);
			for (RecognitionParameter parameter : RecognitionParameters.ALL) {
				if (HIGHER_IS_MORE_LENIENT.containsKey(parameter)) {
					continue;
				}
				assertEquals(base.getValue(parameter), relaxed.getValue(parameter), position + " must not change " + parameter.getKey());
			}
		}
		assertEquals(HIGHER_IS_MORE_LENIENT.keySet(), new java.util.LinkedHashSet<RecognitionParameter.Number>(FollowLeniency.relaxedParameters()), "the rows of the contract");
	}

	@Test
	public void anUnknownOrMissingSavedNameIsPermissive() {
		assertEquals(FollowLeniencyPosition.PERMISSIVE, FollowLeniencyPosition.DEFAULT);
		assertEquals(FollowLeniencyPosition.PERMISSIVE, FollowLeniencyPosition.of(null));
		assertEquals(FollowLeniencyPosition.PERMISSIVE, FollowLeniencyPosition.of("FUN"));
		assertEquals(FollowLeniencyPosition.STRICT, FollowLeniencyPosition.of(" strict "));
		assertEquals(FollowLeniencyPosition.VERY_PERMISSIVE, FollowLeniencyPosition.of("VERY_PERMISSIVE"));
	}

	@Test
	public void theResultIsDeterministic() {
		RecognitionSettings base = RecognitionSettings.defaults();
		for (FollowLeniencyPosition position : FollowLeniencyPosition.values()) {
			assertEquals(FollowLeniency.apply(position, base), FollowLeniency.apply(position, base));
		}
	}

	@Test
	public void theValuesMeasuredForT032() {
		// research R9 (final): the most lenient values that keep 35 of 38 right notes and 0 of 98 negatives on the reference recording
		RecognitionSettings base = RecognitionSettings.defaults();
		RecognitionSettings permissive = FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, base);
		RecognitionSettings very = FollowLeniency.apply(FollowLeniencyPosition.VERY_PERMISSIVE, base);
		assertEquals(35.0, permissive.get(RecognitionParameters.NOTE_ACCEPT), 1e-9, "40 cents loses a right note");
		assertEquals(0.60, permissive.get(RecognitionParameters.NOTE_CONFIDENCE), 1e-9);
		assertEquals(55.0, permissive.get(RecognitionParameters.NOTE_STABILITY), 1e-9);
		assertEquals(0.22, permissive.get(RecognitionParameters.CHORD_EVIDENCE), 1e-9);
		assertEquals(1.0, permissive.get(RecognitionParameters.CHORD_MIN_PARTIALS), 1e-9);
		assertEquals(24.0, permissive.get(RecognitionParameters.CHORD_CONFIRM), 1e-9);
		assertEquals(35.0, very.get(RecognitionParameters.NOTE_ACCEPT), 1e-9);
		assertEquals(0.50, very.get(RecognitionParameters.NOTE_CONFIDENCE), 1e-9);
		assertEquals(50.0, very.get(RecognitionParameters.NOTE_STABILITY), 1e-9);
		assertEquals(0.20, very.get(RecognitionParameters.CHORD_EVIDENCE), 1e-9);
		assertEquals(1.0, very.get(RecognitionParameters.CHORD_MIN_PARTIALS), 1e-9);
		assertEquals(20.0, very.get(RecognitionParameters.CHORD_CONFIRM), 1e-9);
	}
}
