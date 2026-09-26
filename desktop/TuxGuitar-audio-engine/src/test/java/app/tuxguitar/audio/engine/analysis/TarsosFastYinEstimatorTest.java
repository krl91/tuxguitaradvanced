package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class TarsosFastYinEstimatorTest {

	private static final int SAMPLE_RATE = 48000;
	private static final int WINDOW_SIZE = 2048;

	private final TarsosFastYinEstimator estimator = new TarsosFastYinEstimator();

	@Test
	public void defaultsMatchProvisionalMvpBaseline() {
		assertEquals(WINDOW_SIZE, this.estimator.getWindowSize());
		assertEquals(256, this.estimator.getHopSize());
	}

	@Test
	public void detectsGuitarRangeNotesIncludingDropD() {
		int[] midiNotes = { 38, 40, 45, 50, 55, 59, 64, 71, 83 };
		for (int midi : midiNotes) {
			PitchEstimate estimate = this.estimator.estimate(SyntheticSignals.detunedSine(midi, 0.0, SAMPLE_RATE, WINDOW_SIZE, 0.7));
			assertTrue(estimate.isPitched(), "MIDI " + midi);
			assertEquals(midi, nearestMidi(estimate.getF0Hz()), "MIDI " + midi);
			assertEquals(0.0, centsFromMidi(estimate.getF0Hz(), midi), 2.0, "MIDI " + midi);
		}
	}

	@Test
	public void preservesPositiveAndNegativeCentOffsets() {
		for (double cents : new double[] { -20.0, 20.0 }) {
			PitchEstimate estimate = this.estimator.estimate(SyntheticSignals.detunedSine(69, cents, SAMPLE_RATE, WINDOW_SIZE, 0.7));
			assertTrue(estimate.isPitched(), cents + " cents");
			assertEquals(cents, centsFromMidi(estimate.getF0Hz(), 69), 2.0);
		}
	}

	@Test
	public void selectsFundamentalWhenSecondHarmonicIsStrongest() {
		assertFundamental(110.0, new double[] { 0.45, 0.55, 0.10 });
	}

	@Test
	public void selectsFundamentalWhenThirdHarmonicIsStrongest() {
		assertFundamental(146.832, new double[] { 0.45, 0.10, 0.55 });
	}

	@Test
	public void silenceAndDeterministicNoiseAreNotPitched() {
		PitchEstimate silence = this.estimator.estimate(SyntheticSignals.silence(WINDOW_SIZE));
		PitchEstimate noise = this.estimator.estimate(SyntheticSignals.noise(WINDOW_SIZE, 0.02, 90210L));

		assertFalse(silence.isPitched());
		assertTrue(Double.isNaN(silence.getF0Hz()));
		assertFalse(noise.isPitched());
		assertTrue(Double.isNaN(noise.getF0Hz()));
	}

	@Test
	public void validatesConfigurationAndWindowSize() {
		assertThrows(IllegalArgumentException.class, () -> new TarsosFastYinEstimator(0f, WINDOW_SIZE, 256));
		assertThrows(IllegalArgumentException.class, () -> new TarsosFastYinEstimator(SAMPLE_RATE, 1, 1));
		assertThrows(IllegalArgumentException.class, () -> new TarsosFastYinEstimator(SAMPLE_RATE, WINDOW_SIZE, 0));
		assertThrows(IllegalArgumentException.class, () -> this.estimator.estimate(new float[1024]));
	}

	private void assertFundamental(double fundamentalHz, double[] amplitudes) {
		PitchEstimate estimate = this.estimator.estimate(
				SyntheticSignals.harmonics(fundamentalHz, SAMPLE_RATE, WINDOW_SIZE, amplitudes));
		assertTrue(estimate.isPitched());
		assertEquals(fundamentalHz, estimate.getF0Hz(), 1.0);
	}

	private static int nearestMidi(double frequencyHz) {
		return (int) Math.round(69.0 + 12.0 * Math.log(frequencyHz / 440.0) / Math.log(2.0));
	}

	private static double centsFromMidi(double frequencyHz, int midi) {
		return 1200.0 * Math.log(frequencyHz / SyntheticSignals.midiFrequency(midi)) / Math.log(2.0);
	}
}
