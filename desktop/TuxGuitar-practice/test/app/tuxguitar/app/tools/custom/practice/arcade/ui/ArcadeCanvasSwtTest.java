package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeReferenceSceneTest;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ReferenceScenes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneCapture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the Arcade canvas (spec 003 T043): the real canvas paints a six-string and a four-string scene on the real painter without an
 * exception, on the UI thread, one frame per request and never a queue; the frame the canvas is fed with is the reference scene. Runs only with the profile
 * {@code swt-gui-tests} (see {@code RecognitionSettingsSwtTest}). It also writes the measured cost of real paints, whatever the toolkit does with them.
 */
@Tag("swt-gui")
public class ArcadeCanvasSwtTest {

	private static final long FIRST_TICK = 960L;

	private static final class Harness {
		final Object display;
		final UIFactory factory;
		final UIWindow window;
		final FakeTempoClock clock;
		final ArcadeFrameSource source;
		final ArcadeCanvas canvas;
		final AtomicInteger posted = new AtomicInteger();
		final AtomicReference<Thread> paintThread = new AtomicReference<Thread>();
		final Thread uiThread = Thread.currentThread();

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height) throws Exception {
			this(display, factory, fixture, width, height, null, false);
		}

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height, app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View run)
				throws Exception {
			this(display, factory, fixture, width, height, run, false);
		}

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height, app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View run,
				boolean forceFallback) throws Exception {
			this(display, factory, fixture, width, height, run, forceFallback, app.tuxguitar.app.tools.custom.practice.session.FlameSetting.OFF);
		}

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height, app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View run,
				boolean forceFallback, app.tuxguitar.app.tools.custom.practice.session.FlameSetting flames) throws Exception {
			this.display = display;
			this.factory = factory;
			this.window = factory.createWindow();
			this.window.setLayout(new UITableLayout());
			this.clock = new FakeTempoClock(ticks -> fixture.timeline.nanosAtTick((long) ticks), FIRST_TICK, 1_000_000L);
			this.source = new ArcadeFrameSource(fixture.scene(false), fixture.timeline, FIRST_TICK, this.clock, () -> {
				this.paintThread.set(Thread.currentThread());
				return run;
			}, () -> 100, new QualityController(ArcadeCanvas.FRAME_NANOS), 0L, 0L, flames, true,
					app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring.standard());
			Method asyncExec = display.getClass().getMethod("asyncExec", Runnable.class);
			this.canvas = new ArcadeCanvas(factory, this.window, this.source, task -> {
				this.posted.incrementAndGet();
				try {
					asyncExec.invoke(display, task);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
			}, this.clock::nowNanos, "Sans", forceFallback);
			((UITableLayout) this.window.getLayout()).set(this.canvas.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, (float) width,
					(float) height, 0f);
			this.window.pack();
			this.window.setBounds(new UIRectangle(20, 20, width, height));
			this.window.open();
			flush();
		}

		void flush() throws Exception {
			Method readAndDispatch = this.display.getClass().getMethod("readAndDispatch");
			for (int i = 0; i < 200 && (Boolean) readAndDispatch.invoke(this.display); i++) {
				// drain the pending events
			}
		}

		/** One tick of the timer, then the event loop until the frame it asked for was painted (bounded). */
		boolean paintOneFrame() throws Exception {
			boolean asked = this.canvas.scheduler().tick();
			settle();
			return asked && !this.canvas.scheduler().isPending();
		}

		/** The event loop until the requested frame was painted, for at most two seconds: the system decides when it paints, the test only waits for the event. */
		void settle() throws Exception {
			Object nativeCanvas = this.canvas.getControl().getClass().getMethod("getControl").invoke(this.canvas.getControl());
			Method update = Class.forName("org.eclipse.swt.widgets.Control").getMethod("update");
			long deadline = System.nanoTime() + 2_000_000_000L;
			while (this.canvas.scheduler().isPending() && System.nanoTime() < deadline) {
				flush();
				if (this.canvas.scheduler().isPending()) {
					update.invoke(nativeCanvas);
				}
			}
		}

		void close() {
			this.canvas.dispose();
			this.window.dispose();
		}
	}

	private static void withDisplay(DisplayTask task) throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			// the application starts the environment of the toolkit once, with its display
			Class<?> environment = Class.forName("app.tuxguitar.ui.swt.SWTEnvironment");
			environment.getMethod("start", displayClass).invoke(environment.getMethod("getInstance").invoke(null), display);
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			task.run(display, factory);
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	private void paintsWithoutException(ReferenceScenes.Reference reference) throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, reference.fixture, 1280, 720, reference.run);
			try {
				harness.clock.start(FIRST_TICK);
				harness.clock.seek(FIRST_TICK + reference.tick);
				assertTrue(harness.paintOneFrame(), "the canvas painted the frame it was asked for");
				assertTrue(harness.canvas.lastFrameCostNanos() > 0, "a real paint took place");
				assertSame(harness.uiThread, harness.paintThread.get(), "painted on the UI thread");
				assertEquals(1, harness.posted.get());

				// the frame the canvas is fed with is the reference scene
				assertEquals(ArcadeReferenceSceneTest.expected(reference.name), SceneCapture.of(harness.source.frame(harness.clock.nowNanos(), ReferenceScenes.SCREEN)),
						reference.name + ": the scene fed to the canvas is not the reference");

				// a smaller window, a bigger one: still painted
				harness.window.setBounds(new UIRectangle(20, 20, 640, 360));
				harness.flush();
				assertTrue(harness.paintOneFrame());
				harness.window.setBounds(new UIRectangle(20, 20, 1600, 900));
				harness.flush();
				assertTrue(harness.paintOneFrame());
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aSixStringSceneIsPaintedOnTheRealPainter() throws Exception {
		paintsWithoutException(ReferenceScenes.sixStrings());
	}

	@Test
	public void aFourStringSceneIsPaintedOnTheRealPainter() throws Exception {
		paintsWithoutException(ReferenceScenes.fourStrings());
	}

	@Test
	public void everyReferenceSceneIsPaintedInEveryTierWithoutExceptionAndKeepsItsLabelsApart() throws Exception {
		withDisplay((display, factory) -> {
			for (ReferenceScenes.Reference reference : ReferenceScenes.all()) {
				Harness harness = new Harness(display, factory, reference.fixture, 1280, 720, reference.run);
				try {
					harness.clock.start(FIRST_TICK);
					harness.clock.seek(FIRST_TICK + reference.tick);
					assertTrue(harness.paintOneFrame(), reference.name + " painted");
					assertEquals(ArcadeReferenceSceneTest.expected(reference.name),
							SceneCapture.of(harness.source.frame(harness.clock.nowNanos(), ReferenceScenes.SCREEN)), reference.name + ": the scene fed to the canvas is not the reference");
					for (app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier tier : app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier.values()) {
						app.tuxguitar.app.tools.custom.practice.arcade.view.Scene scene = reference.scene(false, tier);
						assertEquals(null, app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes.firstOverlap(app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxes.labels(scene)),
								reference.name + " at " + tier);
					}
				} finally {
					harness.close();
				}
			}
		});
	}

	@Test
	public void aForcedFallbackCanvasPaintsEveryReferenceWithTheSameScene() throws Exception {
		withDisplay((display, factory) -> {
			for (ReferenceScenes.Reference reference : ReferenceScenes.all()) {
				Harness harness = new Harness(display, factory, reference.fixture, 1280, 720, reference.run, true);
				try {
					assertTrue(harness.canvas.rendererName().contains("toolkit painter"), "named before the first paint: " + harness.canvas.rendererName());
					harness.clock.start(FIRST_TICK);
					harness.clock.seek(FIRST_TICK + reference.tick);
					assertTrue(harness.paintOneFrame(), reference.name + " painted by the fallback");
					assertTrue(harness.canvas.rendererName().contains("fallback"), harness.canvas.rendererName());
					assertEquals(ArcadeReferenceSceneTest.expected(reference.name),
							SceneCapture.of(harness.source.frame(harness.clock.nowNanos(), ReferenceScenes.SCREEN)), reference.name + ": the fallback changed the scene");
				} finally {
					harness.close();
				}
			}
		});
	}

	/** The stage flames (spec 009 T028): painted by the real painter in every tier without an exception, and nothing of them at the lowest tier. */
	@Test
	public void theStageFlamesArePaintedOnTheRealPainterInEveryTierAndNotAtAllAtTheLowestOne() throws Exception {
		withDisplay((display, factory) -> {
			ReferenceScenes.Reference reference = ReferenceScenes.flameReading();
			app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View perfect = new app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View(
					new java.util.ArrayList<app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.EventState>(),
					app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring.standard().maximum(10), 0, 0, 0, 10);
			for (boolean fallback : new boolean[] { false, true }) {
				Harness harness = new Harness(display, factory, reference.fixture, 1280, 720, perfect, fallback, app.tuxguitar.app.tools.custom.practice.session.FlameSetting.FULL);
				try {
					harness.clock.start(FIRST_TICK);
					harness.clock.seek(FIRST_TICK + reference.tick);
					QualityTier[] seen = new QualityTier[3];
					int tongueLayers[] = new int[3];
					for (int step = 0; step < 3; step++) {
						QualityTier wanted = QualityTier.values()[step];
						for (int guard = 0; guard < 2000 && harness.source.tier() != wanted; guard++) {
							harness.source.painted(40_000_000L); // a slow frame: the controller goes down one tier at a time
						}
						assertEquals(wanted, harness.source.tier(), "the controller reached " + wanted);
						for (int frame = 0; frame < 24; frame++) {
							assertTrue(harness.paintOneFrame(), "painted at " + wanted + (fallback ? " by the fallback" : ""));
							harness.clock.advance(50_000_000L);
						}
						app.tuxguitar.app.tools.custom.practice.arcade.view.Scene scene = harness.source.frame(harness.clock.nowNanos(), ReferenceScenes.SCREEN);
						java.util.Set<Integer> codes = new java.util.HashSet<Integer>();
						for (app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem item : scene.withRole(app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role.FLAME_TONGUE)) {
							codes.add(item.tgString());
						}
						seen[step] = harness.source.tier();
						tongueLayers[step] = codes.size();
					}
					assertEquals(28 * 3, tongueLayers[0], "the full tier at full level");
					assertEquals(14 * 2, tongueLayers[1], "the balanced tier");
					assertEquals(0, tongueLayers[2], "nothing at the lowest tier");
				} finally {
					harness.close();
				}
			}
		});
	}

	@Test
	public void aLiveResizeKeepsPaintingAndKeepsTheLabelsApartAtEverySize() throws Exception {
		withDisplay((display, factory) -> {
			ReferenceScenes.Reference reference = ReferenceScenes.denseRealistic();
			Harness harness = new Harness(display, factory, reference.fixture, 1280, 720, reference.run);
			try {
				harness.clock.start(FIRST_TICK);
				harness.clock.seek(FIRST_TICK + reference.tick);
				int[][] sizes = { { 640, 360 }, { 800, 450 }, { 1280, 720 }, { 1600, 900 }, { 2560, 1440 }, { 1280, 720 }, { 480, 300 }, { 1920, 1080 }, { 1000, 600 } };
				int painted = 0;
				for (int[] size : sizes) {
					harness.window.setBounds(new UIRectangle(20, 20, size[0], size[1]));
					harness.flush();
					for (int frame = 0; frame < 5; frame++) {
						if (harness.paintOneFrame()) {
							painted++;
						}
					}
					UIRectangle bounds = harness.canvas.getControl().getBounds();
					app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport viewport = new app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport(bounds.getWidth(), bounds.getHeight());
					app.tuxguitar.app.tools.custom.practice.arcade.view.Scene scene = harness.source.frame(harness.clock.nowNanos(), viewport);
					assertEquals(null, SceneBoxes.firstOverlap(SceneBoxes.labels(scene)), "labels at " + size[0] + "x" + size[1]);
					for (SceneBoxes.Box box : SceneBoxes.labels(scene)) {
						assertTrue(box.x1 >= 0 && box.y1 >= 0 && box.x2 <= viewport.width() && box.y2 <= viewport.height(), "clipped at " + size[0] + "x" + size[1] + ": " + box);
					}
					assertEquals(viewport.height() * 0.60, scene.withRole(app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role.HIT_LINE).get(0).value(1), 0.01,
							"the hit line keeps its place at " + size[0] + "x" + size[1]);
				}
				assertEquals(sizes.length * 5, painted, "every frame asked for during the resize was painted");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void reportTheFallbackFrameRateAndTierOnADensePassage() throws Exception {
		withDisplay((display, factory) -> {
			SceneFixture fixture = dense();
			Harness harness = new Harness(display, factory, fixture, 1280, 720, null, true);
			try {
				harness.clock.start(FIRST_TICK);
				int frames = 600;
				double[] costs = new double[frames];
				int painted = 0;
				long begin = System.nanoTime();
				for (int f = 0; f < frames; f++) {
					double position = (double) f / frames * (fixture.events.size() - 16) + 8;
					int event = (int) position;
					long ticks = fixture.timeline.targetTicks(event) + Math.round((position - event)
							* (fixture.timeline.targetTicks(event + 1) - fixture.timeline.targetTicks(event)));
					harness.clock.seek(FIRST_TICK + ticks);
					if (harness.paintOneFrame()) {
						costs[painted++] = harness.canvas.lastFrameCostNanos() / 1e6;
					}
				}
				double seconds = (System.nanoTime() - begin) / 1e9;
				assertTrue(painted > frames / 2, "most requested frames were really painted: " + painted);
				double[] sorted = Arrays.copyOf(costs, painted);
				Arrays.sort(sorted);
				StringBuilder report = new StringBuilder();
				report.append("Arcade canvas, forced fallback path (spec 003 T048), ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
						.append(", Java ").append(System.getProperty("java.version")).append('\n');
				report.append(String.format(Locale.ROOT, "dense passage under the density budget, window 1280x720, renderer \"%s\": %d frames painted of %d asked in %.1f s, one after the other through the event loop (the rate is that of this test loop, not a display rate)%n",
						harness.canvas.rendererName(), painted, frames, seconds));
				report.append(String.format(Locale.ROOT, "cost of one paint: p50 %.3f ms, p95 %.3f ms, p99 %.3f ms, max %.3f ms%n", sorted[sorted.length / 2],
						sorted[(int) (sorted.length * 0.95)], sorted[(int) (sorted.length * 0.99)], sorted[sorted.length - 1]));
				report.append("quality tier chosen by the controller at the end: ").append(harness.source.tier()).append('\n');
				Path out = Paths.get("../../specs/003-arcade-mode/benchmark/fallback-2026-10-08.txt");
				Files.createDirectories(out.getParent());
				Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
				System.out.println(report);
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aStalledUiThreadMakesTicksSkipAndNeverQueueFrames() throws Exception {
		withDisplay((display, factory) -> {
			ReferenceScenes.Reference reference = ReferenceScenes.sixStrings();
			Harness harness = new Harness(display, factory, reference.fixture, 800, 450);
			try {
				harness.clock.start(FIRST_TICK);
				harness.clock.seek(FIRST_TICK + reference.fixture.timeline.targetTicks(3) + 144);
				// the UI thread does not run while the timer ticks one hundred times
				for (int i = 0; i < 100; i++) {
					harness.canvas.scheduler().tick();
				}
				assertEquals(1, harness.posted.get(), "one request posted to the UI thread");
				assertEquals(99, harness.canvas.scheduler().skipped());
				harness.settle();
				assertFalse(harness.canvas.scheduler().isPending(), "the one frame was painted");
				assertTrue(harness.paintOneFrame(), "and the next tick asks again");
				assertEquals(2, harness.posted.get(), "a single new request, not the hundred that were skipped");
			} finally {
				harness.close();
			}
		});
	}

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

	/** Not an assertion on speed: the cost of real paints of a dense passage, written for the record of T042/T043. */
	@Test
	public void reportTheCostOfRealPaintsOfADensePassage() throws Exception {
		withDisplay((display, factory) -> {
			SceneFixture fixture = dense();
			Harness harness = new Harness(display, factory, fixture, 1280, 720);
			try {
				harness.clock.start(FIRST_TICK);
				int frames = 600;
				double[] costs = new double[frames];
				int painted = 0;
				for (int f = 0; f < frames; f++) {
					double position = (double) f / frames * (fixture.events.size() - 16) + 8;
					int event = (int) position;
					long ticks = fixture.timeline.targetTicks(event) + Math.round((position - event)
							* (fixture.timeline.targetTicks(event + 1) - fixture.timeline.targetTicks(event)));
					harness.clock.seek(FIRST_TICK + ticks);
					boolean asked = harness.canvas.scheduler().tick();
					harness.settle();
					boolean done = !harness.canvas.scheduler().isPending();
					if (asked && done) {
						costs[painted++] = harness.canvas.lastFrameCostNanos() / 1e6;
					}
				}
				assertTrue(painted > frames / 2, "most requested frames were really painted: " + painted);
				double[] sorted = Arrays.copyOf(costs, painted);
				Arrays.sort(sorted);
				StringBuilder report = new StringBuilder();
				report.append("Arcade canvas, real SWT paints (spec 003 T043), ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
						.append(", Java ").append(System.getProperty("java.version")).append('\n');
				report.append(String.format(Locale.ROOT, "dense passage, window 1280x720, %d frames painted of %d asked, painted through the event loop one at a time%n", painted, frames));
				report.append(String.format(Locale.ROOT, "cost of one paint (scene build + replay on the SWT painter): p50 %.3f ms, p95 %.3f ms, p99 %.3f ms, max %.3f ms%n",
						sorted[sorted.length / 2], sorted[(int) (sorted.length * 0.95)], sorted[(int) (sorted.length * 0.99)], sorted[sorted.length - 1]));
				report.append("final quality tier chosen by the controller: ").append(harness.source.tier()).append('\n');
				report.append("Whether the toolkit paints with a hardware accelerated path is not known to this test and is not claimed: SWT on macOS draws with Core Graphics, "
						+ "and nothing here reads the GPU. Not measured here: MIDI playback and live capture running (T047).\n");
				Path out = Paths.get("../../specs/003-arcade-mode/benchmark/scene-swt-2026-10-08.txt");
				Files.createDirectories(out.getParent());
				Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
				System.out.println(report);
			} finally {
				harness.close();
			}
		});
	}

	@SuppressWarnings("unused")
	private static void unused() throws IOException {
	}
}
