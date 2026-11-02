package app.tuxguitar.audio.engine.monitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.fx.OutputStage;

/** Every ready-made sound stays quiet with a quiet input (spec 022 FR-011, US1/AC4; task T033): a −70 dBFS noise gives under −60 dBFS at the output. */
public class ReadyMadeSilenceTest {

	private static final int RATE = 48000;
	private static final int BLOCK = 128;

	@Test
	public void aQuietNoiseStaysUnderMinusSixtyThroughEveryReadyMadeSound() {
		for (ReadyMadeChains.Named named : ReadyMadeChains.all()) {
			Chain chain = new Chain();
			chain.prepare(RATE, BLOCK);
			chain.apply(named.chain());
			OutputStage stage = new OutputStage(RATE, BLOCK);
			stage.setVolume(0.7);
			stage.start();
			java.util.Random random = new java.util.Random(1);
			double rms = Math.pow(10, -70 / 20.0);
			double sum = 0;
			int counted = 0;
			float[] block = new float[BLOCK];
			for (int b = 0; b < 2 * RATE / BLOCK; b++) {
				for (int i = 0; i < BLOCK; i++) {
					block[i] = (float) (rms * random.nextGaussian());
				}
				chain.process(block, BLOCK);
				stage.process(block, BLOCK);
				if (b >= RATE / BLOCK) {
					for (float sample : block) {
						sum += (double) sample * sample;
					}
					counted += BLOCK;
				}
			}
			double db = 10 * Math.log10(Math.max(1e-30, sum / counted));
			Assertions.assertTrue(db < -60, named.name() + ": " + db + " dBFS");
		}
	}
}
