package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Number;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The settings of the chord rule by profile (spec 024, data-model "Settings", task T013): keys, units, defaults (T007's Normal), bounds and
 * sections; the new option of the chord rule; the text round trip; none is a device-profile parameter.
 */
public class ChordProfileSettingsTest {

	private static void number(Number parameter, String key, String unit, double value, double min, double max, boolean integer, Section section) {
		assertEquals(key, parameter.getKey());
		assertEquals(unit, parameter.getUnit(), key);
		assertEquals(value, parameter.getDefault(), 1e-12, key);
		assertEquals(min, parameter.getMinimum(), 1e-12, key);
		assertEquals(max, parameter.getMaximum(), 1e-12, key);
		assertEquals(integer, parameter.isInteger(), key);
		assertEquals(section, parameter.getSection(), key);
		assertTrue(RecognitionParameters.ALL.contains(parameter), key);
		assertFalse(ProfileParameters.LIST.contains(parameter), key + " is not a device-profile parameter");
	}

	@Test
	public void theNewParametersHaveTheirKeysUnitsDefaultsBoundsAndSections() {
		number(RecognitionParameters.CHORD_PROFILE_MATCH, "recognition.chord.profile.match", "%", 0.79, 0.50, 0.99, false, Section.CHORDS);
		assertEquals(100.0, RecognitionParameters.CHORD_PROFILE_MATCH.getDisplayFactor());
		number(RecognitionParameters.CHORD_PROFILE_NAMES, "recognition.chord.profile.names", "%", 0.40, 0.05, 0.60, false, Section.CHORDS);
		assertEquals(100.0, RecognitionParameters.CHORD_PROFILE_NAMES.getDisplayFactor());
		number(RecognitionParameters.CHORD_PROFILE_TOP, "recognition.chord.detail.top", "MIDI", 84, 60, 112, true, Section.CHORD_DETAILS);
		number(RecognitionParameters.CHORD_PROFILE_SETTLE, "recognition.chord.detail.settle", "ms", 200, 0, 500, true, Section.CHORD_DETAILS);
		assertEquals("recognition.chord.attack", RecognitionParameters.CHORD_ATTACK.getKey());
		assertEquals(Boolean.TRUE, RecognitionParameters.CHORD_ATTACK.getDefault());
		assertEquals(Section.CHORDS, RecognitionParameters.CHORD_ATTACK.getSection());
		assertTrue(RecognitionParameters.ALL.contains(RecognitionParameters.CHORD_ATTACK));
	}

	@Test
	public void theChordsSectionHoldsSevenParametersAndTheDetailsSectionTwo() {
		assertEquals(Arrays.asList(RecognitionParameters.CHORD_EVIDENCE, RecognitionParameters.CHORD_WINDOW, RecognitionParameters.CHORD_COMPLETENESS,
				RecognitionParameters.CHORD_FOREIGN, RecognitionParameters.CHORD_PROFILE_MATCH, RecognitionParameters.CHORD_PROFILE_NAMES,
				RecognitionParameters.CHORD_ATTACK), RecognitionParameters.of(Section.CHORDS));
		assertEquals(Arrays.asList(RecognitionParameters.CHORD_PROFILE_TOP, RecognitionParameters.CHORD_PROFILE_SETTLE),
				RecognitionParameters.of(Section.CHORD_DETAILS));
	}

	@Test
	public void theChordRuleOffersBothRulesAndDefaultsToTheProfile() {
		assertEquals(Arrays.asList("all-verifiable", "profile"), RecognitionParameters.CHORD_COMPLETENESS.getOptions());
		assertEquals("profile", RecognitionParameters.COMPLETENESS_PROFILE);
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, RecognitionParameters.CHORD_COMPLETENESS.getDefault());
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, RecognitionSettings.defaults().get(RecognitionParameters.CHORD_COMPLETENESS));
	}

	@Test
	public void theValuesRoundTripThroughText() {
		RecognitionSettings changed = RecognitionSettings.defaults()
				.with(RecognitionParameters.CHORD_COMPLETENESS, RecognitionParameters.COMPLETENESS_ALL)
				.with(RecognitionParameters.CHORD_PROFILE_MATCH, 0.70)
				.with(RecognitionParameters.CHORD_PROFILE_NAMES, 0.25)
				.with(RecognitionParameters.CHORD_ATTACK, false)
				.with(RecognitionParameters.CHORD_PROFILE_TOP, 96.0)
				.with(RecognitionParameters.CHORD_PROFILE_SETTLE, 100.0);
		Map<String, String> text = changed.toStrings();
		RecognitionSettings.Loaded read = RecognitionSettings.fromStrings(text);
		assertEquals(changed, read.getSettings());
		assertTrue(read.getRepairs().isEmpty(), read.getRepairs().toString());
		assertEquals(RecognitionSettings.defaults(), RecognitionSettings.fromStrings(RecognitionSettings.defaults().toStrings()).getSettings());
	}

	@Test
	public void outOfBoundsValuesAreBroughtToTheBounds() {
		RecognitionSettings d = RecognitionSettings.defaults();
		assertEquals(0.99, d.with(RecognitionParameters.CHORD_PROFILE_MATCH, 1.5).get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
		assertEquals(0.05, d.with(RecognitionParameters.CHORD_PROFILE_NAMES, 0.0).get(RecognitionParameters.CHORD_PROFILE_NAMES), 1e-12);
		assertEquals(112.0, d.with(RecognitionParameters.CHORD_PROFILE_TOP, 130.0).get(RecognitionParameters.CHORD_PROFILE_TOP), 1e-12);
		assertEquals(0.0, d.with(RecognitionParameters.CHORD_PROFILE_SETTLE, -5.0).get(RecognitionParameters.CHORD_PROFILE_SETTLE), 1e-12);
	}
}
