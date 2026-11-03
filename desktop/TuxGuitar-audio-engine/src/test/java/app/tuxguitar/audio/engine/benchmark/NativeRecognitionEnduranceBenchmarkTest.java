package app.tuxguitar.audio.engine.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.backend.NativeBackend;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeParameters;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Live native recognition on the system's default input for ten minutes, spectrum on and attack candidates set (spec 023, T057, SC-005, research R9).
 * Capture only: nothing is played. Reports the dropped blocks (goal: none) and the median and 99th percentile of one hop's analysis cost (goal: under
 * 10 % of a hop at the 99th percentile) into {@code endurance.md} and {@code cost.md} of the spec's benchmark folder, and fails above those limits.
 * Excluded by default (benchmark tag); {@code -Dendurance.minutes=<n>} shortens it.
 */
@Tag("benchmark")
public class NativeRecognitionEnduranceBenchmarkTest {

	private static final Path REPORTS = Paths.get("../../specs/023-native-recognition-engine/benchmark");

	@Test
	public void tenMinutesOfLiveNativeRecognitionDropNothingAndCostUnderATenthOfAHop() throws Exception {
		Assumptions.assumeTrue(NativeRecognitionLibrary.load() == null, "the native library is not built");
		double minutes = Double.parseDouble(System.getProperty("endurance.minutes", "10"));
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		NativeBackend backend = new NativeBackend(context, CurrentInputSelection.getInstance(context), NativeRecognitionLibrary.Api.JNI,
				() -> NativeParameters.of(RecognitionSettings.defaults()));
		AtomicLong frames = new AtomicLong();
		backend.subscribe(frame -> frames.incrementAndGet());
		backend.setSpectrumEnabled(true);
		backend.setAttackCandidates(new int[] { 40, 45, 47, 50, 52, 55, 57, 59, 64 });
		float[] stats;
		float rate;
		try {
			backend.open();
			backend.acquire("endurance");
			rate = backend.getFormat().getSampleRate();
			Thread.sleep(Math.round(minutes * 60_000.0));
			stats = backend.stats();
		} finally {
			backend.release("endurance");
			backend.close();
			TGThreadManager.getInstance(context).dispose();
		}
		long dropped = (long) stats[0];
		double medianMs = stats[1] / 1e6;
		double p99Ms = stats[2] / 1e6;
		double hopMs = NativeBackend.HOP_SIZE * 1000.0 / rate;
		String date = LocalDate.now().toString();
		Files.createDirectories(REPORTS);
		Files.write(REPORTS.resolve("endurance.md"), String.format(Locale.ROOT,
				"# Native recognition endurance (T057, SC-005)%n%nDate: %s. Default input at %.0f Hz, spectrum on, %d attack candidates, %.1f minutes.%n%n"
						+ "| Frames | Dropped blocks | Goal |%n|---|---|---|%n| %d | %d | 0 |%n",
				date, rate, 9, minutes, frames.get(), dropped).getBytes(StandardCharsets.UTF_8));
		Files.write(REPORTS.resolve("cost.md"), String.format(Locale.ROOT,
				"# Native analysis cost per hop (T057, R9)%n%nDate: %s. Default input at %.0f Hz, hop of %d samples = %.3f ms.%n%n"
						+ "| Median | 99th percentile | Limit (10 %% of a hop) |%n|---|---|---|%n| %.3f ms | %.3f ms | %.3f ms |%n%n"
						+ "The percentiles come from a histogram of 1 µs bins (the middle of the bin).%n",
				date, rate, NativeBackend.HOP_SIZE, hopMs, medianMs, p99Ms, hopMs / 10.0).getBytes(StandardCharsets.UTF_8));
		Assertions.assertTrue(frames.get() > 0, "frames arrived");
		Assertions.assertEquals(0L, dropped, "dropped blocks");
		Assertions.assertTrue(p99Ms < hopMs / 10.0, "99th percentile " + p99Ms + " ms, limit " + hopMs / 10.0 + " ms");
	}
}
