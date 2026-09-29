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
