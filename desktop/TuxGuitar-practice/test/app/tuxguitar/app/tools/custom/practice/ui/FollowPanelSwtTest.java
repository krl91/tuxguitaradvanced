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
import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the tempo-following part of the preparation window (spec 002 T141): the real widgets, the learner's picks sent as native
 * events, the real layout of the window refitted as the real window does. It checks that the values shown are the ones the settings hold, that a
 * visible control or message is never outside the window after the content changed or the window was made small, and that closing and opening
 * again leaves no listener doubled and no stale value. Runs only with the profile {@code swt-gui-tests} (see {@link RecognitionSettingsSwtTest}).
 */
@Tag("swt-gui")
public class FollowPanelSwtTest {

	private static final int SWT_SELECTION = 13;

	@Test
	public void thePickedValuesReachTheSettingsAndEveryVisibleControlStaysInsideTheWindow() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			window.setLayout(new UITableLayout());
			FollowSettings settings = FollowPanelTest.settings();
			FollowPanelTest.FakeBackend backend = new FollowPanelTest.FakeBackend();
			FollowPanel panel = new FollowPanel(factory, window, settings, backend);
			((UITableLayout) window.getLayout()).set(panel.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			UIRectangle area = new UIRectangle(0, 0, 1440, 900);
			panel.setContentListener(() -> WindowFit.refit(window, area));
			window.pack();
			window.open();
			flush(display);
			WindowFit.refit(window, area);
			flush(display);

			// the wait mode first: nothing of the tempo-following mode is shown
			Object percent = nativeOf(panel.percentSpinner());
			Object mode = nativeOf(panel.modeSelect());
			assertFalse(nativeBoolean(percent, "isVisible"), "the percentage belongs to the tempo-following mode");
			assertEquals(FollowSettings.Mode.WAIT, settings.getMode());
			float waitHeight = window.getBounds().getHeight();
			assertInside(display, window, panel);

			// the learner picks the tempo-following mode: the controls appear and the window grows to show them all
			selectIndex(display, mode, 1);
			assertEquals(FollowSettings.Mode.FOLLOW, settings.getMode());
			assertTrue(nativeBoolean(percent, "isVisible"));
			assertTrue(window.getBounds().getHeight() > waitHeight, "the window made room for what appeared: " + waitHeight + " -> " + window.getBounds().getHeight() + " packed " + window.getPackedSize().getHeight());
			assertInside(display, window, panel);

			// the percentage and the tolerance, as native events; each reaches the settings and none moves the other
			setSpinner(display, percent, 70);
			assertEquals(70, settings.getPercent());
			selectIndex(display, nativeOf(panel.toleranceSelect()), 2);
			assertEquals(TimingTolerance.Preset.LENIENT, settings.getTolerance());
			assertEquals(70, nativeInt(percent, "getSelection"), "the percentage stays when the tolerance changes");
			setSpinner(display, percent, 5);
			assertEquals(FollowSettings.MIN_PERCENT, settings.getPercent(), "outside its bounds, brought back");
			assertEquals(FollowSettings.MIN_PERCENT, nativeInt(percent, "getSelection"), "and shown as brought back");
			assertEquals(TimingTolerance.Preset.LENIENT, settings.getTolerance());

			// not calibrated: the reduced precision is said, in the window
			assertTrue(panel.precisionLabel().getText().contains("practice.follow.precision.reduced"));
			assertInside(display, window, panel);

			// a delay adjusted by hand, applied by a native click
			setSpinner(display, nativeOf(panel.manualSpinner()), -35);
			click(display, nativeOf(panel.manualApplyButton()));
			assertEquals(-35_000_000L, backend.calibrations.forRoute(FollowPanelTest.ROUTE).offsetNanos());
			assertEquals("", panel.precisionLabel().getText());
			assertInside(display, window, panel);

			// a long message (a calibration that failed) must not leave the window either
			panel.message("A message long enough to wrap on several lines inside the window of the learner, so that it must be shown whole "
					+ "and not be cut off at the edge of the window or pushed under the buttons, in any language of the application.");
			flush(display);
			assertInside(display, window, panel);

			// the window made small by the learner: refitted as the real window does, nothing is left outside
			window.setBounds(new UIRectangle(100, 100, 260, 180));
			flush(display);
			WindowFit.refit(window, area);
			flush(display);
			assertInside(display, window, panel);

			// a calibration is stopped by Cancel, a native click, and the sound with it
			click(display, nativeOf(panel.calibrateButton()));
			assertTrue(panel.isCalibrating());
			assertFalse(nativeBoolean(nativeOf(panel.calibrateButton()), "isEnabled"));
			assertTrue(nativeBoolean(nativeOf(panel.cancelButton()), "isEnabled"));
			click(display, nativeOf(panel.cancelButton()));
			assertFalse(panel.isCalibrating());
			assertEquals(1, backend.clicks.silenced);
			assertEquals(1, backend.ended);

			// closing the window during a calibration stops it too, once
			click(display, nativeOf(panel.calibrateButton()));
			assertTrue(panel.isCalibrating());
			window.dispose();
			panel.dispose();
			assertEquals(2, backend.clicks.silenced);
			assertEquals(2, backend.ended);
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	@Test
	public void closingAndOpeningAgainLeavesNoListenerDoubledAndNoStaleValue() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			FollowSettings settings = FollowPanelTest.settings();
			FollowPanelTest.FakeBackend backend = new FollowPanelTest.FakeBackend();
			int[] modeChanges = new int[1];
			for( int opening = 1 ; opening <= 3 ; opening ++ ) {
				UIWindow window = factory.createWindow();
				window.setLayout(new UITableLayout());
				FollowPanel panel = new FollowPanel(factory, window, settings, backend);
				panel.setModeListener(() -> modeChanges[0]++);
				((UITableLayout) window.getLayout()).set(panel.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
				window.pack();
				window.open();
				flush(display);

				// what the settings held at the close is what the new window shows
				assertEquals(opening == 1 ? FollowSettings.Mode.WAIT : FollowSettings.Mode.FOLLOW, panel.modeSelect().getSelectedValue());
				if( opening == 1 ) {
					selectIndex(display, nativeOf(panel.modeSelect()), 1);
					setSpinner(display, nativeOf(panel.percentSpinner()), 85);
					assertEquals(1, modeChanges[0], "one native pick, one change");
				} else {
					assertEquals(85, nativeInt(nativeOf(panel.percentSpinner()), "getSelection"), "the value of the last opening, not a default");
					int before = modeChanges[0];
					selectIndex(display, nativeOf(panel.modeSelect()), 0);
					assertEquals(before + 1, modeChanges[0], "a listener of an earlier window is not called again");
					selectIndex(display, nativeOf(panel.modeSelect()), 1);
				}
				window.dispose();
				panel.dispose();
			}
			assertEquals(85, settings.getPercent());
			assertFalse(backend.calibrations.forRoute(FollowPanelTest.ROUTE).isCalibrated());
			assertEquals(0, LatencyCompensation.none().offsetNanos());
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	// ------------------------------------------------------------------ native helpers (SWT is reached by reflection)

	private static Object nativeOf(Object uiControl) throws Exception {
		return uiControl.getClass().getMethod("getControl").invoke(uiControl);
	}

	private static void selectIndex(Object display, Object nativeCombo, int index) throws Exception {
		nativeCombo.getClass().getMethod("select", int.class).invoke(nativeCombo, index);
		notifySelection(display, nativeCombo);
	}

	private static void setSpinner(Object display, Object nativeSpinner, int value) throws Exception {
		nativeSpinner.getClass().getMethod("setSelection", int.class).invoke(nativeSpinner, value);
		notifySelection(display, nativeSpinner);
	}

	private static void click(Object display, Object nativeButton) throws Exception {
		notifySelection(display, nativeButton);
	}

	private static void notifySelection(Object display, Object nativeWidget) throws Exception {
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeWidget, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static int nativeInt(Object nativeControl, String method) throws Exception {
		return (Integer) nativeControl.getClass().getMethod(method).invoke(nativeControl);
	}

	private static boolean nativeBoolean(Object nativeControl, String method) throws Exception {
		return (Boolean) Class.forName("org.eclipse.swt.widgets.Control").getMethod(method).invoke(nativeControl);
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for( int i = 0 ; i < 50 && (Boolean) readAndDispatch.invoke(display) ; i ++ ) {
			// drain the pending events
		}
	}

	/** Every visible native control of the panel lies inside the client area of the shell: no button and no message is left outside. */
	private static void assertInside(Object display, UIWindow window, FollowPanel panel) throws Exception {
		Object shell = nativeOf(window);
		Class<?> controlClass = Class.forName("org.eclipse.swt.widgets.Control");
		Class<?> compositeClass = Class.forName("org.eclipse.swt.widgets.Composite");
		Class<?> rectangleClass = Class.forName("org.eclipse.swt.graphics.Rectangle");
		Object client = compositeClass.getMethod("getClientArea").invoke(shell);
		int clientWidth = rectangleClass.getField("width").getInt(client);
		int clientHeight = rectangleClass.getField("height").getInt(client);
		List<Object> controls = new ArrayList<Object>();
		collect(nativeOf(panel.getControl()), controls, compositeClass, controlClass);
		Method map = display.getClass().getMethod("map", controlClass, controlClass, rectangleClass);
		for( Object control : controls ) {
			if( !(Boolean) controlClass.getMethod("isVisible").invoke(control) ) {
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

	private static void collect(Object control, List<Object> into, Class<?> compositeClass, Class<?> controlClass) throws Exception {
		into.add(control);
		if( compositeClass.isInstance(control) ) {
			for( Object child : (Object[]) compositeClass.getMethod("getChildren").invoke(control) ) {
				collect(child, into, compositeClass, controlClass);
			}
		}
	}
}
