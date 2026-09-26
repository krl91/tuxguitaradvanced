package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame.LevelState;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class LevelAnalyzerTest {

	private final LevelAnalyzer analyzer = new LevelAnalyzer(-40.0, -6.0, 0.99, 0.10);

	@Test
	public void computesRmsPeakAndSuitableClassification() {
		LevelMeasurement result = this.analyzer.analyze(new float[] { 0.5f, -0.5f, 0.5f, -0.5f });
		assertEquals(-6.0206, result.getRmsDbfs(), 0.001);
		assertEquals(-6.0206, result.getPeakDbfs(), 0.001);
		assertEquals(0.0, result.getClippedRatio());
		assertEquals(0.0, result.getExactZeroRatio());
		assertEquals(LevelState.SUITABLE, result.getLevelState());
	}

	@Test
	public void allZeroWindowIsNoSignalNotPermissionDiagnosis() {
		LevelMeasurement result = this.analyzer.analyze(SyntheticSignals.silence(32));
		assertEquals(Double.NEGATIVE_INFINITY, result.getRmsDbfs());
		assertEquals(Double.NEGATIVE_INFINITY, result.getPeakDbfs());
		assertEquals(1.0, result.getExactZeroRatio());
		assertTrue(result.isNoSignal());
		assertEquals(LevelState.TOO_LOW, result.getLevelState());
	}

	@Test
	public void classifiesLowHighAndClippingUsingParameters() {
		assertEquals(LevelState.TOO_LOW, this.analyzer.analyze(new float[] { 0.001f, -0.001f }).getLevelState());
		assertEquals(LevelState.TOO_HIGH, this.analyzer.analyze(new float[] { 0.8f, -0.8f }).getLevelState());
		LevelMeasurement clipping = this.analyzer.analyze(new float[] { 1f, -1f, 0.2f, 0.2f });
		assertEquals(0.5, clipping.getClippedRatio());
		assertEquals(LevelState.CLIPPING, clipping.getLevelState());
		assertFalse(clipping.isNoSignal());
	}

	@Test
	public void analyzesOnlyRequestedRange() {
		LevelMeasurement result = this.analyzer.analyze(new float[] { 1f, 0.1f, -0.1f, 1f }, 1, 2);
		assertEquals(-20.0, result.getRmsDbfs(), 0.001);
		assertEquals(LevelState.SUITABLE, result.getLevelState());
	}

	@Test
	public void validatesThresholdsAndInputRange() {
		assertThrows(IllegalArgumentException.class, () -> new LevelAnalyzer(-5.0, -10.0, 0.99, 0.1));
		assertThrows(IllegalArgumentException.class, () -> new LevelAnalyzer(-40.0, -6.0, 0.0, 0.1));
		assertThrows(IllegalArgumentException.class, () -> this.analyzer.analyze(new float[0]));
	}
}
