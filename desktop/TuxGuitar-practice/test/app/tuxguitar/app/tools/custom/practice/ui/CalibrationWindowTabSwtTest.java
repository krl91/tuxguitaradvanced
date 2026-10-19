package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController;
import app.tuxguitar.app.tools.custom.practice.ui.TabDiagram.Mark;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.ui.UIFactory;

/**
 * Spec 013 (T061, FR-021) on the real SWT toolkit: the picture of the tablature at the piece step has a size of at least 300 by 120 and lies inside the
 * window, and the native painting of it goes through. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class CalibrationWindowTabSwtTest {

	private static final int SWT_SELECTION = 13;

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	@Test
	public void thePictureHasItsSizeInsideTheWindowAndIsPaintedNatively() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			List<List<Mark>> sounds = Arrays.asList(Arrays.asList(new Mark(5, 2)), Arrays.asList(new Mark(6, 0), new Mark(5, 2), new Mark(4, 2)));
			CalibrationWindow window = new CalibrationWindow(factory, null, (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments)), false,
					Arrays.asList("a", "b"), sounds, listener -> new CalibrationController(2, DeviceKey.of("Micro", null, 0), new ProfileLibrary(new Memory()),
							RecognitionSettings::defaults, new CalibrationController.ListeningPort() {
								public void start(HopListener... listeners) {
								}

								public void stop() {
								}

								public FrontEndState frontEnd() {
									return new FrontEndState(1.0, false);
								}

								public int sampleRate() {
									return 48000;
								}
							}, new CalibrationController.TunerPort() {
								public boolean available() {
									return true;
								}

								public void open() {
								}

								public void setClosedListener(Runnable closed) {
								}
							}, (segments, rate) -> settings -> null, Runnable::run, () -> 1L, listener), Runnable::run, state -> {
							});
			window.open();
			flush(display);
			for (int i = 0; i < 3; i++) {
				click(display, window.button(0)); // calibrate, in tune, OK on the advice: the piece step
			}
			assertEquals(sounds.get(0), window.shownSound());
			Object canvas = window.tabCanvas().getClass().getMethod("getControl").invoke(window.tabCanvas());
			Class<?> control = Class.forName("org.eclipse.swt.widgets.Control");
			assertTrue((Boolean) control.getMethod("isVisible").invoke(canvas));
			Object bounds = control.getMethod("getBounds").invoke(canvas);
			Class<?> rectangle = Class.forName("org.eclipse.swt.graphics.Rectangle");
			int width = rectangle.getField("width").getInt(bounds);
			int height = rectangle.getField("height").getInt(bounds);
			assertTrue(width >= 300 && height >= 120, "the picture is " + width + "x" + height);
			Object shell = window.window().getClass().getMethod("getControl").invoke(window.window());
			Object client = Class.forName("org.eclipse.swt.widgets.Composite").getMethod("getClientArea").invoke(shell);
			assertTrue(rectangle.getField("x").getInt(bounds) >= 0 && rectangle.getField("x").getInt(bounds) + width <= rectangle.getField("width").getInt(client),
					"inside the window");
			control.getMethod("redraw").invoke(canvas);
			control.getMethod("update").invoke(canvas); // paints now: an exception of the painting would come out here
			flush(display);
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static void click(Object display, Object uiButton) throws Exception {
		Object nativeButton = uiButton.getClass().getMethod("getControl").invoke(uiButton);
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeButton, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 50 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}
}
