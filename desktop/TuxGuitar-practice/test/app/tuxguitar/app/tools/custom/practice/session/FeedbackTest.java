package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame.LevelState;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;

/** FR-017, FR-018, FR-019, FR-023, FR-026: one feedback state per frame for an expected note. */
public class FeedbackTest {

	private static final int E4 = 64;
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);

	private final NoteInterpreter interpreter = new NoteInterpreter(0.8);
	private final TolerancePolicy tolerance = new TolerancePolicy(5.0, 30.0);

	@Test
	public void wrongOctaveIsIncorrectWithAnOctaveHint() {
		Feedback feedback = evaluate(hz(E4 + 12, 0), 0.95, LevelState.SUITABLE, false);
		assertEquals(Feedback.Kind.INCORRECT, feedback.getKind());
		assertEquals(E4 + 12, feedback.getDetectedMidi());
		assertTrue(feedback.isOctaveOnly());

		Feedback lower = evaluate(hz(E4 - 12, 0), 0.95, LevelState.SUITABLE, false);
		assertTrue(lower.isOctaveOnly());
	}

	@Test
	public void anotherNoteIsIncorrectAndShowsTheDetectedNote() {
		Feedback feedback = evaluate(hz(E4 + 1, 0), 0.95, LevelState.SUITABLE, false);
		assertEquals(Feedback.Kind.INCORRECT, feedback.getKind());
		assertEquals(E4 + 1, feedback.getDetectedMidi());
		assertFalse(feedback.isOctaveOnly());
	}

	@Test
	public void detectedCentsAreRelativeToTheDetectedNoteNotTheExpectedOne() {
		PitchReading reading = this.interpreter.interpret(hz(E4 + 1, -20), 0.95, SUITABLE);
		assertEquals(E4 + 1, reading.getMidi());
		assertEquals(-20.0, reading.getCents(), 0.01);
	}

	@Test
	public void theRightNoteTooFarOutOfTuneIsIncorrectAsThatSameNote() {
		Feedback feedback = evaluate(hz(E4, 40), 0.95, LevelState.SUITABLE, false);
		assertEquals(Feedback.Kind.INCORRECT, feedback.getKind());
		assertEquals(E4, feedback.getDetectedMidi());
		assertFalse(feedback.isOctaveOnly());
	}

	@Test
	public void unreliableOrClippingSignalsAreUncertainNeverIncorrect() {
		assertEquals(Feedback.Kind.UNCERTAIN, evaluate(hz(E4 + 1, 0), 0.3, LevelState.SUITABLE, false).getKind());
		assertEquals(Feedback.Kind.UNCERTAIN, evaluate(hz(E4 + 1, 0), 0.95, LevelState.CLIPPING, false).getKind());
		assertEquals(Feedback.Kind.UNCERTAIN, evaluate(Double.NaN, 0.0, LevelState.SUITABLE, false).getKind());
		PitchReading unusable = this.interpreter.interpret(hz(E4 + 1, 0), 0.95,
				new InputAssessment(InputAssessment.Status.UNUSABLE, InputAssessment.Reason.TOO_LOW));
		assertEquals(Feedback.Kind.UNCERTAIN, Feedback.evaluate(frame(LevelState.SUITABLE, hz(E4 + 1, 0), 0.95),
				unusable, E4, this.tolerance, false).getKind());
	}

	@Test
	public void silenceWaitsAndAHeldRepeatedNoteAsksForANewPluck() {
		assertEquals(Feedback.Kind.WAITING, evaluate(Double.NaN, 0.0, LevelState.TOO_LOW, false).getKind());

		Feedback matching = evaluate(hz(E4, 3), 0.95, LevelState.SUITABLE, false);
		assertEquals(Feedback.Kind.WAITING, matching.getKind());
		assertFalse(matching.needsArticulation());

		Feedback held = evaluate(hz(E4, 3), 0.95, LevelState.SUITABLE, true);
		assertEquals(Feedback.Kind.WAITING, held.getKind());
		assertTrue(held.needsArticulation());
	}

	@Test
	public void acceptanceIsReportedAsCorrect() {
		assertEquals(Feedback.Kind.CORRECT, Feedback.CORRECT.getKind());
	}

	private Feedback evaluate(double f0Hz, double confidence, LevelState level, boolean articulationMissing) {
		PitchReading reading = this.interpreter.interpret(f0Hz, confidence, SUITABLE);
		return Feedback.evaluate(frame(level, f0Hz, confidence), reading, E4, this.tolerance, articulationMissing);
	}

	private static AnalysisFrame frame(LevelState level, double f0Hz, double confidence) {
		return new AnalysisFrame(0L, -20.0, -10.0, 0.0, level, f0Hz, confidence, false);
	}

	private static double hz(int midi, double cents) {
		return 440.0 * Math.pow(2.0, (midi - 69 + cents / 100.0) / 12.0);
	}
}
