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

	// ---------------------------------------------------------------- practice session (spec 026): the acceptance of struck notes
	/** The decision: share explained, chord balance, strongest pitch, hops in a row, neighbours, attack strength, rest after a note. */
	public static final Number ACCEPTANCE_MIN_SHARE = new Number(PREFIX + "acceptance.min-share", Section.ACCEPTANCE_1, "%", 0.30, 0.05, 0.9, false, 100.0);
	public static final Number ACCEPTANCE_MIN_BALANCE = new Number(PREFIX + "acceptance.min-balance", Section.ACCEPTANCE_1, "%", 0.10, 0.0, 0.5, false, 100.0);
	public static final Number ACCEPTANCE_MIN_PEAK = new Number(PREFIX + "acceptance.min-peak", Section.ACCEPTANCE_1, "%", 0.7, 0.1, 1.0, false, 100.0);
	public static final Number ACCEPTANCE_HOPS = new Number(PREFIX + "acceptance.hops", Section.ACCEPTANCE_1, "hops", 6, 1, 30, true);
	public static final Number ACCEPTANCE_NEIGHBOUR = new Number(PREFIX + "acceptance.neighbour", Section.ACCEPTANCE_1, "×", 0.0, 0.0, 4.0, false);
	public static final Number ACCEPTANCE_ATTACK_STRENGTH = new Number(PREFIX + "acceptance.attack-strength", Section.ACCEPTANCE_1, "", 0.0, 0.0, 5.0, false);
	public static final Number ACCEPTANCE_REARM = new Number(PREFIX + "acceptance.rearm", Section.ACCEPTANCE_1, "ms", 220, 50, 1000, true);
	/** The attack: tuning margin, forgiveness, thump guard, quietest note, quiet-level window, sudden attack from silence, smallest onset. */
	public static final Number ACCEPTANCE_OFFSET_MARGIN = new Number(PREFIX + "acceptance.offset-margin", Section.ACCEPTANCE_2, "cents", 15.0, 0.0, 50.0, false);
	public static final Number ACCEPTANCE_DECAY = new Number(PREFIX + "acceptance.decay", Section.ACCEPTANCE_2, "hops", 1, 0, 5, true);
	public static final Number ACCEPTANCE_GUARD = new Number(PREFIX + "acceptance.guard", Section.ACCEPTANCE_2, "ms", 60, 0, 200, true);
	public static final Number ACCEPTANCE_MIN_TOTAL = new Number(PREFIX + "acceptance.min-total", Section.ACCEPTANCE_2, "‰", 0.01, 0.001, 0.1, false, 1000.0);
	public static final Number ACCEPTANCE_FLOOR_HOPS = new Number(PREFIX + "acceptance.floor-hops", Section.ACCEPTANCE_2, "hops", 10, 1, 50, true);
	public static final Number ACCEPTANCE_SILENCE = new Number(PREFIX + "acceptance.silence", Section.ACCEPTANCE_2, "%", 0.6, 0.1, 1.0, false, 100.0);
	public static final Number ACCEPTANCE_MIN_ONSET = new Number(PREFIX + "acceptance.min-onset", Section.ACCEPTANCE_2, "‰", 0.002, 0.001, 0.05, false, 1000.0);
	/** Where the attack begins (times the quiet level) and ends (share of its peak). */
	public static final Number ACCEPTANCE_ONSET_FLOOR = new Number(PREFIX + "acceptance.onset-floor", Section.ACCEPTANCE_3, "×", 3.0, 1.0, 10.0, false);
	public static final Number ACCEPTANCE_RELEASE = new Number(PREFIX + "acceptance.release", Section.ACCEPTANCE_3, "%", 0.25, 0.05, 0.9, false, 100.0);

	/** The Wait session (spec 026, T021): how long a verdict shows, how long a close neighbour read by the pitch estimate holds the acceptance back. */
	public static final Number WAIT_VERDICT_HOLD = new Number(PREFIX + "wait.verdict-hold", Section.WAIT_1, "ms", 1000, 200, 3000, true);
	public static final Number WAIT_NEIGHBOUR_VETO = new Number(PREFIX + "wait.neighbour-veto", Section.WAIT_1, "ms", 120, 0, 500, true);
	public static final Number WAIT_NEIGHBOUR_CONFIDENCE = new Number(PREFIX + "wait.neighbour-confidence", Section.WAIT_1, "%", 0.8, 0.5, 1.0, false, 100.0);
	/** How long a note's techniques are observed, and a bend's (its written length, within these bounds), and the margin around a bend's path. */
	public static final Number WAIT_TECHNIQUE_WINDOW = new Number(PREFIX + "wait.technique-window", Section.WAIT_1, "ms", 1500, 500, 4000, true);
	public static final Number WAIT_BEND_WINDOW_MIN = new Number(PREFIX + "wait.bend-window-min", Section.WAIT_1, "ms", 1000, 250, 2500, true);
	public static final Number WAIT_BEND_WINDOW_MAX = new Number(PREFIX + "wait.bend-window-max", Section.WAIT_1, "ms", 2500, 1000, 4000, true);
	public static final Number WAIT_BEND_MARGIN = new Number(PREFIX + "wait.bend-margin", Section.WAIT_1, "semitones", 0.5, 0.1, 2.0, false);
	/** How far before the acceptance a note's segment starts; the speed limit's window after an acceptance and its lead before it. */
	public static final Number WAIT_TECHNIQUE_LEAD = new Number(PREFIX + "wait.technique-lead", Section.WAIT_2, "ms", 150, 0, 500, true);
	public static final Number WAIT_SPEED_WINDOW = new Number(PREFIX + "wait.speed-window", Section.WAIT_2, "ms", 200, 50, 1000, true);
	public static final Number WAIT_SPEED_LEAD = new Number(PREFIX + "wait.speed-lead", Section.WAIT_2, "ms", 20, 0, 100, true);
	/** The early stability (when it is switched on): the evidence margin and the shorter window; the level below which fast recognition is weak. */
	public static final Number WAIT_EARLY_MARGIN = new Number(PREFIX + "wait.early-margin", Section.WAIT_2, "", 0.5, 0.1, 2.0, false);
	public static final Number WAIT_EARLY_HOPS = new Number(PREFIX + "wait.early-hops", Section.WAIT_2, "hops", 12, 4, 40, true);
	public static final Number WAIT_WEAK_FAST = new Number(PREFIX + "wait.weak-fast", Section.WAIT_2, "dBFS", -30.0, -50.0, -10.0, false);

	/** The Follow session (spec 026, T022): how attacks are resolved, how the attack evidence is dated, how techniques and bends are observed. */
	public static final Number FOLLOW_EVIDENCE_HORIZON = new Number(PREFIX + "follow.evidence-horizon", Section.FOLLOW_1, "ms", 400, 100, 1500, true);
	public static final Number FOLLOW_SILENCE = new Number(PREFIX + "follow.silence", Section.FOLLOW_1, "ms", 40, 10, 200, true);
	public static final Number FOLLOW_SHORT_NOTE = new Number(PREFIX + "follow.short-note", Section.FOLLOW_1, "ms", 40, 10, 200, true);
	public static final Number FOLLOW_SAME_SOUND = new Number(PREFIX + "follow.same-sound", Section.FOLLOW_1, "ms", 100, 20, 400, true);
	public static final Number FOLLOW_RINGING = new Number(PREFIX + "follow.ringing", Section.FOLLOW_1, "ms", 1500, 300, 4000, true);
	public static final Number FOLLOW_SCAN_BACK = new Number(PREFIX + "follow.scan-back", Section.FOLLOW_1, "", 8, 0, 32, true);
	public static final Number FOLLOW_ONSET_MEMORY = new Number(PREFIX + "follow.onset-memory", Section.FOLLOW_1, "ms", 3000, 20, 10000, true);
	public static final Number FOLLOW_REFINE_BEFORE = new Number(PREFIX + "follow.refine-before", Section.FOLLOW_2, "ms", 150, 0, 500, true);
	public static final Number FOLLOW_REFINE_AFTER = new Number(PREFIX + "follow.refine-after", Section.FOLLOW_2, "ms", 30, 0, 200, true);
	public static final Number FOLLOW_AFTER_CLOSE = new Number(PREFIX + "follow.after-close", Section.FOLLOW_2, "ms", 100, 0, 400, true);
	public static final Number FOLLOW_TAIL = new Number(PREFIX + "follow.tail", Section.FOLLOW_2, "ms", 100, 0, 400, true);
	public static final Number FOLLOW_HISTORY = new Number(PREFIX + "follow.history", Section.FOLLOW_2, "ms", 1200, 100, 4000, true);
	public static final Number FOLLOW_TECHNIQUE_WINDOW = new Number(PREFIX + "follow.technique-window", Section.FOLLOW_3, "ms", 1500, 500, 4000, true);
	public static final Number FOLLOW_BEND_WINDOW_MIN = new Number(PREFIX + "follow.bend-window-min", Section.FOLLOW_3, "ms", 1000, 250, 2500, true);
	public static final Number FOLLOW_BEND_WINDOW_MAX = new Number(PREFIX + "follow.bend-window-max", Section.FOLLOW_3, "ms", 2500, 1000, 4000, true);
	public static final Number FOLLOW_BEND_MARGIN = new Number(PREFIX + "follow.bend-margin", Section.FOLLOW_3, "semitones", 0.5, 0.1, 2.0, false);
	public static final Number FOLLOW_EVIDENCE_LEAD = new Number(PREFIX + "follow.evidence-lead", Section.FOLLOW_3, "ms", 400, 100, 1500, true);

	/** The player's clock (spec 026, T023, clarification 3): how the stepping position of the sequencer is made continuous; the latency bound. */
	public static final Number CLOCK_FIT_WINDOW = new Number(PREFIX + "clock.fit-window", Section.CLOCK, "ms", 600, 150, 2000, true);
	public static final Number CLOCK_MIN_POINTS = new Number(PREFIX + "clock.min-points", Section.CLOCK, "", 8, 3, 64, true);
	public static final Number CLOCK_MIN_SPAN = new Number(PREFIX + "clock.min-span", Section.CLOCK, "ms", 150, 30, 600, true);
	public static final Number CLOCK_STALL = new Number(PREFIX + "clock.stall", Section.CLOCK, "ms", 60, 20, 300, true);
	public static final Number CLOCK_EXTRAPOLATION = new Number(PREFIX + "clock.extrapolation", Section.CLOCK, "ms", 30, 0, 200, true);
	public static final Number CLOCK_SEEK_MARGIN = new Number(PREFIX + "clock.seek-margin", Section.CLOCK, "ticks", 960, 240, 3840, true);
	public static final Number CLOCK_LATENCY_LIMIT = new Number(PREFIX + "clock.latency-limit", Section.CLOCK, "ms", 1000, 200, 3000, true);

	/** The timing windows (spec 026, T024): the reference beat of the tolerance width, Follow's advance and gap, the unstable route's spread. */
	public static final Number TIMING_REFERENCE_BEAT = new Number(PREFIX + "timing.reference-beat", Section.TIMING, "ms", 500, 250, 1000, true);
	public static final Number TIMING_ADVANCE = new Number(PREFIX + "timing.advance", Section.TIMING, "ms", 50, 0, 200, true);
	public static final Number TIMING_GAP = new Number(PREFIX + "timing.gap", Section.TIMING, "ms", 20, 0, 100, true);
	public static final Number TIMING_CLOCK_SPREAD = new Number(PREFIX + "timing.clock-spread", Section.TIMING, "ms", 80, 20, 300, true);

	/**
	 * The leniency of Follow and Arcade (spec 026, T025): the value each relaxed setting takes at each position (one way: the more lenient of it and the
	 * learner's), with the bounds of the setting it relaxes, and Arcade's factors on the width of the timing window.
	 */
	public static final Number FOLLOW_LENIENCY_PERMISSIVE_ACCEPT = new Number(PREFIX + "follow-leniency.permissive.accept", Section.LENIENCY_1, "cents", 35, 10, 45, true);
	public static final Number FOLLOW_LENIENCY_PERMISSIVE_CONFIDENCE = new Number(PREFIX + "follow-leniency.permissive.confidence", Section.LENIENCY_1, "%", 0.6, 0.5, 0.95, false, 100.0);
	public static final Number FOLLOW_LENIENCY_PERMISSIVE_STABILITY = new Number(PREFIX + "follow-leniency.permissive.stability", Section.LENIENCY_1, "ms", 55, 50, 300, true);
	public static final Number FOLLOW_LENIENCY_PERMISSIVE_CHORD_EVIDENCE = new Number(PREFIX + "follow-leniency.permissive.chord-evidence", Section.LENIENCY_1, "%", 0.22, 0.2, 0.8, false, 100.0);
	public static final Number FOLLOW_LENIENCY_PERMISSIVE_CHORD_PARTIALS = new Number(PREFIX + "follow-leniency.permissive.chord-partials", Section.LENIENCY_1, "", 1, 1, 4, true);
	public static final Number FOLLOW_LENIENCY_PERMISSIVE_CHORD_CONFIRM = new Number(PREFIX + "follow-leniency.permissive.chord-confirm", Section.LENIENCY_1, "ms", 24, 20, 150, true);
	public static final Number FOLLOW_LENIENCY_VERY_PERMISSIVE_ACCEPT = new Number(PREFIX + "follow-leniency.very-permissive.accept", Section.LENIENCY_2, "cents", 35, 10, 45, true);
	public static final Number FOLLOW_LENIENCY_VERY_PERMISSIVE_CONFIDENCE = new Number(PREFIX + "follow-leniency.very-permissive.confidence", Section.LENIENCY_2, "%", 0.5, 0.5, 0.95, false, 100.0);
	public static final Number FOLLOW_LENIENCY_VERY_PERMISSIVE_STABILITY = new Number(PREFIX + "follow-leniency.very-permissive.stability", Section.LENIENCY_2, "ms", 50, 50, 300, true);
	public static final Number FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_EVIDENCE = new Number(PREFIX + "follow-leniency.very-permissive.chord-evidence", Section.LENIENCY_2, "%", 0.2, 0.2, 0.8, false, 100.0);
	public static final Number FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_PARTIALS = new Number(PREFIX + "follow-leniency.very-permissive.chord-partials", Section.LENIENCY_2, "", 1, 1, 4, true);
	public static final Number FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_CONFIRM = new Number(PREFIX + "follow-leniency.very-permissive.chord-confirm", Section.LENIENCY_2, "ms", 20, 20, 150, true);
	public static final Number ARCADE_LENIENCY_FUN_CONFIDENCE = new Number(PREFIX + "arcade-leniency.fun.confidence", Section.LENIENCY_3, "%", 0.7, 0.5, 0.95, false, 100.0);
	public static final Number ARCADE_LENIENCY_FUN_STABILITY = new Number(PREFIX + "arcade-leniency.fun.stability", Section.LENIENCY_3, "ms", 75, 50, 300, true);
	public static final Number ARCADE_LENIENCY_FUN_CHORD_EVIDENCE = new Number(PREFIX + "arcade-leniency.fun.chord-evidence", Section.LENIENCY_3, "%", 0.28, 0.2, 0.8, false, 100.0);
	public static final Number ARCADE_LENIENCY_FUN_CHORD_PARTIALS = new Number(PREFIX + "arcade-leniency.fun.chord-partials", Section.LENIENCY_3, "", 2, 1, 4, true);
	public static final Number ARCADE_LENIENCY_FUN_CHORD_CONFIRM = new Number(PREFIX + "arcade-leniency.fun.chord-confirm", Section.LENIENCY_3, "ms", 32, 20, 150, true);
	public static final Number ARCADE_LENIENCY_FUN_TEMPO_WINDOW = new Number(PREFIX + "arcade-leniency.fun.tempo-window", Section.LENIENCY_3, "%", 1.25, 1.0, 2.0, false, 100.0);
	public static final Number ARCADE_LENIENCY_VERY_PERMISSIVE_CONFIDENCE = new Number(PREFIX + "arcade-leniency.very-permissive.confidence", Section.LENIENCY_4, "%", 0.6, 0.5, 0.95, false, 100.0);
	public static final Number ARCADE_LENIENCY_VERY_PERMISSIVE_STABILITY = new Number(PREFIX + "arcade-leniency.very-permissive.stability", Section.LENIENCY_4, "ms", 55, 50, 300, true);
	public static final Number ARCADE_LENIENCY_VERY_PERMISSIVE_CHORD_EVIDENCE = new Number(PREFIX + "arcade-leniency.very-permissive.chord-evidence", Section.LENIENCY_4, "%", 0.22, 0.2, 0.8, false, 100.0);
	public static final Number ARCADE_LENIENCY_VERY_PERMISSIVE_CHORD_PARTIALS = new Number(PREFIX + "arcade-leniency.very-permissive.chord-partials", Section.LENIENCY_4, "", 1, 1, 4, true);
	public static final Number ARCADE_LENIENCY_VERY_PERMISSIVE_CHORD_CONFIRM = new Number(PREFIX + "arcade-leniency.very-permissive.chord-confirm", Section.LENIENCY_4, "ms", 24, 20, 150, true);
	public static final Number ARCADE_LENIENCY_VERY_PERMISSIVE_TEMPO_WINDOW = new Number(PREFIX + "arcade-leniency.very-permissive.tempo-window", Section.LENIENCY_4, "%", 1.5, 1.0, 2.0, false, 100.0);

	/**
	 * The technique detectors (spec 026, T026): bend, slide, vibrato and legato thresholds, the pitch motion's outlier and minimum shared by the
	 * pitch-based detectors, and the confidence a pitch frame needs to be kept for a technique (Wait and Follow). Durations are stored in seconds.
	 */
	public static final Number TECHNIQUE_BEND_MIN_RANGE = new Number(PREFIX + "technique.bend.min-range", Section.TECHNIQUE_1, "cents", 60, 20, 200, true);
	public static final Number TECHNIQUE_BEND_PLAIN_RANGE = new Number(PREFIX + "technique.bend.plain-range", Section.TECHNIQUE_1, "cents", 30, 5, 100, true);
	public static final Number TECHNIQUE_BEND_MIN_DURATION = new Number(PREFIX + "technique.bend.min-duration", Section.TECHNIQUE_1, "ms", 0.2, 0.05, 1.0, false, 1000.0);
	public static final Number TECHNIQUE_BEND_VOICED = new Number(PREFIX + "technique.bend.voiced", Section.TECHNIQUE_1, "%", 0.6, 0.2, 1.0, false, 100.0);
	public static final Number TECHNIQUE_BEND_WRITTEN_MOVE = new Number(PREFIX + "technique.bend.written-move", Section.TECHNIQUE_1, "cents", 25, 5, 100, true);
	public static final Number TECHNIQUE_BEND_ON_TOLERANCE = new Number(PREFIX + "technique.bend.on-tolerance", Section.TECHNIQUE_2, "cents", 30, 5, 100, true);
	public static final Number TECHNIQUE_BEND_ON_SHARE = new Number(PREFIX + "technique.bend.on-share", Section.TECHNIQUE_2, "%", 0.15, 0.0, 0.5, false, 100.0);
	public static final Number TECHNIQUE_BEND_MOVED_SHARE = new Number(PREFIX + "technique.bend.moved-share", Section.TECHNIQUE_2, "%", 0.5, 0.1, 1.0, false, 100.0);
	public static final Number TECHNIQUE_BEND_START_TOLERANCE = new Number(PREFIX + "technique.bend.start-tolerance", Section.TECHNIQUE_2, "cents", 40, 5, 150, true);
	public static final Number TECHNIQUE_BEND_EDGE = new Number(PREFIX + "technique.bend.edge", Section.TECHNIQUE_2, "hops", 8, 1, 40, true);
	public static final Number TECHNIQUE_SLIDE_MIN_RANGE = new Number(PREFIX + "technique.slide.min-range", Section.TECHNIQUE_3, "cents", 100, 30, 300, true);
	public static final Number TECHNIQUE_SLIDE_PLAIN_RANGE = new Number(PREFIX + "technique.slide.plain-range", Section.TECHNIQUE_3, "cents", 40, 5, 150, true);
	public static final Number TECHNIQUE_SLIDE_MONOTONY = new Number(PREFIX + "technique.slide.monotony", Section.TECHNIQUE_3, "%", 0.75, 0.3, 1.0, false, 100.0);
	public static final Number TECHNIQUE_SLIDE_STEPS = new Number(PREFIX + "technique.slide.steps", Section.TECHNIQUE_3, "hops", 6, 1, 30, true);
	public static final Number TECHNIQUE_SLIDE_MIN_DURATION = new Number(PREFIX + "technique.slide.min-duration", Section.TECHNIQUE_3, "ms", 0.15, 0.05, 1.0, false, 1000.0);
	public static final Number TECHNIQUE_SLIDE_VOICED = new Number(PREFIX + "technique.slide.voiced", Section.TECHNIQUE_3, "%", 0.5, 0.2, 1.0, false, 100.0);
	public static final Number TECHNIQUE_VIBRATO_DEPTH = new Number(PREFIX + "technique.vibrato.depth", Section.TECHNIQUE_4, "cents", 25, 5, 100, true);
	public static final Number TECHNIQUE_VIBRATO_PLAIN_DEPTH = new Number(PREFIX + "technique.vibrato.plain-depth", Section.TECHNIQUE_4, "cents", 10, 1, 50, true);
	public static final Number TECHNIQUE_VIBRATO_MIN_RATE = new Number(PREFIX + "technique.vibrato.min-rate", Section.TECHNIQUE_4, "Hz", 2.5, 0.5, 6.0, false);
	public static final Number TECHNIQUE_VIBRATO_MAX_RATE = new Number(PREFIX + "technique.vibrato.max-rate", Section.TECHNIQUE_4, "Hz", 9.0, 6.5, 20.0, false);
	public static final Number TECHNIQUE_VIBRATO_MIN_DURATION = new Number(PREFIX + "technique.vibrato.min-duration", Section.TECHNIQUE_4, "ms", 0.35, 0.1, 2.0, false, 1000.0);
	public static final Number TECHNIQUE_VIBRATO_VOICED = new Number(PREFIX + "technique.vibrato.voiced", Section.TECHNIQUE_4, "%", 0.7, 0.2, 1.0, false, 100.0);
	public static final Number TECHNIQUE_LEGATO_START_WINDOW = new Number(PREFIX + "technique.legato.start-window", Section.TECHNIQUE_5, "ms", 0.1, 0.03, 0.5, false, 1000.0);
	public static final Number TECHNIQUE_LEGATO_PICKED_RISE = new Number(PREFIX + "technique.legato.picked-rise", Section.TECHNIQUE_5, "dB", 6.0, 2.0, 20.0, false);
	public static final Number TECHNIQUE_LEGATO_RISE = new Number(PREFIX + "technique.legato.rise", Section.TECHNIQUE_5, "dB", 3.0, 0.0, 10.0, false);
	public static final Number TECHNIQUE_FRAME_CONFIDENCE = new Number(PREFIX + "technique.frame-confidence", Section.TECHNIQUE_5, "%", 0.8, 0.5, 1.0, false, 100.0);
	public static final Number TECHNIQUE_MOTION_OUTLIER = new Number(PREFIX + "technique.motion.outlier", Section.TECHNIQUE_5, "cents", 400, 100, 1200, true);
	public static final Number TECHNIQUE_MOTION_MIN_VOICED = new Number(PREFIX + "technique.motion.min-voiced", Section.TECHNIQUE_5, "hops", 8, 2, 40, true);

	/**
	 * The calibrations and the input checks (spec 026, T027): the delay calibration's note windows and pitch, its evaluation, the profile calibration's
	 * recognition rate, noise words and sound detection, the level check, and the weak-input check of fast recognition.
	 */
	public static final Number CALIBRATION_EARLY_WINDOW = new Number(PREFIX + "calibration.early-window", Section.CALIBRATION_1, "ms", 250, 50, 1000, true);
	public static final Number CALIBRATION_LATE_WINDOW = new Number(PREFIX + "calibration.late-window", Section.CALIBRATION_1, "ms", 700, 200, 2000, true);
	public static final Number CALIBRATION_PITCH_PATIENCE = new Number(PREFIX + "calibration.pitch-patience", Section.CALIBRATION_1, "ms", 25, 5, 200, true);
	public static final Number CALIBRATION_PITCH_BEFORE = new Number(PREFIX + "calibration.pitch-before", Section.CALIBRATION_1, "ms", 30, 0, 200, true);
	public static final Number CALIBRATION_SAME_PITCH = new Number(PREFIX + "calibration.same-pitch", Section.CALIBRATION_1, "semitones", 0.5, 0.1, 2.0, false);
	public static final Number CALIBRATION_MIN_CONFIDENCE = new Number(PREFIX + "calibration.min-confidence", Section.CALIBRATION_1, "%", 0.8, 0.5, 1.0, false, 100.0);
	public static final Number CALIBRATION_MIN_FREQUENCY = new Number(PREFIX + "calibration.min-frequency", Section.CALIBRATION_1, "Hz", 70, 40, 200, true);
	public static final Number CALIBRATION_MAX_FREQUENCY = new Number(PREFIX + "calibration.max-frequency", Section.CALIBRATION_2, "Hz", 1400, 500, 4000, true);
	public static final Number CALIBRATION_MIN_USABLE = new Number(PREFIX + "calibration.min-usable", Section.CALIBRATION_2, "", 5, 2, 8, true);
	public static final Number CALIBRATION_OUTLIER_DEVIATIONS = new Number(PREFIX + "calibration.outlier-deviations", Section.CALIBRATION_2, "×", 3.0, 1.0, 6.0, false);
	public static final Number CALIBRATION_OUTLIER_FLOOR = new Number(PREFIX + "calibration.outlier-floor", Section.CALIBRATION_2, "ms", 40, 5, 200, true);
	public static final Number CALIBRATION_UNSTABLE_SPREAD = new Number(PREFIX + "calibration.unstable-spread", Section.CALIBRATION_2, "ms", 60, 10, 300, true);
	public static final Number CALIBRATION_RECOGNITION_RATE = new Number(PREFIX + "calibration.recognition-rate", Section.CALIBRATION_2, "%", 60, 10, 100, true);
	public static final Number CALIBRATION_CALM_BELOW = new Number(PREFIX + "calibration.calm-below", Section.CALIBRATION_3, "dBFS", -60, -90, -46, true);
	public static final Number CALIBRATION_LOUD_ABOVE = new Number(PREFIX + "calibration.loud-above", Section.CALIBRATION_3, "dBFS", -45, -59, -20, true);
	public static final Number CALIBRATION_SOUND_REFRACTORY = new Number(PREFIX + "calibration.sound-refractory", Section.CALIBRATION_3, "ms", 250, 50, 1000, true);
	public static final Number CALIBRATION_SOUND_LEAD = new Number(PREFIX + "calibration.sound-lead", Section.CALIBRATION_3, "ms", 150, 0, 500, true);
	public static final Number LEVEL_CHECK_PLAYING_CONFIDENCE = new Number(PREFIX + "level-check.playing-confidence", Section.LEVEL_CHECK, "%", 0.8, 0.5, 1.0, false, 100.0);
	public static final Number LEVEL_CHECK_REQUIRED_PLAYING = new Number(PREFIX + "level-check.required-playing", Section.LEVEL_CHECK, "ms", 500, 100, 3000, true);
	public static final Number LEVEL_CHECK_SUBOPTIMAL_SHARE = new Number(PREFIX + "level-check.suboptimal-share", Section.LEVEL_CHECK, "%", 0.1, 0.01, 0.5, false, 100.0);
	public static final Number INPUT_QUALITY_ATTACKS = new Number(PREFIX + "input-quality.attacks", Section.INPUT_QUALITY, "", 3, 1, 10, true);
	public static final Number INPUT_QUALITY_REMEMBER = new Number(PREFIX + "input-quality.remember", Section.INPUT_QUALITY, "ms", 8000, 1000, 30000, true);
	public static final Number INPUT_QUALITY_ATTACK_WINDOW = new Number(PREFIX + "input-quality.attack-window", Section.INPUT_QUALITY, "ms", 200, 50, 1000, true);

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
				GAIN_TARGET, GAIN_MAX_PEAK, GAIN_NO_CHANGE, GAIN_MIN_SNR, ACCEPTANCE_MIN_SHARE, ACCEPTANCE_MIN_BALANCE, ACCEPTANCE_MIN_PEAK,
				ACCEPTANCE_HOPS, ACCEPTANCE_NEIGHBOUR, ACCEPTANCE_ATTACK_STRENGTH, ACCEPTANCE_REARM, ACCEPTANCE_OFFSET_MARGIN, ACCEPTANCE_DECAY,
				ACCEPTANCE_GUARD, ACCEPTANCE_MIN_TOTAL, ACCEPTANCE_FLOOR_HOPS, ACCEPTANCE_SILENCE, ACCEPTANCE_MIN_ONSET, ACCEPTANCE_ONSET_FLOOR,
				ACCEPTANCE_RELEASE, WAIT_VERDICT_HOLD, WAIT_NEIGHBOUR_VETO, WAIT_NEIGHBOUR_CONFIDENCE, WAIT_TECHNIQUE_WINDOW, WAIT_BEND_WINDOW_MIN,
				WAIT_BEND_WINDOW_MAX, WAIT_BEND_MARGIN, WAIT_TECHNIQUE_LEAD, WAIT_SPEED_WINDOW, WAIT_SPEED_LEAD, WAIT_EARLY_MARGIN, WAIT_EARLY_HOPS,
				WAIT_WEAK_FAST, FOLLOW_EVIDENCE_HORIZON, FOLLOW_SILENCE, FOLLOW_SHORT_NOTE, FOLLOW_SAME_SOUND, FOLLOW_RINGING, FOLLOW_SCAN_BACK,
				FOLLOW_ONSET_MEMORY, FOLLOW_REFINE_BEFORE, FOLLOW_REFINE_AFTER, FOLLOW_AFTER_CLOSE, FOLLOW_TAIL, FOLLOW_HISTORY, FOLLOW_TECHNIQUE_WINDOW,
				FOLLOW_BEND_WINDOW_MIN, FOLLOW_BEND_WINDOW_MAX, FOLLOW_BEND_MARGIN, FOLLOW_EVIDENCE_LEAD, CLOCK_FIT_WINDOW, CLOCK_MIN_POINTS, CLOCK_MIN_SPAN,
				CLOCK_STALL, CLOCK_EXTRAPOLATION, CLOCK_SEEK_MARGIN, CLOCK_LATENCY_LIMIT, TIMING_REFERENCE_BEAT, TIMING_ADVANCE, TIMING_GAP,
				TIMING_CLOCK_SPREAD,
				FOLLOW_LENIENCY_PERMISSIVE_ACCEPT, FOLLOW_LENIENCY_PERMISSIVE_CONFIDENCE, FOLLOW_LENIENCY_PERMISSIVE_STABILITY,
				FOLLOW_LENIENCY_PERMISSIVE_CHORD_EVIDENCE, FOLLOW_LENIENCY_PERMISSIVE_CHORD_PARTIALS, FOLLOW_LENIENCY_PERMISSIVE_CHORD_CONFIRM,
				FOLLOW_LENIENCY_VERY_PERMISSIVE_ACCEPT, FOLLOW_LENIENCY_VERY_PERMISSIVE_CONFIDENCE, FOLLOW_LENIENCY_VERY_PERMISSIVE_STABILITY,
				FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_EVIDENCE, FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_PARTIALS, FOLLOW_LENIENCY_VERY_PERMISSIVE_CHORD_CONFIRM,
				ARCADE_LENIENCY_FUN_CONFIDENCE, ARCADE_LENIENCY_FUN_STABILITY, ARCADE_LENIENCY_FUN_CHORD_EVIDENCE, ARCADE_LENIENCY_FUN_CHORD_PARTIALS,
				ARCADE_LENIENCY_FUN_CHORD_CONFIRM, ARCADE_LENIENCY_FUN_TEMPO_WINDOW, ARCADE_LENIENCY_VERY_PERMISSIVE_CONFIDENCE,
				ARCADE_LENIENCY_VERY_PERMISSIVE_STABILITY, ARCADE_LENIENCY_VERY_PERMISSIVE_CHORD_EVIDENCE, ARCADE_LENIENCY_VERY_PERMISSIVE_CHORD_PARTIALS,
				ARCADE_LENIENCY_VERY_PERMISSIVE_CHORD_CONFIRM, ARCADE_LENIENCY_VERY_PERMISSIVE_TEMPO_WINDOW,
				TECHNIQUE_BEND_MIN_RANGE, TECHNIQUE_BEND_PLAIN_RANGE, TECHNIQUE_BEND_MIN_DURATION, TECHNIQUE_BEND_VOICED, TECHNIQUE_BEND_WRITTEN_MOVE,
				TECHNIQUE_BEND_ON_TOLERANCE, TECHNIQUE_BEND_ON_SHARE, TECHNIQUE_BEND_MOVED_SHARE, TECHNIQUE_BEND_START_TOLERANCE, TECHNIQUE_BEND_EDGE,
				TECHNIQUE_SLIDE_MIN_RANGE, TECHNIQUE_SLIDE_PLAIN_RANGE, TECHNIQUE_SLIDE_MONOTONY, TECHNIQUE_SLIDE_STEPS, TECHNIQUE_SLIDE_MIN_DURATION,
				TECHNIQUE_SLIDE_VOICED, TECHNIQUE_VIBRATO_DEPTH, TECHNIQUE_VIBRATO_PLAIN_DEPTH, TECHNIQUE_VIBRATO_MIN_RATE, TECHNIQUE_VIBRATO_MAX_RATE,
				TECHNIQUE_VIBRATO_MIN_DURATION, TECHNIQUE_VIBRATO_VOICED, TECHNIQUE_LEGATO_START_WINDOW, TECHNIQUE_LEGATO_PICKED_RISE, TECHNIQUE_LEGATO_RISE,
				TECHNIQUE_FRAME_CONFIDENCE, TECHNIQUE_MOTION_OUTLIER, TECHNIQUE_MOTION_MIN_VOICED,
				CALIBRATION_EARLY_WINDOW, CALIBRATION_LATE_WINDOW, CALIBRATION_PITCH_PATIENCE, CALIBRATION_PITCH_BEFORE, CALIBRATION_SAME_PITCH,
				CALIBRATION_MIN_CONFIDENCE, CALIBRATION_MIN_FREQUENCY, CALIBRATION_MAX_FREQUENCY, CALIBRATION_MIN_USABLE, CALIBRATION_OUTLIER_DEVIATIONS,
				CALIBRATION_OUTLIER_FLOOR, CALIBRATION_UNSTABLE_SPREAD, CALIBRATION_RECOGNITION_RATE, CALIBRATION_CALM_BELOW, CALIBRATION_LOUD_ABOVE,
				CALIBRATION_SOUND_REFRACTORY, CALIBRATION_SOUND_LEAD, LEVEL_CHECK_PLAYING_CONFIDENCE, LEVEL_CHECK_REQUIRED_PLAYING, LEVEL_CHECK_SUBOPTIMAL_SHARE,
				INPUT_QUALITY_ATTACKS, INPUT_QUALITY_REMEMBER, INPUT_QUALITY_ATTACK_WINDOW);
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
			new OrderedPair(POLYPHONIC_CANDIDATE_LOW, POLYPHONIC_CANDIDATE_HIGH), new OrderedPair(NOISE_GATE_CLOSE, NOISE_GATE_OPEN),
			new OrderedPair(WAIT_BEND_WINDOW_MIN, WAIT_BEND_WINDOW_MAX), new OrderedPair(FOLLOW_BEND_WINDOW_MIN, FOLLOW_BEND_WINDOW_MAX),
			new OrderedPair(CALIBRATION_CALM_BELOW, CALIBRATION_LOUD_ABOVE)));

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
