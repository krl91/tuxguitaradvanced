package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
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
		return new TolerancePolicy(this.settings.get(RecognitionParameters.TUNER_IN_TUNE), this.settings.get(RecognitionParameters.NOTE_ACCEPT));
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

	public long articulationLatencyNanos() {
		return ArticulationDetector.DECISION_DELAY_HOPS * HOP_NANOS;
	}

	public ChordMatcher.Parameters chordParameters() {
		return new ChordMatcher.Parameters((int) this.settings.get(RecognitionParameters.CHORD_MIN_PARTIALS),
				this.settings.get(RecognitionParameters.CHORD_EVIDENCE), this.settings.getChordConfirmHops(),
				(long) this.settings.get(RecognitionParameters.CHORD_WINDOW) * 1_000_000L);
	}

	public RecognitionSettings getSettings() {
		return this.settings;
	}
}
