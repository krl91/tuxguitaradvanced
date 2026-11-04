package app.tuxguitar.app.tools.custom.practice.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.stress.StressScore;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.ui.UIFactory;

/**
 * Arcade at the screen's rate with the native engine live (spec 023, T070, FR-015, FR-011, SC-006, research R7): the stress score of spec 003 played for
 * {@code tuxguitar.arcade.refresh.seconds} (600 by default) in the real Arcade window, maximized, with the live engine on the built-in microphone, once with
 * the native engine and once with the Java engine. The display's rate is read through the toolkit, or else measured from the paints. A frame is on time when
 * it follows the previous one within one and a half display periods; a frame is held when its paint takes longer than a display period.
 *
 * It appends a dated section to {@code specs/023-native-recognition-engine/benchmark/arcade-refresh.md} (with the label of
 * {@code tuxguitar.arcade.refresh.label}, such as "before" or "after"), then fails under 99 % of the frames on time with the native engine, with any frame
 * held, or when the native run is worse than the Java one on the song's drift against the wall clock (by more than 5 ms) or on the paint's 95th percentile
 * (by more than 10 % and 0.2 ms). Silent: the MIDI channels play nothing and nothing is sent to the speakers. Needs the native library, a graphical session and the
 * microphone permission: {@code mvn test -P swt-gui-tests -Dtest=ArcadeRefreshRateBenchmarkTest -Dtuxguitar.native.library.dir=...}.
 */
@Tag("benchmark")
@Tag("swt-gui")
public class ArcadeRefreshRateBenchmarkTest {

	private static final double DRIFT_TOLERANCE_MS = 5.0;
	private static final double PAINT_TOLERANCE_SHARE = 0.10;
	private static final double PAINT_TOLERANCE_MS = 0.2;

	@Test
	public void arcadeHoldsTheScreensRateWithTheNativeEngineLive() throws Exception {
		Assertions.assertNull(NativeRecognitionLibrary.load(), "the native library must be built (-Dtuxguitar.native.library.dir)");
		int seconds = Integer.parseInt(setting("tuxguitar.arcade.refresh.seconds", "600"));
		String label = setting("tuxguitar.arcade.refresh.label", "run");
		ArcadeLiveHarness.Result[] results = new ArcadeLiveHarness.Result[2];
		String[] engines = { "native", "java" };
		withDisplay((display, factory) -> {
			for( int i = 0 ; i < engines.length ; i++ ) {
				StressScore score = new StressScore(seconds, 20261008L, 3.0);
				results[i] = new ArcadeLiveHarness(display, factory).run(score.fixture.song, score.guitar, score.fixture.sequence(), engines[i], null, seconds + 120);
				System.out.println("arcade refresh " + engines[i] + ": " + row(results[i]));
			}
		});
		ArcadeLiveHarness.Result nativeRun = results[0];
		ArcadeLiveHarness.Result javaRun = results[1];
		StringBuilder report = new StringBuilder();
		report.append("\n## ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(", ").append(label).append("\n\n");
		report.append("- Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append(", ")
				.append(Runtime.getRuntime().availableProcessors()).append(" processors.\n");
		report.append("- The stress score of spec 003 (").append(seconds).append(" s, 3 notes a second, then chords), the real Arcade window maximized, the MIDI channels silent, ")
				.append("the live engine on ").append(nativeRun.input).append(".\n");
		report.append("- On time: a frame within 1.5 display periods of the previous one. Held: a paint longer than one display period. Drift: the song's position against the wall clock over the run.\n\n");
		report.append("| Engine | Notice | Display rate, toolkit / measured (Hz) | Frames | On time | Display cycles missed | Held | Paint p50 / p95 / max (ms) | Drift (ms) | Dropped samples | Ended |\n");
		report.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
		report.append(row(nativeRun)).append('\n').append(row(javaRun)).append('\n');
		boolean driftOk = Math.abs(nativeRun.driftMs) <= Math.abs(javaRun.driftMs) + DRIFT_TOLERANCE_MS;
		double paintLimit = javaRun.paintPercentileMs(0.95) * (1.0 + PAINT_TOLERANCE_SHARE) + PAINT_TOLERANCE_MS;
		boolean paintOk = nativeRun.paintPercentileMs(0.95) <= paintLimit;
		report.append(String.format(Locale.ROOT, "%n- SC-006: native on time %.2f %% (needs 99 %%), %d held (needs 0).%n", 100.0 * nativeRun.onTimeShare(), nativeRun.held()));
		report.append(String.format(Locale.ROOT, "- FR-011: drift native %.1f ms against Java %.1f ms (%s); paint p95 native %.2f ms against Java %.2f ms, limit %.2f ms (%s).%n",
				nativeRun.driftMs, javaRun.driftMs, driftOk ? "no worse" : "WORSE", nativeRun.paintPercentileMs(0.95), javaRun.paintPercentileMs(0.95), paintLimit,
				paintOk ? "no worse" : "WORSE"));
		append(report.toString());
		Assertions.assertEquals("none", nativeRun.noticeReason, "the native engine ran (no fallback)");
		Assertions.assertTrue(nativeRun.onTimeShare() >= 0.99, String.format(Locale.ROOT, "on time %.2f %%", 100.0 * nativeRun.onTimeShare()));
		Assertions.assertEquals(0, nativeRun.held(), "frames held");
		Assertions.assertTrue(driftOk, "the song's drift is worse with the native engine");
		Assertions.assertTrue(paintOk, "the paint's 95th percentile is worse with the native engine");
	}

	private static String row(ArcadeLiveHarness.Result r) {
		return String.format(Locale.ROOT, "| %s | %s | %.0f / %.1f | %d | %.2f %% | %d | %d | %.2f / %.2f / %.2f | %.1f | %d | %s |", r.engineAsked, r.noticeReason, r.toolkitRateHz,
				r.measuredRateHz(), r.paintsInRun(), 100.0 * r.onTimeShare(), r.missedCycles(), r.held(), r.paintPercentileMs(0.5), r.paintPercentileMs(0.95),
				r.paintPercentileMs(1.0), r.driftMs, r.droppedSamples, r.ended);
	}

	static String setting(String name, String fallback) {
		String value = System.getProperty(name);
		if( value == null || value.isEmpty() ) {
			value = System.getenv(name.toUpperCase(Locale.ROOT).replace('.', '_'));
		}
		return value == null || value.isEmpty() ? fallback : value;
	}

	interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	static void withDisplay(DisplayTask task) throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			Class<?> environment = Class.forName("app.tuxguitar.ui.swt.SWTEnvironment");
			environment.getMethod("start", displayClass).invoke(environment.getMethod("getInstance").invoke(null), display);
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			task.run(display, factory);
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	static void appendTo(String file, String title, String section) throws Exception {
		Path repository = null;
		for( Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath() ; path != null ; path = path.getParent() ) {
			if( Files.isDirectory(path.resolve("specs/023-native-recognition-engine")) ) {
				repository = path;
				break;
			}
		}
		Assertions.assertNotNull(repository, "repository root not found from " + System.getProperty("user.dir"));
		Path output = repository.resolve("specs/023-native-recognition-engine/benchmark/" + file);
		Files.createDirectories(output.getParent());
		if( !Files.exists(output) ) {
			Files.write(output, title.getBytes(StandardCharsets.UTF_8));
		}
		Files.write(output, section.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
		System.out.println("report: " + output);
	}

	private static void append(String section) throws Exception {
		appendTo("arcade-refresh.md", "# Arcade at the screen's rate (spec 023, T070, T072, T073)\n", section);
	}
}
