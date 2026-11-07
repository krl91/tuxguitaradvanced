package app.tuxguitar.audio.engine.nativeengine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The native tuning array (spec 026, T004, contracts/native-parameters.md): its length is the layout's, the {@code float[21]} of spec 023 is
 * unchanged for the defaults, and the interface's new methods fall back on today's for an implementation that only has those.
 */
public class NativeTuningTest {

	@Test
	public void theTuningHasTheLayoutLength() {
		assertEquals(NativeParameters.TUNING_LENGTH, NativeParameters.tuning(RecognitionSettings.defaults()).length);
	}

	@Test
	public void theParameterArrayIsUnchangedForTheDefaults() {
		float[] expected = { -40f, -6f, 0.99f, 0.10f, 1.5f, 5.0f, -50f, 12f, 4.5f, 19f, 2f, 4f, 13f, 1f, 0f, 0f, -40f, 0.99f, -40f, -6f, 0.20f };
		assertArrayEquals(expected, NativeParameters.of(RecognitionSettings.defaults()));
	}

	@Test
	public void anImplementationOfTodaysMethodsOnlyKeepsWorking() throws Exception {
		final float[][] opened = new float[1][];
		final float[][] configured = new float[1][];
		NativeRecognitionLibrary.Api today = new NativeRecognitionLibrary.Api() {
			@Override
			public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) {
				opened[0] = parameters;
				return 7L;
			}

			@Override
			public void configure(long handle, float[] parameters) {
				configured[0] = parameters;
			}
		};
		float[] parameters = NativeParameters.of(RecognitionSettings.defaults());
		double[] tuning = NativeParameters.tuning(RecognitionSettings.defaults());
		assertEquals(7L, today.open("in", 0, 128, 0L, parameters, tuning), "the new open calls today's");
		assertSame(parameters, opened[0]);
		today.configureTuning(7L, tuning);
		assertNull(configured[0], "configureTuning does nothing by default");
	}
}
