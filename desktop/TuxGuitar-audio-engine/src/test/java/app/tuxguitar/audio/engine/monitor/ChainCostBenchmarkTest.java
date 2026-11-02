package app.tuxguitar.audio.engine.monitor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The cost of the full chain (spec 022 research R4, task T034): every pedal on, the high-gain amp at 4× oversampling, the cabinet, over 60 s
 * of a guitar-like signal at {@link MonitorEngine#BLOCK_FRAMES}. Reports the mean and the 99th percentile of the time per block as a share
 * of the block's duration; the goal is under 25 %. Excluded by default (benchmark tag).
 */
@Tag("benchmark")
public class ChainCostBenchmarkTest {

	private static final int RATE = 48000;

	static ChainSettings heaviest() {
		ChainSettings full = SmoothChangesTest.full();
		return full.withAmp(new AmpSettings(AmpSettings.Character.HIGH_GAIN, 8, 5, 5, 5, 6, true));
	}

	@Test
	public void theFullChainCostsUnderAQuarterOfTheBlock() throws Exception {
		int block = MonitorEngine.BLOCK_FRAMES;
		Chain chain = new Chain();
		chain.prepare(RATE, block);
		chain.apply(heaviest());
		int blocks = 60 * RATE / block;
		long[] nanos = new long[blocks];
		float[] samples = new float[block];
		java.util.Random random = new java.util.Random(3);
		for( int pass = 0 ; pass < 2 ; pass++ ) {
			for( int b = 0 ; b < blocks ; b++ ) {
				for( int i = 0 ; i < block ; i++ ) {
					int t = b * block + i;
					double decay = Math.exp(-(t % RATE) / (0.4 * RATE));
					samples[i] = (float) (0.3 * decay * Math.sin(2 * Math.PI * 196 * t / RATE) + 0.001 * random.nextGaussian());
				}
				long began = System.nanoTime();
				chain.process(samples, block);
				nanos[b] = System.nanoTime() - began;
			}
		}
		double blockNanos = block * 1e9 / RATE;
		double mean = Arrays.stream(nanos).average().orElse(0) / blockNanos * 100;
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		double p99 = sorted[(int) (sorted.length * 0.99)] / blockNanos * 100;
		String row = String.format(Locale.ROOT, "| %d | %d | %.1f | %.1f |", RATE, block, mean, p99);
		System.out.println("chain cost " + row);
		Path out = Paths.get("../../specs/022-guitar-amp-effects/benchmark/chain-cost.md");
		Files.createDirectories(out.getParent());
		Files.write(out, ("# Chain cost (spec 022, task T034)\n\n- Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + ' '
				+ System.getProperty("os.arch") + "\n- Every pedal on, high-gain amp at 4x oversampling, cabinet on; 60 s after a 60 s warm-up.\n\n"
				+ "| Rate (Hz) | Block (frames) | Mean (% of block) | 99th percentile (% of block) |\n|---|---|---|---|\n" + row + "\n")
				.getBytes(StandardCharsets.UTF_8));
		Assertions.assertTrue(mean < 25, "mean " + mean + " % of the block");
	}
}
