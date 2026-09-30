package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Choice;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Flag;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Number;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/**
 * Every audio-recognition setting (task T102; inventory in research.md "T102"). Each default is
 * the value that was hard-coded before the settings existed, so the recommended configuration
 * behaves exactly as before; the bounds only limit what the learner may choose.
 */
public final class RecognitionParameters {

	private static final String PREFIX = "recognition.";

	/** Configuration-file layout version, for later migrations. */
	public static final String VERSION_KEY = PREFIX + "version";
	public static final int VERSION = 1;

	/** Hop duration used to turn millisecond settings into whole analysis hops (256 samples at 48 kHz). */
	public static final double HOP_MILLIS = 256.0 * 1000.0 / 48000.0;

	// ---------------------------------------------------------------- input (shared)
	public static final Number INPUT_LEVEL_MIN = new Number(PREFIX + "input.level.min", Section.INPUT, "dBFS", -40, -60, -25, true);
	public static final Number INPUT_LEVEL_MAX = new Number(PREFIX + "input.level.max", Section.INPUT, "dBFS", -6, -20, -1, true);

	// ---------------------------------------------------------------- tuner
	public static final Number TUNER_IN_TUNE = new Number(PREFIX + "tuner.in-tune", Section.TUNER, "cents", 5, 1, 20, true);
	public static final Flag TUNER_HOLD = new Flag(PREFIX + "tuner.hold", Section.TUNER, true);

	// ---------------------------------------------------------------- practice: notes
	public static final Number NOTE_ACCEPT = new Number(PREFIX + "note.accept", Section.NOTES, "cents", 35, 10, 45, true);
	public static final Number NOTE_CONFIDENCE = new Number(PREFIX + "note.confidence", Section.NOTES, "%", 0.80, 0.50, 0.95, false, 100.0);
	public static final Number NOTE_STABILITY = new Number(PREFIX + "note.stability", Section.NOTES, "ms", 107, 50, 300, true);
	public static final Number NOTE_ATTACK = new Number(PREFIX + "note.attack", Section.NOTES, "dB", 4.5, 2.0, 10.0, false);
	public static final String LEGATO_STRICT = "strict";
	public static final Choice NOTE_LEGATO = new Choice(PREFIX + "note.legato", Section.NOTES, LEGATO_STRICT, LEGATO_STRICT);

	// ---------------------------------------------------------------- practice: chords
	public static final Number CHORD_EVIDENCE = new Number(PREFIX + "chord.evidence", Section.CHORDS, "%", 1.0 / 3.0, 0.20, 0.80, false, 100.0);
	public static final Number CHORD_WINDOW = new Number(PREFIX + "chord.window", Section.CHORDS, "ms", 600, 300, 1500, true);
	public static final String COMPLETENESS_ALL = "all-verifiable";
	public static final Choice CHORD_COMPLETENESS = new Choice(PREFIX + "chord.completeness", Section.CHORDS, COMPLETENESS_ALL, COMPLETENESS_ALL);
	public static final String FOREIGN_IGNORE = "ignore";
	public static final Choice CHORD_FOREIGN = new Choice(PREFIX + "chord.foreign", Section.CHORDS, FOREIGN_IGNORE, FOREIGN_IGNORE);

	// ---------------------------------------------------------------- passages
	public static final String PASSAGES_OFF = "0";
	public static final Choice PASSAGE_MEASURES = new Choice(PREFIX + "passage.measures", Section.PASSAGES, "2", PASSAGES_OFF, "1", "2", "4");
	public static final Choice PASSAGE_THRESHOLD = new Choice(PREFIX + "passage.threshold", Section.PASSAGES, "50", "25", "50", "75");
	public static final Number PASSAGE_RETRIES = new Number(PREFIX + "passage.retries", Section.PASSAGES, "", 2, 0, 5, true);

	// ---------------------------------------------------------------- engine
	public static final Choice ENGINE_PITCH = new Choice(PREFIX + "engine.pitch", Section.ENGINE,
			PitchEngineCatalog.AUTOMATIC, PitchEngineCatalog.AUTOMATIC);

	// ---------------------------------------------------------------- advanced (raw DSP)
	public static final Number LEVEL_CLIP_SAMPLE = new Number(PREFIX + "advanced.level.clip-sample", Section.ADVANCED, "%", 0.99, 0.90, 1.00, false, 100.0);
	public static final Number LEVEL_CLIP_SHARE = new Number(PREFIX + "advanced.level.clip-share", Section.ADVANCED, "%", 0.10, 0.01, 0.50, false, 100.0);
	public static final Number TUNER_CONFIDENCE = new Number(PREFIX + "advanced.tuner.confidence", Section.ADVANCED, "%", 0.80, 0.50, 0.95, false, 100.0);
	public static final Number ATTACK_MIN_LEVEL = new Number(PREFIX + "advanced.attack.min-level", Section.ADVANCED, "dBFS", -50, -70, -30, true);
	public static final Number CHORD_MIN_PARTIALS = new Number(PREFIX + "advanced.chord.min-partials", Section.ADVANCED, "", 2, 1, 4, true);
	public static final Number CHORD_CONFIRM = new Number(PREFIX + "advanced.chord.confirm", Section.ADVANCED, "ms", 43, 20, 150, true);

	/** In display order within each section. */
	public static final List<RecognitionParameter> ALL;

	static {
		List<RecognitionParameter> all = new ArrayList<RecognitionParameter>();
		Collections.addAll(all, INPUT_LEVEL_MIN, INPUT_LEVEL_MAX,
				TUNER_IN_TUNE, TUNER_HOLD,
				NOTE_ACCEPT, NOTE_CONFIDENCE, NOTE_STABILITY, NOTE_ATTACK, NOTE_LEGATO,
				CHORD_EVIDENCE, CHORD_WINDOW, CHORD_COMPLETENESS, CHORD_FOREIGN,
				PASSAGE_MEASURES, PASSAGE_THRESHOLD, PASSAGE_RETRIES,
				ENGINE_PITCH,
				LEVEL_CLIP_SAMPLE, LEVEL_CLIP_SHARE, TUNER_CONFIDENCE, ATTACK_MIN_LEVEL, CHORD_MIN_PARTIALS, CHORD_CONFIRM);
		for( int i = 0 ; i < all.size() ; i ++ ) {
			all.get(i).setIndex(i);
		}
		ALL = Collections.unmodifiableList(all);
	}

	private RecognitionParameters() {
	}

	/** The parameters of one section, in display order. */
	public static List<RecognitionParameter> of(RecognitionParameter.Section section) {
		List<RecognitionParameter> result = new ArrayList<RecognitionParameter>();
		for( RecognitionParameter parameter : ALL ) {
			if( parameter.getSection() == section ) {
				result.add(parameter);
			}
		}
		return result;
	}

	/** The parameter with this configuration key, or null. */
	public static RecognitionParameter byKey(String key) {
		for( RecognitionParameter parameter : ALL ) {
			if( parameter.getKey().equals(key) ) {
				return parameter;
			}
		}
		return null;
	}

	/** Whole analysis hops for a duration in milliseconds, at least 1 (107 ms → 20, 43 ms → 8). */
	public static int toHops(double millis) {
		return Math.max(1, (int) Math.round(millis / HOP_MILLIS));
	}
}
