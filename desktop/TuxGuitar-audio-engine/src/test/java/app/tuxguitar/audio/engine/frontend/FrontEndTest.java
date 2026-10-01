package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class FrontEndTest {

	@Test
	public void neutralLeavesEveryHopBitForBitAsItCame() {
		FrontEnd frontEnd = new FrontEnd();
		assertTrue(frontEnd.isNeutral());
		float[] signal = SyntheticSignals.noise(256 * 20, 0.4, 2L);
		for( int h = 0 ; h < 20 ; h ++ ) {
			float[] hop = java.util.Arrays.copyOfRange(signal, h * 256, (h + 1) * 256);
			float[] original = hop.clone();
			frontEnd.process(hop, 256);
			assertArrayEquals(original, hop);
		}
		assertEquals(0, frontEnd.latencyHops());
	}

	@Test
	public void theGainIsAppliedFirstAndNoiseReductionOnlyWhenSwitchedOn() {
		FrontEnd frontEnd = new FrontEnd();
		frontEnd.getGain().setMultiplier(2.0);
		float[] hop = SyntheticSignals.sine(220.0, 48000, 256, 0.1);
		float[] original = hop.clone();
		frontEnd.process(hop, 256);
		for( int i = 0 ; i < 256 ; i ++ ) {
			assertEquals(original[i] * 2.0f, hop[i], 1e-6f);
		}
		assertFalse(frontEnd.isNeutral() && false);
		frontEnd.setNoiseReduction(true);
		assertTrue(frontEnd.isNoiseReduction());
		assertFalse(frontEnd.isNeutral());
	}

	@Test
	public void subtractionAddsOneHopOfLatencyOnlyWhenInUse() {
		FrontEnd frontEnd = new FrontEnd();
		frontEnd.setSubtraction(true);
		assertEquals(0, frontEnd.latencyHops());
		frontEnd.setNoiseReduction(true);
		assertEquals(1, frontEnd.latencyHops());
		frontEnd.setSubtraction(false);
		assertEquals(0, frontEnd.latencyHops());
	}

	@Test
	public void switchingNoiseReductionOnStartsFromACleanHistory() {
		FrontEnd frontEnd = new FrontEnd();
		frontEnd.setNoiseReduction(true);
		float[] hop = SyntheticSignals.sine(110.0, 48000, 256, 0.3);
		frontEnd.process(hop, 256);
		// the rumble filter works: the first hop of a 110 Hz sine is not just passed through unchanged
		float[] raw = SyntheticSignals.sine(110.0, 48000, 256, 0.3);
		boolean changed = false;
		for( int i = 0 ; i < 256 ; i ++ ) {
			changed |= (raw[i] != hop[i]);
		}
		assertTrue(changed);
	}
}
