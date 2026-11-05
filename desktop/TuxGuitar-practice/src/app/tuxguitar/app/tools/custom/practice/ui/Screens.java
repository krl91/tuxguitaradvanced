package app.tuxguitar.app.tools.custom.practice.ui;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import app.tuxguitar.ui.resource.UIRectangle;

/**
 * Whether bounds kept from an earlier launch are on one of the screens now (spec 022, FR-020: a window kept on a screen that was unplugged opens where it can
 * be seen). The toolkit has no screens, so they are asked of SWT when it is the toolkit in use; with another toolkit, or when SWT does not answer, the bounds
 * are taken as they are.
 */
final class Screens {

	/** How much of the window's top must be on a screen to grab it. */
	private static final int GRIP = 40;

	private Screens() {
	}

	static boolean contains(UIRectangle bounds) {
		try {
			Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
			Object display = displayClass.getMethod("getCurrent").invoke(null);
			if (display == null) {
				return true;
			}
			Object monitors = displayClass.getMethod("getMonitors").invoke(display);
			for (int i = 0; i < Array.getLength(monitors); i++) {
				Object area = Array.get(monitors, i).getClass().getMethod("getClientArea").invoke(Array.get(monitors, i));
				int x = field(area, "x");
				int y = field(area, "y");
				int width = field(area, "width");
				int height = field(area, "height");
				float left = bounds.getX();
				float top = bounds.getY();
				if (left + GRIP <= x + width && left + bounds.getWidth() - GRIP >= x && top >= y && top + GRIP <= y + height) {
					return true;
				}
			}
			return false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			return true;
		}
	}

	/**
	 * The height of the client area (without the menu bar and the dock) of the screen holding the middle of {@code bounds} (spec 025, FR-005), or 0 when it is
	 * unknown: no bounds, another toolkit, SWT not answering, or a middle on no screen.
	 */
	static float usableHeight(UIRectangle bounds) {
		if (bounds == null) {
			return 0f;
		}
		try {
			Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
			Object display = displayClass.getMethod("getCurrent").invoke(null);
			if (display == null) {
				return 0f;
			}
			float middleX = bounds.getX() + bounds.getWidth() / 2f;
			float middleY = bounds.getY() + bounds.getHeight() / 2f;
			Object monitors = displayClass.getMethod("getMonitors").invoke(display);
			for (int i = 0; i < Array.getLength(monitors); i++) {
				Object area = Array.get(monitors, i).getClass().getMethod("getClientArea").invoke(Array.get(monitors, i));
				int x = field(area, "x");
				int y = field(area, "y");
				int width = field(area, "width");
				int height = field(area, "height");
				if (middleX >= x && middleX < x + width && middleY >= y && middleY < y + height) {
					return height;
				}
			}
			return 0f;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
			return 0f;
		}
	}

	private static int field(Object rectangle, String name) throws ReflectiveOperationException {
		Field field = rectangle.getClass().getField(name);
		return field.getInt(rectangle);
	}
}
