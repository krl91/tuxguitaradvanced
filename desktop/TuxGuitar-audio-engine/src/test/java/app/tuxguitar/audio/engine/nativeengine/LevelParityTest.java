package app.tuxguitar.audio.engine.nativeengine;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;

/** The native level gives the Java figures (spec 023 T017): RMS and peak within 0.01 dB, clipped share within 1e-4, the same state on every frame. */
public class LevelParityTest {

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void everyFrameHasTheSameLevel() throws Exception {
		ParityEngines.Request request = new ParityEngines.Request();
		for (ParityRecordings.Recording recording : ParityRecordings.all()) {
			compare(recording, request);
		}
	}

	@Test
	public void anotherProfileToo() throws Exception {
		ParityEngines.Request request = new ParityEngines.Request();
		request.settings = request.settings.with(RecognitionParameters.INPUT_LEVEL_MIN, -30).with(RecognitionParameters.LEVEL_CLIP_SAMPLE, 0.9)
				.with(RecognitionParameters.LEVEL_CLIP_SHARE, 0.02);
		for (ParityRecordings.Recording recording : ParityRecordings.first(10)) {
			compare(recording, request);
		}
	}

	private static void compare(ParityRecordings.Recording recording, ParityEngines.Request request) {
		List<AnalysisFrame> java = ParityEngines.java(recording.mono, recording.rate, request);
		List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(recording.mono, recording.rate, request);
		Assertions.assertEquals(java.size(), nativeFrames.size(), recording.toString());
		for (int i = 0; i < java.size(); i++) {
			AnalysisFrame a = java.get(i);
			AnalysisFrame b = nativeFrames.get(i);
			String label = recording + " frame " + i;
			Assertions.assertEquals(a.getTimestampNanos(), b.getTimestampNanos(), label);
			Assertions.assertTrue(close(a.getRmsDbfs(), b.getRmsDbfs(), 0.01), label + " rms " + a.getRmsDbfs() + " / " + b.getRmsDbfs());
			Assertions.assertTrue(close(a.getPeakDbfs(), b.getPeakDbfs(), 0.01), label + " peak " + a.getPeakDbfs() + " / " + b.getPeakDbfs());
			Assertions.assertEquals(a.getClippedRatio(), b.getClippedRatio(), 1e-4, label);
			Assertions.assertEquals(a.getLevelState(), b.getLevelState(), label);
		}
	}

	private static boolean close(double a, double b, double tolerance) {
		return a == b || Math.abs(a - b) <= tolerance;
	}
}
