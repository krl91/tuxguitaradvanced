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

import app.tuxguitar.audio.engine.monitor.fx.OutputStage;

/**
 * The cost of the engine's whole processing per block (spec 022 research R4 cost budget, task T072): the heaviest chain of {@link ChainCostBenchmarkTest},
 * then the output stage and the feedback guard, as the engine's loop runs them, over 60 s after a 60 s warm-up. The goal is under 25 % of the block; the
 * report is {@code benchmark/engine-cost.md} ({@code chain-cost.md} stays the chain alone). Excluded by default (benchmark tag).
 */
@Tag("benchmark")
public class EngineCostBenchmarkTest {

	private static final int RATE = 48000;

	@Test
	public void theChainTheOutputStageAndTheFeedbackGuardCostUnderAQuarterOfTheBlock() throws Exception {
		int block = MonitorEngine.BLOCK_FRAMES;
		Chain chain = new Chain();
		chain.prepare(RATE, block);
		chain.apply(ChainCostBenchmarkTest.heaviest());
		OutputStage stage = new OutputStage(RATE, block);
		stage.setVolume(0.7);
		stage.start();
		FeedbackGuard guard = new FeedbackGuard(RATE, block);
		int blocks = 60 * RATE / block;
		long[] nanos = new long[blocks];
		long[] guardNanos = new long[blocks];
		float[] samples = new float[block];
		java.util.Random random = new java.util.Random(3);
		boolean found = false;
		for( int pass = 0 ; pass < 2 ; pass++ ) {
			for( int b = 0 ; b < blocks ; b++ ) {
				for( int i = 0 ; i < block ; i++ ) {
					int t = b * block + i;
					double decay = Math.exp(-(t % RATE) / (0.4 * RATE));
					samples[i] = (float) (0.3 * decay * Math.sin(2 * Math.PI * 196 * t / RATE) + 0.001 * random.nextGaussian());
				}
				long began = System.nanoTime();
				chain.process(samples, block);
				stage.process(samples, block);
				long guarded = System.nanoTime();
				// as the engine: watched once faded in; a howl found would stop the guard's work and hide its cost
				if( stage.isFadedIn() && guard.process(samples, block) ) {
					found = true;
				}
				long ended = System.nanoTime();
				nanos[b] = ended - began;
				guardNanos[b] = ended - guarded;
			}
		}
		double blockNanos = block * 1e9 / RATE;
		double mean = Arrays.stream(nanos).average().orElse(0) / blockNanos * 100;
		long[] sorted = nanos.clone();
		Arrays.sort(sorted);
		double p99 = sorted[(int) (sorted.length * 0.99)] / blockNanos * 100;
		double guardMean = Arrays.stream(guardNanos).average().orElse(0) / blockNanos * 100;
		long[] guardSorted = guardNanos.clone();
		Arrays.sort(guardSorted);
		double guardP99 = guardSorted[(int) (guardSorted.length * 0.99)] / blockNanos * 100;
		String row = String.format(Locale.ROOT, "| %d | %d | %.1f | %.1f | %.1f | %.1f |", RATE, block, mean, p99, guardMean, guardP99);
		System.out.println("engine cost " + row);
		Path out = Paths.get("../../specs/022-guitar-amp-effects/benchmark/engine-cost.md");
		Files.createDirectories(out.getParent());
		Files.write(out, ("# Engine cost per block (spec 022, task T072)\n\n- Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name") + ' '
				+ System.getProperty("os.arch") + "\n- The heaviest chain (every pedal on, high-gain amp at 4x oversampling, cabinet on), then the output stage and the"
				+ " feedback guard (a 2048-point FFT every 10 ms); 60 s after a 60 s warm-up.\n\n"
				+ "| Rate (Hz) | Block (frames) | Whole, mean (% of block) | Whole, 99th percentile | Guard alone, mean | Guard alone, 99th percentile |\n"
				+ "|---|---|---|---|---|---|\n" + row + "\n").getBytes(StandardCharsets.UTF_8));
		Assertions.assertFalse(found, "the guitar-like signal is no howl, so the guard worked all along");
		Assertions.assertTrue(mean < 25, "mean " + mean + " % of the block");
	}
}
