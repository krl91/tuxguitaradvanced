package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UILegendPanel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the Recognition settings on a 768-pixel-high screen (spec 026, T010, FR-005a, SC-007): the real panel, in a window built as the
 * Configuration dialog is ({@code TGSettingsEditor}: the option's legend panel, then the row of Defaults, OK and Cancel buttons), is packed and held
 * within 768 pixels as such a screen holds it. Every category's every control, value, name and <i>Restore</i> button then lies inside the window,
 * without scrolling. A second case builds, in the same window, a test panel with more value rows than 768 pixels hold, and expects the same check to
 * find a control outside: the check is seen red on a real overflow. Runs only with the profile {@code swt-gui-tests}:
 *
 * <pre>
 * cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
 * mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest=RecognitionSettingsFitSwtTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@Tag("swt-gui")
public class RecognitionSettingsFitSwtTest {

	private static final float USABLE_HEIGHT = 768f;

	/** The Configuration dialog around one option: the window and the option's panel. */
	private static final class Dialog {
		UIWindow window;
		UIPanel option;
	}

	private static Dialog dialog(UIFactory factory) {
		Dialog dialog = new Dialog();
		dialog.window = factory.createWindow(factory.createWindow(), true, false);
		UITableLayout dialogLayout = new UITableLayout();
		dialog.window.setLayout(dialogLayout);
		UIPanel main = factory.createPanel(dialog.window, false);
		main.setLayout(new UITableLayout(0f));
		dialogLayout.set(main, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
		UILegendPanel group = factory.createLegendPanel(main);
		group.setText("Audio recognition");
		UITableLayout groupLayout = new UITableLayout();
		group.setLayout(groupLayout);
		((UITableLayout) main.getLayout()).set(group, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, null, null, null, null, 0f);
		dialog.option = factory.createPanel(group, false);
		dialog.option.setLayout(new UITableLayout());
		groupLayout.set(dialog.option, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, true);

		UIPanel buttons = factory.createPanel(dialog.window, false);
		UITableLayout buttonsLayout = new UITableLayout();
		buttons.setLayout(buttonsLayout);
		dialogLayout.set(buttons, 2, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, false);
		String[] names = { "Defaults", "OK", "Cancel" };
		for( int i = 0 ; i < names.length ; i ++ ) {
			UIButton button = factory.createButton(buttons);
			button.setText(names[i]);
			buttonsLayout.set(button, 1, i + 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);
		}
		return dialog;
	}

	/** Packs the window, then holds it within the usable height, as a 768-pixel screen does. */
	private static void packOnScreen(UIWindow window) {
		window.pack();
		UIRectangle bounds = window.getBounds();
		window.setBounds(new UIRectangle(0f, 0f, bounds.getWidth(), Math.min(bounds.getHeight(), USABLE_HEIGHT)));
		window.layout();
	}

	@Test
	public void everyCategoryFitsA768PixelScreen() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			final Dialog dialog = dialog(factory);
			RecognitionSettingsPanel panel = new RecognitionSettingsPanel(factory, dialog.option, french(), RecognitionSettings.defaults(), null,
					new Runnable() {
						public void run() {
							packOnScreen(dialog.window);
						}
					});
			panel.build();
			packOnScreen(dialog.window);
			dialog.window.open();
			flush(display);

			for( Section section : Section.values() ) {
				panel.getCategorySelect().setSelectedValue(section);
				panel.showSection(section);
				flush(display);
				assertTrue(dialog.window.getBounds().getHeight() <= USABLE_HEIGHT, section + ": the window is " + dialog.window.getBounds().getHeight() + " high");
				for( RecognitionParameter parameter : panel.getShown(section) ) {
					assertTrue(inside(panel.getNameLabel(parameter)), section + ": " + parameter.getKey() + " name outside the window");
					assertTrue(inside(panel.getControl(parameter)), section + ": " + parameter.getKey() + " outside the window");
					assertTrue(inside(panel.getValueLabel(parameter)), section + ": " + parameter.getKey() + " value outside the window");
				}
				assertTrue(inside(panel.getRestoreSectionButton(section)), section + ": Restore outside the window");
				assertTrue(inside(panel.getRestoreAllButton()), section + ": Restore all outside the window");
			}
			dialog.window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	@Test
	public void aPanelTallerThanTheScreenIsSeenOutside() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			// one value row, measured, gives the number of rows that 768 pixels cannot hold
			Dialog probe = dialog(factory);
			addRow(factory, probe.option, 1);
			probe.window.pack();
			float oneRow = probe.window.getBounds().getHeight();
			addRow(factory, probe.option, 2);
			probe.window.pack();
			float rowHeight = probe.window.getBounds().getHeight() - oneRow;
			probe.window.dispose();
			assertTrue(rowHeight > 0f, "a value row has a height");
			int rows = (int) Math.ceil((USABLE_HEIGHT - oneRow) / rowHeight) + 3;

			Dialog dialog = dialog(factory);
			UIScale last = null;
			for( int row = 1 ; row <= rows ; row ++ ) {
				last = addRow(factory, dialog.option, row);
			}
			packOnScreen(dialog.window);
			dialog.window.open();
			flush(display);
			assertFalse(inside(last), "the last of " + rows + " rows (" + rowHeight + " pixels each) lies outside a 768-pixel window");
			dialog.window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	/** A value row as the panel builds it: the name, the slider, the value text. */
	private static UIScale addRow(UIFactory factory, UIPanel panel, int row) {
		UITableLayout layout = (UITableLayout) panel.getLayout();
		UILabel name = factory.createLabel(panel);
		name.setText("Setting " + row);
		layout.set(name, row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		UIScale scale = factory.createHorizontalScale(panel);
		scale.setMaximum(100);
		scale.setMinimum(0);
		layout.set(scale, row, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(scale, UITableLayout.PACKED_WIDTH, RecognitionControls.SLIDER_WIDTH);
		UILabel value = factory.createLabel(panel);
		value.setText("50 ms (recommended)");
		layout.set(value, row, 3, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(value, UITableLayout.PACKED_WIDTH, RecognitionSettingsPanel.VALUE_WIDTH);
		return scale;
	}

	/**
	 * The control's native bounds lie inside each parent's client area, up to the shell. The dialog's shell has the main window as its parent but
	 * is a window of its own, never inside it: the walk stops there (owner's exception of 2026-11-07, T042).
	 */
	private static boolean inside(UIControl control) throws Exception {
		Object current = control.getClass().getMethod("getControl").invoke(control);
		Class<?> controlClass = Class.forName("org.eclipse.swt.widgets.Control");
		while( true ) {
			Object parent = controlClass.getMethod("getParent").invoke(current);
			if( parent == null || Class.forName("org.eclipse.swt.widgets.Shell").isInstance(current) ) {
				return true;
			}
			Object bounds = controlClass.getMethod("getBounds").invoke(current);
			Object area = Class.forName("org.eclipse.swt.widgets.Scrollable").getMethod("getClientArea").invoke(parent);
			int x = field(bounds, "x"), y = field(bounds, "y"), w = field(bounds, "width"), h = field(bounds, "height");
			if( x < 0 || y < 0 || x + w > field(area, "width") || y + h > field(area, "height") ) {
				return false;
			}
			current = parent;
		}
	}

	private static int field(Object rectangle, String name) throws Exception {
		Field field = rectangle.getClass().getField(name);
		return field.getInt(rectangle);
	}

	private static void flush(Object display) throws Exception {
		Method read = display.getClass().getMethod("readAndDispatch");
		for( int i = 0 ; i < 50 && (Boolean) read.invoke(display) ; i ++ ) {
			// drain the pending events
		}
	}

	private static RecognitionSettingsPanel.Messages french() throws Exception {
		final Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/messages_fr.properties")), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return new RecognitionSettingsPanel.Messages() {
			public String get(String key) {
				String value = properties.getProperty(key);
				if( value == null ) {
					throw new AssertionError("missing text: " + key);
				}
				return value;
			}

			public String get(String key, String[] arguments) {
				String value = get(key);
				for( int i = 0 ; i < arguments.length ; i ++ ) {
					value = value.replace("{" + i + "}", arguments[i]);
				}
				return value;
			}
		};
	}
}
