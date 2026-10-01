package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class InputGainTest {

	@Test
	public void aNeutralGainLeavesTheSamplesBitIdentical() {
		float[] original = SyntheticSignals.noise(256, 0.3, 11L);
		float[] hop = original.clone();
		new InputGain().process(hop, hop.length);
		assertArrayEquals(original, hop);
	}

	@Test
	public void theGainMultipliesTheSamples() {
		InputGain gain = new InputGain();
		gain.setMultiplier(2.0);
		float[] hop = { 0.1f, -0.2f, 0.0f };
		gain.process(hop, hop.length);
		assertArrayEquals(new float[] { 0.2f, -0.4f, 0.0f }, hop, 1e-7f);
		assertFalse(gain.isNeutral());
	}

	@Test
	public void valuesOutsideTheRangeAreLimitedAndBadOnesAreNeutral() {
		InputGain gain = new InputGain();
		assertEquals(InputGain.MAX_MULTIPLIER, gain.setMultiplier(100.0), 0.0);
		assertEquals(InputGain.MIN_MULTIPLIER, gain.setMultiplier(0.0), 0.0);
		assertEquals(InputGain.MIN_MULTIPLIER, gain.setMultiplier(-3.0), 0.0);
		assertEquals(InputGain.NEUTRAL, gain.setMultiplier(Double.NaN), 0.0);
	}

	@Test
	public void resetReturnsToNeutralAndDecibelsConvertBothWays() {
		InputGain gain = new InputGain();
		gain.setMultiplier(4.0);
		gain.resetToNeutral();
		assertTrue(gain.isNeutral());
		assertEquals(6.0206, InputGain.toDb(2.0), 1e-3);
		assertEquals(2.0, InputGain.fromDb(InputGain.toDb(2.0)), 1e-9);
		assertEquals(0, gain.latencyHops());
	}
}
