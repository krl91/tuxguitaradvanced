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
 * The endurance run of T035 with the engine's thread promoted to a real-time audio thread (spec 022 SC-006, research R3 addendum, task T066): the same
 * paced device and full chain, the thread promoted through the crate's library at its first read, as {@link NativeMonitorDevice} does when it opens.
 * Reports the policy taken, the dropped and silent blocks after a one-minute warm-up (goal: none) and the heap growth (goal: under 1 MB). Excluded by
 * default (benchmark tag); {@code -Dendurance.minutes=<n>} shortens it, {@code -Dprobe.library=<file>} chooses the library.
 */
@Tag("benchmark")
public class RealtimeEnduranceBenchmarkTest {

	private static final float RATE = 48000f;

	private static String library() {
		String given = System.getProperty("probe.library");
		if (given != null) {
			return given;
		}
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
		String file = os.contains("mac") ? "libtuxguitar_monitor_native.dylib" : os.contains("win") ? "tuxguitar_monitor_native.dll" : "libtuxguitar_monitor_native.so";
		Path crate = Paths.get("../build-scripts/native-modules/tuxguitar-monitor-native/target");
		for (Path candidate : new Path[] { crate.resolve("release").resolve(file), crate.resolve("aarch64-apple-darwin/release").resolve(file),
				crate.resolve("x86_64-unknown-linux-gnu/release").resolve(file) }) {
			if (Files.exists(candidate)) {
				return candidate.toAbsolutePath().toString();
			}
		}
		return crate.resolve("release").resolve(file).toAbsolutePath().toString();
	}

	/** One block per block time from the clock; at most two wait, as in the native rings. The first read promotes the reading thread. */
	private static final class PacedDevice implements MonitorDevice {
		private final long started = System.nanoTime();
		private long delivered;
		private long dropped;
		private volatile String realtime;
		private volatile long worstBetweenReadsNanos;
		private long lastReturn;

		@Override
		public Opened open(String inputName, int inputChannel, String outputName, int blockFrames) {
			long blockNanos = Math.round(blockFrames * 1e9 / RATE);
			PacedDevice.this.realtime = NativeMonitorLibrary.promoteThread(blockFrames, RATE);
			return new Opened() {
				@Override
				public float sampleRate() {
					return RATE;
				}

				@Override
				public int read(float[] block, int n) {
					long now = System.nanoTime();
					if (PacedDevice.this.lastReturn != 0) {
						PacedDevice.this.worstBetweenReadsNanos = Math.max(PacedDevice.this.worstBetweenReadsNanos, now - PacedDevice.this.lastReturn);
					}
					int frames = deliver(block, n);
					PacedDevice.this.lastReturn = System.nanoTime();
					return frames;
				}

				private int deliver(float[] block, int n) {
					long due = (System.nanoTime() - PacedDevice.this.started) / blockNanos;
					if (due - PacedDevice.this.delivered > 2) {
						PacedDevice.this.dropped += due - PacedDevice.this.delivered - 2;
						PacedDevice.this.delivered = due - 2;
					}
					if (PacedDevice.this.delivered >= due) {
						long wake = PacedDevice.this.started + (PacedDevice.this.delivered + 1) * blockNanos;
						long wait = wake - System.nanoTime();
						if (wait > 0) {
							java.util.concurrent.locks.LockSupport.parkNanos(wait);
						}
						if (System.nanoTime() < wake) {
							return 0;
						}
					}
					long index = PacedDevice.this.delivered++;
					for (int i = 0; i < n; i++) {
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
		for (int i = 0; i < 3; i++) {
			System.gc();
			Thread.sleep(200);
		}
		return runtime.totalMemory() - runtime.freeMemory();
	}

	@Test
	public void tenMinutesOnARealtimeThreadWithoutADroppedBlock() throws Exception {
		System.load(library());
		int minutes = Integer.getInteger("endurance.minutes", 10);
		PacedDevice device = new PacedDevice();
		MonitorEngine engine = new MonitorEngine(device, MonitorEngine.BLOCK_FRAMES, System::nanoTime, runnable -> {
			Thread thread = new Thread(runnable, "monitor-endurance-realtime");
			thread.setDaemon(true);
			thread.start();
		}, Runnable::run);
		engine.setChain(ChainCostBenchmarkTest.heaviest());
		engine.start("", -1, "");
		Thread.sleep(60_000);
		// the test's own garbage collections pause every thread: they are kept out of the counted span
		long heapAfterWarmUp = usedHeap();
		Thread.sleep(1_000);
		MonitorStats warm = engine.stats();
		device.worstBetweenReadsNanos = 0;
		Thread.sleep(Math.max(0, minutes - 1) * 60_000L);
		MonitorStats end = engine.stats();
		double worstBetween = device.worstBetweenReadsNanos / 1e6;
		long growth = usedHeap() - heapAfterWarmUp;
		engine.stop();
		long dropped = end.droppedBlocks() - warm.droppedBlocks();
		long silent = end.silentBlocks() - warm.silentBlocks();
		String row = String.format(Locale.ROOT, "| %d | %s | %d | %d | %d | %.2f | %.1f |", minutes, device.realtime, dropped, silent,
				end.overBudgetBlocks() - warm.overBudgetBlocks(), growth / 1048576.0, worstBetween);
		System.out.println("endurance realtime " + row);
		Path out = Paths.get("../../specs/022-guitar-amp-effects/benchmark/endurance-realtime.md");
		Files.createDirectories(out.getParent());
		Files.write(out, ("# Endurance on a real-time thread (spec 022, task T066, SC-006)\n\n- Java " + System.getProperty("java.version") + ", "
				+ System.getProperty("os.name") + ' ' + System.getProperty("os.arch")
				+ "\n- The full chain of T034 on a device paced by the clock, the engine's thread promoted; counts after a one-minute warm-up.\n\n"
				+ "| Minutes | Thread | Dropped blocks | Silent reads | Over-budget blocks | Heap growth (MB) | Worst time between reads (ms) |\n"
				+ "|---|---|---|---|---|---|---|\n" + row + "\n").getBytes(StandardCharsets.UTF_8));
		Assertions.assertTrue(device.realtime != null && device.realtime.startsWith("real-time"), String.valueOf(device.realtime));
		Assertions.assertEquals(0, dropped, "dropped blocks");
		Assertions.assertTrue(growth < 1048576, "heap growth " + growth + " bytes");
	}
}
