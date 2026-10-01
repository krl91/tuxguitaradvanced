package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class HighPassTest {

	private static final int RATE = 48000;

	private static double gainDb(double frequencyHz) {
		HighPass filter = new HighPass(RATE);
		float[] signal = SyntheticSignals.sine(frequencyHz, RATE, RATE, 0.5);
		float[] hop = new float[256];
		double before = 0.0;
		double after = 0.0;
		for( int start = 0 ; start + 256 <= signal.length ; start += 256 ) {
			System.arraycopy(signal, start, hop, 0, 256);
			filter.process(hop, 256);
			if( start >= RATE / 2 ) {
				for( int i = 0 ; i < 256 ; i ++ ) {
					before += signal[start + i] * signal[start + i];
					after += hop[i] * hop[i];
				}
			}
		}
		return 10.0 * Math.log10(after / before);
	}

	@Test
	public void theLowestGuitarStringsPassAlmostUntouched() {
		// low E in standard tuning and drop D
		assertTrue(gainDb(82.4) > -0.5, "82 Hz: " + gainDb(82.4));
		assertTrue(gainDb(73.4) > -0.5, "73 Hz: " + gainDb(73.4));
		assertTrue(gainDb(440.0) > -0.01);
	}

	@Test
	public void rumbleAndDirectCurrentAreCut() {
		assertTrue(gainDb(10.0) < -20.0, "10 Hz: " + gainDb(10.0));
		assertTrue(gainDb(40.0) < -2.5 && gainDb(40.0) > -3.5, "corner: " + gainDb(40.0));
		HighPass filter = new HighPass(RATE);
		float[] dc = new float[256];
		java.util.Arrays.fill(dc, 0.3f);
		for( int i = 0 ; i < 400 ; i ++ ) {
			java.util.Arrays.fill(dc, 0.3f);
			filter.process(dc, 256);
		}
		assertEquals(0.0, dc[255], 1e-3);
	}

	@Test
	public void resetClearsTheHistory() {
		HighPass filter = new HighPass(RATE);
		float[] hop = SyntheticSignals.sine(100.0, RATE, 256, 0.5);
		filter.process(hop, 256);
		filter.reset();
		float[] silence = new float[256];
		filter.process(silence, 256);
		for( float sample : silence ) {
			assertEquals(0.0f, sample, 0.0f);
		}
	}
}
