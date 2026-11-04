package app.tuxguitar.app.tools.custom.practice.benchmark;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;

/**
 * From the strike to the hit shown in Arcade, per engine (spec 023, T071, FR-015, SC-006, research R7): T003's plucked tones (A2, D3, G3, B3, E4 in turn,
 * 120 ms, 1.5 s apart) played through the default output at the written times of a 20-note passage, into the real Arcade window with the live engine on the
 * built-in microphone, once with the Java engine and once with the native one. For each tone, from its first sample at the output (dated from the line's
 * position) to the end of the first paint that showed its event's hit ({@code ArcadeCanvas.FrameObserver}).
 *
 * It appends a section to {@code recognition-delay.md}, then fails if the native median exceeds T054's native median on the built-in microphone plus one
 * display frame (FR-015), or if the native median or 90th percentile is higher than the Java engine's (SC-006). The tones are audible.
 * {@code mvn test -P swt-gui-tests -Dtest=ArcadeHitDelayProbeTest -Dtuxguitar.native.library.dir=...}.
 */
@Tag("benchmark")
@Tag("swt-gui")
public class ArcadeHitDelayProbeTest {

	/** T054, the built-in microphone, native engine, pitch received median (benchmark/recognition-delay.md). */
	static final double T054_NATIVE_MEDIAN_MS = 120.2;
	private static final double[] TONES_HZ = { 110.0, 146.83, 196.0, 246.94, 329.63 };
	/** The open strings of these tones: A, D, G, B, high E. */
	private static final int[] STRINGS = { 5, 4, 3, 2, 1 };
	private static final int TONES = 20;

	@Test
	public void theHitIsShownSoonerWithTheNativeEngine() throws Exception {
		Assertions.assertNull(NativeRecognitionLibrary.load(), "the native library must be built (-Dtuxguitar.native.library.dir)");
		String[] engines = { "java", "native" };
		ArcadeLiveHarness.Result[] results = new ArcadeLiveHarness.Result[engines.length];
		ArcadeRefreshRateBenchmarkTest.withDisplay((display, factory) -> {
			for( int e = 0 ; e < engines.length ; e++ ) {
				// 40 quarters a minute: one note every 1.5 s, the first one on the third beat of the second measure
				SongFixture song = new SongFixture(2 + (TONES + 2) / 4 + 2);
				song.tempoChange(0, 40);
				List<ArcadeLiveHarness.Tone> tones = new ArrayList<ArcadeLiveHarness.Tone>();
				for( int i = 0 ; i < TONES ; i++ ) {
					int slot = i + 6;
					song.quarter(slot / 4, slot % 4, STRINGS[i % STRINGS.length], 0);
					tones.add(new ArcadeLiveHarness.Tone(i, TONES_HZ[i % TONES_HZ.length]));
				}
				results[e] = new ArcadeLiveHarness(display, factory).run(song.song, song.track(), song.sequence(), engines[e], tones, 120);
				System.out.println("arcade hit delay " + engines[e] + ": " + results[e].hitDelaysMs());
			}
		});
		ArcadeLiveHarness.Result javaRun = results[0];
		ArcadeLiveHarness.Result nativeRun = results[1];
		double frameMs = nativeRun.periodNanos() / 1e6;
		double[] java = sorted(javaRun.hitDelaysMs());
		double[] nat = sorted(nativeRun.hitDelaysMs());
		StringBuilder report = new StringBuilder();
		report.append("\n## ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append(", Arcade hit delay (T071)\n\n");
		report.append("- ").append(TONES).append(" plucked tones at the written times of a 20-note passage (40 quarters a minute), the real Arcade window, the live engine on ")
				.append(nativeRun.input).append(". From the tone's first sample at the output to the end of the first paint showing the hit.\n");
		report.append(String.format(Locale.ROOT, "- Display: toolkit %.0f Hz, measured %.1f Hz (a frame is %.2f ms).%n%n", nativeRun.toolkitRateHz, nativeRun.measuredRateHz(), frameMs));
		report.append("| Engine | Notice | Hits shown | Hit painted median / p90 (ms) | Ended |\n|---|---|---|---|---|\n");
		report.append(row(javaRun, java)).append('\n').append(row(nativeRun, nat)).append('\n');
		double limit = T054_NATIVE_MEDIAN_MS + frameMs;
		boolean found = java.length > 0 && nat.length > 0;
		double nativeMedian = found ? ArcadeLiveHarness.percentile(nat, 0.5) : Double.NaN;
		double nativeP90 = found ? ArcadeLiveHarness.percentile(nat, 0.9) : Double.NaN;
		double javaMedian = found ? ArcadeLiveHarness.percentile(java, 0.5) : Double.NaN;
		double javaP90 = found ? ArcadeLiveHarness.percentile(java, 0.9) : Double.NaN;
		report.append(String.format(Locale.ROOT, "%n- FR-015: native median %.1f ms, limit %.1f ms (T054's %.1f ms plus one frame).%n", nativeMedian, limit, T054_NATIVE_MEDIAN_MS));
		report.append(String.format(Locale.ROOT, "- SC-006: native %.1f / %.1f ms against Java %.1f / %.1f ms (median / p90).%n", nativeMedian, nativeP90, javaMedian, javaP90));
		ArcadeRefreshRateBenchmarkTest.appendTo("recognition-delay.md", "# Live recognition delay (spec 023, research R1)\n", report.toString());
		Assertions.assertEquals("none", nativeRun.noticeReason, "the native engine ran (no fallback)");
		Assertions.assertTrue(found, "no hit shown: java " + java.length + ", native " + nat.length);
		Assertions.assertTrue(nativeMedian <= limit, String.format(Locale.ROOT, "FR-015: native median %.1f ms over %.1f ms", nativeMedian, limit));
		Assertions.assertTrue(nativeMedian <= javaMedian, String.format(Locale.ROOT, "SC-006: native median %.1f ms over Java's %.1f ms", nativeMedian, javaMedian));
		Assertions.assertTrue(nativeP90 <= javaP90, String.format(Locale.ROOT, "SC-006: native p90 %.1f ms over Java's %.1f ms", nativeP90, javaP90));
	}

	private static String row(ArcadeLiveHarness.Result r, double[] delays) {
		String figures = delays.length == 0 ? "-"
				: String.format(Locale.ROOT, "%.1f / %.1f", ArcadeLiveHarness.percentile(delays, 0.5), ArcadeLiveHarness.percentile(delays, 0.9));
		return "| " + r.engineAsked + " | " + r.noticeReason + " | " + delays.length + " / " + r.hits.size() + " | " + figures + " | " + r.ended + " |";
	}

	private static double[] sorted(List<Double> values) {
		double[] sorted = new double[values.size()];
		for( int i = 0 ; i < sorted.length ; i++ ) {
			sorted[i] = values.get(i);
		}
		Arrays.sort(sorted);
		return sorted;
	}
}
