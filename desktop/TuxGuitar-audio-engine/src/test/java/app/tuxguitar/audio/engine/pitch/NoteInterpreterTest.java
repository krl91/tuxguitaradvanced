package app.tuxguitar.audio.engine.pitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.input.InputAssessment.Reason;
import app.tuxguitar.audio.engine.input.InputAssessment.Status;

public class NoteInterpreterTest {

	private final NoteInterpreter interpreter = new NoteInterpreter(0.8);

	@Test
	public void interpretsA4AndGuitarLowEInSoundingPitch() {
		PitchReading a4 = this.interpreter.interpret(440.0, 0.9, InputAssessment.UNCHECKED);
		PitchReading e2 = this.interpreter.interpret(82.406889, 0.9, InputAssessment.UNCHECKED);

		assertEquals(69, a4.getMidi());
		assertEquals("A", a4.getNoteName());
		assertEquals(4, a4.getOctave());
		assertEquals(0.0, a4.getCents(), 0.001);
		assertEquals(40, e2.getMidi());
		assertEquals("E", e2.getNoteName());
		assertEquals(2, e2.getOctave());
	}

	@Test
	public void centsAreNegativeBelowAndPositiveAboveNoteCentre() {
		PitchReading flat = this.interpreter.interpret(440.0 * Math.pow(2.0, -25.0 / 1200.0), 1.0, InputAssessment.UNCHECKED);
		PitchReading sharp = this.interpreter.interpret(440.0 * Math.pow(2.0, 25.0 / 1200.0), 1.0, InputAssessment.UNCHECKED);

		assertEquals(-25.0, flat.getCents(), 0.001);
		assertEquals(25.0, sharp.getCents(), 0.001);
	}

	@Test
	public void reliabilityUsesParameterizedConfidenceAndUnusableAssessment() {
		assertTrue(this.interpreter.interpret(440.0, 0.8, InputAssessment.UNCHECKED).isReliable());
		assertFalse(this.interpreter.interpret(440.0, 0.79, InputAssessment.UNCHECKED).isReliable());
		InputAssessment unusable = new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL);
		assertFalse(this.interpreter.interpret(440.0, 1.0, unusable).isReliable());
	}

	@Test
	public void unpitchedAndInvalidFrequenciesReturnNoReading() {
		assertFalse(this.interpreter.interpret(new PitchEstimate(Double.NaN, 0.0, false), InputAssessment.UNCHECKED).hasPitch());
		assertFalse(this.interpreter.interpret(0.0, 1.0, InputAssessment.UNCHECKED).hasPitch());
	}

	@Test
	public void validatesConfidenceThreshold() {
		assertThrows(IllegalArgumentException.class, () -> new NoteInterpreter(-0.1));
		assertThrows(IllegalArgumentException.class, () -> new NoteInterpreter(1.1));
	}
}
