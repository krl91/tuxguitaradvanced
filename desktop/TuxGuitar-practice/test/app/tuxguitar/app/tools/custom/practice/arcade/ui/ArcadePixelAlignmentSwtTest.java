package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The destination of an incoming note, measured on the pixels that the real SWT painter put on the real canvas (spec 003 FR-066, SC-024): the canvas is
 * painted, copied back as an image, and what is measured is what was painted, not the model. The target disk is looked for by its outline, the fret wires
 * and the strings by their own colours on the neck; the disk must lie between the two painted wires of its fret, at the middle, on the painted string, and
 * the painted trajectory must reach the hit line at that same x. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class ArcadePixelAlignmentSwtTest {

	private static final long FIRST_TICK = 960L;
	private static final int TOLERANCE = 6;
	private static final double PIXELS = 1.5;

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	private static void withDisplay(DisplayTask task) throws Exception {
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

	private static final class Harness {
		final Object display;
		final UIWindow window;
		final FakeTempoClock clock;
		final ArcadeFrameSource source;
		final ArcadeCanvas canvas;
		final SceneFixture fixture;

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height) throws Exception {
			this.display = display;
			this.fixture = fixture;
			this.window = factory.createWindow();
			this.window.setLayout(new UITableLayout());
			this.clock = new FakeTempoClock(ticks -> fixture.timeline.nanosAtTick((long) ticks), FIRST_TICK, 1_000_000L);
			this.source = new ArcadeFrameSource(fixture.scene(false), fixture.timeline, FIRST_TICK, this.clock, () -> null, () -> 100,
					new QualityController(ArcadeCanvas.FRAME_NANOS));
			Method asyncExec = display.getClass().getMethod("asyncExec", Runnable.class);
			this.canvas = new ArcadeCanvas(factory, this.window, this.source, task -> {
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

		void resize(int width, int height) throws Exception {
			this.window.setBounds(new UIRectangle(20, 20, width, height));
			flush();
		}

		/** Paints the frame at the clock's time and returns what is on the canvas. */
		PaintedAlignment.Picture paint() throws Exception {
			this.canvas.scheduler().tick();
			Object nativeCanvas = this.canvas.getControl().getClass().getMethod("getControl").invoke(this.canvas.getControl());
			Method update = Class.forName("org.eclipse.swt.widgets.Control").getMethod("update");
			long deadline = System.nanoTime() + 2_000_000_000L;
			while (this.canvas.scheduler().isPending() && System.nanoTime() < deadline) {
				flush();
				if (this.canvas.scheduler().isPending()) {
					update.invoke(nativeCanvas);
				}
			}
			assertTrue(!this.canvas.scheduler().isPending(), "the frame was painted");
			return copy(nativeCanvas);
		}

		/** The canvas copied into an image through a graphics context, read back pixel by pixel. */
		private PaintedAlignment.Picture copy(Object nativeCanvas) throws Exception {
			Class<?> controlClass = Class.forName("org.eclipse.swt.widgets.Control");
			Class<?> drawable = Class.forName("org.eclipse.swt.graphics.Drawable");
			Class<?> gcClass = Class.forName("org.eclipse.swt.graphics.GC");
			Class<?> imageClass = Class.forName("org.eclipse.swt.graphics.Image");
			Class<?> deviceClass = Class.forName("org.eclipse.swt.graphics.Device");
			Object bounds = controlClass.getMethod("getBounds").invoke(nativeCanvas);
			int width = bounds.getClass().getField("width").getInt(bounds);
			int height = bounds.getClass().getField("height").getInt(bounds);
			Object gc = gcClass.getConstructor(drawable).newInstance(nativeCanvas);
			Object image = imageClass.getConstructor(deviceClass, int.class, int.class).newInstance(this.display, width, height);
			gcClass.getMethod("copyArea", imageClass, int.class, int.class).invoke(gc, image, 0, 0);
			gcClass.getMethod("dispose").invoke(gc);
			Object data = imageClass.getMethod("getImageData").invoke(image);
			Class<?> dataClass = data.getClass();
			int w = dataClass.getField("width").getInt(data);
			int h = dataClass.getField("height").getInt(data);
			Object palette = dataClass.getField("palette").get(data);
			Method getPixel = dataClass.getMethod("getPixel", int.class, int.class);
			Method getRGB = palette.getClass().getMethod("getRGB", int.class);
			int[] rgb = new int[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					Object colour = getRGB.invoke(palette, getPixel.invoke(data, x, y));
					rgb[y * w + x] = (colour.getClass().getField("red").getInt(colour) << 16) | (colour.getClass().getField("green").getInt(colour) << 8)
							| colour.getClass().getField("blue").getInt(colour);
				}
			}
			imageClass.getMethod("dispose").invoke(image);
			return new PaintedAlignment.Picture(w, h, rgb);
		}

		void close() {
			this.canvas.dispose();
			this.window.dispose();
		}

		/** The clock at the moment of the note given as a progress (1 is the onset), by bisection on the timeline: no assumption about the tempo. */
		void seekProgress(int event, double progress) {
			long look = CameraTrack.Settings.standard().lookAheadNanos;
			long want = this.fixture.timeline.targetNanos(event) - (long) ((1.0 - progress) * look);
			long low = 0;
			long high = this.fixture.timeline.targetTicks(event) + 10_000L;
			while (high - low > 1) {
				long mid = (low + high) / 2;
				if (this.fixture.timeline.nanosAtTick(mid) < want) {
					low = mid;
				} else {
					high = mid;
				}
			}
			this.clock.start(FIRST_TICK);
			this.clock.seek(FIRST_TICK + high);
		}
	}

	/** One note on the string, alone, and a later one far away so that the camera has somewhere to go. */
	private static SceneFixture note(int string, int fret, int later) {
		SongFixture song = new SongFixture(4);
		song.quarter(1, 0, string, fret);
		song.quarter(1, 2, 1 + (string % 6), later);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	private void check(Harness harness, int string, int fret, double progress, String where, boolean cellRequired) throws Exception {
		harness.seekProgress(0, progress);
		PaintedAlignment.Picture picture = harness.paint();
		Viewport viewport = new Viewport(picture.width, picture.height);
		Scene scene = harness.source.frame(harness.clock.nowNanos(), viewport);
		PaintedAlignment.check(scene, picture, fret, progress, where, cellRequired);
	}

	@Test
	public void theTargetDiskIsOnItsPaintedCellAndItsPaintedStringForEveryFretAndStringAndSize() throws Exception {
		withDisplay((display, factory) -> {
			int[][] sizes = { { 1280, 720 }, { 900, 560 }, { 1600, 900 } };
			int[] frets = { 1, 5, 10, 12, 20 };
			int checked = 0;
			for (int[] size : sizes) {
				for (int fret : frets) {
					for (int string = 1; string <= 6; string++) {
						SceneFixture fixture = note(string, fret, fret > 3 ? fret - 2 : fret + 2);
						Harness harness = new Harness(display, factory, fixture, size[0], size[1]);
						try {
							// at the onset the marker, with its halo, is on the target by design (Q31): the target alone is measured at the onset by the Java2D test, which
							// paints the scene without the figure of the note; here the native painter is measured while the note is still coming
							for (double progress : new double[] { 0.3, 0.6 }) {
								check(harness, string, fret, progress, "fret " + fret + " string " + string + " " + size[0] + "x" + size[1] + " p=" + progress, true);
								checked++;
							}
						} finally {
							harness.close();
						}
					}
				}
			}
			assertTrue(checked >= 180, "frames measured: " + checked);
		});
	}

	@Test
	public void aLiveResizeAndAMovingCameraKeepTheDiskOnItsPaintedCell() throws Exception {
		withDisplay((display, factory) -> {
			SceneFixture fixture = note(1, 10, 20);
			Harness harness = new Harness(display, factory, fixture, 1280, 720);
			try {
				int[][] sizes = { { 640, 400 }, { 1000, 600 }, { 1280, 720 }, { 1920, 1080 }, { 800, 500 } };
				for (int[] size : sizes) {
					harness.resize(size[0], size[1]);
					for (double progress : new double[] { 0.2, 0.6, 0.9, 1.0 }) {
						check(harness, 1, 10, progress, "resize " + size[0] + "x" + size[1] + " p=" + progress, false);
					}
				}
			} finally {
				harness.close();
			}
		});
	}
}
