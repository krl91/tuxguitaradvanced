package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Choice;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Flag;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Number;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Repair;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * Validation of every audio-recognition setting (task T102): defaults, normal values, exact
 * bounds, just outside, extremes, empty, wrong type, invalid text, NaN and ±Inf, old and unknown
 * keys, incoherent combinations, section restore and text round trip.
 */
public class RecognitionSettingsTest {

	// ---------------------------------------------------------------- defaults = former constants

	@Test
	public void recommendedValuesAreTheFormerHardCodedValues() {
		RecognitionSettings d = RecognitionSettings.defaults();
		assertEquals(AudioEngine.DEFAULT_TOO_LOW_RMS_DBFS, d.get(RecognitionParameters.INPUT_LEVEL_MIN));
		assertEquals(AudioEngine.DEFAULT_TOO_HIGH_RMS_DBFS, d.get(RecognitionParameters.INPUT_LEVEL_MAX));
		assertEquals(AudioEngine.DEFAULT_CLIPPED_SAMPLE_LEVEL, d.get(RecognitionParameters.LEVEL_CLIP_SAMPLE));
		assertEquals(AudioEngine.DEFAULT_CLIPPING_RATIO, d.get(RecognitionParameters.LEVEL_CLIP_SHARE));
		assertEquals(ArticulationDetector.DEFAULT_GATE_DB, d.get(RecognitionParameters.NOTE_ATTACK));
		assertEquals(ArticulationDetector.DEFAULT_MIN_LEVEL_DB, d.get(RecognitionParameters.ATTACK_MIN_LEVEL));
		assertEquals(5.0, d.get(RecognitionParameters.TUNER_IN_TUNE));
		assertTrue(d.get(RecognitionParameters.TUNER_HOLD));
		assertEquals(0.80, d.get(RecognitionParameters.TUNER_CONFIDENCE));
		assertEquals(35.0, d.get(RecognitionParameters.NOTE_ACCEPT));
		assertEquals(0.80, d.get(RecognitionParameters.NOTE_CONFIDENCE));
		assertEquals(20, d.getNoteStabilityHops());
		assertEquals(1.0 / 3.0, d.get(RecognitionParameters.CHORD_EVIDENCE));
		assertEquals(2.0, d.get(RecognitionParameters.CHORD_MIN_PARTIALS));
		assertEquals(8, d.getChordConfirmHops());
		assertEquals(600.0, d.get(RecognitionParameters.CHORD_WINDOW));
		assertEquals(2, d.getPassageMeasures());
		assertEquals(50, d.getPassageThresholdPercent());
		assertEquals(2, d.getPassageRetries());
		assertEquals(PitchEngineCatalog.AUTOMATIC, d.get(RecognitionParameters.ENGINE_PITCH));
		assertEquals(RecognitionParameters.LEGATO_STRICT, d.get(RecognitionParameters.NOTE_LEGATO));
	}

	@Test
	public void everyParameterHasAUniqueKeyAndASection() {
		List<String> keys = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			assertTrue(parameter.getKey().startsWith("recognition."), parameter.getKey());
			assertFalse(keys.contains(parameter.getKey()), parameter.getKey());
			keys.add(parameter.getKey());
			assertEquals(parameter, RecognitionParameters.byKey(parameter.getKey()));
		}
		for( Section section : Section.values() ) {
			assertFalse(RecognitionParameters.of(section).isEmpty(), section.name());
		}
	}

	// ---------------------------------------------------------------- per numeric parameter

	@TestFactory
	public List<DynamicTest> everyNumberIsValidatedOnReadAndOnInput() {
		List<DynamicTest> tests = new ArrayList<DynamicTest>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( parameter instanceof Number ) {
				final Number number = (Number) parameter;
				tests.add(DynamicTest.dynamicTest(number.getKey(), () -> checkNumber(number)));
			}
		}
		return tests;
	}

	private static void checkNumber(Number p) {
		double min = p.getMinimum();
		double max = p.getMaximum();
		double def = p.getDefault();
		double step = (p.isInteger() ? 1.0 : (max - min) / 1000.0);
		// default and a normal value
		assertParsed(p, p.format(def), def, Repair.NONE);
		double normal = (p.isInteger() ? Math.rint((min + max) / 2) : (min + max) / 2);
		assertParsed(p, Double.toString(normal), normal, Repair.NONE);
		// exact bounds
		assertParsed(p, p.format(min), min, Repair.NONE);
		assertParsed(p, p.format(max), max, Repair.NONE);
		// just outside
		assertParsed(p, Double.toString(min - step), min, Repair.CLAMPED);
		assertParsed(p, Double.toString(max + step), max, Repair.CLAMPED);
		// extremes
		assertParsed(p, "1e308", max, Repair.CLAMPED);
		assertParsed(p, "-1e308", min, Repair.CLAMPED);
		assertParsed(p, Double.toString(Double.MAX_VALUE), max, Repair.CLAMPED);
		assertParsed(p, Double.toString(-Double.MAX_VALUE), min, Repair.CLAMPED);
		// empty, wrong type, invalid text, NaN, infinities
		for( String invalid : new String[] { null, "", "   ", "abc", "true", "12abc", "0x10", "NaN", "Infinity", "-Infinity", "∞" } ) {
			assertParsed(p, invalid, def, Repair.DEFAULTED);
		}
		// surrounding spaces are tolerated
		assertParsed(p, "  " + p.format(def) + "  ", def, Repair.NONE);
		// typed input from the UI
		assertEquals(Repair.DEFAULTED, p.validate(Double.POSITIVE_INFINITY).getRepair());
		assertEquals(def, (Double) p.validate(Double.NaN).getValue());
		assertEquals(Repair.DEFAULTED, p.validate(Double.NEGATIVE_INFINITY).getRepair());
		assertEquals(def, (Double) p.validate("35").getValue());
		assertEquals(def, (Double) p.validate(null).getValue());
		assertEquals(max, (Double) p.validate(Integer.MAX_VALUE).getValue());
		assertEquals(min, (Double) p.validate(Long.MIN_VALUE).getValue());
		if( p.isInteger() ) {
			// whole numbers only
			assertParsed(p, Double.toString(min + 0.4), min, Repair.CLAMPED);
		}
		// the settings apply the same validation
		RecognitionSettings high = RecognitionSettings.defaults().with(p, max + 1000);
		assertTrue(high.get(p) <= max);
		RecognitionSettings low = RecognitionSettings.defaults().with(p, min - 1000);
		assertTrue(low.get(p) >= min);
	}

	private static void assertParsed(RecognitionParameter p, String raw, Object expected, Repair repair) {
		RecognitionParameter.Parsed parsed = p.parse(raw);
		assertEquals(expected, parsed.getValue(), p.getKey() + " <" + raw + ">");
		assertEquals(repair, parsed.getRepair(), p.getKey() + " <" + raw + ">");
	}

	// ---------------------------------------------------------------- flags and choices

	@Test
	public void flagsAcceptOnlyTrueOrFalse() {
		Flag hold = RecognitionParameters.TUNER_HOLD;
		assertParsed(hold, "true", true, Repair.NONE);
		assertParsed(hold, "false", false, Repair.NONE);
		assertParsed(hold, " FALSE ", false, Repair.NONE);
		for( String invalid : new String[] { null, "", "yes", "1", "0", "NaN", "Infinity", "off" } ) {
			assertParsed(hold, invalid, true, Repair.DEFAULTED);
		}
		assertEquals(Repair.DEFAULTED, hold.validate("true").getRepair());
		assertEquals(Repair.DEFAULTED, hold.validate(1).getRepair());
		assertEquals(false, hold.validate(Boolean.FALSE).getValue());
	}

	@TestFactory
	public List<DynamicTest> everyChoiceAcceptsOnlyItsOptions() {
		List<DynamicTest> tests = new ArrayList<DynamicTest>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( parameter instanceof Choice ) {
				final Choice choice = (Choice) parameter;
				tests.add(DynamicTest.dynamicTest(choice.getKey(), () -> {
					for( String option : choice.getOptions() ) {
						assertParsed(choice, option, option, Repair.NONE);
						assertParsed(choice, " " + option + " ", option, Repair.NONE);
					}
					for( String invalid : new String[] { null, "", "unknown", "3", "-1", "NaN", "Infinity", "2.0" } ) {
						if( !choice.getOptions().contains(invalid) ) {
							assertParsed(choice, invalid, choice.getDefault(), Repair.DEFAULTED);
						}
					}
					assertEquals(Repair.DEFAULTED, choice.validate(2).getRepair());
				}));
			}
		}
		return tests;
	}

	@Test
	public void passageChoicesAreOffOneTwoFourMeasuresAndTwentyFiveFiftySeventyFivePercent() {
		assertEquals(List.of("0", "1", "2", "4"), RecognitionParameters.PASSAGE_MEASURES.getOptions());
		assertEquals(List.of("25", "50", "75"), RecognitionParameters.PASSAGE_THRESHOLD.getOptions());
		assertEquals(0, RecognitionSettings.defaults().with(RecognitionParameters.PASSAGE_MEASURES, "0").getPassageMeasures());
	}

	// ---------------------------------------------------------------- whole configurations

	@Test
	public void anEmptyOrMissingConfigurationGivesTheRecommendedValuesWithoutRepairs() {
		RecognitionSettings.Loaded fromNull = RecognitionSettings.fromStrings(null);
		RecognitionSettings.Loaded fromEmpty = RecognitionSettings.fromStrings(new HashMap<String, String>());
		assertEquals(RecognitionSettings.defaults(), fromNull.getSettings());
		assertEquals(RecognitionSettings.defaults(), fromEmpty.getSettings());
		assertTrue(fromEmpty.getRepairs().isEmpty());
	}

	@Test
	public void anOlderConfigurationKeepsItsValuesAndTakesDefaultsForNewKeys() {
		// e.g. written before the chord and passage settings existed, without a version
		Map<String, String> old = new HashMap<String, String>();
		old.put("recognition.tuner.in-tune", "8");
		old.put("recognition.note.accept", "30");
		RecognitionSettings.Loaded loaded = RecognitionSettings.fromStrings(old);
		assertEquals(8.0, loaded.getSettings().get(RecognitionParameters.TUNER_IN_TUNE));
		assertEquals(30.0, loaded.getSettings().get(RecognitionParameters.NOTE_ACCEPT));
		assertEquals(600.0, loaded.getSettings().get(RecognitionParameters.CHORD_WINDOW));
		assertTrue(loaded.getRepairs().isEmpty());
	}

	@Test
	public void unknownKeysAreReportedAndIgnoredAndForeignKeysLeftAlone() {
		Map<String, String> stored = new HashMap<String, String>();
		stored.put("recognition.future.setting", "42");
		stored.put("recognition.version", "7");
		stored.put("some.other.plugin", "x");
		RecognitionSettings.Loaded loaded = RecognitionSettings.fromStrings(stored);
		assertEquals(List.of("recognition.future.setting"), loaded.getUnknownKeys());
		assertEquals(RecognitionSettings.defaults(), loaded.getSettings());
		assertFalse(loaded.getSettings().toStrings().containsKey("recognition.future.setting"));
	}

	@Test
	public void aBrokenConfigurationIsRepairedKeyByKeyWithoutFailing() {
		Map<String, String> stored = new HashMap<String, String>();
		stored.put("recognition.note.accept", "NaN");
		stored.put("recognition.tuner.in-tune", "1e9");
		stored.put("recognition.tuner.hold", "maybe");
		stored.put("recognition.passage.measures", "3");
		stored.put("recognition.chord.window", "");
		RecognitionSettings.Loaded loaded = RecognitionSettings.fromStrings(stored);
		RecognitionSettings s = loaded.getSettings();
		assertEquals(20.0, s.get(RecognitionParameters.TUNER_IN_TUNE));
		// NaN → default 35, still ≥ in-tune 20
		assertEquals(35.0, s.get(RecognitionParameters.NOTE_ACCEPT));
		assertTrue(s.get(RecognitionParameters.TUNER_HOLD));
		assertEquals(2, s.getPassageMeasures());
		assertEquals(600.0, s.get(RecognitionParameters.CHORD_WINDOW));
		assertEquals(Repair.CLAMPED, loaded.getRepairs().get("recognition.tuner.in-tune"));
		assertEquals(Repair.DEFAULTED, loaded.getRepairs().get("recognition.tuner.hold"));
		assertEquals(Repair.DEFAULTED, loaded.getRepairs().get("recognition.chord.window"));
	}

	@Test
	public void anAcceptanceRangeNarrowerThanTheInTuneRangeIsRaised() {
		Map<String, String> stored = new HashMap<String, String>();
		stored.put("recognition.tuner.in-tune", "20");
		stored.put("recognition.note.accept", "10");
		RecognitionSettings.Loaded loaded = RecognitionSettings.fromStrings(stored);
		assertEquals(20.0, loaded.getSettings().get(RecognitionParameters.NOTE_ACCEPT));
		assertEquals(Repair.CLAMPED, loaded.getRepairs().get("recognition.note.accept"));

		RecognitionSettings raised = RecognitionSettings.defaults().with(RecognitionParameters.NOTE_ACCEPT, 10).with(RecognitionParameters.TUNER_IN_TUNE, 15);
		assertEquals(15.0, raised.get(RecognitionParameters.NOTE_ACCEPT));
		RecognitionSettings kept = RecognitionSettings.defaults().with(RecognitionParameters.TUNER_IN_TUNE, 15).with(RecognitionParameters.NOTE_ACCEPT, 10);
		assertEquals(15.0, kept.get(RecognitionParameters.NOTE_ACCEPT));
	}

	@Test
	public void levelBoundsCanNeverCross() {
		// disjoint ranges: the minimum level always stays below the high-level warning
		assertTrue(RecognitionParameters.INPUT_LEVEL_MIN.getMaximum() < RecognitionParameters.INPUT_LEVEL_MAX.getMinimum());
		RecognitionSettings s = RecognitionSettings.defaults().with(RecognitionParameters.INPUT_LEVEL_MIN, 0).with(RecognitionParameters.INPUT_LEVEL_MAX, -100);
		assertTrue(s.get(RecognitionParameters.INPUT_LEVEL_MIN) < s.get(RecognitionParameters.INPUT_LEVEL_MAX));
		// the analyser accepts every combination the settings allow
		AudioEngine.levelAnalyzer(s);
	}

	@Test
	public void sectionsAreRestoredIndependently() {
		RecognitionSettings changed = RecognitionSettings.defaults()
				.with(RecognitionParameters.TUNER_IN_TUNE, 10)
				.with(RecognitionParameters.CHORD_WINDOW, 900);
		RecognitionSettings tunerRestored = changed.withDefaults(Section.TUNER);
		assertEquals(5.0, tunerRestored.get(RecognitionParameters.TUNER_IN_TUNE));
		assertEquals(900.0, tunerRestored.get(RecognitionParameters.CHORD_WINDOW));
		assertTrue(tunerRestored.isDefault(Section.TUNER));
		assertFalse(tunerRestored.isDefault(Section.CHORDS));
		assertEquals(RecognitionSettings.defaults(), tunerRestored.withDefaults(Section.CHORDS));
	}

	@Test
	public void textRoundTripKeepsEveryValueExactly() {
		RecognitionSettings s = RecognitionSettings.defaults();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( parameter instanceof Number ) {
				Number n = (Number) parameter;
				s = s.with(n, n.isInteger() ? n.getMaximum() : (n.getMinimum() + n.getMaximum()) / 3.0);
			}
		}
		s = s.with(RecognitionParameters.TUNER_HOLD, false).with(RecognitionParameters.PASSAGE_MEASURES, "4");
		Map<String, String> text = s.toStrings();
		assertEquals(Integer.toString(RecognitionParameters.VERSION), text.get(RecognitionParameters.VERSION_KEY));
		RecognitionSettings.Loaded back = RecognitionSettings.fromStrings(text);
		assertEquals(s, back.getSettings());
		assertTrue(back.getRepairs().isEmpty());
		// the exact one third of the default chord evidence survives too
		assertEquals(RecognitionSettings.defaults(), RecognitionSettings.fromStrings(RecognitionSettings.defaults().toStrings()).getSettings());
	}

	@Test
	public void millisecondsBecomeWholeHopsAtTheReferenceRate() {
		assertEquals(20, RecognitionParameters.toHops(107));
		assertEquals(8, RecognitionParameters.toHops(43));
		assertEquals(9, RecognitionParameters.toHops(50));
		assertEquals(56, RecognitionParameters.toHops(300));
		assertEquals(1, RecognitionParameters.toHops(0));
	}

	// ---------------------------------------------------------------- the timing window of the tempo modes (spec 012)

	private static Map<String, String> stored(String... pairs) {
		Map<String, String> map = new HashMap<String, String>();
		for( int i = 0 ; i < pairs.length ; i += 2 ) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private static double windowOf(Map<String, String> map) {
		return RecognitionSettings.fromStrings(map).getSettings().get(RecognitionParameters.TEMPO_WINDOW);
	}

	@Test
	public void theTimingWindowIsAWholeNumberOfMillisecondsFromFortyToTwoHundredAndHundredByDefault() {
		Number window = RecognitionParameters.TEMPO_WINDOW;
		assertEquals("ms", window.getUnit());
		assertEquals(100.0, window.getDefault());
		assertEquals(40.0, window.getMinimum());
		assertEquals(200.0, window.getMaximum());
		assertEquals(Section.NOTES, window.getSection());
		assertEquals(100.0, RecognitionSettings.defaults().get(window));
	}

	@Test
	public void anOldSavedPresetIsReadAsAWidthStrictFiftyLenientOneHundredAndTwentyFiveAndNormalTheNewDefault() {
		String old = RecognitionParameters.LEGACY_TEMPO_TOLERANCE_KEY;
		assertEquals(50.0, windowOf(stored(old, "strict")));
		assertEquals(125.0, windowOf(stored(old, "lenient")));
		// the store writes every key as soon as one setting is saved, so a saved "normal" is the old default, not a choice
		assertEquals(100.0, windowOf(stored(old, "normal")));
		assertEquals(100.0, windowOf(stored(old, "something else")));
		assertEquals(100.0, windowOf(stored()));
	}

	@Test
	public void aSavedWindowWinsOverAnOldPresetAndIsBroughtIntoTheRange() {
		String old = RecognitionParameters.LEGACY_TEMPO_TOLERANCE_KEY;
		String key = RecognitionParameters.TEMPO_WINDOW.getKey();
		assertEquals(150.0, windowOf(stored(old, "strict", key, "150")));
		assertEquals(40.0, windowOf(stored(key, "10")));
		assertEquals(200.0, windowOf(stored(key, "900")));
		assertEquals(100.0, windowOf(stored(key, "not a number")));
	}

	@Test
	public void theOldPresetKeyIsNotAParameterNorAnUnknownKeyAndIsNotWrittenAgain() {
		String old = RecognitionParameters.LEGACY_TEMPO_TOLERANCE_KEY;
		assertEquals(null, RecognitionParameters.byKey(old), "the old choice has no control any more");
		RecognitionSettings.Loaded loaded = RecognitionSettings.fromStrings(stored(old, "lenient"));
		assertFalse(loaded.getUnknownKeys().contains(old), "it is read for the migration, not reported as a key of another version");
		Map<String, String> written = loaded.getSettings().toStrings();
		assertTrue(written.containsKey(RecognitionParameters.TEMPO_WINDOW.getKey()));
		assertFalse(written.containsKey(old));
		assertEquals("125", written.get(RecognitionParameters.TEMPO_WINDOW.getKey()));
	}
}
