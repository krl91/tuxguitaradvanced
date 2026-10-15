package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/** Spec 013 (T028): the measures shown at the quality question, and the refusals (FR-004, FR-005); the noise is shown, never a cause of refusal. */
public class RecordingQualityTest {

	private static final int RATE = 48000;

	private static float[] plucksOverNoise(double noiseAmplitude, double[] seconds, double length) {
		int[] onsets = new int[seconds.length];
		for (int i = 0; i < seconds.length; i++) {
			onsets[i] = (int) (seconds[i] * RATE);
		}
		float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, (int) (length * RATE), onsets, 7L);
		// the synthesiser's plucks have no fixed level: scaled so that the loudest sample is 0.5 (about -6 dBFS)
		float max = 0f;
		for (float value : signal) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < signal.length; i++) {
			signal[i] *= 0.5f / max;
		}
		return SyntheticSignals.mix(signal, SyntheticSignals.noise(signal.length, noiseAmplitude, 5L), 0);
	}

	@Test
	public void theMeasuresAreTheSoundsHeardTheHighestLevelTheBackgroundNoiseAndTheClipping() {
		float[] signal = plucksOverNoise(0.0005, new double[] { 2.0, 4.0, 6.0, 8.0 }, 11.0);
		RecordingQuality quality = RecordingQuality.measure(signal, RATE, 4, 4);
		assertEquals(4, quality.getHeard());
		assertEquals(4, quality.getExpected());
		assertTrue(quality.getPeakDbfs() < 0 && quality.getPeakDbfs() > -30, "highest level " + quality.getPeakDbfs());
		assertTrue(quality.getNoiseDbfs() < -55, "a faint hiss: " + quality.getNoiseDbfs());
		assertEquals(false, quality.isClipped());
		assertNull(quality.getRefusal());
	}

	@Test
	public void theNoiseWordComesFromTheDisplayBands() {
		assertEquals(RecordingQuality.NoiseWord.CALM, RecordingQuality.wordFor(-70.0));
		assertEquals(RecordingQuality.NoiseWord.CALM, RecordingQuality.wordFor(-60.5));
		assertEquals(RecordingQuality.NoiseWord.MEDIUM, RecordingQuality.wordFor(-60.0));
		assertEquals(RecordingQuality.NoiseWord.MEDIUM, RecordingQuality.wordFor(-45.0));
		assertEquals(RecordingQuality.NoiseWord.LOUD, RecordingQuality.wordFor(-44.9));
		assertEquals(RecordingQuality.NoiseWord.LOUD, RecordingQuality.wordFor(-20.0));
	}

	@Test
	public void aMeasuredHissIsNamedCalmMediumOrLoud() {
		double[] at = { 2.0, 4.0, 6.0, 8.0 };
		assertEquals(RecordingQuality.NoiseWord.CALM, RecordingQuality.measure(plucksOverNoise(0.0005, at, 11.0), RATE, 4, 4).getNoiseWord());
		assertEquals(RecordingQuality.NoiseWord.MEDIUM, RecordingQuality.measure(plucksOverNoise(0.005, at, 11.0), RATE, 4, 4).getNoiseWord());
		assertEquals(RecordingQuality.NoiseWord.LOUD, RecordingQuality.measure(plucksOverNoise(0.05, at, 11.0), RATE, 4, 4).getNoiseWord());
	}

	@Test
	public void noBandOfNoiseEverRefusesARecording() {
		for (double noise : new double[] { -90.0, -60.0, -50.0, -45.0, -30.0, -10.0 }) {
			RecordingQuality quality = new RecordingQuality(13, 13, -12.0, noise, false);
			assertNull(quality.getRefusal(), "noise " + noise + " dBFS is shown, not refused");
		}
	}

	@Test
	public void aWrongNumberOfSoundsIsRefusedWithTheNumbers() {
		RecordingQuality quality = new RecordingQuality(14, 13, -12.0, -70.0, false);
		assertEquals(SoundSegmenter.Refusal.WRONG_COUNT, quality.getRefusal());
		assertEquals(14, quality.getHeard());
		assertEquals(13, quality.getExpected());
	}

	@Test
	public void aSilentRecordingIsRefused() {
		assertEquals(SoundSegmenter.Refusal.SILENT, new RecordingQuality(0, 13, -80.0, -85.0, false).getRefusal());
		RecordingQuality measured = RecordingQuality.measure(SyntheticSignals.silence(5 * RATE), RATE, 0, 13);
		assertEquals(SoundSegmenter.Refusal.SILENT, measured.getRefusal());
	}

	@Test
	public void aClippedRecordingIsRefused() {
		assertEquals(SoundSegmenter.Refusal.CLIPPED, new RecordingQuality(13, 13, 0.0, -70.0, true).getRefusal());
		float[] loud = plucksOverNoise(0.0005, new double[] { 2.0, 4.0 }, 7.0);
		for (int i = 0; i < loud.length; i++) {
			loud[i] *= 60f;
		}
		assertEquals(true, RecordingQuality.measure(SyntheticSignals.hardClip(loud, 1.0), RATE, 2, 2).isClipped());
	}
}
