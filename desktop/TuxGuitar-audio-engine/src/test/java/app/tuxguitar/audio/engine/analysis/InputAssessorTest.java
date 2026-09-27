package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame.LevelState;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.input.InputAssessment.Reason;
import app.tuxguitar.audio.engine.input.InputAssessment.Status;

public class InputAssessorTest {

	private static final long HOP = 5_000_000L;

	private final List<InputAssessment> results = new ArrayList<InputAssessment>();
	private long now;

	private InputAssessor assessor() {
		InputAssessor.Parameters parameters = new InputAssessor.Parameters();
		parameters.playingConfidence = 0.8;
		parameters.requiredPlayingNanos = 200_000_000L;
		parameters.timeoutNanos = 1_000_000_000L;
		parameters.unusableClippingShare = 0.5;
		parameters.suboptimalShare = 0.1;
		parameters.tooLowShare = 0.5;
		parameters.digitalSilenceDbfs = -120.0;
		InputAssessor assessor = new InputAssessor(parameters, this.results::add);
		assessor.runLevelCheck();
		return assessor;
	}

	@Test
	public void suitablePlayingIsSuitable() {
		InputAssessor assessor = assessor();
		feed(assessor, 60, -20.0, LevelState.SUITABLE, 0.95);
		assertResult(Status.SUITABLE, Reason.NONE);
		assertFalse(assessor.isChecking());
	}

	@Test
	public void quietButPitchedPlayingIsSuboptimalTooLow() {
		InputAssessor assessor = assessor();
		feed(assessor, 60, -45.0, LevelState.TOO_LOW, 0.9);
		assertResult(Status.SUBOPTIMAL, Reason.TOO_LOW);
	}

	@Test
	public void loudPlayingIsSuboptimalTooHigh() {
		InputAssessor assessor = assessor();
		feed(assessor, 20, -4.0, LevelState.TOO_HIGH, 0.95);
		feed(assessor, 40, -20.0, LevelState.SUITABLE, 0.95);
		assertResult(Status.SUBOPTIMAL, Reason.TOO_HIGH);
	}

	@Test
	public void occasionalClippingIsSuboptimalAndSevereClippingUnusable() {
		InputAssessor some = assessor();
		feed(some, 10, -1.0, LevelState.CLIPPING, 0.95);
		feed(some, 50, -20.0, LevelState.SUITABLE, 0.95);
		assertResult(Status.SUBOPTIMAL, Reason.CLIPPING);

		this.results.clear();
		InputAssessor severe = assessor();
		feed(severe, 60, -1.0, LevelState.CLIPPING, 0.95);
		assertResult(Status.UNUSABLE, Reason.CLIPPING);
	}

	@Test
	public void noPitchedPlayingBeforeTheTimeoutIsUnusable() {
		InputAssessor noise = assessor();
		feed(noise, 100, -50.0, LevelState.TOO_LOW, 0.2);
		assertNull(result());
		feed(noise, 110, -50.0, LevelState.TOO_LOW, 0.2);
		assertResult(Status.UNUSABLE, Reason.NO_SIGNAL);

		this.results.clear();
		InputAssessor zeros = assessor();
		feed(zeros, 210, -240.0, LevelState.TOO_LOW, 0.0);
		assertResult(Status.UNUSABLE, Reason.ACCESS_DENIED_SUSPECTED);

		this.results.clear();
		InputAssessor distorted = assessor();
		feed(distorted, 210, -1.0, LevelState.CLIPPING, 0.1);
		assertResult(Status.UNUSABLE, Reason.CLIPPING);
	}

	@Test
	public void deviceLossIsUnusableAndFramesAfterACompletedCheckAreIgnored() {
		InputAssessor assessor = assessor();
		assessor.deviceLost();
		assertResult(Status.UNUSABLE, Reason.DEVICE_LOST);

		feed(assessor, 60, -20.0, LevelState.SUITABLE, 0.95);
		assertEquals(1, this.results.size());
	}

	private void feed(InputAssessor assessor, int frames, double rmsDbfs, LevelState state, double confidence) {
		for( int i = 0 ; i < frames ; i ++ ) {
			this.now += HOP;
			assessor.analysisFrame(new AnalysisFrame(this.now, rmsDbfs, rmsDbfs + 6.0, 0.0, state,
					confidence > 0.5 ? 196.0 : Double.NaN, confidence, false));
		}
	}

	private InputAssessment result() {
		return (this.results.isEmpty() ? null : this.results.get(this.results.size() - 1));
	}

	private void assertResult(Status status, Reason reason) {
		InputAssessment result = result();
		assertEquals(1, this.results.size());
		assertEquals(status, result.getStatus());
		assertEquals(reason, result.getReason());
	}
}
