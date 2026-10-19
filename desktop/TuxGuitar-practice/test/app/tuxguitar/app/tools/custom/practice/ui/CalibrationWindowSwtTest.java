package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.ui.UIFactory;

/**
 * Native SWT test of the calibration window (found by the owner on the built application: cancelling at the piece step ended the interface thread). The real
 * window on the real toolkit, driven by native clicks, with a fake port: the cancel button at the steps that have one, and the cross.
 * Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class CalibrationWindowSwtTest {

	private static final int SWT_SELECTION = 13;
	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final CalibrationWindow.Texts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments));

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static final class Port implements CalibrationController.ListeningPort {
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
	}

	@Test
	public void cancellingAtTheTuningTheAdviceAndThePieceStepsClosesTheWindowAndLeavesTheInterfaceAlive() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			for (int stepsBefore = 0; stepsBefore <= 3; stepsBefore++) {
				List<State> finished = new ArrayList<State>();
				CalibrationWindow window = new CalibrationWindow(factory, null, TEXTS, false, Arrays.asList("a", "b", "c", "d"), listener -> new CalibrationController(4, KEY,
						new ProfileLibrary(new Memory()), RecognitionSettings::defaults, new Port(), new CalibrationController.TunerPort() {
							public boolean available() {
								return true;
							}

							public void open() {
							}

							public void setClosedListener(Runnable closed) {
							}
						}, (segments, rate) -> settings -> null, Runnable::run, () -> 1L, listener), Runnable::run, finished::add);
				window.open();
				flush(display);
				for (int i = 0; i < stepsBefore; i++) {
					click(display, window.button(0)); // calibrate, in tune, OK on the advice (the first button each time)
				}
				// the cancel button: third on the offer and on the tuning question, second on the advice and on the piece
				click(display, window.button(stepsBefore <= 1 ? 2 : 1));
				flush(display);
				assertEquals(Arrays.asList(State.CANCELLED), finished, "cancelled after " + stepsBefore + " steps");
			}
			// the cross: the question "Stop the calibration?" first, No keeps the window, Yes cancels and closes it
			List<State> finished = new ArrayList<State>();
			CalibrationWindow window = new CalibrationWindow(factory, null, TEXTS, false, Arrays.asList("a"), listener -> new CalibrationController(1, KEY,
					new ProfileLibrary(new Memory()), RecognitionSettings::defaults, new Port(), new CalibrationController.TunerPort() {
						public boolean available() {
							return true;
						}

						public void open() {
						}

						public void setClosedListener(Runnable closed) {
						}
					}, (segments, rate) -> settings -> null, Runnable::run, () -> 1L, listener), Runnable::run, finished::add);
			window.open();
			flush(display);
			window.window().close();
			flush(display);
			assertEquals("practice.profile.stop.question", String.valueOf(window.bodyLabel().getText()));
			click(display, window.button(1)); // No
			assertEquals(new ArrayList<State>(), finished);
			window.window().close();
			flush(display);
			click(display, window.button(0)); // Yes
			assertEquals(Arrays.asList(State.CANCELLED), finished);
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
