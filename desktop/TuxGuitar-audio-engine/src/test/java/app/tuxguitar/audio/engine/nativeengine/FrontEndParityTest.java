package app.tuxguitar.audio.engine.nativeengine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.frontend.FrontEnd;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The native front end gives the hops of {@link FrontEnd} (spec 023 T016, research R3): gain, high-pass, noise gate and spectral subtraction, hop by hop,
 * within 1e-4 relative, with the default profile and with every stage on, on every parity recording.
 */
public class FrontEndParityTest {

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void theDefaultProfileLeavesEveryBitInBothEngines() throws Exception {
		for (ParityRecordings.Recording recording : ParityRecordings.all()) {
			compare(recording, 1.0, false, false);
		}
	}

	@Test
	public void everyStageOnGivesTheSameHops() throws Exception {
		for (ParityRecordings.Recording recording : ParityRecordings.all()) {
			compare(recording, 2.0, true, true);
			compare(recording, 0.7, true, false);
		}
	}

	private static void compare(ParityRecordings.Recording recording, double gain, boolean noiseReduction, boolean subtraction) {
		int hop = ParityEngines.HOP;
		FrontEnd front = new FrontEnd();
		front.configure(recording.rate, hop);
		front.getGain().setMultiplier(gain);
		front.setNoiseReduction(noiseReduction);
		front.setSubtraction(subtraction);
		int hops = recording.mono.length / hop;
		float[] expected = new float[hops * hop];
		float[] block = new float[hop];
		for (int h = 0; h < hops; h++) {
			System.arraycopy(recording.mono, h * hop, block, 0, hop);
			front.process(block, hop);
			System.arraycopy(block, 0, expected, h * hop, hop);
		}
		float[] actual = NativeRecognitionLibrary.frontEnd(recording.mono, recording.rate,
				NativeParameters.of(RecognitionSettings.defaults(), front.getGain().getMultiplier(), noiseReduction, subtraction));
		String label = recording + " gain " + gain + " nr " + noiseReduction + " sub " + subtraction;
		Assertions.assertEquals(expected.length, actual.length, label);
		for (int i = 0; i < expected.length; i++) {
			double tolerance = 1e-4 * Math.max(Math.abs(expected[i]), 1e-3);
			if (Math.abs(expected[i] - actual[i]) > tolerance) {
				Assertions.fail(label + ": sample " + i + " (hop " + i / hop + ") Java " + expected[i] + " native " + actual[i]);
			}
		}
	}
}
