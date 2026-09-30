package app.tuxguitar.audio.engine.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.recognition.TargetEvidence.Hypothesis;

public class TargetEvidenceTest {

	private static TargetEvidence sample() {
		return new TargetEvidence(Arrays.asList(new Hypothesis(64, 0.9), new Hypothesis(63, 0.2), new Hypothesis(65, 0.3), new Hypothesis(76, 0.1)), 30.0);
	}

	@Test
	public void theMarginIsTheGapToTheBestOtherHypothesis() {
		TargetEvidence evidence = sample();
		assertEquals(0.6, evidence.marginOver(64), 1e-9);
		assertTrue(evidence.marginOver(65) < 0.0);
		assertEquals(30.0, evidence.getWindowMillis(), 1e-9);
	}

	@Test
	public void unknownPitchesAreRefusedOrAbsent() {
		TargetEvidence evidence = sample();
		assertNull(evidence.of(50));
		assertThrows(IllegalArgumentException.class, () -> evidence.marginOver(50));
		assertThrows(IllegalArgumentException.class, () -> new TargetEvidence(Arrays.<Hypothesis>asList(), 10.0));
		assertThrows(IllegalArgumentException.class, () -> new Hypothesis(60, 1.5));
	}

	@Test
	public void framesStayUnchangedWhenTheNewMembersAreNotEnabled() {
		AnalysisFrame old = new AnalysisFrame(1L, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 329.6, 0.95, true);
		assertNull(old.getTargetEvidence());
		assertNull(old.getTechniqueFeatures());
		assertNull(old.getSpectrum());
		assertEquals(329.6, old.getF0Hz(), 1e-9);

		AnalysisFrame with = new AnalysisFrame(1L, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 329.6, 0.95, true, null, sample(), null);
		assertEquals(64, with.getTargetEvidence().getHypotheses().get(0).getMidi());
	}
}
