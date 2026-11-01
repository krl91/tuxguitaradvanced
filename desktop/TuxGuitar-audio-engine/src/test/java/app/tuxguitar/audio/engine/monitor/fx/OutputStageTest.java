package app.tuxguitar.audio.engine.monitor.fx;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The last stage before the output: volume, fade in and out, limiter, clamp (spec 022 FR-010, SC-004; task T006). */
public class OutputStageTest {

	private static final int RATE = 48000;
	private static final int BLOCK = 128;

	private static OutputStage started(double volume) {
		OutputStage stage = new OutputStage(RATE, BLOCK);
		stage.setVolume(volume);
		stage.start();
		return stage;
	}

	@Test
	public void aFullScaleSquareAtTheLoudestNeverPassesTheCeiling() {
		OutputStage stage = started(1.0);
		float[] block = new float[BLOCK];
		double peakAfterFade = 0;
		for( int b = 0 ; b < RATE * 2 / BLOCK ; b++ ) {
			for( int i = 0 ; i < BLOCK ; i++ ) {
				int n = b * BLOCK + i;
				block[i] = ((n / 50) % 2 == 0) ? 1.0f : -1.0f;
				// an input louder than full scale, as a chain at its loudest can give
				block[i] *= 4f;
			}
			stage.process(block, BLOCK);
			for( int i = 0 ; i < BLOCK ; i++ ) {
				Assertions.assertTrue(Math.abs(block[i]) <= 1.0f, "sample above 0 dBFS: " + block[i]);
				if( b * BLOCK > RATE ) {
					peakAfterFade = Math.max(peakAfterFade, Math.abs(block[i]));
				}
			}
		}
		double peakDb = 20 * Math.log10(peakAfterFade);
		Assertions.assertEquals(-1.0, peakDb, 0.1, "peaks held at -1 dBFS");
	}

	@Test
	public void startRisesFromSilenceToTheSetLevelInHalfASecondWithoutAStep() {
		OutputStage stage = started(1.0);
		float[] block = new float[BLOCK];
		double previous = 0;
		double maxStep = 0;
		int reached = -1;
		for( int b = 0 ; b < RATE / BLOCK ; b++ ) {
			java.util.Arrays.fill(block, 0.5f);
			stage.process(block, BLOCK);
			for( int i = 0 ; i < BLOCK ; i++ ) {
				maxStep = Math.max(maxStep, Math.abs(block[i] - previous));
				previous = block[i];
				if( reached < 0 && block[i] >= 0.5f * 0.999f ) {
					reached = b * BLOCK + i;
				}
			}
		}
		Assertions.assertTrue(reached > 0, "never reached the set level");
		Assertions.assertEquals(0.5, reached / (double) RATE, 0.05, "time to reach the set level");
		Assertions.assertTrue(maxStep < 1e-3, "a step of " + maxStep);
	}

	@Test
	public void stopFadesToSilenceInTwentyMilliseconds() {
		OutputStage stage = started(1.0);
		float[] block = new float[BLOCK];
		for( int b = 0 ; b < RATE / BLOCK ; b++ ) {
			java.util.Arrays.fill(block, 0.5f);
			stage.process(block, BLOCK);
		}
		Assertions.assertFalse(stage.isSilent());
		stage.stop();
		int silentAt = -1;
		for( int b = 0 ; b < 20 && silentAt < 0 ; b++ ) {
			java.util.Arrays.fill(block, 0.5f);
			stage.process(block, BLOCK);
			for( int i = 0 ; i < BLOCK ; i++ ) {
				if( block[i] == 0f ) {
					silentAt = b * BLOCK + i;
					break;
				}
			}
		}
		Assertions.assertEquals(20.0, silentAt * 1000.0 / RATE, 2.0, "fade-out time in ms");
		Assertions.assertTrue(stage.isSilent());
		java.util.Arrays.fill(block, 0.5f);
		stage.process(block, BLOCK);
		for( float sample : block ) {
			Assertions.assertEquals(0f, sample);
		}
	}

	@Test
	public void aVolumeChangeRampsWithinOneBlock() {
		OutputStage stage = started(1.0);
		float[] block = new float[BLOCK];
		for( int b = 0 ; b < RATE / BLOCK ; b++ ) {
			java.util.Arrays.fill(block, 0.5f);
			stage.process(block, BLOCK);
		}
		stage.setVolume(0.5);
		java.util.Arrays.fill(block, 0.5f);
		stage.process(block, BLOCK);
		double maxStep = Math.abs(block[0] - 0.5);
		for( int i = 1 ; i < BLOCK ; i++ ) {
			maxStep = Math.max(maxStep, Math.abs(block[i] - block[i - 1]));
		}
		Assertions.assertEquals(0.25, block[BLOCK - 1], 1e-4, "the new volume is reached at the end of the block");
		Assertions.assertTrue(maxStep <= 0.25 / BLOCK * 1.5, "a step of " + maxStep);
		java.util.Arrays.fill(block, 0.5f);
		stage.process(block, BLOCK);
		Assertions.assertEquals(0.25, block[0], 1e-4);
	}
}
