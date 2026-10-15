package app.tuxguitar.audio.engine.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Choice;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Flag;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Number;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Parsed;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Repair;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * A complete, validated set of audio-recognition values (task T102). Immutable: every value is
 * within its bounds and the cross-parameter rules hold, whatever it was built from, so the UI,
 * the tuner, practice and the engine all read the same valid values.
 *
 * <p>Cross-parameter rule: the practice acceptance range is never narrower than the tuner's
 * in-tune range ({@code TolerancePolicy} requires {@code inTune <= accept}); a lower acceptance is
 * raised to the in-tune range.</p>
 */
public final class RecognitionSettings {

	/** Settings read from a configuration, with what validation repaired (key → repair). */
	public static final class Loaded {
		private final RecognitionSettings settings;
		private final Map<String, Repair> repairs;
		private final List<String> unknownKeys;

		Loaded(RecognitionSettings settings, Map<String, Repair> repairs, List<String> unknownKeys) {
			this.settings = settings;
			this.repairs = Collections.unmodifiableMap(repairs);
			this.unknownKeys = Collections.unmodifiableList(unknownKeys);
		}

		public RecognitionSettings getSettings() {
			return this.settings;
		}

		/** Keys whose stored value was missing, invalid or out of bounds, and what was done. */
		public Map<String, Repair> getRepairs() {
			return this.repairs;
		}

		/** Recognition keys this version does not know (from another version); dropped on the next save. */
		public List<String> getUnknownKeys() {
			return this.unknownKeys;
		}
	}

	private static final RecognitionSettings DEFAULTS;

	static {
		Object[] values = new Object[RecognitionParameters.ALL.size()];
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			values[parameter.getIndex()] = parameter.getDefault();
		}
		DEFAULTS = new RecognitionSettings(values);
	}

	private final Object[] values;

	private RecognitionSettings(Object[] values) {
		this.values = values;
	}

	/** The recommended values (the behaviour before the settings existed). */
	public static RecognitionSettings defaults() {
		return DEFAULTS;
	}

	/**
	 * Reads stored text values: a missing, empty, unreadable, NaN or infinite value gives the
	 * recommended value; an out-of-bounds number is brought to the nearest bound; the
	 * cross-parameter rules are then applied. Never throws.
	 */
	public static Loaded fromStrings(Map<String, String> stored) {
		Map<String, String> source = (stored != null ? stored : Collections.<String, String>emptyMap());
		Object[] values = new Object[RecognitionParameters.ALL.size()];
		Map<String, Repair> repairs = new LinkedHashMap<String, Repair>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			String raw = source.get(parameter.getKey());
			Parsed parsed = parameter.parse(raw);
			values[parameter.getIndex()] = parsed.getValue();
			// a missing key is not a repair: older configurations simply do not have it yet
			if( raw != null && parsed.getRepair() != Repair.NONE ) {
				repairs.put(parameter.getKey(), parsed.getRepair());
			}
		}
		// a configuration saved before spec 012 holds the choice of a preset and no window: strict and lenient are carried over as their width; "normal" is not, because
		// the store writes every key as soon as one setting is saved, so a saved "normal" is the old default that nobody chose and gets the new default
		if( source.get(RecognitionParameters.TEMPO_WINDOW.getKey()) == null ) {
			String legacy = source.get(RecognitionParameters.LEGACY_TEMPO_TOLERANCE_KEY);
			if( RecognitionParameters.TOLERANCE_STRICT.equals(legacy) ) {
				values[RecognitionParameters.TEMPO_WINDOW.getIndex()] = 50.0;
			} else if( RecognitionParameters.TOLERANCE_LENIENT.equals(legacy) ) {
				values[RecognitionParameters.TEMPO_WINDOW.getIndex()] = 125.0;
			}
		}
		List<String> unknown = new java.util.ArrayList<String>();
		for( String key : source.keySet() ) {
			if( key != null && key.startsWith("recognition.") && !key.equals(RecognitionParameters.VERSION_KEY) && !key.equals(RecognitionParameters.LEGACY_TEMPO_TOLERANCE_KEY)
					&& RecognitionParameters.byKey(key) == null ) {
				unknown.add(key);
			}
		}
		Collections.sort(unknown);
		RecognitionSettings settings = normalized(values, repairs);
		return new Loaded(settings, repairs, unknown);
	}

	/** The values to store, including the layout version. */
	public Map<String, String> toStrings() {
		Map<String, String> result = new LinkedHashMap<String, String>();
		result.put(RecognitionParameters.VERSION_KEY, Integer.toString(RecognitionParameters.VERSION));
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			result.put(parameter.getKey(), parameter.format(this.values[parameter.getIndex()]));
		}
		return result;
	}

	public double get(Number parameter) {
		return (Double) this.values[parameter.getIndex()];
	}

	public boolean get(Flag parameter) {
		return (Boolean) this.values[parameter.getIndex()];
	}

	public String get(Choice parameter) {
		return (String) this.values[parameter.getIndex()];
	}

	/** The raw validated value of any parameter. */
	public Object getValue(RecognitionParameter parameter) {
		return this.values[parameter.getIndex()];
	}

	/**
	 * A copy with one value changed, validated like a stored value (bounds, whole numbers, type)
	 * and with the cross-parameter rules applied.
	 */
	public RecognitionSettings with(RecognitionParameter parameter, Object value) {
		Object[] copy = this.values.clone();
		copy[parameter.getIndex()] = parameter.validate(value).getValue();
		return normalized(copy, null);
	}

	/** A copy with the recommended values of one section. */
	public RecognitionSettings withDefaults(Section section) {
		Object[] copy = this.values.clone();
		for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
			copy[parameter.getIndex()] = parameter.getDefault();
		}
		return normalized(copy, null);
	}

	/** Whether every value of the section is its recommended value. */
	public boolean isDefault(Section section) {
		for( RecognitionParameter parameter : RecognitionParameters.of(section) ) {
			if( !parameter.getDefault().equals(getValue(parameter)) ) {
				return false;
			}
		}
		return true;
	}

	public boolean isDefault(RecognitionParameter parameter) {
		return parameter.getDefault().equals(getValue(parameter));
	}

	// ---------------------------------------------------------------- derived values for consumers

	/** Practice stability window in whole analysis hops (107 ms → 20). */
	public int getNoteStabilityHops() {
		return RecognitionParameters.toHops(get(RecognitionParameters.NOTE_STABILITY));
	}

	/** Chord confirmation in whole analysis hops (43 ms → 8). */
	public int getChordConfirmHops() {
		return RecognitionParameters.toHops(get(RecognitionParameters.CHORD_CONFIRM));
	}

	/** Passage length in measures, 0 when passages are off. */
	public int getPassageMeasures() {
		return Integer.parseInt(get(RecognitionParameters.PASSAGE_MEASURES));
	}

	/** Passage error threshold in percent. */
	public int getPassageThresholdPercent() {
		return Integer.parseInt(get(RecognitionParameters.PASSAGE_THRESHOLD));
	}

	public int getPassageRetries() {
		return (int) get(RecognitionParameters.PASSAGE_RETRIES);
	}

	private static RecognitionSettings normalized(Object[] values, Map<String, Repair> repairs) {
		double inTune = (Double) values[RecognitionParameters.TUNER_IN_TUNE.getIndex()];
		int acceptIndex = RecognitionParameters.NOTE_ACCEPT.getIndex();
		if( (Double) values[acceptIndex] < inTune ) {
			values[acceptIndex] = inTune;
			if( repairs != null ) {
				repairs.put(RecognitionParameters.NOTE_ACCEPT.getKey(), Repair.CLAMPED);
			}
		}
		return new RecognitionSettings(values);
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof RecognitionSettings && Arrays.equals(this.values, ((RecognitionSettings) other).values));
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.values);
	}

	@Override
	public String toString() {
		return toStrings().toString();
	}
}
