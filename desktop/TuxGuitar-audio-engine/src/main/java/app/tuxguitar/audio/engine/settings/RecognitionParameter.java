package app.tuxguitar.audio.engine.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One audio-recognition setting (task T102): its persisted key, section, type, recommended
 * default and valid values, with the validation used both for what the learner enters and for
 * what is read back from the configuration file. Immutable; the instances are the constants of
 * {@link RecognitionParameters}.
 */
public abstract class RecognitionParameter {

	/** Where a setting is shown in the Configuration rubric. */
	public enum Section {
		INPUT,
		TUNER,
		NOTES,
		CHORDS,
		PASSAGES,
		ENGINE,
		ADVANCED
	}

	/** What validation did with a raw value. */
	public enum Repair {
		/** Valid as given. */
		NONE,
		/** A number outside the bounds was brought to the nearest bound (or whole value). */
		CLAMPED,
		/** Missing, empty, unreadable, not a number, infinite or of the wrong type: the recommended value is used. */
		DEFAULTED
	}

	/** A validated value and what validation did. */
	public static final class Parsed {
		private final Object value;
		private final Repair repair;

		Parsed(Object value, Repair repair) {
			this.value = value;
			this.repair = repair;
		}

		public Object getValue() {
			return this.value;
		}

		public Repair getRepair() {
			return this.repair;
		}
	}

	private final String key;
	private final Section section;
	private final String unit;
	private int index = -1;

	RecognitionParameter(String key, Section section, String unit) {
		this.key = key;
		this.section = section;
		this.unit = unit;
	}

	/** The key in the configuration file, e.g. {@code recognition.note.accept}. */
	public String getKey() {
		return this.key;
	}

	public Section getSection() {
		return this.section;
	}

	/** Unit shown next to the value ("cents", "ms", "dB", "%", …); empty when none. */
	public String getUnit() {
		return this.unit;
	}

	/** The recommended value (the current behaviour). */
	public abstract Object getDefault();

	/** Validates a value read from the configuration (null for a missing key). */
	public abstract Parsed parse(String raw);

	/** Validates a typed value set by the UI or code. */
	public abstract Parsed validate(Object value);

	/** The text written to the configuration for a validated value. */
	public abstract String format(Object value);

	int getIndex() {
		return this.index;
	}

	void setIndex(int index) {
		this.index = index;
	}

	@Override
	public String toString() {
		return this.key;
	}

	/** A number with inclusive bounds; whole numbers only when {@code integer}. */
	public static final class Number extends RecognitionParameter {
		private final double defaultValue;
		private final double minimum;
		private final double maximum;
		private final boolean integer;
		private final double displayFactor;

		Number(String key, Section section, String unit, double defaultValue, double minimum, double maximum, boolean integer) {
			this(key, section, unit, defaultValue, minimum, maximum, integer, 1.0);
		}

		/**
		 * @param displayFactor shown value = stored value × factor (100 for a fraction shown in %),
		 *        so the stored default stays the exact current value
		 */
		Number(String key, Section section, String unit, double defaultValue, double minimum, double maximum, boolean integer,
				double displayFactor) {
			super(key, section, unit);
			this.displayFactor = displayFactor;
			if( !(minimum <= defaultValue && defaultValue <= maximum) ) {
				throw new IllegalArgumentException(key + ": default outside bounds");
			}
			this.defaultValue = defaultValue;
			this.minimum = minimum;
			this.maximum = maximum;
			this.integer = integer;
		}

		@Override
		public Double getDefault() {
			return this.defaultValue;
		}

		public double getMinimum() {
			return this.minimum;
		}

		public double getMaximum() {
			return this.maximum;
		}

		public boolean isInteger() {
			return this.integer;
		}

		/** Shown value = stored value × this factor (100 for fractions shown in %). */
		public double getDisplayFactor() {
			return this.displayFactor;
		}

		@Override
		public Parsed parse(String raw) {
			if( raw == null || raw.trim().isEmpty() ) {
				return new Parsed(this.defaultValue, Repair.DEFAULTED);
			}
			double value;
			try {
				value = Double.parseDouble(raw.trim());
			} catch (NumberFormatException exception) {
				return new Parsed(this.defaultValue, Repair.DEFAULTED);
			}
			return validate(value);
		}

		@Override
		public Parsed validate(Object value) {
			if( !(value instanceof java.lang.Number) ) {
				return new Parsed(this.defaultValue, Repair.DEFAULTED);
			}
			double number = ((java.lang.Number) value).doubleValue();
			if( Double.isNaN(number) || Double.isInfinite(number) ) {
				return new Parsed(this.defaultValue, Repair.DEFAULTED);
			}
			double repaired = (this.integer ? Math.rint(number) : number);
			repaired = Math.max(this.minimum, Math.min(this.maximum, repaired));
			return new Parsed(repaired, repaired == number ? Repair.NONE : Repair.CLAMPED);
		}

		@Override
		public String format(Object value) {
			double number = ((java.lang.Number) value).doubleValue();
			if( this.integer || number == Math.rint(number) ) {
				return Long.toString(Math.round(number));
			}
			return Double.toString(number);
		}
	}

	/** On / off. */
	public static final class Flag extends RecognitionParameter {
		private final boolean defaultValue;

		Flag(String key, Section section, boolean defaultValue) {
			super(key, section, "");
			this.defaultValue = defaultValue;
		}

		@Override
		public Boolean getDefault() {
			return this.defaultValue;
		}

		@Override
		public Parsed parse(String raw) {
			if( raw != null ) {
				String text = raw.trim();
				if( text.equalsIgnoreCase("true") ) {
					return new Parsed(Boolean.TRUE, Repair.NONE);
				}
				if( text.equalsIgnoreCase("false") ) {
					return new Parsed(Boolean.FALSE, Repair.NONE);
				}
			}
			return new Parsed(this.defaultValue, Repair.DEFAULTED);
		}

		@Override
		public Parsed validate(Object value) {
			if( value instanceof Boolean ) {
				return new Parsed(value, Repair.NONE);
			}
			return new Parsed(this.defaultValue, Repair.DEFAULTED);
		}

		@Override
		public String format(Object value) {
			return value.toString();
		}
	}

	/** One of a fixed list of identifiers. */
	public static final class Choice extends RecognitionParameter {
		private final List<String> options;
		private final String defaultValue;

		Choice(String key, Section section, String defaultValue, String... options) {
			super(key, section, "");
			this.options = Collections.unmodifiableList(Arrays.asList(options));
			if( !this.options.contains(defaultValue) ) {
				throw new IllegalArgumentException(key + ": default not an option");
			}
			this.defaultValue = defaultValue;
		}

		@Override
		public String getDefault() {
			return this.defaultValue;
		}

		public List<String> getOptions() {
			return this.options;
		}

		@Override
		public Parsed parse(String raw) {
			return validate(raw == null ? null : raw.trim());
		}

		@Override
		public Parsed validate(Object value) {
			if( value instanceof String && this.options.contains(value) ) {
				return new Parsed(value, Repair.NONE);
			}
			return new Parsed(this.defaultValue, Repair.DEFAULTED);
		}

		@Override
		public String format(Object value) {
			return (String) value;
		}
	}
}
