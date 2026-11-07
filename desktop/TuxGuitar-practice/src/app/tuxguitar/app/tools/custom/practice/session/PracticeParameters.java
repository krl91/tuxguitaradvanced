package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * What a practice session uses, derived from the validated audio-recognition settings (task
 * T102). With the recommended settings every value equals the one hard-coded before: confidence
 * 0.80, tolerance 5 / 35 cents, stability 20 hops, chord 2 partials / one third / 8 hops /
 * 600 ms. Immutable; built once per Start, so a running session never sees a partial change.
 */
public final class PracticeParameters {

	/**
	 * What recognition measured, shown to the learner without more precision than measured (spec 004 SC-003, FR-008): the delay between a
	 * note and its recognition on the reference recording and on the benchmark clips (median 105 ms, 90th percentile 169-172 ms), and the
	 * closest picked notes that are followed (about 0.3 s: research R11, note spacing benchmark).
	 */
	public static final long MEASURED_DELAY_MEDIAN_MILLIS = 105;
	public static final long MEASURED_DELAY_P90_MILLIS = 172;
	public static final double MEASURED_SPEED_LIMIT_SECONDS = 0.3;

	/**
	 * The level the notes are played at (RMS of the strongest window after an attack, dBFS) below which fast recognition is not reliable (spec 002
	 * FR-015): 100 % of three-note runs followed from -25, 95 % at -30, 80 to 90 % at -35, 10 % at -40 (research R3, weak input benchmark).
	 */
	public static final double WEAK_FAST_DBFS = -30.0;

	/** One analysis hop at the reference rate, as practice has always computed it. */
	public static final long HOP_NANOS = TarsosFastYinEstimator.DEFAULT_HOP_SIZE * 1_000_000_000L / 48000L;

	private final RecognitionSettings settings;

	private PracticeParameters(RecognitionSettings settings) {
		this.settings = settings;
	}

	public static PracticeParameters of(RecognitionSettings settings) {
		return new PracticeParameters(settings);
	}

	public NoteInterpreter noteInterpreter() {
		return new NoteInterpreter(this.settings.get(RecognitionParameters.NOTE_CONFIDENCE));
	}

	/** In-tune range (shared with the tuner) and acceptance range; the settings keep in-tune ≤ accept. */
	public TolerancePolicy tolerance() {
		return new TolerancePolicy(this.settings.get(RecognitionParameters.TUNER_IN_TUNE), this.settings.get(RecognitionParameters.NOTE_ACCEPT),
				this.settings.get(RecognitionParameters.NOTE_FRET_SHARP), this.settings.get(RecognitionParameters.PITCH_MAX_SHARP));
	}

	public long stabilityNanos() {
		return this.settings.getNoteStabilityHops() * HOP_NANOS;
	}

	/**
	 * Off: on the real built-in-microphone clips the expected-pitch evidence does not separate the
	 * played note from a wrong one (margin 0.07-0.41 for the right pitch, up to 0.31 for a wrong
	 * one; research.md R3, builtin-mic-evidence.csv), so enabling it would bring no gain, or false
	 * early acceptances. The mechanism is built and tested; switch it on only when a better
	 * evidence measure or the recorded clips of T053 show a margin that separates them.
	 */
	public static final boolean EARLY_STABILITY_ENABLED = false;

	/**
	 * Confidence-scaled stability (spec 002 T049, research R3 L2), provisional until the recorded
	 * fast runs of T053: an evidence margin of at least this value over the neighbours...
	 */
	public static final double EARLY_STABILITY_MARGIN = 0.5;

	/** ...shortens the stability window to 12 hops (about 64 ms); the standard window stays the fallback. */
	public static final long EARLY_STABILITY_NANOS = 12 * HOP_NANOS;

	/** The early stability's margin, the default of {@code recognition.wait.early-margin} being {@link #EARLY_STABILITY_MARGIN} (spec 026). */
	public double earlyStabilityMargin() {
		return this.settings.get(RecognitionParameters.WAIT_EARLY_MARGIN);
	}

	/** The early stability's window, the default of {@code recognition.wait.early-hops} giving {@link #EARLY_STABILITY_NANOS} (spec 026). */
	public long earlyStabilityNanos() {
		return (long) this.settings.get(RecognitionParameters.WAIT_EARLY_HOPS) * HOP_NANOS;
	}

	/** The Follow session's values (spec 026, T022). */
	public FollowSession.FollowParameters followParameters() {
		return FollowSession.FollowParameters.of(this.settings);
	}

	/** The beat at which Arcade's timing width is read, in milliseconds (spec 026, T024). */
	public double referenceBeatMillis() {
		return this.settings.get(RecognitionParameters.TIMING_REFERENCE_BEAT);
	}

	/** Follow's time to play with the advance and gap of the settings (spec 026, T024). */
	public PlayTimeWindow playTimeWindow(int playTimeMillis) {
		return new PlayTimeWindow(playTimeMillis, (long) this.settings.get(RecognitionParameters.TIMING_ADVANCE) * 1_000_000L,
				(long) this.settings.get(RecognitionParameters.TIMING_GAP) * 1_000_000L);
	}

	/** The spread of the capture's lag from which the route is called unstable, in milliseconds (spec 026, T024). */
	public double unstableSpreadMillis() {
		return this.settings.get(RecognitionParameters.TIMING_CLOCK_SPREAD);
	}

	/** The Wait session's values (spec 026, T021). */
	public PracticeSession.WaitParameters waitParameters() {
		return PracticeSession.WaitParameters.of(this.settings);
	}

	/** The detector reports an onset this long after its attack: the last hop of its post window (spec 026, {@code onset.post-last}). */
	public long articulationLatencyNanos() {
		return (long) this.settings.get(RecognitionParameters.ONSET_POST_LAST) * HOP_NANOS;
	}

	public ChordMatcher.Parameters chordParameters() {
		return new ChordMatcher.Parameters((int) this.settings.get(RecognitionParameters.CHORD_MIN_PARTIALS),
				this.settings.get(RecognitionParameters.CHORD_EVIDENCE), this.settings.getChordConfirmHops(),
				(long) this.settings.get(RecognitionParameters.CHORD_WINDOW) * 1_000_000L, this.settings.get(RecognitionParameters.CHORD_COMPLETENESS),
				this.settings.get(RecognitionParameters.CHORD_PROFILE_MATCH), this.settings.get(RecognitionParameters.CHORD_PROFILE_NAMES),
				(int) this.settings.get(RecognitionParameters.CHORD_PROFILE_TOP), (long) this.settings.get(RecognitionParameters.CHORD_PROFILE_SETTLE) * 1_000_000L,
				this.settings.get(RecognitionParameters.CHORD_ATTACK));
	}

	public RecognitionSettings getSettings() {
		return this.settings;
	}
}
