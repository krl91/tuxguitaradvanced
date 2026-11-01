package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The texts of the Amp and effects window (spec 022, FR-016): {@link #KEYS} are the controls, each with a tooltip {@code <key>.tip}; {@link #PLAIN} are the
 * texts without a tooltip (titles, values, messages); {@link #KNOBS} are the controls whose tooltip names a unit and a default. The list grows with each
 * user story, with its texts in the three language files.
 */
final class AmpTexts {

	static final String P = "practice.amp.";

	/** A knob: its key, the key of its unit, and its default as the tooltip writes it (the value of the Clean sound, the one a fresh store gives). */
	static final class KnobText {
		private final String key;
		private final String unitKey;
		private final String defaultText;

		KnobText(String key, String unitKey, String defaultText) {
			this.key = key;
			this.unitKey = unitKey;
			this.defaultText = defaultText;
		}

		String key() {
			return this.key;
		}

		String unitKey() {
			return this.unitKey;
		}

		String defaultText() {
			return this.defaultText;
		}
	}

	static final String OPEN = P + "open";
	static final String ENABLED = P + "enabled";
	static final String OUTPUT = P + "output";
	static final String GUITAR_VOLUME = P + "guitar-volume";
	static final String SCORE_VOLUME = P + "score-volume";
	static final String DELAY = P + "delay";
	static final String CHARACTER = P + "character";
	static final String GAIN = P + "gain";
	static final String BASS = P + "bass";
	static final String MIDDLE = P + "middle";
	static final String TREBLE = P + "treble";
	static final String VOLUME = P + "volume";
	static final String CABINET = P + "cabinet";

	static final String TITLE = P + "title";
	static final String AMP = P + "amp";
	static final String OUTPUT_DEFAULT = P + "output.default";
	static final String DELAY_VALUE = P + "delay.value";
	static final String DELAY_UNKNOWN = P + "delay.unknown";
	static final String CLEAN = P + "character.clean";
	static final String CRUNCH = P + "character.crunch";
	static final String HIGH_GAIN = P + "character.high-gain";
	static final String UNIT_SCALE = P + "unit.scale";
	static final String UNIT_PERCENT = P + "unit.percent";
	static final String FAILED = P + "failed";
	static final String CLIPPING = P + "clipping";

	static final List<String> KEYS = Collections.unmodifiableList(Arrays.asList(OPEN, ENABLED, OUTPUT, GUITAR_VOLUME, SCORE_VOLUME, DELAY, CHARACTER, GAIN, BASS,
			MIDDLE, TREBLE, VOLUME, CABINET));

	static final List<String> PLAIN = Collections.unmodifiableList(Arrays.asList(TITLE, AMP, OUTPUT_DEFAULT, DELAY_VALUE, DELAY_UNKNOWN, CLEAN, CRUNCH, HIGH_GAIN,
			UNIT_SCALE, UNIT_PERCENT, FAILED, CLIPPING));

	static final List<KnobText> KNOBS = Collections.unmodifiableList(Arrays.asList(new KnobText(GUITAR_VOLUME, UNIT_PERCENT, "70"),
			new KnobText(SCORE_VOLUME, UNIT_SCALE, "10"), new KnobText(GAIN, UNIT_SCALE, "3"), new KnobText(BASS, UNIT_SCALE, "5"),
			new KnobText(MIDDLE, UNIT_SCALE, "5"), new KnobText(TREBLE, UNIT_SCALE, "6"), new KnobText(VOLUME, UNIT_SCALE, "6")));

	private AmpTexts() {
	}
}
