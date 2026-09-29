package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the "Audio recognition" page (P13): the real SWT toolkit and widgets, the
 * learner's slider moves sent as native selection events. Needs a macOS graphical session
 * (WindowServer) and the main thread; runs only with the profile {@code swt-gui-tests}:
 *
 * <pre>
 * cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
 * mvn -o test -P swt-gui-tests -pl ../../TuxGuitar-practice -am -Dtest=RecognitionSettingsSwtTest -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * SWT is reached by reflection so that the module compiles without it.
 */
@Tag("swt-gui")
public class RecognitionSettingsSwtTest {

	private static final int SWT_SELECTION = 13;

	@Test
	public void nativeSlidersAreIndependentShowTheirValueAtOnceAndStayInsideTheWindow() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			window.setLayout(new UITableLayout());
			UIPanel root = factory.createPanel(window, false);
			root.setLayout(new UITableLayout());
			((UITableLayout) window.getLayout()).set(root, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			RecognitionSettingsPanel panel = new RecognitionSettingsPanel(factory, root, french(), RecognitionSettings.defaults(), null, window::pack);
			panel.build();
			window.pack();
			window.open();
			flush(display);

			UIScale min = (UIScale) panel.getControl(RecognitionParameters.INPUT_LEVEL_MIN);
			UIScale max = (UIScale) panel.getControl(RecognitionParameters.INPUT_LEVEL_MAX);
			assertEquals(35, nativeInt(min, "getMaximum"));
			assertEquals(20, nativeInt(min, "getSelection"));
			assertEquals(14, nativeInt(max, "getSelection"));
			assertEquals("-40 dBFS (recommandé)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MIN).getText());

			userMoves(display, min, 30);
			assertEquals("-30 dBFS (recommandé : -40 dBFS)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MIN).getText());
			assertEquals(14, nativeInt(max, "getSelection"));
			assertEquals("-6 dBFS (recommandé)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MAX).getText());

			userMoves(display, max, 0);
			assertEquals("-20 dBFS (recommandé : -6 dBFS)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MAX).getText());
			assertEquals(30, nativeInt(min, "getSelection"));
			assertEquals("-30 dBFS (recommandé : -40 dBFS)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MIN).getText());

			userMoves(display, max, 19);
			userMoves(display, min, 0);
			assertEquals("-60 dBFS (recommandé : -40 dBFS)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MIN).getText());
			assertEquals(19, nativeInt(max, "getSelection"));
			assertEquals("-1 dBFS (recommandé : -6 dBFS)", panel.getValueLabel(RecognitionParameters.INPUT_LEVEL_MAX).getText());

			// every numeric setting, every category: native range, own text, and inside the window
			for( Section section : Section.values() ) {
				panel.getCategorySelect().setSelectedValue(section);
				panel.showSection(section);
				flush(display);
				for( RecognitionParameter parameter : panel.getShown(section) ) {
					UIControl control = panel.getControl(parameter);
					assertInsideWindow(control, parameter.getKey());
					assertInsideWindow(panel.getValueLabel(parameter), parameter.getKey() + " value");
					if( parameter instanceof RecognitionParameter.Number ) {
						RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
						int top = RecognitionControls.sliderMaximum(number);
						assertEquals(top, nativeInt(control, "getMaximum"), number.getKey());
						userMoves(display, (UIScale) control, top);
						assertTrue(panel.getValueLabel(number).getText().startsWith(RecognitionControls.displayNumber(number, number.getMaximum())),
								number.getKey() + ": " + panel.getValueLabel(number).getText());
					}
				}
				assertInsideWindow(panel.getRestoreSectionButton(section), section + " restore");
			}
			window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	/** The learner drags the native slider: selection changes, then SWT's selection event. */
	private static void userMoves(Object display, UIScale scale, int position) throws Exception {
		Object nativeScale = scale.getClass().getMethod("getControl").invoke(scale);
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeScale, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static int nativeInt(UIControl control, String method) throws Exception {
		Object nativeControl = control.getClass().getMethod("getControl").invoke(control);
		return (Integer) nativeControl.getClass().getMethod(method).invoke(nativeControl);
	}

	/** The control's native bounds lie inside each parent's client area, up to the shell. */
	private static void assertInsideWindow(UIControl control, String what) throws Exception {
		Object current = control.getClass().getMethod("getControl").invoke(control);
		Class<?> controlClass = Class.forName("org.eclipse.swt.widgets.Control");
		while( true ) {
			Object parent = controlClass.getMethod("getParent").invoke(current);
			if( parent == null ) {
				return;
			}
			Object bounds = controlClass.getMethod("getBounds").invoke(current);
			Object area = Class.forName("org.eclipse.swt.widgets.Scrollable").getMethod("getClientArea").invoke(parent);
			int x = field(bounds, "x"), y = field(bounds, "y"), w = field(bounds, "width"), h = field(bounds, "height");
			assertTrue(x >= 0 && y >= 0 && x + w <= field(area, "width") && y + h <= field(area, "height"),
					what + " outside its container: " + bounds + " in " + area);
			current = parent;
		}
	}

	private static int field(Object rectangle, String name) throws Exception {
		Field field = rectangle.getClass().getField(name);
		return field.getInt(rectangle);
	}

	private static void flush(Object display) throws Exception {
		Method read = display.getClass().getMethod("readAndDispatch");
		while( (Boolean) read.invoke(display) ) {
			// process pending events
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
