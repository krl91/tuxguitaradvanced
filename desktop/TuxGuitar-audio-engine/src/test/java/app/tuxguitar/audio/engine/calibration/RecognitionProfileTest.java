package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/** Spec 013 (T006): what a profile holds, and when it applies (FR-007, FR-010, data-model). */
public class RecognitionProfileTest {

	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final FrontEndState FRONT_END = new FrontEndState(1.0, false);
	private static final RecognitionRate FOUND = new RecognitionRate(9, 10, 2, 3);
	private static final RecognitionRate DEFAULT_RATE = new RecognitionRate(7, 10, 1, 3);

	private static RecognitionProfile profile(Map<RecognitionParameter.Number, Double> values, int rate, RecognitionProfile.Origin origin) {
		return new RecognitionProfile(KEY, rate, 1760000000000L, values, FOUND, DEFAULT_RATE, FRONT_END, origin);
	}

	private static Map<RecognitionParameter.Number, Double> accept(double value) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, value);
		return values;
	}

	@Test
	public void theProfileParametersAreTheElevenOfThePlanInAFixedOrder() {
		assertEquals(11, ProfileParameters.LIST.size());
		assertEquals(RecognitionParameters.NOTE_ACCEPT, ProfileParameters.LIST.get(0));
		assertEquals(RecognitionParameters.INPUT_LEVEL_MIN, ProfileParameters.LIST.get(10));
		for (RecognitionParameter forbidden : new RecognitionParameter[] { RecognitionParameters.ENGINE_PITCH, RecognitionParameters.TEMPO_WINDOW,
				RecognitionParameters.TUNER_IN_TUNE, RecognitionParameters.TUNER_HOLD, RecognitionParameters.TUNER_CONFIDENCE,
				RecognitionParameters.INPUT_NOISE_REDUCTION, RecognitionParameters.PASSAGE_MEASURES, RecognitionParameters.NOTE_LEGATO }) {
			assertFalse(ProfileParameters.LIST.contains(forbidden), forbidden.getKey() + " is never in a profile");
		}
	}

	@Test
	public void aProfileHoldsOnlyProfileParametersWithinTheirBounds() {
		double ok = RecognitionParameters.NOTE_ACCEPT.getMinimum();
		RecognitionProfile profile = profile(accept(ok), 48000, RecognitionProfile.Origin.SEARCHED);
		assertEquals(ok, profile.getValues().get(RecognitionParameters.NOTE_ACCEPT));
		Map<RecognitionParameter.Number, Double> window = new LinkedHashMap<RecognitionParameter.Number, Double>();
		window.put(RecognitionParameters.TEMPO_WINDOW, 100.0);
		assertThrows(IllegalArgumentException.class, () -> profile(window, 48000, RecognitionProfile.Origin.SEARCHED), "the timing window is the learner's own");
		assertThrows(IllegalArgumentException.class,
				() -> profile(accept(RecognitionParameters.NOTE_ACCEPT.getMaximum() + 1000), 48000, RecognitionProfile.Origin.SEARCHED), "out of bounds");
	}

	@Test
	public void theProfileKeepsItsFactsAndCopiesItsValues() {
		Map<RecognitionParameter.Number, Double> values = accept(RecognitionParameters.NOTE_ACCEPT.getMinimum());
		RecognitionProfile profile = profile(values, 48000, RecognitionProfile.Origin.SEARCHED);
		values.put(RecognitionParameters.NOTE_CONFIDENCE, RecognitionParameters.NOTE_CONFIDENCE.getMinimum());
		assertEquals(1, profile.getValues().size(), "the map given is copied");
		assertEquals(KEY, profile.getKey());
		assertEquals(48000, profile.getSampleRate());
		assertEquals(1760000000000L, profile.getDateMillis());
		assertEquals(FOUND, profile.getFoundRate());
		assertEquals(DEFAULT_RATE, profile.getDefaultRate());
		assertEquals(FRONT_END, profile.getFrontEnd());
		assertEquals(RecognitionProfile.Origin.SEARCHED, profile.getOrigin());
		assertEquals(1, RecognitionProfile.LAYOUT);
	}

	@Test
	public void aProfileAppliesOnlyAtItsSampleRateOrWhenTheRateIsNotKnown() {
		RecognitionProfile profile = profile(accept(RecognitionParameters.NOTE_ACCEPT.getMinimum()), 48000, RecognitionProfile.Origin.SEARCHED);
		assertTrue(profile.appliesAt(48000));
		assertFalse(profile.appliesAt(44100), "a profile made at 48 kHz is not applied at 44.1 kHz");
		assertTrue(profile.appliesAt(0), "the capture rate is not known yet");
	}

	@Test
	public void theProfileChangesOnlyItsOwnValuesOfTheSettings() {
		double changed = RecognitionParameters.NOTE_ACCEPT.getMinimum();
		RecognitionProfile profile = profile(accept(changed), 48000, RecognitionProfile.Origin.SEARCHED);
		RecognitionSettings base = RecognitionSettings.defaults().with(RecognitionParameters.TEMPO_WINDOW, 150.0);
		RecognitionSettings merged = profile.applyTo(base);
		assertEquals(changed, merged.get(RecognitionParameters.NOTE_ACCEPT));
		for (RecognitionParameter parameter : RecognitionParameters.ALL) {
			if (parameter != RecognitionParameters.NOTE_ACCEPT) {
				assertEquals(base.getValue(parameter), merged.getValue(parameter), parameter.getKey() + " is the base's");
			}
		}
	}

	@Test
	public void theDefaultChosenHoldsNoValueAndLeavesTheSettingsAsTheyAre() {
		RecognitionProfile profile = RecognitionProfile.defaultChosen(KEY, 48000, 1760000000000L, FRONT_END);
		assertEquals(RecognitionProfile.Origin.DEFAULT_CHOSEN, profile.getOrigin());
		assertTrue(profile.getValues().isEmpty());
		RecognitionSettings base = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_ACCEPT.getMinimum());
		assertEquals(base, profile.applyTo(base), "the default configuration, whatever it is edited to later");
	}

	@Test
	public void theRatesAreCountedOverEventsAndTheThresholdIsExactlySixtyPercent() {
		assertEquals(9 + 2, FOUND.getAccepted());
		assertEquals(13, FOUND.getTotal());
		assertEquals(100.0 * 11 / 13, FOUND.totalPercent(), 1e-9);
		assertEquals(90.0, FOUND.notesPercent(), 1e-9);
		assertEquals(100.0 * 2 / 3, FOUND.chordsPercent(), 1e-9);
		assertTrue(new RecognitionRate(8, 10, 0, 3).meetsThreshold(), "8 of 13 is 61.5 %");
		assertFalse(new RecognitionRate(7, 10, 0, 3).meetsThreshold(), "7 of 13 is 53.8 %");
		assertTrue(new RecognitionRate(3, 5, 0, 0).meetsThreshold(), "exactly 60 % is green");
	}
}
