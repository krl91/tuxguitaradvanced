package app.tuxguitar.audio.engine.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The recognition settings a device profile may hold (spec 013, research R2), in a fixed order that the search follows: the note and chord settings
 * and the level thresholds of the input. Never the pitch engine, the tuner's own settings, the passages, the flames, the timing window, the legato or
 * technique choices, the noise reduction or the gain.
 */
public final class ProfileParameters {

	public static final List<RecognitionParameter.Number> LIST = Collections.unmodifiableList(Arrays.asList(
			RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_FRET_SHARP, RecognitionParameters.NOTE_CONFIDENCE,
			RecognitionParameters.NOTE_STABILITY, RecognitionParameters.NOTE_ATTACK,
			RecognitionParameters.CHORD_EVIDENCE, RecognitionParameters.CHORD_WINDOW, RecognitionParameters.CHORD_MIN_PARTIALS, RecognitionParameters.CHORD_CONFIRM,
			RecognitionParameters.ATTACK_MIN_LEVEL, RecognitionParameters.INPUT_LEVEL_MIN));

	private ProfileParameters() {
	}

	public static boolean contains(RecognitionParameter parameter) {
		return LIST.contains(parameter);
	}

	/** True when {@code value} is a number the parameter accepts as it is (inside its bounds, a whole number where it must be). */
	public static boolean isValid(RecognitionParameter.Number parameter, double value) {
		return parameter.validate(value).getRepair() == RecognitionParameter.Repair.NONE;
	}

	/** {@code base} with the given values of profile parameters; every other value is the base's. */
	public static RecognitionSettings merge(RecognitionSettings base, Map<RecognitionParameter.Number, Double> values) {
		RecognitionSettings merged = base;
		for (RecognitionParameter.Number parameter : LIST) {
			Double value = values.get(parameter);
			if (value != null) {
				merged = merged.with(parameter, value);
			}
		}
		return merged;
	}
}
