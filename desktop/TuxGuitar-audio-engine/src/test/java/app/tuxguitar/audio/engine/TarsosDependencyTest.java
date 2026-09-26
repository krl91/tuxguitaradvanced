package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.PitchDetectionResult;

/**
 * Checks that the TarsosDSP dependency resolves, compiles and runs in this module
 * (48 kHz, 2048-sample window, as validated in research.md "S3").
 */
public class TarsosDependencyTest {

	@Test
	public void fastYinDetectsA4Sine() {
		float sampleRate = 48000f;
		float[] window = new float[2048];
		for (int i = 0; i < window.length; i++) {
			window[i] = (float) (0.5 * Math.sin(2 * Math.PI * 440.0 * i / sampleRate));
		}

		PitchDetectionResult result = new FastYin(sampleRate, window.length).getPitch(window);

		assertTrue(result.isPitched());
		assertEquals(440.0, result.getPitch(), 1.0);
	}
}
