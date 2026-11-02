package app.tuxguitar.audio.engine.monitor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Ten minutes of the full chain (spec 022 SC-006, task T035) on a device paced by the real clock: one input block every block time, the
 * output thrown away. Reports the dropped and silent blocks after the start (goal: none) and the heap growth after a one-minute warm-up
 * (goal: under 1 MB). Excluded by default (benchmark tag); {@code -Dendurance.minutes=<n>} shortens it.
 */
@Tag("benchmark")
public class MonitorEnduranceBenchmarkTest {

	private static final float RATE = 48000f;

	/** One block per block time from the clock; at most two wait, as in the native rings. */
	private static final class PacedDevice implements MonitorDevice {
		private final long started = System.nanoTime();
		private long delivered;
		private long dropped;
		private volatile long worstLateNanos;
		private volatile long worstBetweenReadsNanos;
		private long lastReturn;

		@Override
		public Opened open(String inputName, int inputChannel, String outputName, int blockFrames) {
			long blockNanos = Math.round(blockFrames * 1e9 / RATE);
			return new Opened() {
				@Override
				public float sampleRate() {
					return RATE;
				}

				@Override
				public int read(float[] block, int n) {
					long now = System.nanoTime();
					if( PacedDevice.this.lastReturn != 0 ) {
						PacedDevice.this.worstBetweenReadsNanos = Math.max(PacedDevice.this.worstBetweenReadsNanos, now - PacedDevice.this.lastReturn);
					}
					long late = now - (PacedDevice.this.started + (PacedDevice.this.delivered + 1) * blockNanos);
					PacedDevice.this.worstLateNanos = Math.max(PacedDevice.this.worstLateNanos, late);
					int frames = deliver(block, n);
					PacedDevice.this.lastReturn = System.nanoTime();
					return frames;
				}

				private int deliver(float[] block, int n) {
					long due = (System.nanoTime() - PacedDevice.this.started) / blockNanos;
					if( due - PacedDevice.this.delivered > 2 ) {
						PacedDevice.this.dropped += due - PacedDevice.this.delivered - 2;
						PacedDevice.this.delivered = due - 2;
					}
					if( PacedDevice.this.delivered >= due ) {
						long wake = PacedDevice.this.started + (PacedDevice.this.delivered + 1) * blockNanos;
						long wait = wake - System.nanoTime();
						if( wait > 0 ) {
							java.util.concurrent.locks.LockSupport.parkNanos(wait);
						}
						if( System.nanoTime() < wake ) {
							return 0;
						}
					}
					long index = PacedDevice.this.delivered++;
					for( int i = 0 ; i < n ; i++ ) {
						long t = index * n + i;
						block[i] = (float) (0.3 * Math.exp(-(t % 48000) / 19200.0) * Math.sin(2 * Math.PI * 196 * t / RATE));
					}
					return n;
				}

				@Override
				public long droppedBlocks() {
					return PacedDevice.this.dropped;
				}

				@Override
				public void write(float[] left, float[] right, int n) {
				}

				@Override
				public int estimatedLatencyFrames() {
					return blockFrames;
				}

				@Override
				public void close() {
				}
			};
		}
	}

	private static long usedHeap() throws InterruptedException {
		Runtime runtime = Runtime.getRuntime();
		for( int i = 0 ; i < 3 ; i++ ) {
			System.gc();
			Thread.sleep(200);
		}
		return runtime.totalMemory() - runtime.freeMemory();
	}

	@Test
	public void tenMinutesWithoutADroppedBlockOrAGrowingHeap() throws Exception {
		int minutes = Integer.getInteger("endurance.minutes", 10);
		PacedDevice device = new PacedDevice();
		MonitorEngine engine = new MonitorEngine(device, MonitorEngine.BLOCK_FRAMES, System::nanoTime, runnable -> {
			Thread thread = new Thread(runnable, "monitor-endurance");
			thread.setDaemon(true);
			thread.setPriority(Thread.MAX_PRIORITY);
			thread.start();
		}, Runnable::run);
		engine.setChain(ChainCostBenchmarkTest.heaviest());
		engine.start("", -1, "");
		Thread.sleep(60_000);
		// the test's own garbage collections pause every thread: they are kept out of the counted span
		long heapAfterWarmUp = usedHeap();
		Thread.sleep(1_000);
		MonitorStats warm = engine.stats();
		device.worstLateNanos = 0;
		device.worstBetweenReadsNanos = 0;
		Thread.sleep(Math.max(0, minutes - 1) * 60_000L);
		MonitorStats end = engine.stats();
		double worstLate = device.worstLateNanos / 1e6;
		double worstBetween = device.worstBetweenReadsNanos / 1e6;
		long growth = usedHeap() - heapAfterWarmUp;
		engine.stop();
		long dropped = end.droppedBlocks() - warm.droppedBlocks();
		long silent = end.silentBlocks() - warm.silentBlocks();
		String row = String.format(Locale.ROOT, "| %d | %d | %d | %d | %.2f | %.1f | %.1f |", minutes, dropped, silent, end.overBudgetBlocks() - warm.overBudgetBlocks(),
				growth / 1048576.0, worstLate, worstBetween);
		System.out.println("endurance " + row);
		Path out = Paths.get("../../specs/022-guitar-amp-effects/benchmark/endurance.md");
		Files.createDirectories(out.getParent());
		Files.write(out, ("# Endurance (spec 022, task T035, SC-006)\n\n- Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name")
				+ ' ' + System.getProperty("os.arch") + "\n- The full chain of T034 on a device paced by the clock; counts after a one-minute warm-up.\n\n"
				+ "| Minutes | Dropped blocks | Silent reads | Over-budget blocks | Heap growth (MB) | Worst lateness of a read (ms) | Worst time between reads (ms) |\n|---|---|---|---|---|---|---|\n" + row + "\n")
				.getBytes(StandardCharsets.UTF_8));
		Assertions.assertEquals(0, dropped, "dropped blocks");
		Assertions.assertTrue(growth < 1048576, "heap growth " + growth + " bytes");
	}
}
