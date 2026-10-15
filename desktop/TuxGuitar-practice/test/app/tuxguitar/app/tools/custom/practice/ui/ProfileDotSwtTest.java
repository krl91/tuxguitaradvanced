package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.ProfileState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Spec 013 (T043): the dot, its text and its two buttons on the real SWT toolkit. The dot and the buttons exist, are visible and have a size inside the
 * window, the dot follows the profile of the selected device, and the buttons act on a native click. {@code InputSelectorPanel} itself needs the real input
 * catalog and the whole application, so it is not built here: what the panel adds is checked on the source by {@code InputSelectorPanelOptInTest}. Runs only
 * with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class ProfileDotSwtTest {

	private static final int SWT_SELECTION = 13;
	private static final DeviceKey MIC = DeviceKey.of("Micro MacBook Pro", null, 0);

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static final class Host implements ProfileDot.Host {
		DeviceKey selected = MIC;
		int calibrations;
		int confirmations;

		public DeviceKey selectedKey() {
			return this.selected;
		}

		public int sampleRate() {
			return 48000;
		}

		public FrontEndState frontEnd() {
			return new FrontEndState(1.0, false);
		}

		public void confirmForget(Runnable yes) {
			this.confirmations++;
			yes.run();
		}

		public void calibrate() {
			this.calibrations++;
		}
	}

	@Test
	public void theDotAndTheButtonsAreShownInsideTheWindowAndActOnANativeClick() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			ProfileLibrary library = new ProfileLibrary(new Memory());
			Host host = new Host();
			UIWindow window = factory.createWindow();
			app.tuxguitar.ui.layout.UITableLayout windowLayout = new app.tuxguitar.ui.layout.UITableLayout();
			window.setLayout(windowLayout);
			ProfileDot dot = new ProfileDot(factory, window, (key, arguments) -> key + (arguments.length == 0 ? "" : java.util.Arrays.toString(arguments)), library, host);
			windowLayout.set(dot.control(), 1, 1, app.tuxguitar.ui.layout.UITableLayout.ALIGN_FILL, app.tuxguitar.ui.layout.UITableLayout.ALIGN_FILL, true, true);
			window.pack();
			window.open();
			// as the preparation window does when its content changes: the layout is redone and the window refitted
			window.pack();
			flush(display);

			assertEquals(ProfileState.ORANGE, dot.state());
			for (Object control : new Object[] { dot.dotLabel(), dot.textLabel(), dot.calibrateButton(), dot.forgetButton() }) {
				Object shown = nativeOf(control);
				assertTrue(nativeBoolean(shown, "isVisible"));
				Object size = Class.forName("org.eclipse.swt.widgets.Control").getMethod("getSize").invoke(shown);
				Class<?> point = Class.forName("org.eclipse.swt.graphics.Point");
				assertTrue(point.getField("x").getInt(size) > 0 && point.getField("y").getInt(size) > 0, "a size: " + control + " " + point.getField("x").getInt(size) + "x" + point.getField("y").getInt(size) + " text=" + textOf(shown));
			}
			assertFalse(nativeBoolean(nativeOf(dot.forgetButton()), "isEnabled"), "nothing to forget yet");
			assertFalse(nativeBoolean(nativeOf(dot.warningLabel()), "isVisible"));

			click(display, nativeOf(dot.calibrateButton()));
			assertEquals(1, host.calibrations);

			Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
			values.put(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_ACCEPT.getMinimum());
			assertTrue(library.save(new RecognitionProfile(MIC, 48000, 1L, values, new RecognitionRate(9, 10, 0, 0), new RecognitionRate(5, 10, 0, 0),
					new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED)));
			dot.refresh();
			flush(display);
			assertEquals(ProfileState.GREEN, dot.state());
			assertTrue(nativeBoolean(nativeOf(dot.forgetButton()), "isEnabled"));

			click(display, nativeOf(dot.forgetButton()));
			assertEquals(1, host.confirmations);
			assertEquals(ProfileState.ORANGE, dot.state());

			host.selected = null;
			dot.refresh();
			assertEquals(ProfileState.NONE, dot.state());
			assertFalse(nativeBoolean(nativeOf(dot.calibrateButton()), "isEnabled"));
			window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static String textOf(Object control) {
		try {
			return String.valueOf(control.getClass().getMethod("getText").invoke(control));
		} catch (Exception exception) {
			return "";
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
}
