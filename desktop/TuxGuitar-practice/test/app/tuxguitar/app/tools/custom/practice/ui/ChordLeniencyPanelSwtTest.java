package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.ChordLeniency;
import app.tuxguitar.audio.engine.settings.ChordLeniency.Level;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the chord check setting (spec 024, task T031; contracts/chord-leniency.md, FR-005, FR-006, FR-008): the real widgets driven by
 * native events, on a store of its own. Choosing a level writes its values into the store's default layer; a value changed in the store shows
 * <i>Custom</i> and its line; choosing a level again removes <i>Custom</i>; the control stays enabled. Its place in the <i>Mode</i> tab is checked by
 * {@link ChordLeniencyPanelSourceTest}. Texts are their keys here (no application
 * loaded). Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class ChordLeniencyPanelSwtTest {

	private static final int SWT_SELECTION = 13;
	private static final String STRICT = "practice.chord-leniency.strict";
	private static final String NORMAL = "practice.chord-leniency.normal";
	private static final String LENIENT = "practice.chord-leniency.lenient";
	private static final String CUSTOM = "practice.chord-leniency.custom";

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	@Test
	public void choosingALevelWritesItsValuesAndAValueChangedByHandShowsCustom() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			window.setLayout(new UITableLayout());
			RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
			ChordLeniencyPanel panel = new ChordLeniencyPanel(factory, window, store, Runnable::run);
			((UITableLayout) window.getLayout()).set(panel.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			window.pack();
			window.open();
			flush(display);

			Object select = nativeOf(panel.levelSelect());
			assertTrue(panel.label().getText().contains("practice.chord-leniency"), "the label: " + panel.label().getText());
			assertEquals(Arrays.asList(STRICT, NORMAL, LENIENT), items(select), "the three levels, no Custom with the defaults");
			assertEquals(NORMAL, text(select), "the defaults are Normal");
			assertEquals(NORMAL + ".line", panel.line().getText());

			// the learner picks Lenient: its values reach the default layer at once
			userSelects(display, select, 2);
			assertEquals(Level.LENIENT, ChordLeniency.of(store.getDefault()));
			assertEquals(0.70, store.getDefault().get(RecognitionParameters.CHORD_PROFILE_MATCH), 1e-12);
			assertEquals(LENIENT + ".line", panel.line().getText());
			assertTrue(enabled(select));

			// a level value changed by hand (the Recognition settings window): Custom appears, chosen, with its line
			store.update(store.getDefault().with(RecognitionParameters.CHORD_PROFILE_MATCH, 0.75));
			flush(display);
			assertEquals(Arrays.asList(STRICT, NORMAL, LENIENT, CUSTOM), items(select));
			assertEquals(CUSTOM, text(select));
			assertEquals(CUSTOM + ".line", panel.line().getText());
			assertTrue(enabled(select), "the control stays enabled");

			// a value that is no level value leaves the level as it is
			RecognitionSettings beforeEvidence = store.getDefault();
			store.update(beforeEvidence.with(RecognitionParameters.CHORD_EVIDENCE, 0.5));
			flush(display);
			assertEquals(CUSTOM, text(select));

			// choosing Strict again: its values are written, Custom is removed, the value that is not a level value stays
			userSelects(display, select, 0);
			assertEquals(Level.STRICT, ChordLeniency.of(store.getDefault()));
			assertFalse(store.getDefault().get(RecognitionParameters.CHORD_ATTACK));
			assertEquals(0.5, store.getDefault().get(RecognitionParameters.CHORD_EVIDENCE), 1e-12);
			assertEquals(Arrays.asList(STRICT, NORMAL, LENIENT), items(select));
			assertEquals(STRICT, text(select));
			assertEquals(STRICT + ".line", panel.line().getText());

			// Restore of the chords section gives Normal
			store.restoreDefaults(app.tuxguitar.audio.engine.settings.RecognitionParameter.Section.CHORDS);
			flush(display);
			assertEquals(NORMAL, text(select));
			assertTrue(enabled(select));

			// closing releases the listener: a later change reaches no disposed widget
			window.dispose();
			panel.dispose();
			store.update(store.getDefault().with(RecognitionParameters.CHORD_PROFILE_MATCH, 0.75));
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private static Object nativeOf(Object uiControl) throws Exception {
		return uiControl.getClass().getMethod("getControl").invoke(uiControl);
	}

	private static List<String> items(Object combo) throws Exception {
		return Arrays.asList((String[]) combo.getClass().getMethod("getItems").invoke(combo));
	}

	private static String text(Object combo) throws Exception {
		return (String) combo.getClass().getMethod("getText").invoke(combo);
	}

	private static boolean enabled(Object combo) throws Exception {
		return (Boolean) combo.getClass().getMethod("isEnabled").invoke(combo);
	}

	/** The learner picks an item of the native list: selection, then SWT's selection event. */
	private static void userSelects(Object display, Object combo, int index) throws Exception {
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
}
