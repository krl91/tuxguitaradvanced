package app.tuxguitar.audio.engine.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How strict the chord check is (spec 024, data-model "Chord leniency level", research R7). The level is not stored: it is derived from the
 * settings. This class also keeps the history of the past values of the levels, so that a file saved with a level's old values is read as that
 * level's current values (the table-driven migration of {@link RecognitionSettings#fromStrings}): a later version that changes a level's values adds
 * the old set to {@link #HISTORY}.
 */
public final class ChordLeniency {

	public enum Level {
		STRICT,
		NORMAL,
		LENIENT,
		CUSTOM
	}

	/** A past set of a level's values: key to stored text, a null text meaning that the key was absent from the file. */
	public static final class PastSet {
		private final Level level;
		private final Map<String, String> values;

		public PastSet(Level level, Map<String, String> values) {
			this.level = level;
			this.values = Collections.unmodifiableMap(new HashMap<String, String>(values));
		}

		public Level getLevel() {
			return this.level;
		}

		/** True when the stored file holds exactly these values (compared as validated values, so that "0.5" and "0.50" are one). */
		boolean matches(Map<String, String> stored) {
			for( Map.Entry<String, String> entry : this.values.entrySet() ) {
				String raw = stored.get(entry.getKey());
				if( entry.getValue() == null || raw == null ) {
					if( entry.getValue() != raw ) {
						return false;
					}
					continue;
				}
				RecognitionParameter parameter = RecognitionParameters.byKey(entry.getKey());
				if( parameter == null ) {
					if( !entry.getValue().equals(raw) ) {
						return false;
					}
					continue;
				}
				if( !parameter.parse(raw).getValue().equals(parameter.parse(entry.getValue()).getValue()) ) {
					return false;
				}
			}
			return true;
		}
	}

	/** Gives the settings with a level's current values. */
	public interface Applier {
		RecognitionSettings apply(Level level, RecognitionSettings settings);
	}

	/** The past sets, oldest first. Spec 023's chord values, before the profile rule existed, are Normal's. */
	/** The levels a learner can choose, from the strictest. */
	public static final List<Level> NAMED = Collections.unmodifiableList(Arrays.asList(Level.STRICT, Level.NORMAL, Level.LENIENT));

	/** Lenient's chord match (T007: the right chords of the reference series keep a margin, no clearly wrong one comes near). */
	private static final Double LENIENT_MATCH = Double.valueOf(0.70);

	public static final List<PastSet> HISTORY = Collections.unmodifiableList(Arrays.asList(spec023()));

	private ChordLeniency() {
	}

	private static PastSet spec023() {
		Map<String, String> values = new HashMap<String, String>();
		values.put(RecognitionParameters.CHORD_COMPLETENESS.getKey(), RecognitionParameters.COMPLETENESS_ALL);
		values.put(RecognitionParameters.CHORD_EVIDENCE.getKey(), Double.toString(1.0 / 3.0));
		values.put(RecognitionParameters.CHORD_CONFIRM.getKey(), "43");
		values.put(RecognitionParameters.CHORD_PROFILE_MATCH.getKey(), null);
		return new PastSet(Level.NORMAL, values);
	}

	/** The level the settings hold: the named level whose values they all hold, otherwise {@link Level#CUSTOM}. */
	public static Level of(RecognitionSettings settings) {
		for( Level level : NAMED ) {
			boolean holds = true;
			for( Map.Entry<RecognitionParameter, Object> entry : values(level).entrySet() ) {
				holds &= entry.getValue().equals(settings.getValue(entry.getKey()));
			}
			if( holds ) {
				return level;
			}
		}
		return Level.CUSTOM;
	}

	/** The settings with a level's values, every other value as it was. Custom has no values and cannot be applied. */
	public static RecognitionSettings apply(Level level, RecognitionSettings settings) {
		RecognitionSettings applied = settings;
		for( Map.Entry<RecognitionParameter, Object> entry : values(level).entrySet() ) {
			applied = applied.with(entry.getKey(), entry.getValue());
		}
		return applied;
	}

	/**
	 * A level's values (data-model "Chord leniency level", research R6, the T007 figures): the rule, the match and the attack path. Normal's are the
	 * defaults; Strict refuses the attack path, Lenient lowers the match.
	 */
	public static Map<RecognitionParameter, Object> values(Level level) {
		if( level == null || level == Level.CUSTOM ) {
			throw new IllegalArgumentException("no values for the level " + level);
		}
		RecognitionSettings defaults = RecognitionSettings.defaults();
		Map<RecognitionParameter, Object> values = new LinkedHashMap<RecognitionParameter, Object>();
		values.put(RecognitionParameters.CHORD_COMPLETENESS, defaults.getValue(RecognitionParameters.CHORD_COMPLETENESS));
		values.put(RecognitionParameters.CHORD_PROFILE_MATCH, level == Level.LENIENT ? LENIENT_MATCH : defaults.getValue(RecognitionParameters.CHORD_PROFILE_MATCH));
		values.put(RecognitionParameters.CHORD_ATTACK, level == Level.STRICT ? Boolean.FALSE : defaults.getValue(RecognitionParameters.CHORD_ATTACK));
		return Collections.unmodifiableMap(values);
	}

	/** The settings read from {@code stored}, with a level's current values when the file holds a past set of them; otherwise as they are. */
	static RecognitionSettings migrate(Map<String, String> stored, RecognitionSettings read, List<PastSet> history, Applier current) {
		for( PastSet past : history ) {
			if( past.matches(stored) ) {
				return current.apply(past.getLevel(), read);
			}
		}
		return read;
	}
}
