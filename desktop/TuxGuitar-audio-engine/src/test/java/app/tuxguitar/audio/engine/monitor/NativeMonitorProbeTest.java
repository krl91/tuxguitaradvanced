package app.tuxguitar.audio.engine.monitor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The round trip through the native device (spec 022 T063, quickstart §1b): the same clicks as the Java Sound probe (T002), now through
 * {@link NativeMonitorDevice} at 64, 128 and 256 frames, 20 clicks 3 s apart per setting. The block written is silence apart from the clicks: the input is
 * never played back. Delay = the click's index in the input minus the first frame of the input block read in the same iteration. Each median is set beside
 * the device's estimate (both declared latencies and the buffers): the two should be within 3 ms.
 *
 * Excluded by default (benchmark tag); the clicks are audible. Run with
 * {@code -Dgroups=benchmark -DexcludedGroups= -Dtest=NativeMonitorProbeTest}; {@code -Dprobe.input=<name>} chooses the input (else the default) and
 * {@code -Dprobe.library=<file>} the library (else the crate's own release build).
 */
@Tag("benchmark")
public class NativeMonitorProbeTest {

	private static final int[] BLOCKS = { 64, 128, 256 };
	private static final int CLICKS = 20;
	private static final double CLICK_SECONDS = 3.0;
	private static final double CLICK_LENGTH_SECONDS = 0.001;

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

	@Test
	public void probe() throws Exception {
		String library = library();
		NativeMonitorDevice device = new NativeMonitorDevice(() -> {
			try {
				System.load(library);
				return null;
			} catch (UnsatisfiedLinkError error) {
				return error.getMessage();
			}
		});
		String input = System.getProperty("probe.input", "");
		StringBuilder report = new StringBuilder();
		report.append("# Native delay probe (spec 022, task T063, quickstart §1b)\n\n");
		report.append("- Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
		report.append("- Input: ").append(input.isEmpty() ? "the system's default input" : input).append("; output: the system's default output\n");
		report.append("- ").append(CLICKS).append(" clicks of 1 ms, 3 s apart, per setting; the input is never played back.\n");
		report.append("- Estimate: both devices' declared latencies, the input and output buffers and the output ring (one block each), as the window shows it.\n\n");
		report.append("| Block (frames) | Rate (Hz) | Clicks found | Median (ms) | Min (ms) | Max (ms) | Estimate (ms) | Median − estimate (ms) | Dropped blocks | Silent reads |\n");
		report.append("|---|---|---|---|---|---|---|---|---|---|\n");
		int found = 0;
		for (int block : BLOCKS) {
			String row = run(device, input, block);
			report.append(row).append('\n');
			System.out.println("native probe " + row);
			found++;
		}
		Path out = Paths.get("../../specs/022-guitar-amp-effects/benchmark/native-probe.md");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		Assertions.assertEquals(BLOCKS.length, found);
	}

	private static String run(NativeMonitorDevice device, String input, int block) throws MonitorDeviceException {
		MonitorDevice.Opened opened = device.open(input, -1, "", block);
		try {
			float rate = opened.sampleRate();
			int spacing = (int) Math.round(CLICK_SECONDS * rate);
			int clickFrames = (int) Math.round(CLICK_LENGTH_SECONDS * rate);
			int total = spacing * (CLICKS + 1);
			float[] captured = new float[total + block];
			float[] in = new float[block];
			float[] out = new float[block];
			float[] silence = new float[block];
			List<Integer> clickStarts = new ArrayList<Integer>();
			int position = 0;
			int silent = 0;
			int sinceClick = spacing / 2;
			while (position < total) {
				int frames = opened.read(in, block);
				if (frames <= 0) {
					silent++;
					if (position == 0) {
						// as the engine: silence only until the input starts, so that the output starts
						opened.write(silence, silence, block);
					}
					continue;
				}
				System.arraycopy(in, 0, captured, position, frames);
				Arrays.fill(out, 0f);
				if (sinceClick >= spacing && clickStarts.size() < CLICKS) {
					for (int i = 0; i < Math.min(clickFrames, block); i++) {
						out[i] = 0.8f;
					}
					clickStarts.add(position);
					sinceClick = 0;
				}
				opened.write(out, out, block);
				position += frames;
				sinceClick += frames;
			}
			List<Double> delays = new ArrayList<Double>();
			double noise = 0;
			for (int i = 0; i < spacing / 4 && i < position; i++) {
				noise = Math.max(noise, Math.abs(captured[i]));
			}
			double threshold = Math.max(0.02, 6 * noise);
			for (int index = 0; index < clickStarts.size(); index++) {
				int from = clickStarts.get(index);
				int to = Math.min(position, from + spacing / 2);
				for (int i = from; i < to; i++) {
					if (Math.abs(captured[i]) >= threshold) {
						delays.add((i - from) * 1000.0 / rate);
						break;
					}
				}
			}
			double estimate = opened.estimatedLatencyFrames() * 1000.0 / rate;
			Collections.sort(delays);
			String median = delays.isEmpty() ? "-" : format(delays.get(delays.size() / 2));
			String min = delays.isEmpty() ? "-" : format(delays.get(0));
			String max = delays.isEmpty() ? "-" : format(delays.get(delays.size() - 1));
			String gap = delays.isEmpty() ? "-" : format(delays.get(delays.size() / 2) - estimate);
			return "| " + block + " | " + Math.round(rate) + " | " + delays.size() + " / " + clickStarts.size() + " | " + median + " | " + min + " | " + max
					+ " | " + format(estimate) + " | " + gap + " | " + opened.droppedBlocks() + " | " + silent + " |";
		} finally {
			opened.close();
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
