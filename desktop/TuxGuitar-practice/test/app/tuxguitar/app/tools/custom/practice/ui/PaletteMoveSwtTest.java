package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIPosition;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UITextField;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Spec 017 (T008, SC-003, research R7) on the real SWT toolkit: moving the palette changes its position only; the control that had the keyboard focus
 * keeps it, so the practice shortcuts work without a click. Also checks the screen mapping of the toolkit (research R3). Runs only with the profile
 * {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class PaletteMoveSwtTest {

	@Test
	public void movingThePaletteKeepsTheFocusWhereItWasAndChangesOnlyItsPosition() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow main = factory.createWindow();
			main.setBounds(new UIRectangle(100f, 100f, 900f, 600f));
			UITextField field = factory.createTextField(main);
			field.setBounds(new UIRectangle(10f, 10f, 200f, 30f));
			main.open();
			UIWindow palette = factory.createWindow(main, false, false);
			palette.setBounds(new UIRectangle(600f, 150f, 300f, 200f));
			palette.open();
			flush(display);
			// a test run from a terminal is not the application in front: the focus may be nowhere; what is checked is that the move changes neither
			Object nativeMain = main.getClass().getMethod("getControl").invoke(main);
			nativeMain.getClass().getMethod("forceActive").invoke(nativeMain);
			field.setFocus();
			flush(display);
			Object focused = displayClass.getMethod("getFocusControl").invoke(display);
			Object active = displayClass.getMethod("getActiveShell").invoke(display);

			PracticePalette.moveWindow(palette, 150f, 420f);
			flush(display);

			assertSame(focused, displayClass.getMethod("getFocusControl").invoke(display), "the focus stays where it was");
			assertSame(active, displayClass.getMethod("getActiveShell").invoke(display), "and so does the active window");
			UIRectangle bounds = palette.getBounds();
			assertEquals(150f, bounds.getX(), 0.5f);
			assertEquals(420f, bounds.getY(), 0.5f);
			assertEquals(300f, bounds.getWidth(), 0.5f, "same size");
			assertEquals(200f, bounds.getHeight(), 0.5f, "same size");

			// the toolkit maps a point of a control to the screen: the field's origin is inside the main window
			UIPosition origin = field.toDisplay(new UIPosition(0f, 0f));
			UIRectangle window = main.getBounds();
			assertEquals(true, origin != null && origin.getX() >= window.getX() && origin.getY() >= window.getY(), "mapped inside the main window: " + origin);
			UIPosition shifted = field.toDisplay(new UIPosition(5f, 7f));
			assertEquals(origin.getX() + 5f, shifted.getX(), 0.5f);
			assertEquals(origin.getY() + 7f, shifted.getY(), 0.5f);
			palette.dispose();
			main.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 50 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}
}
