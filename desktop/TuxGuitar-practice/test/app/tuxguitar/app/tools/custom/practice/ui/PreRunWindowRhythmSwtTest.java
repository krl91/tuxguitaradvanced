package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.widget.UIControl;

/**
 * Spec 016 (T020, SC-005) on the real SWT toolkit: the offer of the rhythm calibration inside the start window, with a size, at the small size of the
 * window; one native click on Calibrate now starts the clicks; a native click on Go on without lets the mode begin; the Escape key begins nothing. Runs
 * only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class PreRunWindowRhythmSwtTest {

	private static final int SWT_SELECTION = 13;
	private static final int SWT_KEY_DOWN = 1;
	private static final int SWT_ESC = 27;

	private static final class Source implements PreRunSource {
		public StrumClipMeter.Reading reading() {
			return new StrumClipMeter.Reading(StrumVerdict.GOOD, 0.0, -20.0);
		}

		public double levelDbfs() {
			return -20.0;
		}

		public boolean boostActive() {
			return false;
		}

		public void reset() {
		}

		public void close() {
		}
	}

	private static final class Tuner implements PreRunWindow.TunerOpener {
		public boolean available() {
			return true;
		}

		public void open() {
		}

		public void toFront() {
		}

		public void setClosedListener(Runnable listener) {
		}
	}

	private interface Body {
		void run(Object display, PreRunWindow window, FollowPanelTest.FakeBackend backend, AtomicInteger finished) throws Exception;
	}

	private static void withOffer(Body body) throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			FollowPanelTest.FakeBackend backend = new FollowPanelTest.FakeBackend();
			AtomicInteger finished = new AtomicInteger();
			PreRunCheck check = new PreRunCheck(FollowSettings.Mode.FOLLOW);
			PreRunWindow window = new PreRunWindow(factory, null, check, new Source(), (key, arguments) -> key, () -> 0L, new Tuner(), finished::incrementAndGet, null,
					new RhythmOffer(FollowSettings.Mode.FOLLOW, backend::route, backend.calibrations, new DeclinedRoutes()), backend);
			window.refresh();
			window.open();
			check.answerTuned(true);
			window.refresh();
			flush(display);
			body.run(display, window, backend, finished);
			if (!window.windowWidget().isDisposed()) {
				window.windowWidget().dispose();
			}
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	@Test
	public void theOfferIsInsideTheSmallStartWindowAndOneClickStartsTheClicks() throws Exception {
		withOffer((display, window, backend, finished) -> {
			assertEquals(0, finished.get(), "the offer is shown, the window stays");
			Object shell = nativeOf(window.windowWidget());
			Class<?> rectangle = Class.forName("org.eclipse.swt.graphics.Rectangle");
			Object client = Class.forName("org.eclipse.swt.widgets.Composite").getMethod("getClientArea").invoke(shell);
			int clientWidth = rectangle.getField("width").getInt(client);
			int clientHeight = rectangle.getField("height").getInt(client);
			for (UIControl widget : new UIControl[] { window.rhythmSentenceLabel(), window.calibrateNowButton(), window.goOnWithoutButton(), window.cancelButton() }) {
				assertInside(display, shell, nativeOf(widget), clientWidth, clientHeight);
			}
			click(window.calibrateNowButton());
			flush(display);
			assertNotNull(backend.begun, "one click on Calibrate now starts the calibration");
			assertEquals(CalibrationRun.State.RUNNING, backend.begun.state());
			assertFalse(window.mayBegin());
		});
	}

	@Test
	public void aNativeClickOnGoOnWithoutLetsTheModeBegin() throws Exception {
		withOffer((display, window, backend, finished) -> {
			click(window.goOnWithoutButton());
			flush(display);
			assertEquals(1, finished.get());
			assertTrue(window.mayBegin());
		});
	}

	@Test
	public void theEscapeKeyBeginsNothing() throws Exception {
		withOffer((display, window, backend, finished) -> {
			click(window.calibrateNowButton());
			flush(display);
			Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
			Object event = eventClass.getConstructor().newInstance();
			eventClass.getField("keyCode").setInt(event, SWT_ESC);
			eventClass.getField("character").setChar(event, (char) SWT_ESC);
			Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
			notify.invoke(nativeOf(window.windowWidget()), SWT_KEY_DOWN, event);
			flush(display);
			assertEquals(1, finished.get(), "the window gave the hand back");
			assertFalse(window.mayBegin());
			assertEquals(CalibrationRun.State.CANCELLED, backend.begun.state(), "the clicks stopped");
		});
	}

	private static Object nativeOf(Object widget) throws Exception {
		return widget.getClass().getMethod("getControl").invoke(widget);
	}

	private static void click(Object widget) throws Exception {
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeOf(widget), SWT_SELECTION, eventClass.getConstructor().newInstance());
	}

	private static void assertInside(Object display, Object shell, Object nativeWidget, int clientWidth, int clientHeight) throws Exception {
		Class<?> control = Class.forName("org.eclipse.swt.widgets.Control");
		Class<?> rectangle = Class.forName("org.eclipse.swt.graphics.Rectangle");
		assertTrue((Boolean) control.getMethod("isVisible").invoke(nativeWidget), "visible");
		Object bounds = control.getMethod("getBounds").invoke(nativeWidget);
		Object parent = control.getMethod("getParent").invoke(nativeWidget);
		Object inShell = display.getClass().getMethod("map", control, control, rectangle).invoke(display, parent, shell, bounds);
		int x = rectangle.getField("x").getInt(inShell);
		int y = rectangle.getField("y").getInt(inShell);
		int width = rectangle.getField("width").getInt(inShell);
		int height = rectangle.getField("height").getInt(inShell);
		assertTrue(width > 0 && height > 0, "a size: " + width + "x" + height);
		assertTrue(x >= 0 && y >= 0 && x + width <= clientWidth && y + height <= clientHeight, "inside the window: " + x + "," + y + " " + width + "x" + height + " in " + clientWidth + "x" + clientHeight);
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 50 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}
}
