package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.input.InputAssessment.Reason;
import app.tuxguitar.audio.engine.input.InputAssessment.Status;

/**
 * A level check that failed starts again by itself when the guitar is heard (spec 025, FR-001, clarification 5, research R1): after "no signal" and
 * "saturating" only, never during a check or a run, and only on a frame the check itself counts as playing.
 */
public class LevelRecheckTest {

	private static AnalysisFrame frame(double f0, double confidence) {
		return new AnalysisFrame(0L, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, false, null, null, null, null, 0L);
	}

	private static final AnalysisFrame PLAYING = frame(220.0, 0.9);

	@Test
	public void noSignalAndSaturatingStartAgainOnAPlayingFrame() {
		assertTrue(LevelRecheck.shouldCheck(new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL), false, false, PLAYING));
		assertTrue(LevelRecheck.shouldCheck(new InputAssessment(Status.UNUSABLE, Reason.CLIPPING), false, false, PLAYING));
	}

	@Test
	public void aLostInputOrARefusedAccessWaitsForTheLearner() {
		assertFalse(LevelRecheck.shouldCheck(new InputAssessment(Status.UNUSABLE, Reason.DEVICE_LOST), false, false, PLAYING));
		assertFalse(LevelRecheck.shouldCheck(new InputAssessment(Status.UNUSABLE, Reason.ACCESS_DENIED_SUSPECTED), false, false, PLAYING));
	}

	@Test
	public void aUsableOrUncheckedInputIsLeftAlone() {
		assertFalse(LevelRecheck.shouldCheck(new InputAssessment(Status.SUITABLE, Reason.NONE), false, false, PLAYING));
		assertFalse(LevelRecheck.shouldCheck(new InputAssessment(Status.SUBOPTIMAL, Reason.TOO_LOW), false, false, PLAYING));
		assertFalse(LevelRecheck.shouldCheck(InputAssessment.UNCHECKED, false, false, PLAYING));
	}

	@Test
	public void onlyAFrameTheCheckCountsAsPlaying() {
		InputAssessment noSignal = new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL);
		assertFalse(LevelRecheck.shouldCheck(noSignal, false, false, frame(Double.NaN, 0.0)), "no pitch");
		assertFalse(LevelRecheck.shouldCheck(noSignal, false, false, frame(220.0, 0.5)), "an unsure pitch");
		assertTrue(LevelRecheck.shouldCheck(noSignal, false, false, frame(220.0, 0.80)), "at the check's own threshold");
	}

	@Test
	public void neverDuringACheckOrARun() {
		InputAssessment noSignal = new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL);
		assertFalse(LevelRecheck.shouldCheck(noSignal, true, false, PLAYING), "a check runs");
		assertFalse(LevelRecheck.shouldCheck(noSignal, false, true, PLAYING), "a run is going");
	}
}
