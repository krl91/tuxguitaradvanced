package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.RecognitionLoad;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * Baseline cost of the Arcade scene (spec 003 T042): the time to build a scene and to replay it through the renderer, for a dense passage, a chord-heavy
 * one and a passage with wide jumps of the camera, at every quality tier and at four sizes of the window, alone and while another thread loads the
 * processor with the recognition chain. Tag {@code benchmark}: not part of the normal build; the report is written to
 * {@code specs/003-arcade-mode/benchmark/}. The replay goes to a stand-in painter that does nothing (the real toolkit's cost is measured by the native
 * test T043), so what is reported here is the part of the cost that belongs to the scene and the renderer's walk over it. Nothing here selects a backend.
 */
@Tag("benchmark")
public class ArcadeRenderBenchmarkTest {

	private static final long MS = SceneFixture.MS;
	private static final long FRAME = ArcadeCanvas.FRAME_NANOS;
	private static final int FRAMES = 1800;

	/** The acceptance bound fixed by this task: the scene (build and replay walk) takes at most a quarter of the frame at its 99th percentile. */
	private static final double BOUND_P99_MS = 4.2;

	private static SceneFixture dense() {
		SongFixture song = new SongFixture(32);
		song.tempoChange(0, 400);
		for (int i = 0; i < 128; i++) {
			if (i % 3 == 0) {
				song.chord(i / 4, i % 4, new int[] { 6, 5, 4 }, new int[] { i % 12, 2 + i % 12, 2 + i % 12 });
			} else {
				song.quarter(i / 4, i % 4, 1 + i % 6, i % 15);
			}
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 300);
	}

	private static SceneFixture chordHeavy() {
		SongFixture song = new SongFixture(32);
		song.tempoChange(0, 300);
		for (int i = 0; i < 128; i++) {
			song.chord(i / 4, i % 4, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { i % 8, 2 + i % 8, 2 + i % 8, 1 + i % 8, i % 8, i % 8 });
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 300);
	}

	private static SceneFixture jumps() {
		SongFixture song = new SongFixture(32);
		song.tempoChange(0, 240);
		for (int i = 0; i < 128; i++) {
			song.quarter(i / 4, i % 4, 1 + i % 6, i % 2 == 0 ? 0 : 19 + (i % 3));
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 300);
	}

	private static UIPainter nullPainter() {
		return (UIPainter) Proxy.newProxyInstance(UIPainter.class.getClassLoader(), new Class<?>[] { UIPainter.class },
				(p, method, args) -> method.getReturnType() == float.class ? (Object) 10f : null);
	}

	private static UIFactory nullFactory() {
		return (UIFactory) Proxy.newProxyInstance(UIFactory.class.getClassLoader(), new Class<?>[] { UIFactory.class }, (p, method, args) -> {
			if (method.getName().equals("createColor")) {
				return Proxy.newProxyInstance(UIColor.class.getClassLoader(), new Class<?>[] { UIColor.class }, (q, m, a) -> null);
			}
			if (method.getName().equals("createFont")) {
				return Proxy.newProxyInstance(UIFont.class.getClassLoader(), new Class<?>[] { UIFont.class }, (q, m, a) -> null);
			}
			return null;
		});
	}

	private static long allocatedBytes() {
		java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
		if (bean instanceof com.sun.management.ThreadMXBean) {
			return ((com.sun.management.ThreadMXBean) bean).getThreadAllocatedBytes(Thread.currentThread().getId());
		}
		return -1L;
	}

	private static double percentile(double[] sorted, double p) {
		return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
	}

	private static final class Result {
		String line;
		double p99;
	}

	private static Result measure(String name, SceneFixture fixture, QualityTier tier, Viewport viewport) {
		app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene scene = fixture.scene(false);
		UiPainterRenderer renderer = new UiPainterRenderer(nullFactory(), "Sans");
		renderer.target(nullPainter());
		long start = fixture.onset(8);
		long end = fixture.onset(fixture.events.size() - 8);
		double[] build = new double[FRAMES];
		double[] replay = new double[FRAMES];
		long allocated = 0;
		int items = 0;
		int maxItems = 0;
		for (int pass = 0; pass < 2; pass++) {
			for (int f = 0; f < FRAMES; f++) {
				long now = start + (end - start) * f / FRAMES;
				long a0 = allocatedBytes();
				long t0 = System.nanoTime();
				Scene picture = scene.build(now, viewport, null, 300, tier);
				long t1 = System.nanoTime();
				renderer.render(picture, tier);
				long t2 = System.nanoTime();
				if (pass == 1) {
					build[f] = (t1 - t0) / 1e6;
					replay[f] = (t2 - t1) / 1e6;
					allocated += allocatedBytes() - a0;
					items += picture.items().size();
					maxItems = Math.max(maxItems, picture.items().size());
				}
			}
		}
		double[] total = new double[FRAMES];
		for (int f = 0; f < FRAMES; f++) {
			total[f] = build[f] + replay[f];
		}
		Arrays.sort(build);
		Arrays.sort(replay);
		Arrays.sort(total);
		int misses = 0;
		int consecutive = 0;
		int worstRun = 0;
		for (double t : total) {
			boolean miss = t > FRAME / 1e6;
			consecutive = miss ? consecutive + 1 : 0;
			worstRun = Math.max(worstRun, consecutive);
			misses += miss ? 1 : 0;
		}
		Result result = new Result();
		result.p99 = percentile(total, 0.99);
		result.line = String.format(java.util.Locale.ROOT, "%-12s %-12s %-10s items avg %4d max %4d | build p50 %.3f p95 %.3f p99 %.3f max %.3f | replay p50 %.3f p95 %.3f p99 %.3f max %.3f"
				+ " | total p99 %.3f max %.3f | over 16.7 ms: %d (longest run %d) | alloc %d KB/frame", name, tier, viewport.width() + "x" + viewport.height(),
				items / FRAMES, maxItems, percentile(build, 0.50), percentile(build, 0.95), percentile(build, 0.99), build[FRAMES - 1],
				percentile(replay, 0.50), percentile(replay, 0.95), percentile(replay, 0.99), replay[FRAMES - 1], result.p99, total[FRAMES - 1], misses, worstRun,
				allocated < 0 ? -1 : allocated / FRAMES / 1024);
		return result;
	}

	@Test
	public void reportTheCostOfTheSceneAloneAndWhileTheRecognitionLoadsTheProcessor() throws IOException {
		StringBuilder report = new StringBuilder();
		report.append("Arcade scene baseline (spec 003 T042), ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append(", Java ")
				.append(System.getProperty("java.version")).append(", ").append(Runtime.getRuntime().availableProcessors()).append(" processors\n");
		report.append("Each line: ").append(FRAMES).append(" frames after one warm-up pass, times in ms. The replay goes to a stand-in painter that does nothing: the toolkit's own cost is in the native report (T043).\n");
		report.append("Frame budget at 60 frames/s: 16.7 ms. Bound fixed by T042 for the scene itself: p99 of build + replay walk <= ").append(BOUND_P99_MS).append(" ms at every tier and size.\n\n");
		Object[][] workloads = { { "dense", dense() }, { "chords", chordHeavy() }, { "jumps", jumps() } };
		Viewport[] sizes = { new Viewport(640, 360), new Viewport(1280, 720), new Viewport(1920, 1080), new Viewport(2880, 1800) };
		double worst = 0;
		for (int load = 0; load < 2; load++) {
			final AtomicBoolean stop = new AtomicBoolean();
			final AtomicLong matched = new AtomicLong();
			Thread recognition = null;
			if (load == 1) {
				report.append("\n== while one thread replays synthetic guitar audio through the recognition chain as fast as it can ==\n");
				recognition = new Thread(() -> {
					long seed = 1;
					while (!stop.get()) {
						matched.addAndGet(RecognitionLoad.runOnce(seed++));
					}
				}, "recognition-load");
				recognition.setDaemon(true);
				recognition.start();
			} else {
				report.append("== alone ==\n");
			}
			for (Object[] workload : workloads) {
				for (QualityTier tier : QualityTier.values()) {
					for (Viewport size : sizes) {
						Result result = measure((String) workload[0], (SceneFixture) workload[1], tier, size);
						report.append(result.line).append('\n');
						worst = Math.max(worst, result.p99);
					}
				}
			}
			stop.set(true);
			if (recognition != null) {
				try {
					recognition.join(60_000);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				}
				report.append("(recognition events matched meanwhile: ").append(matched.get()).append(")\n");
			}
		}
		report.append(String.format(java.util.Locale.ROOT, "%nWorst p99 of build + replay walk over every line: %.3f ms (bound %.1f ms).%n", worst, BOUND_P99_MS));
		report.append("Not measured here: the real toolkit painter (T043), MIDI playback and live capture (T047), whether any acceleration is active (T043 states what is known).\n");
		Path out = Paths.get("../../specs/003-arcade-mode/benchmark/scene-baseline-2026-10-08.txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
		assertTrue(worst <= BOUND_P99_MS, "the scene costs more than a quarter of a frame at its 99th percentile: " + worst + " ms");
		List<String> none = new ArrayList<String>();
		assertTrue(none.isEmpty());
	}
}
