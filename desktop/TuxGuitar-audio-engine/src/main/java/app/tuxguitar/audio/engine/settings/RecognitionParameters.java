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
	/** Noise gate and noise subtraction on the input (spec 002, FR-064); off by default until the benchmark justifies it (S-B1). */
	public static final Flag INPUT_NOISE_REDUCTION = new Flag(PREFIX + "input.noise-reduction", Section.INPUT, false);
	/** Offers a suggested gain after the level check (spec 002, FR-066); the suggestion is never applied without confirmation. */
	public static final Flag INPUT_GAIN_SUGGESTION = new Flag(PREFIX + "input.gain-suggestion", Section.INPUT, true);

	// ---------------------------------------------------------------- tuner
	public static final Number TUNER_IN_TUNE = new Number(PREFIX + "tuner.in-tune", Section.TUNER, "cents", 5, 1, 20, true);
	public static final Flag TUNER_HOLD = new Flag(PREFIX + "tuner.hold", Section.TUNER, true);

	// ---------------------------------------------------------------- practice: notes
	public static final Number NOTE_ACCEPT = new Number(PREFIX + "note.accept", Section.NOTES, "cents", 35, 10, 45, true);
	/** Extra sharp cents accepted per fret (fretted strings go sharp, more so higher up); 0 switches it off. */
	public static final Number NOTE_FRET_SHARP = new Number(PREFIX + "note.fret-sharp", Section.ADVANCED, "cents", 0.7, 0, 2, false);
	public static final Number NOTE_CONFIDENCE = new Number(PREFIX + "note.confidence", Section.NOTES, "%", 0.80, 0.50, 0.95, false, 100.0);
	public static final Number NOTE_STABILITY = new Number(PREFIX + "note.stability", Section.NOTES, "ms", 107, 50, 300, true);
	public static final Number NOTE_ATTACK = new Number(PREFIX + "note.attack", Section.NOTES, "dB", 4.5, 2.0, 10.0, false);
	public static final String LEGATO_STRICT = "strict";
	public static final Choice NOTE_LEGATO = new Choice(PREFIX + "note.legato", Section.NOTES, LEGATO_STRICT, LEGATO_STRICT);
	/** Evaluates the written playing techniques (spec 002, FR-020, FR-032); off makes every technique "not verifiable". */
	public static final Flag NOTE_TECHNIQUE = new Flag(PREFIX + "note.technique", Section.NOTES, true);
	public static final String TOLERANCE_STRICT = "strict";
	public static final String TOLERANCE_NORMAL = "normal";
	public static final String TOLERANCE_LENIENT = "lenient";
	/**
	 * How far from its written time a note may be and still count as on time in the tempo-following modes (spec 012): the half-width of the on-time window in milliseconds
	 * <b>at 120 beats a minute</b>; the real window grows and shrinks with the tempo. It replaced the three presets of spec 002 FR-052 (the values behind them come from the
	 * tempo spike); the default is a third more lenient than the old Normal (75 ms).
	 */
	public static final Number TEMPO_WINDOW = new Number(PREFIX + "tempo.window", Section.NOTES, "ms", 100, 40, 200, true);
	/**
	 * The key under which versions before spec 012 saved the choice of the preset ({@link #TOLERANCE_STRICT}, {@link #TOLERANCE_NORMAL}, {@link #TOLERANCE_LENIENT}). It is no
	 * longer a parameter (it has no control), only read once to carry an older choice over to {@link #TEMPO_WINDOW}, and never written again.
	 */
	public static final String LEGACY_TEMPO_TOLERANCE_KEY = PREFIX + "tempo.tolerance";

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
	/**
	 * The time to play a note in Follow (spec 016, FR-011): from the moment a note turns red, how long it may still be played, in milliseconds. It replaces
	 * {@link #TEMPO_WINDOW} in Follow only (Arcade keeps that one). The learner's choice, like the window: never a parameter of a device profile. Shown with the passages (spec 016 T029):
	 * the section of the notes has no room left in the Configuration window.
	 */
	public static final Number FOLLOW_PLAY_TIME = new Number(PREFIX + "follow.play-time", Section.PASSAGES, "ms", 600, 100, 1200, true);

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
		Collections.addAll(all, INPUT_LEVEL_MIN, INPUT_LEVEL_MAX, INPUT_NOISE_REDUCTION, INPUT_GAIN_SUGGESTION,
				TUNER_IN_TUNE, TUNER_HOLD,
				NOTE_ACCEPT, NOTE_FRET_SHARP, NOTE_CONFIDENCE, NOTE_STABILITY, NOTE_ATTACK, NOTE_LEGATO, NOTE_TECHNIQUE, TEMPO_WINDOW,
				CHORD_EVIDENCE, CHORD_WINDOW, CHORD_COMPLETENESS, CHORD_FOREIGN,
				PASSAGE_MEASURES, PASSAGE_THRESHOLD, PASSAGE_RETRIES, FOLLOW_PLAY_TIME,
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
