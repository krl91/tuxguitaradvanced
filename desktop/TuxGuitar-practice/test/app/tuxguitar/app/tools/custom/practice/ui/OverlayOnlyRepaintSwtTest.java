package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.view.util.TGBufferedPainterListenerLocked;
import app.tuxguitar.app.view.util.TGBufferedPainterLocked;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.resource.UIResourceFactory;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;

/**
 * Native SWT test of the overlay-only repaint of the score (spec 019 T026, research R2): after a request for the overlay only, the next paint draws the
 * buffer as it is, without painting the score into it again; any request of a full picture since the last paint wins, whatever the order; a buffer of the
 * wrong size is painted again. Without a request, every paint paints the score, as before. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class OverlayOnlyRepaintSwtTest {

	private static final class Harness {
		final Object display;
		final UIWindow window;
		final UICanvas canvas;
		final TGBufferedPainterListenerLocked painter;
		int scorePaints;
		int paintEvents;

		Harness(Object display, final UIFactory factory) throws Exception {
			this.display = display;
			this.window = factory.createWindow();
			this.window.setLayout(new UITableLayout());
			this.canvas = factory.createCanvas(this.window, false);
			this.painter = new TGBufferedPainterListenerLocked(new TGContext(), new TGBufferedPainterLocked.TGBufferedPainterHandle() {
				public void paintControl(UIPainter painter) {
					Harness.this.scorePaints++;
				}

				public UICanvas getPaintableControl() {
					return Harness.this.canvas;
				}
			}) {
				@Override
				public UIResourceFactory getResourceFactory() {
					return factory;
				}
			};
			this.canvas.addPaintListener(this.painter);
			this.canvas.addPaintListener(event -> Harness.this.paintEvents++);
			((UITableLayout) this.window.getLayout()).set(this.canvas, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 400f, 300f, 0f);
			this.window.pack();
			this.window.setBounds(new UIRectangle(20, 20, 400, 300));
			this.window.open();
			paint();
		}

		void flush() throws Exception {
			Method readAndDispatch = this.display.getClass().getMethod("readAndDispatch");
			for (int i = 0; i < 200 && (Boolean) readAndDispatch.invoke(this.display); i++) {
				// drain the pending events
			}
		}

		/** One picture now: on Cocoa, update() alone does not always draw the view, copying it does (as {@code ScoreFireOverlaySwtTest}). */
		void paint() throws Exception {
			this.canvas.redraw();
			Object nativeCanvas = this.canvas.getClass().getMethod("getControl").invoke(this.canvas);
			Class.forName("org.eclipse.swt.widgets.Control").getMethod("update").invoke(nativeCanvas);
			flush();
			Class<?> gcClass = Class.forName("org.eclipse.swt.graphics.GC");
			Class<?> imageClass = Class.forName("org.eclipse.swt.graphics.Image");
			Object gc = gcClass.getConstructor(Class.forName("org.eclipse.swt.graphics.Drawable")).newInstance(nativeCanvas);
			Object image = imageClass.getConstructor(Class.forName("org.eclipse.swt.graphics.Device"), int.class, int.class).newInstance(this.display,
					Math.round(this.canvas.getBounds().getWidth()), Math.round(this.canvas.getBounds().getHeight()));
			gcClass.getMethod("copyArea", imageClass, int.class, int.class).invoke(gc, image, 0, 0);
			gcClass.getMethod("dispose").invoke(gc);
			imageClass.getMethod("dispose").invoke(image);
		}

		/**
		 * One picture now, as {paint events, score paints} during it: the toolkit may send more than one paint for a picture, and a request is used by the
		 * first one only.
		 */
		int[] step() throws Exception {
			int events = this.paintEvents;
			int score = this.scorePaints;
			paint();
			return new int[] { this.paintEvents - events, this.scorePaints - score };
		}
	}

	@Test
	public void theOverlayOnlyRepaintSkipsTheScoreUnlessAFullPictureWasAsked() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				assertEquals(true, harness.scorePaints > 0, "the first picture paints the score");

				int[] step = harness.step();
				assertEquals(true, step[0] > 0, "a picture is painted");
				assertEquals(step[0], step[1], "no request: the score is painted at each paint, as before");

				harness.painter.requestOverlayOnly();
				step = harness.step();
				assertEquals(true, step[0] > 0, "a picture is painted");
				assertEquals(step[0] - 1, step[1], "overlay only: the first paint draws the buffer as it is");

				harness.painter.requestFull();
				harness.painter.requestOverlayOnly();
				step = harness.step();
				assertEquals(step[0], step[1], "a full picture asked before wins");

				harness.painter.requestOverlayOnly();
				harness.painter.requestFull();
				step = harness.step();
				assertEquals(step[0], step[1], "a full picture asked after wins");

				step = harness.step();
				assertEquals(step[0], step[1], "the request is used once");

				int score = harness.scorePaints;
				harness.painter.requestOverlayOnly();
				harness.window.setBounds(new UIRectangle(20, 20, 500, 360));
				harness.flush();
				harness.paint();
				assertEquals(true, harness.scorePaints > score, "a buffer of another size is painted again");
			} finally {
				harness.window.dispose();
			}
		});
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
