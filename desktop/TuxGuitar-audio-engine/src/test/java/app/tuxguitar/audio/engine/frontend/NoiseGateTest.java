package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class NoiseGateTest {

	private static final int RATE = 48000;
	private static final int HOP = 256;

	private static float[] hop(float[] signal, int index) {
		float[] hop = new float[HOP];
		System.arraycopy(signal, index * HOP, hop, 0, HOP);
		return hop;
	}

	private static double rms(float[] samples) {
		return NoiseEstimator.rms(samples, samples.length);
	}

	/** Runs noise alone for the given number of hops through a fresh gate and returns it. */
	private static NoiseGate warmedUp(NoiseEstimator estimator, float[] noise, int hops) {
		NoiseGate gate = new NoiseGate(estimator);
		for( int h = 0 ; h < hops ; h ++ ) {
			gate.process(hop(noise, h), HOP);
		}
		return gate;
	}

	@Test
	public void backgroundNoiseAloneIsReducedOnceTheEstimateIsTrusted() {
		float[] noise = SyntheticSignals.noise(RATE * 3, 0.004, 5L);
		NoiseEstimator estimator = new NoiseEstimator(RATE, HOP);
		NoiseGate gate = warmedUp(estimator, noise, NoiseEstimator.TRUSTED_AFTER_HOPS + 20);
		assertTrue(estimator.isTrusted());
		double in = 0.0;
		double out = 0.0;
		int start = NoiseEstimator.TRUSTED_AFTER_HOPS + 20;
		for( int h = start ; h < start + 60 ; h ++ ) {
			float[] block = hop(noise, h);
			in += rms(block);
			gate.process(block, HOP);
			out += rms(block);
		}
		// at least 15 dB lower (the reduction is 20 dB)
		assertTrue(out < in * 0.18, "in " + in + " out " + out);
		assertFalse(gate.isOpen());
	}

	@Test
	public void theStartOfAPluckIsNeverAttenuated() {
		float[] noise = SyntheticSignals.noise(RATE * 3, 0.004, 5L);
		NoiseEstimator estimator = new NoiseEstimator(RATE, HOP);
		int warm = NoiseEstimator.TRUSTED_AFTER_HOPS + 20;
		NoiseGate gate = warmedUp(estimator, noise, warm);
		float[] pluck = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(52), RATE, RATE, 9L);
		// the first 50 ms (about 9 hops) of the pluck, mixed over the same noise, must come out as they went in
		for( int h = 0 ; h < 9 ; h ++ ) {
			float[] block = hop(noise, warm + h);
			for( int i = 0 ; i < HOP ; i ++ ) {
				block[i] += 0.5f * pluck[h * HOP + i];
			}
			float[] original = block.clone();
			gate.process(block, HOP);
			assertArrayEquals(original, block, "hop " + h);
		}
	}

	@Test
	public void aDecayingNoteIsKeptWhileItStaysAboveTheNoise() {
		float[] noise = SyntheticSignals.noise(RATE * 4, 0.004, 5L);
		NoiseEstimator estimator = new NoiseEstimator(RATE, HOP);
		int warm = NoiseEstimator.TRUSTED_AFTER_HOPS + 20;
		NoiseGate gate = warmedUp(estimator, noise, warm);
		float[] pluck = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(45), RATE, RATE * 2, 7L);
		int kept = 0;
		for( int h = 0 ; h < 150 ; h ++ ) {
			float[] block = hop(noise, warm + h);
			for( int i = 0 ; i < HOP ; i ++ ) {
				block[i] += 0.6f * pluck[h * HOP + i];
			}
			float[] original = block.clone();
			double level = rms(block);
			gate.process(block, HOP);
			double floor = estimator.getFloorRms();
			if( level > floor * Math.pow(10.0, NoiseGate.CLOSE_MARGIN_DB / 20.0) * 1.05 ) {
				assertArrayEquals(original, block, "hop " + h + " level " + level + " floor " + floor);
				kept ++;
			}
		}
		assertTrue(kept > 100, "kept " + kept);
	}

	@Test
	public void theGateHoldsBeforeClosingAndDoesNotChatter() {
		float[] noise = SyntheticSignals.noise(RATE * 3, 0.004, 5L);
		NoiseEstimator estimator = new NoiseEstimator(RATE, HOP);
		int warm = NoiseEstimator.TRUSTED_AFTER_HOPS + 20;
		NoiseGate gate = warmedUp(estimator, noise, warm);
		float[] loud = SyntheticSignals.sine(220.0, RATE, HOP, 0.3);
		gate.process(loud.clone(), HOP);
		assertTrue(gate.isOpen());
		// quiet again: stays open for the hold time, then closes
		int openHops = 0;
		for( int h = 0 ; h < 40 ; h ++ ) {
			gate.process(hop(noise, warm + 1 + h), HOP);
			if( gate.isOpen() ) {
				openHops ++;
			}
		}
		assertTrue(openHops >= NoiseGate.HOLD_HOPS - 2 && openHops <= NoiseGate.HOLD_HOPS + 2, "open for " + openHops);
		assertFalse(gate.isOpen());
	}

	@Test
	public void theEstimateFollowsAChangeOfNoiseLevel() {
		NoiseEstimator estimator = new NoiseEstimator(RATE, HOP);
		for( int h = 0 ; h < 300 ; h ++ ) {
			estimator.update(0.004);
		}
		double before = estimator.getFloorRms();
		assertEquals(0.004, before, 1e-4);
		for( int h = 0 ; h < 300 ; h ++ ) {
			estimator.update(0.001);
		}
		assertEquals(0.001, estimator.getFloorRms(), 2e-4);
		// a long sustained note does not become the floor: it rises by 0.5 dB per second at most
		double base = estimator.getFloorRms();
		for( int h = 0 ; h < 375 ; h ++ ) { // 2 seconds
			estimator.update(0.5);
		}
		assertTrue(estimator.getFloorRms() < base * Math.pow(10.0, 1.2 / 20.0));
	}

	@Test
	public void theLevelCheckCanSeedTheEstimate() {
		NoiseEstimator estimator = new NoiseEstimator(RATE, HOP);
		assertFalse(estimator.isTrusted());
		estimator.seed(0.002);
		assertTrue(estimator.isTrusted());
		assertEquals(NoiseEstimator.Source.LEVEL_CHECK, estimator.getSource());
		assertEquals(0.002, estimator.getFloorRms(), 1e-12);
	}
}
