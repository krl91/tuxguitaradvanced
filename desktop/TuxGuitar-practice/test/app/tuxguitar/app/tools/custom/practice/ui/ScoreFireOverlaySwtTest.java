package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the score fire (spec 019 T019, FR-001, FR-010): the fire is a second paint listener of a canvas, painted after the first one on the
 * same painter, and it leaves the first one's pixels alone. Runs only with the profile {@code swt-gui-tests}, like {@code ArcadeCanvasSwtTest}.
 */
@Tag("swt-gui")
public class ScoreFireOverlaySwtTest {

	private static final int WIDTH = 640;
	private static final int HEIGHT = 400;
	private static final int WHITE = 0xFFFFFF;

	private static final class Harness {
		final Object display;
		final UIFactory factory;
		final UIWindow window;
		final UICanvas canvas;
		final AtomicLong wall = new AtomicLong(10_000_000_000L);
		final AtomicLong cost = new AtomicLong();
		long costStep = 1_000_000L;
		List<double[]> boxes = Collections.emptyList();
		int redraws;
		final ScoreFire fire;
		UIColor white;

		Harness(Object display, UIFactory factory) throws Exception {
			this.display = display;
			this.factory = factory;
			this.window = factory.createWindow();
			this.window.setLayout(new UITableLayout());
			this.canvas = factory.createCanvas(this.window, false);
			this.white = factory.createColor(255, 255, 255);
			UIPaintListener first = event -> {
				UIPainter painter = event.getPainter();
				painter.setBackground(this.white);
				painter.setAlpha(255);
				painter.initPath(UIPainter.PATH_FILL);
				painter.addRectangle(0, 0, 4000, 4000);
				painter.closePath();
			};
			this.canvas.addPaintListener(first);
			((UITableLayout) this.window.getLayout()).set(this.canvas, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, (float) WIDTH,
					(float) HEIGHT, 0f);
			this.window.pack();
			this.window.setBounds(new UIRectangle(20, 20, WIDTH, HEIGHT));
			this.window.open();
			ScoreFire.Score port = new ScoreFire.Score() {
				public UICanvas canvas() {
					return Harness.this.canvas;
				}

				public List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next, PaletteDodge.Box palette) {
					return Harness.this.boxes;
				}

				public void redraw(boolean overlayOnly) {
					Harness.this.redraws++;
					Harness.this.canvas.redraw();
				}
			};
			ScoreFire.Ticker ticker = new ScoreFire.Ticker() {
				public void start(Runnable tick) {
					// the test ticks by hand
				}

				public void stop() {
				}
			};
			this.fire = new ScoreFire(factory, port, ticker, this.wall::get, () -> this.cost.addAndGet(this.costStep));
			flush();
		}

		/** Full fire: the streak at its full height, and the level grown to it. */
		void grow() {
			this.fire.update(16, 16, Collections.<Integer>emptyList(), false, null, null, null);
			for (int i = 0; i < 12; i++) {
				this.wall.addAndGet(100_000_000L);
				this.fire.tick();
			}
		}

		void flush() throws Exception {
			Method readAndDispatch = this.display.getClass().getMethod("readAndDispatch");
			for (int i = 0; i < 200 && (Boolean) readAndDispatch.invoke(this.display); i++) {
				// drain the pending events
			}
		}

		int[] paint() throws Exception {
			this.canvas.redraw();
			Object nativeCanvas = this.canvas.getClass().getMethod("getControl").invoke(this.canvas);
			Class.forName("org.eclipse.swt.widgets.Control").getMethod("update").invoke(nativeCanvas);
			flush();
			return copy(nativeCanvas);
		}

		/** The canvas copied into an image through a graphics context, read back pixel by pixel (as {@code ArcadePixelAlignmentSwtTest}). */
		int[] copy(Object nativeCanvas) throws Exception {
			Class<?> drawable = Class.forName("org.eclipse.swt.graphics.Drawable");
			Class<?> gcClass = Class.forName("org.eclipse.swt.graphics.GC");
			Class<?> imageClass = Class.forName("org.eclipse.swt.graphics.Image");
			Class<?> deviceClass = Class.forName("org.eclipse.swt.graphics.Device");
			Object gc = gcClass.getConstructor(drawable).newInstance(nativeCanvas);
			Object image = imageClass.getConstructor(deviceClass, int.class, int.class).newInstance(this.display, width(), height());
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
			return rgb;
		}

		int width() {
			return Math.round(this.canvas.getBounds().getWidth());
		}

		int height() {
			return Math.round(this.canvas.getBounds().getHeight());
		}

		int at(int[] rgb, int x, int y) {
			return rgb[y * width() + x];
		}

		boolean topHalfWhite(int[] rgb) {
			for (int y = 0; y < height() / 2; y++) {
				for (int x = 0; x < width(); x++) {
					if (at(rgb, x, y) != WHITE) {
						return false;
					}
				}
			}
			return true;
		}

		boolean allWhite(int[] rgb) {
			for (int value : rgb) {
				if (value != WHITE) {
					return false;
				}
			}
			return true;
		}

		void close() {
			this.fire.stop();
			this.white.dispose();
			this.window.dispose();
		}
	}

	@Test
	public void theFireIsPaintedAfterTheScoreAtTheBottomOnly() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.fire.start(FlameSetting.FULL, FireTuning.SCORE_DEFAULTS, true);
				assertTrue(harness.fire.isAttached());
				harness.grow();
				int[] rgb = harness.paint();
				int bottom = harness.height() - 3;
				assertTrue(harness.at(rgb, 3, bottom) != WHITE, "the bottom-left corner burns");
				assertTrue(harness.at(rgb, harness.width() - 4, bottom) != WHITE, "the bottom-right corner burns");
				assertTrue(harness.topHalfWhite(rgb), "the first listener's pixels outside the fire are unchanged");
				assertTrue(harness.redraws > 0, "the fire asks for its pictures");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void offAddsNothingAndStopTakesTheFireAway() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.fire.start(FlameSetting.OFF, FireTuning.SCORE_DEFAULTS, true);
				assertFalse(harness.fire.isAttached(), "off: no listener");
				harness.grow();
				assertTrue(harness.allWhite(harness.paint()), "off: the canvas is the first listener's alone");
				assertEquals(0, harness.redraws, "off: no redraw");

				harness.fire.start(FlameSetting.FULL, FireTuning.SCORE_DEFAULTS, true);
				harness.grow();
				assertFalse(harness.allWhite(harness.paint()));
				harness.fire.stop();
				assertFalse(harness.fire.isAttached());
				assertTrue(harness.allWhite(harness.paint()), "after stop, the canvas is white again");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theGuardBoxesAreAskedAtEachPaint() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.fire.start(FlameSetting.FULL, FireTuning.SCORE_DEFAULTS, false);
				harness.grow();
				int h = harness.height();
				List<double[]> corner = new ArrayList<double[]>();
				corner.add(new double[] { 0, h - 80, 80, h });
				harness.boxes = corner;
				int[] guarded = harness.paint();
				int guardedPixel = harness.at(guarded, 10, h - 5);
				harness.boxes = Collections.emptyList();
				int[] free = harness.paint();
				int freePixel = harness.at(free, 10, h - 5);
				assertTrue(near(guardedPixel, WHITE, 3), "under a guard box the fire fades out: " + Integer.toHexString(guardedPixel));
				assertTrue(freePixel != WHITE, "the box moved away at the next paint: the fire is back");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aSlowComputerStepsDownToNothingAndClimbsBack() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			UIImage image = factory.createImage(WIDTH, HEIGHT);
			try {
				harness.fire.start(FlameSetting.FULL, FireTuning.SCORE_DEFAULTS, true);
				harness.grow();
				assertEquals(QualityTier.FULL, harness.fire.tier());
				harness.costStep = 20_000_000L;
				paintMany(harness, image, 40);
				assertEquals(QualityTier.PERFORMANCE, harness.fire.tier(), "20 ms a picture: down to nothing");
				paintMany(harness, image, 1);
				assertEquals(0, harness.fire.lastPainted(), "nothing of the fire is painted at the performance tier");
				harness.costStep = 1_000_000L;
				paintMany(harness, image, 600);
				assertEquals(QualityTier.FULL, harness.fire.tier(), "1 ms a picture: back to full");
				assertTrue(harness.fire.lastPainted() > 0);
			} finally {
				image.dispose();
				harness.close();
			}
		});
	}

	private static void paintMany(Harness harness, UIImage image, int count) {
		for (int i = 0; i < count; i++) {
			UIPainter painter = image.createPainter();
			harness.fire.paint(painter, WIDTH, HEIGHT);
			painter.dispose();
		}
	}

	private static boolean near(int rgb, int other, int tolerance) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs(((rgb >> shift) & 0xFF) - ((other >> shift) & 0xFF)) > tolerance) {
				return false;
			}
		}
		return true;
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
}
