package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame.LevelState;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;

/** FR-049–FR-051, FR-056 and the needle requirement (left flat, centre in tune, right sharp). */
public class TunerDisplayTest {

	private static final int[] STANDARD = { 64, 59, 55, 50, 45, 40 };
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
	private final NoteInterpreter interpreter = new NoteInterpreter(0.8);

	@Test
	public void chromaticModeShowsCentsFromTheDetectedNote() {
		TunerDisplay flat = display(hz(57, -20), LevelState.SUITABLE, null, 0);
		assertEquals(TunerDisplay.State.FLAT, flat.getState());
		assertEquals("A", flat.getReading().getNoteName());
		assertEquals(3, flat.getReading().getOctave());
		assertEquals(-0.4, flat.getNeedlePosition(), 0.01);

		TunerDisplay inTune = display(hz(57, 3), LevelState.SUITABLE, null, 0);
		assertEquals(TunerDisplay.State.IN_TUNE, inTune.getState());
		assertEquals(220.0, inTune.getFrequencyHz(), 1.0);

		TunerDisplay sharp = display(hz(57, 30), LevelState.SUITABLE, null, 0);
		assertEquals(TunerDisplay.State.SHARP, sharp.getState());
		assertEquals(0.6, sharp.getNeedlePosition(), 0.01);
	}

	@Test
	public void guidedModeMeasuresAgainstTheNearestOrClickedString() {
		TunerDisplay nearest = display(hz(38, 0), LevelState.SUITABLE, STANDARD, 0);
		assertEquals(6, nearest.getTargetString());
		assertEquals(TunerDisplay.State.FLAT, nearest.getState());
		assertEquals(-200.0, nearest.getDeviationCents(), 0.5);
		assertEquals(-1.0, nearest.getNeedlePosition(), 0.001);

		TunerDisplay clicked = display(hz(45, 2), LevelState.SUITABLE, STANDARD, 5);
		assertEquals(5, clicked.getTargetString());
		assertEquals(TunerDisplay.State.IN_TUNE, clicked.getState());
	}

	@Test
	public void weakOrClippingInputShowsAnInputProblemInsteadOfAReading() {
		assertEquals(TunerDisplay.State.INPUT_CLIPPING, display(hz(57, 0), LevelState.CLIPPING, null, 0).getState());
		TunerDisplay weak = display(hz(57, 0), LevelState.TOO_LOW, null, 0);
		assertEquals(TunerDisplay.State.INPUT_TOO_LOW, weak.getState());
		assertFalse(weak.isTuning());
		assertFalse(weak.getReading().hasPitch());
		assertEquals(TunerDisplay.State.NO_READING, display(Double.NaN, LevelState.TOO_LOW, null, 0).getState());
	}

	private TunerDisplay display(double f0Hz, LevelState level, int[] targets, int selectedString) {
		AnalysisFrame frame = new AnalysisFrame(0L, -20.0, -10.0, 0.0, level, f0Hz, Double.isNaN(f0Hz) ? 0.0 : 0.95, false);
		return TunerDisplay.of(frame, this.interpreter.interpret(f0Hz, frame.getConfidence(), SUITABLE), targets, selectedString, 5.0);
	}

	private static double hz(int midi, double cents) {
		return 440.0 * Math.pow(2.0, (midi - 69 + cents / 100.0) / 12.0);
	}
}
