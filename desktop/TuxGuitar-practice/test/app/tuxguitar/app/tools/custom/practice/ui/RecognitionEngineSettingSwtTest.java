package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the engine choice (spec 023, T061, FR-004, FR-005, contract §5): the list is <i>Automatic (Native)</i> or <i>Automatic (Java)</i>,
 * then <i>Native (Rust)</i> when its library loads, then <i>Java (FastYin)</i>, and the tip tells each one; a choice made in the native list is stored,
 * read back by a store reopened on the same file (a restart), and the next run follows it without a restart. Needs a macOS graphical session; runs
 * only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class RecognitionEngineSettingSwtTest {

	private static final int SWT_SELECTION = 13;

	@Test
	public void theEnginesAreListedChosenStoredAndFollowedByTheNextRun() throws Exception {
		Properties french = load();
		boolean loads = NativeRecognitionLibrary.load() == null;
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			window.setLayout(new UITableLayout());
			UIPanel root = factory.createPanel(window, false);
			root.setLayout(new UITableLayout());
			((UITableLayout) window.getLayout()).set(root, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			Memory file = new Memory();
			RecognitionSettingsStore store = new RecognitionSettingsStore(file);
			RecognitionSettingsPanel panel = new RecognitionSettingsPanel(factory, root, messages(french), store.get(), null, window::pack);
			panel.build();
			panel.getCategorySelect().setSelectedValue(Section.ENGINE);
			panel.showSection(Section.ENGINE);
			window.pack();
			window.open();
			flush(display);

			UIControl select = panel.getControl(RecognitionParameters.ENGINE_PITCH);
			String nativeText = french.getProperty("recognition.engine.pitch.option.native");
			String javaText = french.getProperty("recognition.engine.pitch.option.fastyin");
			String automatic = french.getProperty("recognition.engine.pitch.option.automatic").replace("{0}", loads ? "Natif" : "Java");
			String recommended = french.getProperty("recognition.option.recommended").replace("{0}", automatic);
			List<String> expected = new ArrayList<String>(Arrays.asList(recommended));
			if( loads ) {
				expected.add(nativeText);
			}
			expected.add(javaText);
			assertEquals(expected, items(select));
			assertTrue((Boolean) nativeControl(select).getClass().getMethod("getEnabled").invoke(nativeControl(select)), "a real choice");
			String tip = panel.tooltip(RecognitionParameters.ENGINE_PITCH);
			assertTrue(tip.startsWith(french.getProperty("recognition.engine.pitch.tip.v2")), tip);
			assertTrue(tip.contains("Natif (Rust)") && tip.contains("Java (FastYin)"), "the tip tells each engine: " + tip);

			// the learner picks Java: stored, read back after a restart, followed by the next run
			userSelects(display, select, expected.indexOf(javaText));
			assertEquals(PitchEngineCatalog.FASTYIN, panel.getPending().get(RecognitionParameters.ENGINE_PITCH));
			store.update(panel.getPending());
			RecognitionSettingsStore reopened = new RecognitionSettingsStore(file);
			assertEquals(PitchEngineCatalog.FASTYIN, reopened.get().get(RecognitionParameters.ENGINE_PITCH));
			assertEquals(PitchEngineCatalog.Kind.JAVA, PitchEngineCatalog.resolveKind(store.get()), "the next run, without a restart");
			if( loads ) {
				userSelects(display, select, expected.indexOf(nativeText));
				store.update(panel.getPending());
				assertEquals(PitchEngineCatalog.NATIVE, new RecognitionSettingsStore(file).get().get(RecognitionParameters.ENGINE_PITCH));
				assertEquals(PitchEngineCatalog.Kind.NATIVE, PitchEngineCatalog.resolveKind(store.get()));
			}
			window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static Object nativeControl(UIControl control) throws Exception {
		return control.getClass().getMethod("getControl").invoke(control);
	}

	private static List<String> items(UIControl select) throws Exception {
		Object combo = nativeControl(select);
		return Arrays.asList((String[]) combo.getClass().getMethod("getItems").invoke(combo));
	}

	/** The learner picks an item of the native list: selection, then SWT's selection event. */
	private static void userSelects(Object display, UIControl select, int index) throws Exception {
		Object combo = nativeControl(select);
		combo.getClass().getMethod("select", int.class).invoke(combo, index);
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(combo, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static void flush(Object display) throws Exception {
		Method read = display.getClass().getMethod("readAndDispatch");
		while( (Boolean) read.invoke(display) ) {
			// process pending events
		}
	}

	private static Properties load() throws Exception {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/messages_fr.properties")), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static RecognitionSettingsPanel.Messages messages(final Properties properties) {
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

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		private Map<String, String> values = new HashMap<String, String>();

		@Override
		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		@Override
		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}
}
