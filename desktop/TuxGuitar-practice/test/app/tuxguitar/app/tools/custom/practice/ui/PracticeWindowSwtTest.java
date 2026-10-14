package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.ui.UIFactory;

/**
 * Native SWT test of the start window (spec 010 T025): the real {@link PreRunWindow} on the real toolkit, driven by native events, behind the real
 * {@link StartFlow}. {@code PracticeWindow} itself needs the whole application and no test builds it, so what is checked here is the start window and the
 * flow it lives in: one window for a double press, the mode started once, the close button cancelling the start, the listeners released, nothing outside
 * the window. That the restarts never reach the flow is checked on the source by {@code PracticeWindowStartPathSourceTest}. Runs only with the profile
 * {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class PracticeWindowSwtTest {

	private static final int SWT_SELECTION = 13;

	private static final class Source implements PreRunSource {
		StrumClipMeter.Reading reading = new StrumClipMeter.Reading(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
		int closed;

		public StrumClipMeter.Reading reading() {
			return this.reading;
		}

		public double levelDbfs() {
			return -30.0;
		}

		public boolean boostActive() {
			return false;
		}

		public void reset() {
			this.reading = new StrumClipMeter.Reading(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
		}

		public void close() {
			this.closed++;
		}
	}

	private static final class Tuner implements PreRunWindow.TunerOpener {
		int opened;
		Runnable closedListener;

		public boolean available() {
			return true;
		}

		public void open() {
			this.opened++;
		}

		public void toFront() {
		}

		public void setClosedListener(Runnable listener) {
			this.closedListener = listener;
		}
	}

	/** A gate with the real window and a fake source, as {@code PreRunStartGate} makes it with the engine. */
	private static final class Gate implements StartGate {
		final UIFactory factory;
		final Source source = new Source();
		final Tuner tuner = new Tuner();
		PreRunWindow current;
		int windows;

		Gate(UIFactory factory) {
			this.factory = factory;
		}

		public void request(PreRunCheck check, Runnable onReady) {
			this.windows++;
			this.current = new PreRunWindow(this.factory, null, check, this.source, (key, arguments) -> key, System::nanoTime, this.tuner, () -> {
				this.current = null;
				if (check.state() == State.READY) {
					onReady.run();
				}
			});
			this.current.refresh();
			this.current.open();
		}

		public void toFront() {
			if (this.current != null) {
				this.current.toFront();
			}
		}

		public void closePending() {
			if (this.current != null) {
				this.current.refresh();
			}
		}
	}

	@Test
	public void aDoublePressOpensOneStartWindowStartsOnceAndTheCloseButtonCancelsAtOnce() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			Gate gate = new Gate(factory);
			StartFlow flow = new StartFlow(gate);
			List<String> started = new ArrayList<String>();

			// a double press of Start, then the transport-bar button while the window is open: one window
			assertTrue(flow.begin(new PreRunCheck(FollowSettings.Mode.ARCADE), () -> started.add("run")));
			flush(display);
			assertFalse(flow.begin(new PreRunCheck(FollowSettings.Mode.ARCADE), () -> started.add("second")));
			assertTrue(flow.pending());
			assertEquals(1, gate.windows, "one start window");
			PreRunWindow window = gate.current;
			assertInside(display, window);

			// the question is answered by native events; the strum part appears, the question goes
			assertTrue(nativeBoolean(nativeOf(window.yesButton()), "isVisible"));
			assertFalse(nativeBoolean(nativeOf(window.meterBar()), "isVisible"), "no level part before the answer");
			click(display, nativeOf(window.yesButton()));
			assertTrue(nativeBoolean(nativeOf(window.meterBar()), "isVisible"));
			assertFalse(nativeBoolean(nativeOf(window.yesButton()), "isVisible"));
			assertInside(display, window);

			// a clipping strum blocks, with the buttons of the bad level; the mode has not begun
			gate.source.reading = new StrumClipMeter.Reading(StrumVerdict.CLIPPING, 0.02, -6.0);
			window.refresh();
			flush(display);
			assertTrue(nativeBoolean(nativeOf(window.continueButton()), "isVisible"));
			assertTrue(nativeBoolean(nativeOf(window.checkAgainButton()), "isVisible"));
			assertEquals(0, started.size());
			assertInside(display, window);

			// the close button cancels the start; the listeners are released and a new press is accepted at once
			Object shell = nativeOf(window.windowWidget());
			shell.getClass().getMethod("close").invoke(shell);
			flush(display);
			assertFalse(flow.pending());
			assertEquals(0, started.size(), "closing never starts the mode");
			assertEquals(1, gate.source.closed, "its listeners are released");
			assertTrue(flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), () -> started.add("again")));
			assertEquals(2, gate.windows);

			// a good strum after the question starts the mode once
			PreRunWindow second = gate.current;
			click(display, nativeOf(second.yesButton()));
			gate.source.reading = new StrumClipMeter.Reading(StrumVerdict.GOOD, 0.0, -20.0);
			second.refresh();
			flush(display);
			assertEquals(List.of("again"), started);
			assertFalse(flow.pending());
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static Object nativeOf(Object uiControl) throws Exception {
		return uiControl.getClass().getMethod("getControl").invoke(uiControl);
	}

	private static void click(Object display, Object nativeButton) throws Exception {
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeButton, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static boolean nativeBoolean(Object nativeControl, String method) throws Exception {
		return (Boolean) Class.forName("org.eclipse.swt.widgets.Control").getMethod(method).invoke(nativeControl);
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 50 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}

	/** Every visible native control lies inside the client area of the start window. */
	private static void assertInside(Object display, PreRunWindow window) throws Exception {
		Object shell = nativeOf(window.windowWidget());
		Class<?> controlClass = Class.forName("org.eclipse.swt.widgets.Control");
		Class<?> compositeClass = Class.forName("org.eclipse.swt.widgets.Composite");
		Class<?> rectangleClass = Class.forName("org.eclipse.swt.graphics.Rectangle");
		Object client = compositeClass.getMethod("getClientArea").invoke(shell);
		int clientWidth = rectangleClass.getField("width").getInt(client);
		int clientHeight = rectangleClass.getField("height").getInt(client);
		List<Object> controls = new ArrayList<Object>();
		collect(shell, controls, compositeClass);
		Method map = display.getClass().getMethod("map", controlClass, controlClass, rectangleClass);
		for (Object control : controls) {
			if (control == shell || !(Boolean) controlClass.getMethod("isVisible").invoke(control)) {
				continue;
			}
			Object bounds = controlClass.getMethod("getBounds").invoke(control);
			Object parent = controlClass.getMethod("getParent").invoke(control);
			Object inShell = map.invoke(display, parent, shell, bounds);
			int x = rectangleClass.getField("x").getInt(inShell);
			int y = rectangleClass.getField("y").getInt(inShell);
			int width = rectangleClass.getField("width").getInt(inShell);
			int height = rectangleClass.getField("height").getInt(inShell);
			assertTrue(x >= 0 && y >= 0 && x + width <= clientWidth && y + height <= clientHeight,
					control.getClass().getSimpleName() + " at " + x + "," + y + " " + width + "x" + height + " is outside the window " + clientWidth + "x" + clientHeight);
		}
	}

	private static void collect(Object control, List<Object> into, Class<?> compositeClass) throws Exception {
		into.add(control);
		if (compositeClass.isInstance(control)) {
			for (Object child : (Object[]) compositeClass.getMethod("getChildren").invoke(control)) {
				collect(child, into, compositeClass);
			}
		}
	}
}
