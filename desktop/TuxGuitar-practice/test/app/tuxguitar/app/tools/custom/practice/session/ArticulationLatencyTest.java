package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The detector reports an onset {@code onset.post-last} hops after its attack ({@code ArticulationDetector.getDecisionDelayHops}), so the session's
 * articulation latency follows that value once it is a setting (spec 026, T013); with the recommended value it is the former 13 hops.
 */
public class ArticulationLatencyTest {

	@Test
	public void theLatencyIsThePostWindowsLastHop() {
		assertEquals(ArticulationDetector.DECISION_DELAY_HOPS * PracticeParameters.HOP_NANOS,
				PracticeParameters.of(RecognitionSettings.defaults()).articulationLatencyNanos());
		RecognitionSettings shorter = RecognitionSettings.defaults().with(RecognitionParameters.ONSET_POST_LAST, 9);
		assertEquals(9 * PracticeParameters.HOP_NANOS, PracticeParameters.of(shorter).articulationLatencyNanos());
	}
}
