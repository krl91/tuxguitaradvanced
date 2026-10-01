package app.tuxguitar.audio.engine.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

public class SpectralSubtractorTest {

	private static final int RATE = 48000;
	private static final int HOP = SpectralSubtractor.HOP;

	private static float[] run(SpectralSubtractor subtractor, float[] signal, boolean quiet) {
		float[] out = new float[signal.length];
		float[] hop = new float[HOP];
		subtractor.setQuiet(quiet);
		for( int start = 0 ; start + HOP <= signal.length ; start += HOP ) {
			System.arraycopy(signal, start, hop, 0, HOP);
			subtractor.process(hop, HOP);
			System.arraycopy(hop, 0, out, start, HOP);
		}
		return out;
	}

	private static double rms(float[] s, int from, int to) {
		double sum = 0.0;
		for( int i = from ; i < to ; i ++ ) {
			sum += s[i] * s[i];
		}
		return Math.sqrt(sum / (to - from));
	}

	@Test
	public void withoutAnyLearntNoiseTheSignalComesOutUnchangedOneHopLater() {
		float[] signal = SyntheticSignals.sine(220.0, RATE, RATE / 2, 0.3);
		float[] out = run(new SpectralSubtractor(), signal, false);
		int processed = (signal.length / HOP) * HOP; // only whole hops are processed
		for( int i = 3 * HOP ; i + HOP < processed ; i ++ ) {
			assertEquals(signal[i], out[i + HOP], 2e-4, "sample " + i);
		}
		assertEquals(1, new SpectralSubtractor().latencyHops());
	}

	@Test
	public void stationaryNoiseIsReducedOnceLearnt() {
		float[] noise = SyntheticSignals.noise(RATE * 2, 0.05, 3L);
		SpectralSubtractor subtractor = new SpectralSubtractor();
		float[] learning = run(subtractor, java.util.Arrays.copyOfRange(noise, 0, RATE), true);
		assertTrue(rms(learning, 0, RATE) > 0.0);
		float[] later = java.util.Arrays.copyOfRange(noise, RATE, 2 * RATE);
		float[] out = run(subtractor, later, false);
		double in = rms(later, HOP * 4, later.length - HOP * 4);
		double after = rms(out, HOP * 4, out.length - HOP * 4);
		assertTrue(after < 0.5 * in, "in " + in + " out " + after);
	}

	@Test
	public void aPluckOverTheLearntNoiseKeepsItsPitchEnergy() {
		float[] noise = SyntheticSignals.noise(RATE * 2, 0.02, 3L);
		SpectralSubtractor subtractor = new SpectralSubtractor();
		run(subtractor, java.util.Arrays.copyOfRange(noise, 0, RATE), true);
		float[] pluck = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(57), RATE, RATE, 4L);
		float[] mixed = java.util.Arrays.copyOfRange(noise, RATE, 2 * RATE);
		for( int i = 0 ; i < mixed.length ; i ++ ) {
			mixed[i] += 0.3f * pluck[i];
		}
		float[] out = run(subtractor, mixed, false);
		double frequency = SyntheticSignals.midiFrequency(57);
		double before = TechniqueSignals.energyAt(mixed, 4000, 20000, RATE, frequency);
		double after = TechniqueSignals.energyAt(out, 4000 + HOP, 20000 + HOP, RATE, frequency);
		assertTrue(after > 0.7 * before, "before " + before + " after " + after);
		// the noise away from the note is reduced more than the note itself
		double noiseBefore = TechniqueSignals.energyAt(mixed, 4000, 20000, RATE, 3100.0);
		double noiseAfter = TechniqueSignals.energyAt(out, 4000 + HOP, 20000 + HOP, RATE, 3100.0);
		assertTrue(noiseAfter / noiseBefore < after / before);
	}

	@Test
	public void onlyWholeHopsAreAccepted() {
		assertThrows(IllegalArgumentException.class, () -> new SpectralSubtractor().process(new float[100], 100));
	}
}
