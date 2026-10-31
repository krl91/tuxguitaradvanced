package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Theme;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The Arcade view keeps its dark design whatever the appearance of the system (spec 021 FR-001 to FR-004, SC-001): the scene is mostly translucent, so
 * what lies under it must be the design's own background (0x0B0E13, the one of the reference pictures), not the window background the system gives. The
 * canvas is painted by the real SWT painter, copied back as pixels, and every pixel of the areas that no item of the scene covers is measured. Runs only
 * with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class ArcadeDarkBackgroundSwtTest {

	private static final long FIRST_TICK = 960L;
	private static final int TOLERANCE = 6;
	private static final int MARGIN = 4;
	private static final int TILE = 8;
	private static final int BACKGROUND = Theme.DARK.background();

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

		Harness(Object display, UIFactory factory, SceneFixture fixture, int width, int height, boolean fallback) throws Exception {
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
			}, this.clock::nowNanos, "Sans", fallback);
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

		/** The clock this far before the onset of the first note, in look-aheads (2 is before the note comes into view). */
		void seekBefore(double lookAheads) {
			long want = this.fixture.timeline.targetNanos(0) - (long) (lookAheads * CameraTrack.Settings.standard().lookAheadNanos);
			long low = 0;
			long high = this.fixture.timeline.targetTicks(0) + 10_000L;
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
	}

	private static SceneFixture song() {
		SongFixture song = new SongFixture(4);
		song.quarter(1, 0, 1, 5);
		song.quarter(1, 2, 3, 7);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	/** Marks the box of one item, as the painter reads its values (tasks.md T002), with the margin. */
	private static void cover(boolean[] covered, int width, int height, SceneItem item) {
		double half = item.lineWidth() / 2.0;
		double left;
		double top;
		double right;
		double bottom;
		switch (item.shape()) {
			case LINE:
			case CURVE:
			case QUAD: {
				left = Double.MAX_VALUE;
				top = Double.MAX_VALUE;
				right = -Double.MAX_VALUE;
				bottom = -Double.MAX_VALUE;
				int points = item.shape() == SceneItem.Shape.LINE ? 2 : 4;
				for (int i = 0; i < points; i++) {
					left = Math.min(left, item.value(2 * i));
					right = Math.max(right, item.value(2 * i));
					top = Math.min(top, item.value(2 * i + 1));
					bottom = Math.max(bottom, item.value(2 * i + 1));
				}
				if (item.shape() != SceneItem.Shape.QUAD) {
					left -= half;
					right += half;
					top -= half;
					bottom += half;
				}
				break;
			}
			case DISC:
			case RING: {
				double radius = Math.max(item.value(2), item.value(3)) + (item.shape() == SceneItem.Shape.RING ? half : 0.0);
				left = item.value(0) - radius;
				right = item.value(0) + radius;
				top = item.value(1) - radius;
				bottom = item.value(1) + radius;
				break;
			}
			case ROUNDED:
			case ROUNDED_RING: {
				double ring = item.shape() == SceneItem.Shape.ROUNDED_RING ? half : 0.0;
				left = item.value(0) - item.value(2) - ring;
				right = item.value(0) + item.value(2) + ring;
				top = item.value(1) - item.value(3) - ring;
				bottom = item.value(1) + item.value(3) + ring;
				break;
			}
			case TEXT: {
				// the scene does not know the width of a text: everything from its x to the edge on the side it grows
				left = item.align() == SceneItem.Align.LEFT ? item.value(0) : 0.0;
				right = item.align() == SceneItem.Align.RIGHT ? item.value(0) : width;
				top = item.value(1) - item.value(2);
				bottom = item.value(1) + item.value(2);
				break;
			}
			default:
				left = 0;
				top = 0;
				right = width;
				bottom = height;
		}
		int x0 = Math.max(0, (int) Math.floor(left) - MARGIN);
		int x1 = Math.min(width - 1, (int) Math.ceil(right) + MARGIN);
		int y0 = Math.max(0, (int) Math.floor(top) - MARGIN);
		int y1 = Math.min(height - 1, (int) Math.ceil(bottom) + MARGIN);
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				covered[y * width + x] = true;
			}
		}
	}

	/** Every pixel of every 8×8 tile that no item covers has the design's background; at least one such tile. */
	private static void checkEmptyAreas(Scene scene, PaintedAlignment.Picture picture, String where) {
		int width = picture.width;
		int height = picture.height;
		boolean[] covered = new boolean[width * height];
		for (SceneItem item : scene.items()) {
			cover(covered, width, height, item);
		}
		int tiles = 0;
		for (int ty = 0; ty + TILE <= height; ty += TILE) {
			for (int tx = 0; tx + TILE <= width; tx += TILE) {
				boolean empty = true;
				for (int y = ty; y < ty + TILE && empty; y++) {
					for (int x = tx; x < tx + TILE && empty; x++) {
						empty = !covered[y * width + x];
					}
				}
				if (!empty) {
					continue;
				}
				tiles++;
				for (int y = ty; y < ty + TILE; y++) {
					for (int x = tx; x < tx + TILE; x++) {
						assertTrue(picture.near(x, y, BACKGROUND), where + ": the empty pixel at " + x + "," + y + " is " + Integer.toHexString(picture.at(x, y)) + ", not the design's background "
								+ Integer.toHexString(BACKGROUND));
					}
				}
			}
		}
		assertTrue(tiles > 0, where + ": no empty area of " + TILE + "x" + TILE + " pixels in the frame");
	}

	private static void paintAndCheck(Harness harness, String where) throws Exception {
		PaintedAlignment.Picture picture = harness.paint();
		Scene scene = harness.source.frame(harness.clock.nowNanos(), new Viewport(picture.width, picture.height));
		checkEmptyAreas(scene, picture, where);
	}

	@Test
	public void theEmptyAreasOfAFrameHaveTheDesignsBackgroundOnTheNormalPath() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, song(), 1280, 720, false);
			try {
				harness.seekBefore(0.5);
				paintAndCheck(harness, "normal path 1280x720");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theEmptyAreasOfAFrameHaveTheDesignsBackgroundOnTheSimplifiedPath() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, song(), 1280, 720, true);
			try {
				harness.seekBefore(0.5);
				paintAndCheck(harness, "simplified path 1280x720");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theBackgroundFollowsALiveResize() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, song(), 1280, 720, false);
			try {
				harness.seekBefore(0.5);
				int[][] sizes = { { 1600, 900 }, { 800, 500 } };
				for (int[] size : sizes) {
					harness.resize(size[0], size[1]);
					paintAndCheck(harness, "resize " + size[0] + "x" + size[1]);
				}
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theCanvasHasTheDesignsBackgroundAsItsOwn() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, song(), 1280, 720, false);
			try {
				UIColor colour = harness.canvas.getControl().getBgColor();
				assertEquals(BACKGROUND, (colour.getRed() << 16) | (colour.getGreen() << 8) | colour.getBlue(), "the background of the canvas");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theFrameBeforeTheFirstNoteHasTheDesignsBackground() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, song(), 1280, 720, false);
			try {
				harness.seekBefore(2.0);
				paintAndCheck(harness, "before the first note 1280x720");
			} finally {
				harness.close();
			}
		});
	}
}
