package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;

/**
 * The one feedback state shown for an expected note (FR-018). Immutable.
 *
 * <ul>
 * <li>WAITING: no usable playing, or the expected note is heard but not accepted yet (stability,
 * or a new articulation still needed — {@link #needsArticulation()}).</li>
 * <li>UNCERTAIN: a signal is present but the pitch is not reliable (weak, clipping, unreliable
 * input), or a non-matching reading comes from a too-low level; never shown as incorrect
 * (FR-026).</li>
 * <li>INCORRECT: a reliable pitch that does not match: another note, the right note in another
 * octave ({@link #isOctaveOnly()}, FR-019), or the right note too far out of tune.</li>
 * <li>CORRECT: the expected note was accepted (FR-024).</li>
 * </ul>
 */
public final class Feedback {

	public enum Kind {
		WAITING,
		UNCERTAIN,
		INCORRECT,
		CORRECT
	}

	public static final Feedback WAITING = new Feedback(Kind.WAITING, -1, false, false);
	public static final Feedback UNCERTAIN = new Feedback(Kind.UNCERTAIN, -1, false, false);
	public static final Feedback CORRECT = new Feedback(Kind.CORRECT, -1, false, false);
	private static final Feedback WAITING_FOR_ARTICULATION = new Feedback(Kind.WAITING, -1, false, true);

	private final Kind kind;
	private final int detectedMidi;
	private final boolean octaveOnly;
	private final boolean needsArticulation;

	private Feedback(Kind kind, int detectedMidi, boolean octaveOnly, boolean needsArticulation) {
		this.kind = kind;
		this.detectedMidi = detectedMidi;
		this.octaveOnly = octaveOnly;
		this.needsArticulation = needsArticulation;
	}

	/**
	 * Feedback for one analysis frame while the expected note is not accepted yet.
	 *
	 * @param articulationMissing no fresh articulation (onset) was heard since the previous
	 *        acceptance or since practice (re)started (FR-022, owner requirement 2026-09-28)
	 */
	public static Feedback evaluate(AnalysisFrame frame, PitchReading reading, int expectedMidi,
			TolerancePolicy tolerance, boolean articulationMissing) {
		return evaluate(frame, reading, expectedMidi, 0, tolerance, articulationMissing);
	}

	/** @param fret the fret of the written note, for the sharp allowance of the tolerance */
	public static Feedback evaluate(AnalysisFrame frame, PitchReading reading, int expectedMidi, int fret,
			TolerancePolicy tolerance, boolean articulationMissing) {
		if( !reading.hasPitch() ) {
			return (frame.getLevelState() == AnalysisFrame.LevelState.TOO_LOW ? WAITING : UNCERTAIN);
		}
		if( !reading.isReliable() || frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING ) {
			return UNCERTAIN;
		}
		boolean weak = (frame.getLevelState() == AnalysisFrame.LevelState.TOO_LOW);
		if( tolerance.matches(reading, expectedMidi, fret) ) {
			return (articulationMissing ? WAITING_FOR_ARTICULATION : WAITING);
		}
		if( weak ) {
			// FR-026: a signal too weak for a reliable judgement is uncertain, never incorrect (T077:
			// silence yields occasional confident sub-guitar readings, F#1–A1, at a very low level)
			return UNCERTAIN;
		}
		int detected = reading.getMidi();
		boolean octaveOnly = (detected != expectedMidi && (detected - expectedMidi) % 12 == 0);
		return new Feedback(Kind.INCORRECT, detected, octaveOnly, false);
	}

	public Kind getKind() {
		return this.kind;
	}

	/** The detected note for INCORRECT; -1 otherwise. */
	public int getDetectedMidi() {
		return this.detectedMidi;
	}

	/** INCORRECT only because of the octave (FR-019). */
	public boolean isOctaveOnly() {
		return this.octaveOnly;
	}

	/** The expected note is heard but must be played again (FR-022). */
	public boolean needsArticulation() {
		return this.needsArticulation;
	}
}
