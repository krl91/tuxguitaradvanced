package app.tuxguitar.audio.engine.nativeengine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/** The native articulation gives the onsets of {@code ArticulationDetector} on the same hops (spec 023 T019), in normal and fast mode. */
public class OnsetParityTest {

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void theNormalDetectorFindsTheSameOnsets() throws Exception {
		compareAll(false);
	}

	@Test
	public void theFastDetectorFindsTheSameOnsets() throws Exception {
		compareAll(true);
	}

	private static void compareAll(boolean fast) throws Exception {
		ParityEngines.Request request = new ParityEngines.Request();
		request.fastOnset = fast;
		int onsets = 0;
		for (ParityRecordings.Recording recording : ParityRecordings.all()) {
			List<AnalysisFrame> java = ParityEngines.java(recording.mono, recording.rate, request);
			List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(recording.mono, recording.rate, request);
			Assertions.assertEquals(java.size(), nativeFrames.size(), recording.toString());
			for (int i = 0; i < java.size(); i++) {
				Assertions.assertEquals(java.get(i).isOnset(), nativeFrames.get(i).isOnset(), recording + " frame " + i + (fast ? " (fast)" : ""));
				onsets += java.get(i).isOnset() ? 1 : 0;
			}
		}
		Assertions.assertTrue(onsets > 0, "the recordings have onsets");
	}
}
