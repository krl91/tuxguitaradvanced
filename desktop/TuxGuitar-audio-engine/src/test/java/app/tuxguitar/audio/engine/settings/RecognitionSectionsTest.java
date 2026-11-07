package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Repair;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * The sections of the recognition settings and the ordered pairs (spec 026, T008, data-model "Section" and "Validation"): the eight sections of
 * today keep their order and content, every section holds 1 to 7 parameters, a device profile never overlays a new section's value, and an inverted
 * pair is brought back on reading and on every change.
 */
public class RecognitionSectionsTest {

	private static final Section[] TODAY = { Section.INPUT, Section.TUNER, Section.NOTES, Section.CHORDS, Section.CHORD_DETAILS, Section.PASSAGES,
			Section.ENGINE, Section.ADVANCED };

	@Test
	public void theEightSectionsOfTodayKeepTheirOrderAndContent() {
		assertEquals(Arrays.asList(TODAY), Arrays.asList(Section.values()).subList(0, TODAY.length));
		assertEquals(keys(RecognitionParameters.INPUT_LEVEL_MIN, RecognitionParameters.INPUT_LEVEL_MAX, RecognitionParameters.INPUT_NOISE_REDUCTION,
				RecognitionParameters.INPUT_GAIN_SUGGESTION), keys(Section.INPUT));
		assertEquals(keys(RecognitionParameters.TUNER_IN_TUNE, RecognitionParameters.TUNER_HOLD), keys(Section.TUNER));
		assertEquals(keys(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_CONFIDENCE, RecognitionParameters.NOTE_STABILITY,
				RecognitionParameters.NOTE_ATTACK, RecognitionParameters.NOTE_LEGATO, RecognitionParameters.NOTE_TECHNIQUE, RecognitionParameters.TEMPO_WINDOW),
				keys(Section.NOTES));
		assertEquals(keys(RecognitionParameters.CHORD_EVIDENCE, RecognitionParameters.CHORD_WINDOW, RecognitionParameters.CHORD_COMPLETENESS,
				RecognitionParameters.CHORD_FOREIGN, RecognitionParameters.CHORD_PROFILE_MATCH, RecognitionParameters.CHORD_PROFILE_NAMES,
				RecognitionParameters.CHORD_ATTACK), keys(Section.CHORDS));
		assertEquals(keys(RecognitionParameters.CHORD_PROFILE_TOP, RecognitionParameters.CHORD_PROFILE_SETTLE), keys(Section.CHORD_DETAILS));
		assertEquals(keys(RecognitionParameters.PASSAGE_MEASURES, RecognitionParameters.PASSAGE_THRESHOLD, RecognitionParameters.PASSAGE_RETRIES,
				RecognitionParameters.FOLLOW_PLAY_TIME), keys(Section.PASSAGES));
		assertEquals(keys(RecognitionParameters.ENGINE_PITCH), keys(Section.ENGINE));
		assertEquals(keys(RecognitionParameters.NOTE_FRET_SHARP, RecognitionParameters.LEVEL_CLIP_SAMPLE, RecognitionParameters.LEVEL_CLIP_SHARE,
				RecognitionParameters.TUNER_CONFIDENCE, RecognitionParameters.ATTACK_MIN_LEVEL, RecognitionParameters.CHORD_MIN_PARTIALS,
				RecognitionParameters.CHORD_CONFIRM), keys(Section.ADVANCED));
	}

	@Test
	public void everySectionHoldsOneToSevenParameters() {
		for( Section section : Section.values() ) {
			int size = RecognitionParameters.of(section).size();
			assertTrue(size >= 1 && size <= 7, section + " holds " + size);
		}
	}

	@Test
	public void aDeviceProfileNeverOverlaysANewSection() {
		List<Section> today = Arrays.asList(TODAY);
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( !today.contains(parameter.getSection()) ) {
				assertFalse(ProfileParameters.contains(parameter), parameter.getKey());
			}
		}
	}

	@Test
	public void anInvertedPairIsBroughtBackWithinItsUpper() {
		OrderedPair pair = new OrderedPair(RecognitionParameters.NOTE_STABILITY, RecognitionParameters.CHORD_WINDOW);
		Object[] values = RecognitionSettings.defaults().toArray();
		values[RecognitionParameters.NOTE_STABILITY.getIndex()] = 300.0;
		values[RecognitionParameters.CHORD_WINDOW.getIndex()] = 250.0;
		Map<String, Repair> repairs = new HashMap<String, Repair>();
		RecognitionSettings.applyPairs(values, repairs, Collections.singletonList(pair));
		assertEquals(250.0, values[RecognitionParameters.NOTE_STABILITY.getIndex()]);
		assertEquals(250.0, values[RecognitionParameters.CHORD_WINDOW.getIndex()], "the upper value never moves");
		assertEquals(Repair.CLAMPED, repairs.get(RecognitionParameters.NOTE_STABILITY.getKey()));
	}

	@Test
	public void anOrderedPairLeavesAnOrderedOneAlone() {
		OrderedPair pair = new OrderedPair(RecognitionParameters.NOTE_STABILITY, RecognitionParameters.CHORD_WINDOW);
		Object[] values = RecognitionSettings.defaults().toArray();
		Map<String, Repair> repairs = new HashMap<String, Repair>();
		RecognitionSettings.applyPairs(values, repairs, Collections.singletonList(pair));
		assertEquals(107.0, values[RecognitionParameters.NOTE_STABILITY.getIndex()]);
		assertTrue(repairs.isEmpty());
	}

	@Test
	public void everyDeclaredPairHoldsOnReadingAndOnEveryChange() {
		for( OrderedPair pair : RecognitionParameters.ORDERED_PAIRS ) {
			Map<String, String> stored = new HashMap<String, String>();
			stored.put(pair.getLower().getKey(), pair.getLower().format(pair.getLower().getMaximum()));
			stored.put(pair.getUpper().getKey(), pair.getUpper().format(pair.getUpper().getMinimum()));
			RecognitionSettings read = RecognitionSettings.fromStrings(stored).getSettings();
			assertTrue(read.get(pair.getLower()) <= read.get(pair.getUpper()), pair + " on reading");
			RecognitionSettings changed = RecognitionSettings.defaults().with(pair.getUpper(), pair.getUpper().getMinimum())
					.with(pair.getLower(), pair.getLower().getMaximum());
			assertTrue(changed.get(pair.getLower()) <= changed.get(pair.getUpper()), pair + " on a change");
		}
	}

	private static List<String> keys(RecognitionParameter... parameters) {
		List<String> keys = new ArrayList<String>();
		for( RecognitionParameter parameter : parameters ) {
			keys.add(parameter.getKey());
		}
		return keys;
	}

	private static List<String> keys(Section section) {
		List<String> keys = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
			keys.add(parameter.getKey());
		}
		return keys;
	}
}
