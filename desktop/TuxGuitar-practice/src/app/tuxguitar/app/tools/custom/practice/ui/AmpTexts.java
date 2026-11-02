package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.audio.engine.monitor.Knob;

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
	static final String ADD_PEDAL = P + "add-pedal";
	static final String PEDAL_UP = P + "pedal.up";
	static final String PEDAL_DOWN = P + "pedal.down";
	static final String PEDAL_REMOVE = P + "pedal.remove";
	static final String SOUND = P + "sound";
	static final String SOUND_NAME = P + "sound-name";
	static final String SAVE_AS = P + "save-as";
	static final String RENAME = P + "rename";
	static final String DELETE = P + "delete";
	static final String RESTORE = P + "restore";

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
	static final String BEFORE = P + "before";
	static final String AFTER = P + "after";
	static final String UNIT_DB = P + "unit.db";
	static final String UNIT_MS = P + "unit.ms";
	static final String UNIT_HZ = P + "unit.hz";
	static final String CHANGED = P + "changed";
	static final String NAME_READY_MADE = P + "name.ready-made";
	static final String NAME_TOO_LONG = P + "name.too-long";

	static final List<String> KEYS;

	static final List<String> PLAIN = Collections.unmodifiableList(Arrays.asList(TITLE, AMP, OUTPUT_DEFAULT, DELAY_VALUE, DELAY_UNKNOWN, CLEAN, CRUNCH, HIGH_GAIN,
			UNIT_SCALE, UNIT_PERCENT, FAILED, CLIPPING, BEFORE, AFTER, UNIT_DB, UNIT_MS, UNIT_HZ, CHANGED, NAME_READY_MADE, NAME_TOO_LONG));

	static final List<KnobText> KNOBS;

	static {
		List<String> keys = new ArrayList<String>(Arrays.asList(OPEN, ENABLED, OUTPUT, GUITAR_VOLUME, SCORE_VOLUME, DELAY, CHARACTER, GAIN, BASS, MIDDLE, TREBLE,
				VOLUME, CABINET, ADD_PEDAL, PEDAL_UP, PEDAL_DOWN, PEDAL_REMOVE, SOUND, SOUND_NAME, SAVE_AS, RENAME, DELETE, RESTORE));
		List<KnobText> knobs = new ArrayList<KnobText>(Arrays.asList(new KnobText(GUITAR_VOLUME, UNIT_PERCENT, "70"), new KnobText(SCORE_VOLUME, UNIT_SCALE, "10"),
				new KnobText(GAIN, UNIT_SCALE, "3"), new KnobText(BASS, UNIT_SCALE, "5"), new KnobText(MIDDLE, UNIT_SCALE, "5"), new KnobText(TREBLE, UNIT_SCALE, "6"),
				new KnobText(VOLUME, UNIT_SCALE, "6")));
		for (EffectKind kind : EffectKind.values()) {
			keys.add(pedal(kind));
			for (Knob knob : kind.knobs()) {
				keys.add(knob(kind, knob));
				knobs.add(new KnobText(knob(kind, knob), unitKey(knob), AmpPedalRow.format(knob, knob.defaultValue())));
			}
		}
		KEYS = Collections.unmodifiableList(keys);
		KNOBS = Collections.unmodifiableList(knobs);
	}

	private AmpTexts() {
	}

	/** The key of a pedal's name (its switch), e.g. {@code practice.amp.pedal.echo}. */
	static String pedal(EffectKind kind) {
		return P + "pedal." + kind.textName();
	}

	/** The key of a pedal's knob, e.g. {@code practice.amp.pedal.echo.time}. */
	static String knob(EffectKind kind, Knob knob) {
		return pedal(kind) + "." + knob.key();
	}

	static String unitKey(Knob knob) {
		switch (knob.unit()) {
		case "dB":
			return UNIT_DB;
		case "ms":
			return UNIT_MS;
		case "Hz":
			return UNIT_HZ;
		case "%":
			return UNIT_PERCENT;
		default:
			return UNIT_SCALE;
		}
	}
}
