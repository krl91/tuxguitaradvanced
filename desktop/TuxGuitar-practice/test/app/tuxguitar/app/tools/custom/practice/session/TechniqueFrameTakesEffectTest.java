package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The frame confidence takes effect (spec 026, T026, SC-001): Wait and Follow keep a pitch frame for a technique through the same function, with the
 * value of their parameters; at the ends of its range a frame read with 0.85 or 0.6 is kept or dropped differently than with the recommended 0.8.
 */
public class TechniqueFrameTakesEffectTest {

	@Test
	public void theFrameConfidenceDecidesWhichFramesAreKept() {
		RecognitionSettings d = RecognitionSettings.defaults();
		assertEquals(0.8, PracticeSession.WaitParameters.of(d).techniqueFrameConfidence);
		assertEquals(0.8, FollowSession.FollowParameters.of(d).techniqueFrameConfidence);
		String recommended = kept(d);
		assertEquals("kept dropped dropped", recommended);
		for( double end : new double[] { RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE.getMinimum(), RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE.getMaximum() } ) {
			RecognitionSettings s = d.with(RecognitionParameters.TECHNIQUE_FRAME_CONFIDENCE, end);
			assertEquals(end, PracticeSession.WaitParameters.of(s).techniqueFrameConfidence);
			assertEquals(end, FollowSession.FollowParameters.of(s).techniqueFrameConfidence);
			assertTrue(!kept(s).equals(recommended), "frame-confidence " + end + " changes nothing");
		}
	}

	private static String kept(RecognitionSettings s) {
		double minimum = PracticeSession.WaitParameters.of(s).techniqueFrameConfidence;
		StringBuilder text = new StringBuilder();
		for( double confidence : new double[] { 0.85, 0.6, 0.45 } ) {
			text.append(text.length() == 0 ? "" : " ").append(Double.isNaN(PracticeSession.techniqueMidi(220.0, confidence, minimum)) ? "dropped" : "kept");
		}
		return text.toString();
	}
}
