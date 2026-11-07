package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.ArticulationDetector;

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
	/** The rule of spec 023: every string verifiable in the spectrum is checked on its own. */
	public static final String COMPLETENESS_ALL = "all-verifiable";
	/** The rule of spec 024: the note names summed over the strum, matched against the expected chord (contracts/chord-profile-rule.md). */
	public static final String COMPLETENESS_PROFILE = "profile";
	public static final Choice CHORD_COMPLETENESS = new Choice(PREFIX + "chord.completeness", Section.CHORDS, COMPLETENESS_PROFILE, COMPLETENESS_ALL, COMPLETENESS_PROFILE);
	public static final String FOREIGN_IGNORE = "ignore";
	public static final Choice CHORD_FOREIGN = new Choice(PREFIX + "chord.foreign", Section.CHORDS, FOREIGN_IGNORE, FOREIGN_IGNORE);
	/** The cosine between the summed profile and the expected names from which a chord is complete (spec 024 T007: 0.79, Normal and Strict). */
	public static final Number CHORD_PROFILE_MATCH = new Number(PREFIX + "chord.profile.match", Section.CHORDS, "%", 0.79, 0.50, 0.99, false, 100.0);
	/** A required name summed below this share of the strongest required name is weak; two weak names are never complete. */
	public static final Number CHORD_PROFILE_NAMES = new Number(PREFIX + "chord.profile.names", Section.CHORDS, "%", 0.40, 0.05, 0.60, false, 100.0);
	/** Whether a chord may be accepted on its attack (the attack evidence), guarded by the weak names under the profile rule (research R6). */
	public static final Flag CHORD_ATTACK = new Flag(PREFIX + "chord.attack", Section.CHORDS, true);

	// ---------------------------------------------------------------- practice: chord details
	/** The highest pitch summed into the profile. */
	public static final Number CHORD_PROFILE_TOP = new Number(PREFIX + "chord.detail.top", Section.CHORD_DETAILS, "MIDI", 84, 60, 112, true);
	/** The time of profile summed from the strum before a chord can be judged complete. */
	public static final Number CHORD_PROFILE_SETTLE = new Number(PREFIX + "chord.detail.settle", Section.CHORD_DETAILS, "ms", 200, 0, 500, true);

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
	/** Its list shows Automatic only; a hand-made {@code native} or {@code fastyin} is kept, and the menu lists the engines itself (spec 023). */
	public static final Choice ENGINE_PITCH = new Choice(PREFIX + "engine.pitch", Section.ENGINE,
			PitchEngineCatalog.AUTOMATIC, java.util.Arrays.asList(PitchEngineCatalog.AUTOMATIC, PitchEngineCatalog.NATIVE, PitchEngineCatalog.FASTYIN),
			PitchEngineCatalog.AUTOMATIC);

	// ---------------------------------------------------------------- advanced (raw DSP)
	public static final Number LEVEL_CLIP_SAMPLE = new Number(PREFIX + "advanced.level.clip-sample", Section.ADVANCED, "%", 0.99, 0.90, 1.00, false, 100.0);
	public static final Number LEVEL_CLIP_SHARE = new Number(PREFIX + "advanced.level.clip-share", Section.ADVANCED, "%", 0.10, 0.01, 0.50, false, 100.0);
	public static final Number TUNER_CONFIDENCE = new Number(PREFIX + "advanced.tuner.confidence", Section.ADVANCED, "%", 0.80, 0.50, 0.95, false, 100.0);
	public static final Number ATTACK_MIN_LEVEL = new Number(PREFIX + "advanced.attack.min-level", Section.ADVANCED, "dBFS", -50, -70, -30, true);
	public static final Number CHORD_MIN_PARTIALS = new Number(PREFIX + "advanced.chord.min-partials", Section.ADVANCED, "", 2, 1, 4, true);
	public static final Number CHORD_CONFIRM = new Number(PREFIX + "advanced.chord.confirm", Section.ADVANCED, "ms", 43, 20, 150, true);

	// ---------------------------------------------------------------- engine stages (spec 026): onsets
	/** The spectral flux above this multiple of its recent median (plus the offset) is a transient. */
	public static final Number ONSET_FLUX_FACTOR = new Number(PREFIX + "onset.flux-factor", Section.ONSET, "×", ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, 0.5, 6, false);
	public static final Number ONSET_FLUX_OFFSET = new Number(PREFIX + "onset.flux-offset", Section.ONSET, "", ArticulationDetector.DEFAULT_FLUX_OFFSET, 1, 20, false);
	/** Hops after an onset during which no other is reported. */
	public static final Number ONSET_REFRACTORY = new Number(PREFIX + "onset.refractory", Section.ONSET, "hops", ArticulationDetector.DEFAULT_REFRACTORY_HOPS, 3, 48, true);
	/** The level before the attack, over these hops before it (at most the 19 kept). */
	public static final Number ONSET_PRE_FIRST = new Number(PREFIX + "onset.pre-first", Section.ONSET, "hops", 19, 2, 19, true);
	public static final Number ONSET_PRE_LAST = new Number(PREFIX + "onset.pre-last", Section.ONSET, "hops", 2, 1, 19, true);
	/** The level after the attack, over these hops after it; the last one is when the onset is reported (at most the 13 buffered). */
	public static final Number ONSET_POST_FIRST = new Number(PREFIX + "onset.post-first", Section.ONSET, "hops", 4, 1, 13, true);
	public static final Number ONSET_POST_LAST = new Number(PREFIX + "onset.post-last", Section.ONSET, "hops", ArticulationDetector.DECISION_DELAY_HOPS, 4, 13, true);

	// ---------------------------------------------------------------- engine stages (spec 026): the fast-run onsets
	public static final Number ONSET_FAST_FLUX_FACTOR = new Number(PREFIX + "onset-fast.flux-factor", Section.ONSET_FAST, "×", ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, 0.5, 6, false);
	public static final Number ONSET_FAST_FLUX_OFFSET = new Number(PREFIX + "onset-fast.flux-offset", Section.ONSET_FAST, "", ArticulationDetector.DEFAULT_FLUX_OFFSET, 1, 20, false);
	public static final Number ONSET_FAST_REFRACTORY = new Number(PREFIX + "onset-fast.refractory", Section.ONSET_FAST, "hops", ArticulationDetector.FAST_REFRACTORY_HOPS, 2, 24, true);
	public static final Number ONSET_FAST_PRE_FIRST = new Number(PREFIX + "onset-fast.pre-first", Section.ONSET_FAST, "hops", 8, 2, 19, true);
	public static final Number ONSET_FAST_PRE_LAST = new Number(PREFIX + "onset-fast.pre-last", Section.ONSET_FAST, "hops", 2, 1, 8, true);
	public static final Number ONSET_FAST_POST_FIRST = new Number(PREFIX + "onset-fast.post-first", Section.ONSET_FAST, "hops", 3, 1, 8, true);
	public static final Number ONSET_FAST_POST_LAST = new Number(PREFIX + "onset-fast.post-last", Section.ONSET_FAST, "hops", ArticulationDetector.FAST_DECISION_DELAY_HOPS, 3, 8, true);

	// ---------------------------------------------------------------- engine stages (spec 026): pitch
	/** YIN's threshold, the same value for the Java engine (FastYin) and the native one. */
	public static final Number PITCH_YIN_THRESHOLD = new Number(PREFIX + "pitch.yin-threshold", Section.PITCH, "%", 0.20, 0.05, 0.5, false, 100.0);
	/** The expected-pitch evidence: how far around the expected period it searches, and over how many periods. */
	public static final Number PITCH_TARGET_SEARCH = new Number(PREFIX + "pitch.target-search", Section.PITCH, "cents", 20, 5, 50, true);
	public static final Number PITCH_TARGET_PERIODS = new Number(PREFIX + "pitch.target-periods", Section.PITCH, "", 4, 2, 8, true);
	/** The largest sharp deviation of a note ever accepted, whatever its fret. */
	public static final Number PITCH_MAX_SHARP = new Number(PREFIX + "pitch.max-sharp", Section.PITCH, "cents", 49, 20, 49, true);
	/** The range of the neighbours added to the attack candidates. */
	public static final Number PITCH_ATTACK_CANDIDATES_LOW = new Number(PREFIX + "pitch.attack-candidates-low", Section.PITCH, "MIDI", 28, 24, 40, true);
	public static final Number PITCH_ATTACK_CANDIDATES_HIGH = new Number(PREFIX + "pitch.attack-candidates-high", Section.PITCH, "MIDI", 100, 80, 112, true);

	// ---------------------------------------------------------------- engine stages (spec 026): polyphonic spectrum (FFT size and bands fixed)
	/** The range of the pitch candidates. */
	public static final Number POLYPHONIC_CANDIDATE_LOW = new Number(PREFIX + "polyphonic.candidate-low", Section.POLYPHONIC_1, "MIDI", 33, 24, 60, true);
	public static final Number POLYPHONIC_CANDIDATE_HIGH = new Number(PREFIX + "polyphonic.candidate-high", Section.POLYPHONIC_1, "MIDI", 88, 64, 112, true);
	/** The partials summed per candidate, below a highest frequency, within a tolerance. */
	public static final Number POLYPHONIC_HARMONICS = new Number(PREFIX + "polyphonic.harmonics", Section.POLYPHONIC_1, "", 12, 4, 24, true);
	public static final Number POLYPHONIC_MAX_HARMONIC = new Number(PREFIX + "polyphonic.max-harmonic", Section.POLYPHONIC_1, "Hz", 5000, 1000, 12000, true);
	public static final Number POLYPHONIC_TOLERANCE = new Number(PREFIX + "polyphonic.tolerance", Section.POLYPHONIC_1, "cents", 25, 5, 50, true);
	public static final Number POLYPHONIC_CANDIDATES = new Number(PREFIX + "polyphonic.candidates", Section.POLYPHONIC_1, "", 6, 1, 12, true);
	/** When the candidate search stops: below a share of the strongest, or under a floor. */
	public static final Number POLYPHONIC_MIN_RELATIVE = new Number(PREFIX + "polyphonic.min-relative", Section.POLYPHONIC_2, "%", 0.10, 0.01, 0.5, false, 100.0);
	public static final Number POLYPHONIC_FLOOR = new Number(PREFIX + "polyphonic.floor", Section.POLYPHONIC_2, "dB", -70.0, -100.0, -40.0, false);
	/** How strong a subharmonic's own partials must be to replace a pick. */
	public static final Number POLYPHONIC_SUBHARMONIC = new Number(PREFIX + "polyphonic.subharmonic", Section.POLYPHONIC_2, "%", 0.30, 0.05, 0.9, false, 100.0);
	/** The local floor removed from the spectrum: its half width and the multiple of it a partial must exceed. */
	public static final Number POLYPHONIC_FLOOR_WIDTH = new Number(PREFIX + "polyphonic.floor-width", Section.POLYPHONIC_2, "Hz", 25, 5, 100, true);
	public static final Number POLYPHONIC_FLOOR_FACTOR = new Number(PREFIX + "polyphonic.floor-factor", Section.POLYPHONIC_2, "×", 2.0, 1.0, 6.0, false);

	// ---------------------------------------------------------------- engine stages (spec 026): attack evidence (FFT size fixed)
	/** The growth of the spectrum over this many hops is what was just struck. */
	public static final Number ATTACK_LAG = new Number(PREFIX + "attack.lag", Section.ATTACK_1, "hops", 22, 4, 64, true);
	public static final Number ATTACK_MAX_FREQUENCY = new Number(PREFIX + "attack.max-frequency", Section.ATTACK_1, "Hz", 5000, 1000, 12000, true);
	/** The harmonic templates: partials, their weight h^-beta and their width in bins. */
	public static final Number ATTACK_PARTIALS = new Number(PREFIX + "attack.partials", Section.ATTACK_1, "", 14, 4, 24, true);
	public static final Number ATTACK_BETA = new Number(PREFIX + "attack.beta", Section.ATTACK_1, "", 0.3, 0.0, 2.0, false);
	public static final Number ATTACK_SIGMA = new Number(PREFIX + "attack.sigma", Section.ATTACK_1, "", 1.2, 0.3, 4.0, false);
	/** The offset of the new partials, read by the practice: partials measured, search around each, smallest peak kept. */
	public static final Number ATTACK_EVIDENCE_PARTIALS = new Number(PREFIX + "attack.evidence-partials", Section.ATTACK_2, "", 12, 4, 24, true);
	public static final Number ATTACK_EVIDENCE_SEARCH = new Number(PREFIX + "attack.evidence-search", Section.ATTACK_2, "cents", 60, 10, 100, true);
	public static final Number ATTACK_EVIDENCE_PEAK = new Number(PREFIX + "attack.evidence-peak", Section.ATTACK_2, "%", 0.03, 0.01, 0.2, false, 100.0);

	// ---------------------------------------------------------------- engine stages (spec 026): chord evidence and strum meter
	/** A partial of an expected chord is present from this share of the strongest; the chord a whole tone away must explain it this many times better. */
	public static final Number CHORD_EVIDENCE_PARTIAL_PRESENCE = new Number(PREFIX + "chord-evidence.partial-presence", Section.CHORD_EVIDENCE, "%", 0.05, 0.01, 0.3, false, 100.0);
	public static final Number CHORD_EVIDENCE_NEIGHBOUR_MARGIN = new Number(PREFIX + "chord-evidence.neighbour-margin", Section.CHORD_EVIDENCE, "×", 1.5, 1.0, 4.0, false);
	/** The strum meter: its window, the quiet hops a strum rises above, by how much, and the clipped share that means clipping. */
	public static final Number CHORD_EVIDENCE_STRUM_WINDOW = new Number(PREFIX + "chord-evidence.strum-window", Section.CHORD_EVIDENCE, "ms", 300, 100, 1000, true);
	public static final Number CHORD_EVIDENCE_STRUM_QUIET = new Number(PREFIX + "chord-evidence.strum-quiet", Section.CHORD_EVIDENCE, "hops", 60, 10, 200, true);
	public static final Number CHORD_EVIDENCE_STRUM_RISE = new Number(PREFIX + "chord-evidence.strum-rise", Section.CHORD_EVIDENCE, "dB", 12.0, 3.0, 30.0, false);
	public static final Number CHORD_EVIDENCE_STRUM_CLIP_SHARE = new Number(PREFIX + "chord-evidence.strum-clip-share", Section.CHORD_EVIDENCE, "‰", 0.005, 0.001, 0.05, false, 1000.0);

	// ---------------------------------------------------------------- engine stages (spec 026): front end, noise gate, gain suggestion
	/** The rumble filter's corner, and how the background-noise estimate rises, falls and becomes trusted. */
	public static final Number FRONT_END_HIGH_PASS = new Number(PREFIX + "front-end.high-pass", Section.FRONT_END, "Hz", 40.0, 10.0, 120.0, false);
	public static final Number FRONT_END_NOISE_RISE = new Number(PREFIX + "front-end.noise-rise", Section.FRONT_END, "dB/s", 0.5, 0.1, 5.0, false);
	public static final Number FRONT_END_NOISE_FALL = new Number(PREFIX + "front-end.noise-fall", Section.FRONT_END, "", 0.2, 0.1, 1.0, false);
	public static final Number FRONT_END_NOISE_TRUSTED = new Number(PREFIX + "front-end.noise-trusted", Section.FRONT_END, "hops", 188, 20, 1000, true);
	/** The gate's opening and closing margins over the noise (closing ≤ opening), its reduction and hold; the spectral subtraction's values. */
	public static final Number NOISE_GATE_OPEN = new Number(PREFIX + "noise-gate.open", Section.NOISE_GATE, "dB", 9.0, 6.0, 30.0, false);
	public static final Number NOISE_GATE_CLOSE = new Number(PREFIX + "noise-gate.close", Section.NOISE_GATE, "dB", 5.0, 0.0, 8.0, false);
	public static final Number NOISE_GATE_REDUCTION = new Number(PREFIX + "noise-gate.reduction", Section.NOISE_GATE, "dB", 20.0, 0.0, 60.0, false);
	public static final Number NOISE_GATE_HOLD = new Number(PREFIX + "noise-gate.hold", Section.NOISE_GATE, "hops", 19, 1, 100, true);
	public static final Number NOISE_GATE_SUBTRACTION = new Number(PREFIX + "noise-gate.subtraction", Section.NOISE_GATE, "×", 1.5, 0.5, 4.0, false);
	public static final Number NOISE_GATE_SUBTRACTION_FLOOR = new Number(PREFIX + "noise-gate.subtraction-floor", Section.NOISE_GATE, "%", 0.05, 0.0, 0.3, false, 100.0);
	public static final Number NOISE_GATE_SUBTRACTION_LEARN = new Number(PREFIX + "noise-gate.subtraction-learn", Section.NOISE_GATE, "%", 0.1, 0.01, 1.0, false, 100.0);
	/** The gain suggestion of the level check: the playing level aimed at, the highest peak, the no-change margin, the playing-over-noise distance. */
	public static final Number GAIN_TARGET = new Number(PREFIX + "gain.target", Section.GAIN, "dBFS", -22.0, -40.0, -10.0, false);
	public static final Number GAIN_MAX_PEAK = new Number(PREFIX + "gain.max-peak", Section.GAIN, "dBFS", -3.0, -12.0, -1.0, false);
	public static final Number GAIN_NO_CHANGE = new Number(PREFIX + "gain.no-change", Section.GAIN, "dB", 1.5, 0.5, 6.0, false);
	public static final Number GAIN_MIN_SNR = new Number(PREFIX + "gain.min-snr", Section.GAIN, "dB", 20.0, 6.0, 40.0, false);

	/** In display order within each section. */
	public static final List<RecognitionParameter> ALL;

	static {
		List<RecognitionParameter> all = new ArrayList<RecognitionParameter>();
		Collections.addAll(all, INPUT_LEVEL_MIN, INPUT_LEVEL_MAX, INPUT_NOISE_REDUCTION, INPUT_GAIN_SUGGESTION,
				TUNER_IN_TUNE, TUNER_HOLD,
				NOTE_ACCEPT, NOTE_FRET_SHARP, NOTE_CONFIDENCE, NOTE_STABILITY, NOTE_ATTACK, NOTE_LEGATO, NOTE_TECHNIQUE, TEMPO_WINDOW,
				CHORD_EVIDENCE, CHORD_WINDOW, CHORD_COMPLETENESS, CHORD_FOREIGN, CHORD_PROFILE_MATCH, CHORD_PROFILE_NAMES, CHORD_ATTACK,
				CHORD_PROFILE_TOP, CHORD_PROFILE_SETTLE,
				PASSAGE_MEASURES, PASSAGE_THRESHOLD, PASSAGE_RETRIES, FOLLOW_PLAY_TIME,
				ENGINE_PITCH,
				LEVEL_CLIP_SAMPLE, LEVEL_CLIP_SHARE, TUNER_CONFIDENCE, ATTACK_MIN_LEVEL, CHORD_MIN_PARTIALS, CHORD_CONFIRM,
				ONSET_FLUX_FACTOR, ONSET_FLUX_OFFSET, ONSET_REFRACTORY, ONSET_PRE_FIRST, ONSET_PRE_LAST, ONSET_POST_FIRST, ONSET_POST_LAST,
				ONSET_FAST_FLUX_FACTOR, ONSET_FAST_FLUX_OFFSET, ONSET_FAST_REFRACTORY, ONSET_FAST_PRE_FIRST, ONSET_FAST_PRE_LAST, ONSET_FAST_POST_FIRST,
				ONSET_FAST_POST_LAST,
				PITCH_YIN_THRESHOLD, PITCH_TARGET_SEARCH, PITCH_TARGET_PERIODS, PITCH_MAX_SHARP, PITCH_ATTACK_CANDIDATES_LOW, PITCH_ATTACK_CANDIDATES_HIGH,
				POLYPHONIC_CANDIDATE_LOW, POLYPHONIC_CANDIDATE_HIGH, POLYPHONIC_HARMONICS, POLYPHONIC_MAX_HARMONIC, POLYPHONIC_TOLERANCE, POLYPHONIC_CANDIDATES,
				POLYPHONIC_MIN_RELATIVE, POLYPHONIC_FLOOR, POLYPHONIC_SUBHARMONIC, POLYPHONIC_FLOOR_WIDTH, POLYPHONIC_FLOOR_FACTOR,
				ATTACK_LAG, ATTACK_MAX_FREQUENCY, ATTACK_PARTIALS, ATTACK_BETA, ATTACK_SIGMA, ATTACK_EVIDENCE_PARTIALS, ATTACK_EVIDENCE_SEARCH, ATTACK_EVIDENCE_PEAK,
				CHORD_EVIDENCE_PARTIAL_PRESENCE, CHORD_EVIDENCE_NEIGHBOUR_MARGIN, CHORD_EVIDENCE_STRUM_WINDOW, CHORD_EVIDENCE_STRUM_QUIET, CHORD_EVIDENCE_STRUM_RISE,
				CHORD_EVIDENCE_STRUM_CLIP_SHARE, FRONT_END_HIGH_PASS, FRONT_END_NOISE_RISE, FRONT_END_NOISE_FALL, FRONT_END_NOISE_TRUSTED, NOISE_GATE_OPEN,
				NOISE_GATE_CLOSE, NOISE_GATE_REDUCTION, NOISE_GATE_HOLD, NOISE_GATE_SUBTRACTION, NOISE_GATE_SUBTRACTION_FLOOR, NOISE_GATE_SUBTRACTION_LEARN,
				GAIN_TARGET, GAIN_MAX_PEAK, GAIN_NO_CHANGE, GAIN_MIN_SNR);
		for( int i = 0 ; i < all.size() ; i ++ ) {
			all.get(i).setIndex(i);
		}
		ALL = Collections.unmodifiableList(all);
	}

	/** The pairs that must stay ordered (spec 026); each engine family adds its own. */
	public static final List<OrderedPair> ORDERED_PAIRS = Collections.unmodifiableList(java.util.Arrays.asList(
			new OrderedPair(ONSET_PRE_LAST, ONSET_PRE_FIRST), new OrderedPair(ONSET_POST_FIRST, ONSET_POST_LAST),
			new OrderedPair(ONSET_FAST_PRE_LAST, ONSET_FAST_PRE_FIRST), new OrderedPair(ONSET_FAST_POST_FIRST, ONSET_FAST_POST_LAST),
			new OrderedPair(PITCH_ATTACK_CANDIDATES_LOW, PITCH_ATTACK_CANDIDATES_HIGH),
			new OrderedPair(POLYPHONIC_CANDIDATE_LOW, POLYPHONIC_CANDIDATE_HIGH), new OrderedPair(NOISE_GATE_CLOSE, NOISE_GATE_OPEN)));

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
