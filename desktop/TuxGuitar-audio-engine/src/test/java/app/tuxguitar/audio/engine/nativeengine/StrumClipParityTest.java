package app.tuxguitar.audio.engine.nativeengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/** The native strum clip measure gives the reading of {@link StrumClipMeter} (spec 023 T024): the same verdict, clipped share and mean level. */
public class StrumClipParityTest {

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void theRecordingsGiveTheSameReading() throws Exception {
		RecognitionSettings[] profiles = { RecognitionSettings.defaults(),
				RecognitionSettings.defaults().with(RecognitionParameters.INPUT_LEVEL_MIN, -30).with(RecognitionParameters.LEVEL_CLIP_SAMPLE, 0.5) };
		for (RecognitionSettings settings : profiles) {
			for (ParityRecordings.Recording recording : ParityRecordings.all()) {
				compare(recording.toString(), recording.mono, recording.rate, settings);
			}
		}
	}

	@Test
	public void aSyntheticClippedStrum() {
		int rate = 48000;
		float[] mono = new float[rate * 2];
		for (int i = rate / 2; i < mono.length; i++) {
			double value = 1.4 * Math.exp(-(i - rate / 2) / (0.5 * rate)) * Math.sin(2 * Math.PI * 110 * i / rate);
			mono[i] = (float) Math.max(-1.0, Math.min(1.0, value));
		}
		StrumClipMeter.Reading reading = compare("clipped strum", mono, rate, RecognitionSettings.defaults());
		Assertions.assertEquals(StrumVerdict.CLIPPING, reading.verdict());
	}

	private static StrumClipMeter.Reading compare(String label, float[] mono, int rate, RecognitionSettings settings) {
		StrumClipMeter meter = new StrumClipMeter(StrumClipMeter.Parameters.of(settings));
		meter.configure(rate);
		int hop = ParityEngines.HOP;
		for (int offset = 0; offset + hop <= mono.length; offset += hop) {
			meter.hop(mono, offset, hop);
		}
		StrumClipMeter.Reading expected = meter.snapshot();
		float[] actual = NativeRecognitionLibrary.strumClipOffline(mono, rate, NativeParameters.of(settings));
		Assertions.assertEquals(3, actual.length, label);
		Assertions.assertEquals(expected.verdict().ordinal(), (int) actual[0], label + " verdict");
		Assertions.assertEquals(expected.clippedShare(), actual[1], 1e-6, label + " clipped share");
		if (Double.isNaN(expected.rmsDbfs())) {
			Assertions.assertTrue(Float.isNaN(actual[2]), label + " no level before a verdict");
		} else {
			Assertions.assertEquals(expected.rmsDbfs(), actual[2], 1e-3, label + " level");
		}
		return expected;
	}
}
