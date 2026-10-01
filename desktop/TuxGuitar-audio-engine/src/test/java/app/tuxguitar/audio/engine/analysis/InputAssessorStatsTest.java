package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.InputAssessment;

/** What the level check keeps for the gain suggestion (spec 002, FR-066). */
public class InputAssessorStatsTest {

	private static final long HOP_NANOS = 5_333_333L;

	private static AnalysisFrame frame(long hop, double rmsDbfs, double peakDbfs, double f0, double confidence) {
		AnalysisFrame.LevelState state = (rmsDbfs < -40.0 ? AnalysisFrame.LevelState.TOO_LOW : AnalysisFrame.LevelState.SUITABLE);
		return new AnalysisFrame(hop * HOP_NANOS, rmsDbfs, peakDbfs, 0.0, state, f0, confidence, false);
	}

	@Test
	public void theCheckKeepsThePlayingLevelThePeakAndTheQuietestBackground() {
		InputAssessor.Parameters parameters = new InputAssessor.Parameters();
		final InputAssessment[] result = new InputAssessment[1];
		InputAssessor assessor = new InputAssessor(parameters, assessment -> result[0] = assessment);
		assertNull(assessor.getLastStats());
		assessor.runLevelCheck();

		long hop = 0;
		// background first: between -72 and -68 dBFS, no pitch
		for( int i = 0 ; i < 40 ; i ++ ) {
			assessor.analysisFrame(frame(hop ++, -70.0 + (i % 5) - 2, -60.0, Double.NaN, 0.1));
		}
		assertNull(assessor.getLastStats(), "nothing is kept while the check runs");
		// then playing at about -30 dBFS, peaks up to -14
		for( int i = 0 ; i < 130 && result[0] == null ; i ++ ) {
			assessor.analysisFrame(frame(hop ++, -30.0, -14.0 - (i % 3), 220.0, 0.95));
		}
		assertNotNull(result[0]);
		assertEquals(InputAssessment.Status.SUITABLE, result[0].getStatus());

		LevelCheckStats stats = assessor.getLastStats();
		assertNotNull(stats);
		assertTrue(stats.hasPlaying());
		assertEquals(-30.0, stats.getPlayingRmsDbfs(), 1e-9);
		assertEquals(-14.0, stats.getPlayingPeakDbfs(), 1e-9);
		assertTrue(stats.hasQuiet());
		assertEquals(-72.0, stats.getQuietRmsDbfs(), 1e-9);
	}

	@Test
	public void aCheckWithoutPlayingKeepsTheBackgroundOnly() {
		InputAssessor.Parameters parameters = new InputAssessor.Parameters();
		parameters.timeoutNanos = 200_000_000L;
		final InputAssessment[] result = new InputAssessment[1];
		InputAssessor assessor = new InputAssessor(parameters, assessment -> result[0] = assessment);
		assessor.runLevelCheck();
		for( long hop = 0 ; hop < 60 && result[0] == null ; hop ++ ) {
			assessor.analysisFrame(frame(hop, -66.0, -55.0, Double.NaN, 0.0));
		}
		assertNotNull(result[0]);
		LevelCheckStats stats = assessor.getLastStats();
		assertFalse(stats.hasPlaying());
		assertTrue(stats.hasQuiet());
		assertEquals(-66.0, stats.getQuietRmsDbfs(), 1e-9);
	}

	@Test
	public void digitalSilenceIsNotTakenForBackgroundNoise() {
		InputAssessor.Parameters parameters = new InputAssessor.Parameters();
		parameters.timeoutNanos = 100_000_000L;
		InputAssessor assessor = new InputAssessor(parameters, assessment -> { });
		assessor.runLevelCheck();
		for( long hop = 0 ; hop < 40 ; hop ++ ) {
			assessor.analysisFrame(frame(hop, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, 0.0));
		}
		assertFalse(assessor.getLastStats().hasQuiet());
	}
}
