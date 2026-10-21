package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.ui.UIFactory;

/**
 * Spec 014 (T034, FR-006) on the real SWT toolkit: the start window of an Arcade start has its level row, with a size, inside the window, preselected with the
 * level the player has; a native selection changes the level; the rest of the window is still inside it. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class PreRunWindowLevelSwtTest {

	private static final int SWT_SELECTION = 13;

	private static final class Source implements PreRunSource {
		public StrumClipMeter.Reading reading() {
			return new StrumClipMeter.Reading(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
		}

		public double levelDbfs() {
			return Double.NaN;
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

	@Test
	public void theLevelRowIsInsideTheStartWindowAndANativeSelectionChangesTheLevel() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			final List<ArcadeLevel> stored = new ArrayList<ArcadeLevel>();
			final ArcadeLevel[] current = { ArcadeLevel.of(1) };
			PreRunWindow window = new PreRunWindow(factory, null, new PreRunCheck(FollowSettings.Mode.ARCADE), new Source(),
					(key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments)), () -> 0L, new Tuner(), () -> {
					}, new PreRunWindow.LevelChoice() {
						public ArcadeLevel level() {
							return current[0];
						}

						public void choose(ArcadeLevel level) {
							current[0] = level;
							stored.add(level);
						}
					});
			window.refresh();
			window.open();
			flush(display);
			assertEquals(ArcadeLevel.of(1), window.levelSelect().getSelectedValue(), "level 1 the first time");
			Class<?> control = Class.forName("org.eclipse.swt.widgets.Control");
			Class<?> rectangle = Class.forName("org.eclipse.swt.graphics.Rectangle");
			Object shell = window.windowWidget().getClass().getMethod("getControl").invoke(window.windowWidget());
			Object client = Class.forName("org.eclipse.swt.widgets.Composite").getMethod("getClientArea").invoke(shell);
			int clientWidth = rectangle.getField("width").getInt(client);
			int clientHeight = rectangle.getField("height").getInt(client);
			for (Object widget : new Object[] { window.levelLabel(), window.levelSelect() }) {
				Object nativeWidget = widget.getClass().getMethod("getControl").invoke(widget);
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
			window.levelSelect().setSelectedValue(ArcadeLevel.of(4));
			Object nativeSelect = window.levelSelect().getClass().getMethod("getControl").invoke(window.levelSelect());
			Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
			Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
			notify.invoke(nativeSelect, SWT_SELECTION, eventClass.getConstructor().newInstance());
			flush(display);
			assertEquals(List.of(ArcadeLevel.of(4)), stored, "a native selection stores the level");
			window.windowWidget().dispose();
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
