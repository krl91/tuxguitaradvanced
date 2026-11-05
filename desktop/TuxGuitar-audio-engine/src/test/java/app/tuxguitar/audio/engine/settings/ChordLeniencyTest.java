package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.ChordLeniency.Level;

/**
 * The chord leniency levels (spec 024, task T029; data-model "Chord leniency level", FR-005 to FR-007): the level is derived from the settings, choosing
 * a level writes only its values, and a value changed by hand makes the level Custom.
 */
public class ChordLeniencyTest {

	private static final List<Level> NAMED = Arrays.asList(Level.STRICT, Level.NORMAL, Level.LENIENT);

	/** Settings far from every level, in the level values and in the others. */
	private static RecognitionSettings changed() {
		return RecognitionSettings.defaults()
				.with(RecognitionParameters.CHORD_COMPLETENESS, RecognitionParameters.COMPLETENESS_ALL)
				.with(RecognitionParameters.CHORD_PROFILE_MATCH, 0.93)
				.with(RecognitionParameters.CHORD_ATTACK, false)
				.with(RecognitionParameters.CHORD_EVIDENCE, 0.5)
				.with(RecognitionParameters.CHORD_WINDOW, RecognitionParameters.CHORD_WINDOW.getMaximum())
				.with(RecognitionParameters.CHORD_PROFILE_NAMES, 0.20);
	}

	@Test
	public void theDefaultsAreNormal() {
		assertEquals(Level.NORMAL, ChordLeniency.of(RecognitionSettings.defaults()));
	}

	@Test
	public void eachLevelHasTheValuesOfTheDataModel() {
		Map<RecognitionParameter, Object> strict = ChordLeniency.values(Level.STRICT);
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, strict.get(RecognitionParameters.CHORD_COMPLETENESS));
		assertEquals(0.79, (Double) strict.get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
		assertEquals(Boolean.FALSE, strict.get(RecognitionParameters.CHORD_ATTACK));
		Map<RecognitionParameter, Object> normal = ChordLeniency.values(Level.NORMAL);
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, normal.get(RecognitionParameters.CHORD_COMPLETENESS));
		assertEquals(0.79, (Double) normal.get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
		assertEquals(Boolean.TRUE, normal.get(RecognitionParameters.CHORD_ATTACK));
		Map<RecognitionParameter, Object> lenient = ChordLeniency.values(Level.LENIENT);
		assertEquals(RecognitionParameters.COMPLETENESS_PROFILE, lenient.get(RecognitionParameters.CHORD_COMPLETENESS));
		assertEquals(0.70, (Double) lenient.get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
		assertEquals(Boolean.TRUE, lenient.get(RecognitionParameters.CHORD_ATTACK));
		for( Level level : NAMED ) {
			assertEquals(3, ChordLeniency.values(level).size(), "the level values are the rule, the match and the attack path: " + level);
		}
	}

	@Test
	public void applyingALevelGivesThatLevelWhateverTheSettings() {
		for( Level level : NAMED ) {
			assertEquals(level, ChordLeniency.of(ChordLeniency.apply(level, RecognitionSettings.defaults())), "from the defaults: " + level);
			assertEquals(level, ChordLeniency.of(ChordLeniency.apply(level, changed())), "from changed settings: " + level);
		}
	}

	@Test
	public void applyingALevelChangesNoOtherValue() {
		RecognitionSettings before = changed();
		for( Level level : NAMED ) {
			RecognitionSettings after = ChordLeniency.apply(level, before);
			Map<RecognitionParameter, Object> values = ChordLeniency.values(level);
			for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
				if( !values.containsKey(parameter) ) {
					assertEquals(before.getValue(parameter), after.getValue(parameter), level + " left " + parameter.getKey() + " alone");
				} else {
					assertEquals(values.get(parameter), after.getValue(parameter), level + " wrote " + parameter.getKey());
				}
			}
		}
	}

	@Test
	public void customCannotBeApplied() {
		assertThrows(IllegalArgumentException.class, () -> ChordLeniency.apply(Level.CUSTOM, RecognitionSettings.defaults()));
		assertThrows(IllegalArgumentException.class, () -> ChordLeniency.values(Level.CUSTOM));
	}

	@Test
	public void aLevelValueChangedByHandGivesCustom() {
		RecognitionSettings normal = RecognitionSettings.defaults();
		assertEquals(Level.CUSTOM, ChordLeniency.of(normal.with(RecognitionParameters.CHORD_PROFILE_MATCH, 0.75)));
		assertEquals(Level.CUSTOM, ChordLeniency.of(normal.with(RecognitionParameters.CHORD_COMPLETENESS, RecognitionParameters.COMPLETENESS_ALL)));
		RecognitionSettings lenient = ChordLeniency.apply(Level.LENIENT, normal);
		assertEquals(Level.CUSTOM, ChordLeniency.of(lenient.with(RecognitionParameters.CHORD_ATTACK, false)), "Lenient without the attack is no level");
		assertEquals(Level.CUSTOM, ChordLeniency.of(changed()));
	}

	@Test
	public void aValueThatIsNotALevelValueDoesNotChangeTheLevel() {
		RecognitionSettings normal = RecognitionSettings.defaults();
		assertEquals(Level.NORMAL, ChordLeniency.of(normal.with(RecognitionParameters.CHORD_EVIDENCE, 0.5)));
		assertEquals(Level.NORMAL, ChordLeniency.of(normal.with(RecognitionParameters.CHORD_PROFILE_NAMES, 0.20)));
		assertEquals(Level.STRICT, ChordLeniency.of(ChordLeniency.apply(Level.STRICT, normal).with(RecognitionParameters.CHORD_WINDOW,
				RecognitionParameters.CHORD_WINDOW.getMaximum())));
	}
}
