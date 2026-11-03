package app.tuxguitar.audio.engine.nativeengine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;

/** The native target evidence gives that of {@code ExpectedPitchVerifier} (spec 023 T022): the hypotheses (midi, score) within 1e-4 and the same window. */
public class TargetEvidenceParityTest {

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void aNoteAndAChordGiveTheSameHypotheses() throws Exception {
		int[][] expected = { { 45 }, { 40 }, { 64 }, { 48, 52, 55 }, { 40, 47, 52, 56, 59, 64 } };
		for (int[] pitches : expected) {
			ParityEngines.Request request = new ParityEngines.Request();
			request.expectedPitches = pitches;
			for (ParityRecordings.Recording recording : ParityRecordings.all()) {
				List<AnalysisFrame> java = ParityEngines.java(recording.mono, recording.rate, request);
				List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(recording.mono, recording.rate, request);
				Assertions.assertEquals(java.size(), nativeFrames.size(), recording.toString());
				for (int i = 0; i < java.size(); i++) {
					String label = recording + " frame " + i;
					TargetEvidence a = java.get(i).getTargetEvidence();
					TargetEvidence b = nativeFrames.get(i).getTargetEvidence();
					Assertions.assertNotNull(b, label);
					Assertions.assertEquals(a.getWindowMillis(), b.getWindowMillis(), 1e-9, label);
					Assertions.assertEquals(a.getHypotheses().size(), b.getHypotheses().size(), label);
					for (int h = 0; h < a.getHypotheses().size(); h++) {
						Assertions.assertEquals(a.getHypotheses().get(h).getMidi(), b.getHypotheses().get(h).getMidi(), label);
						Assertions.assertEquals(a.getHypotheses().get(h).getScore(), b.getHypotheses().get(h).getScore(), 1e-4, label + " hypothesis " + h);
					}
				}
			}
		}
	}
}
