package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame.LevelState;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;

/** Owner requirement 2026-09-28: keep the last reliable note and needle, marked as held, when the level drops. */
public class TunerHoldTest {

	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
	private final NoteInterpreter interpreter = new NoteInterpreter(0.8);

	@Test
	public void silenceBeforeAnyReadingHoldsNothing() {
		TunerHold.Shown shown = new TunerHold().update(display(Double.NaN, 0.0, LevelState.TOO_LOW));

		assertFalse(shown.isHeld());
		assertFalse(shown.getDisplay().isTuning());
	}

	@Test
	public void theLastReliableReadingIsHeldWhenTheLevelDrops() {
		TunerHold hold = new TunerHold();
		TunerDisplay live = display(hz(45, -12), 0.95, LevelState.SUITABLE);
		assertFalse(hold.update(live).isHeld());

		TunerHold.Shown decayed = hold.update(display(Double.NaN, 0.0, LevelState.TOO_LOW));
		assertTrue(decayed.isHeld());
		assertSame(live, decayed.getDisplay());
		assertEquals(TunerDisplay.State.FLAT, decayed.getDisplay().getState());
		assertEquals(-(12.0 - 5.0) / (TunerDisplay.NEEDLE_RANGE_CENTS - 5.0), decayed.getDisplay().getNeedlePosition(), 0.01);
		assertEquals(TunerDisplay.State.NO_READING, decayed.getLiveState());

		TunerHold.Shown quiet = hold.update(display(hz(45, 0), 0.95, LevelState.TOO_LOW));
		assertTrue(quiet.isHeld());
		assertEquals(TunerDisplay.State.INPUT_TOO_LOW, quiet.getLiveState());
	}

	@Test
	public void ambientNoiseAndTransientsDoNotReplaceTheHeldReading() {
		TunerHold hold = new TunerHold();
		TunerDisplay live = display(hz(40, 3), 0.95, LevelState.SUITABLE);
		hold.update(live);

		// low-confidence noise, and a clipping knock
		assertSame(live, hold.update(display(hz(52, 20), 0.40, LevelState.SUITABLE)).getDisplay());
		TunerHold.Shown knock = hold.update(display(hz(70, 0), 0.95, LevelState.CLIPPING));
		assertSame(live, knock.getDisplay());
		assertEquals(TunerDisplay.State.INPUT_CLIPPING, knock.getLiveState());
	}

	@Test
	public void aNewReliableReadingReplacesTheHeldOneAndClearForgetsIt() {
		TunerHold hold = new TunerHold();
		hold.update(display(hz(40, 3), 0.95, LevelState.SUITABLE));
		hold.update(display(Double.NaN, 0.0, LevelState.TOO_LOW));

		TunerDisplay next = display(hz(45, 0), 0.95, LevelState.SUITABLE);
		TunerHold.Shown shown = hold.update(next);
		assertFalse(shown.isHeld());
		assertSame(next, shown.getDisplay());

		hold.clear();
		assertFalse(hold.update(display(Double.NaN, 0.0, LevelState.TOO_LOW)).isHeld());
	}

	@Test
	public void withHoldSwitchedOffTheLiveStateIsShownAndNothingIsKept() {
		TunerHold hold = new TunerHold();
		TunerDisplay live = display(hz(45, -12), 0.95, LevelState.SUITABLE);
		assertFalse(hold.update(live, false).isHeld());

		TunerDisplay silent = display(Double.NaN, 0.0, LevelState.TOO_LOW);
		TunerHold.Shown shown = hold.update(silent, false);
		assertFalse(shown.isHeld());
		assertSame(silent, shown.getDisplay());
		// switching it back on does not bring back a reading from while it was off
		assertFalse(hold.update(silent, true).isHeld());
	}

	@Test
	public void theInTuneRangeSettingChangesTheTunerState() {
		// 8 cents sharp: in tune with a 10-cent range, sharp with the recommended 5 cents
		AnalysisFrame frame = new AnalysisFrame(0L, -20.0, -10.0, 0.0, LevelState.SUITABLE, hz(45, 8), 0.95, false);
		assertEquals(TunerDisplay.State.IN_TUNE, TunerDisplay.of(frame, this.interpreter.interpret(hz(45, 8), 0.95, SUITABLE), null, 0, 10.0).getState());
		assertEquals(TunerDisplay.State.SHARP, TunerDisplay.of(frame, this.interpreter.interpret(hz(45, 8), 0.95, SUITABLE), null, 0, 5.0).getState());
	}

	private TunerDisplay display(double f0Hz, double confidence, LevelState level) {
		AnalysisFrame frame = new AnalysisFrame(0L, -20.0, -10.0, 0.0, level, f0Hz, confidence, false);
		return TunerDisplay.of(frame, this.interpreter.interpret(f0Hz, confidence, SUITABLE), null, 0, 5.0);
	}

	private static double hz(int midi, double cents) {
		return 440.0 * Math.pow(2.0, (midi - 69 + cents / 100.0) / 12.0);
	}
}
