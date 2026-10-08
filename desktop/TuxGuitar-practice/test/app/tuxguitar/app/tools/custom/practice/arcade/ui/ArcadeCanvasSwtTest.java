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
			this(display, factory, fixture, width, height, null);
		}

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height, app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun.View run)
				throws Exception {
			this.display = display;
			this.factory = factory;
			this.window = factory.createWindow();
			this.window.setLayout(new UITableLayout());
			this.clock = new FakeTempoClock(ticks -> fixture.timeline.nanosAtTick((long) ticks), FIRST_TICK, 1_000_000L);
			this.source = new ArcadeFrameSource(fixture.scene(false), fixture.timeline, FIRST_TICK, this.clock, () -> {
				this.paintThread.set(Thread.currentThread());
				return run;
			}, () -> 100, new QualityController(ArcadeCanvas.FRAME_NANOS));
			Method asyncExec = display.getClass().getMethod("asyncExec", Runnable.class);
			this.canvas = new ArcadeCanvas(factory, this.window, this.source, task -> {
				this.posted.incrementAndGet();
				try {
					asyncExec.invoke(display, task);
				} catch (ReflectiveOperationException exception) {
					throw new AssertionError(exception);
				}
			}, this.clock::nowNanos, "Sans");
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
