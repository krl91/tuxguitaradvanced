package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.FireTuningStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the Advanced tab (spec 019 T036, US4, FR-014, FR-015, FR-018, FR-019): two groups with one row per setting, the values shown from the
 * store, a tooltip on every label and control, a scale written through at once, *Default values* per group, and the preview timers stopped when hidden.
 * The learner's moves are sent as native selection events. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class FireSettingsPanelSwtTest {

	private static final int SWT_SELECTION = 13;

	/** A store in memory, every write counted. */
	private static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();
		int writes;

		public String get(String key) {
			return this.values.get(key);
		}

		public void set(String key, String value) {
			this.writes++;
			this.values.put(key, value);
		}
	}

	private static final class Ticks implements ScoreFire.Ticker {
		boolean running;

		public void start(Runnable tick) {
			this.running = true;
		}

		public void stop() {
			this.running = false;
		}
	}

	private static final class Harness {
		final Object display;
		final UIWindow window;
		final Memory memory = new Memory();
		final FireTuningStore store = new FireTuningStore(this.memory);
		final List<Ticks> ticks = new ArrayList<Ticks>();
		final FireSettingsPanel.Messages messages;
		FireSettingsPanel panel;

		Harness(Object display, UIFactory factory, Map<String, String> stored) throws Exception {
			this.display = display;
			this.memory.values.putAll(stored);
			this.messages = english();
			this.window = factory.createWindow();
			this.window.setLayout(new UITableLayout());
			this.panel = new FireSettingsPanel(factory, this.window, this.window, this.messages, this.store, () -> {
				Ticks ticker = new Ticks();
				this.ticks.add(ticker);
				return ticker;
			}, System::nanoTime, true);
			((UITableLayout) this.window.getLayout()).set(this.panel.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 640f,
					480f, 0f);
			this.window.pack();
			this.window.setBounds(new UIRectangle(20, 20, 640, 480));
			this.window.open();
			flush(display);
		}

		void close() {
			this.panel.dispose();
			this.window.dispose();
		}
	}

	private static FireSettingsPanel.Messages english() throws Exception {
		final Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/messages.properties")), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return new FireSettingsPanel.Messages() {
			public String get(String key) {
				String value = properties.getProperty(key);
				if (value == null) {
					throw new AssertionError("missing text: " + key);
				}
				return value;
			}

			public String get(String key, String[] arguments) {
				String value = get(key);
				for (int i = 0; i < arguments.length; i++) {
					value = value.replace("{" + i + "}", arguments[i]);
				}
				return value;
			}
		};
	}

	@Test
	public void twoGroupsWithOneRowPerSettingShowingTheDefaultsOrTheStoredValues() throws Exception {
		Map<String, String> stored = new HashMap<String, String>();
		stored.put(FireTuningStore.key(FireTuning.Group.SCORE, FireParameter.OPACITY_MOST), "0.5");
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, stored);
			try {
				for (FireTuning.Group group : FireTuning.Group.values()) {
					int expected = 0;
					for (FireParameter parameter : FireParameter.values()) {
						if (!parameter.in(group)) {
							continue;
						}
						expected++;
						assertNotNull(harness.panel.getLabel(group, parameter), group + " " + parameter);
						assertNotNull(harness.panel.getControl(group, parameter), group + " " + parameter);
						double value = harness.store.load(group).value(parameter);
						assertEquals(FireSettingsPanel.shown(harness.messages, parameter, value), harness.panel.getValueLabel(group, parameter).getText(),
								group + " " + parameter);
					}
					assertEquals(expected, harness.panel.getParameters(group).size(), group + ": one row per setting of the group");
					assertNotNull(harness.panel.getDefaultsButton(group));
					assertNotNull(harness.panel.getPreview(group));
				}
				assertEquals("50 %", harness.panel.getValueLabel(FireTuning.Group.SCORE, FireParameter.OPACITY_MOST).getText(), "the stored value");
				assertEquals("85 %", harness.panel.getValueLabel(FireTuning.Group.ARCADE, FireParameter.OPACITY_MOST).getText(), "the default");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void everyLabelAndControlSaysWhatItChangesItsUnitAndItsDefault() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new HashMap<String, String>());
			try {
				for (FireTuning.Group group : FireTuning.Group.values()) {
					for (FireParameter parameter : harness.panel.getParameters(group)) {
						String tip = harness.messages.get(parameter.tooltipKey());
						String byDefault = FireSettingsPanel.shown(harness.messages, parameter, parameter.defaultFor(group));
						String label = harness.panel.getLabel(group, parameter).getToolTipText();
						String control = toolTip(harness.panel.getControl(group, parameter));
						for (String text : new String[] { label, control }) {
							assertNotNull(text, group + " " + parameter);
							assertTrue(text.contains(tip), group + " " + parameter + ": " + text);
							assertTrue(text.contains(byDefault), group + " " + parameter + " default " + byDefault + ": " + text);
							if (parameter.unit().labelKey() != null) {
								assertTrue(text.contains(harness.messages.get(parameter.unit().labelKey())), group + " " + parameter + " unit: " + text);
							}
						}
					}
					assertTrue(harness.panel.getDefaultsButton(group).getToolTipText().contains(harness.messages.get("practice.fire.defaults.tip")));
				}
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void movingAScaleWritesTheValueAtOnceAndLeavesARunningRunAlone() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new HashMap<String, String>());
			try {
				FireTuning running = harness.store.load(FireTuning.Group.SCORE);
				UIScale columns = (UIScale) harness.panel.getControl(FireTuning.Group.SCORE, FireParameter.CELL_COLUMNS);
				int writes = harness.memory.writes;
				userMoves(display, columns, 0);
				assertEquals("16.0", harness.memory.values.get(FireTuningStore.key(FireTuning.Group.SCORE, FireParameter.CELL_COLUMNS)), "written at once");
				assertEquals(1, harness.memory.writes - writes, "one value written for one move");
				assertEquals(16, harness.store.load(FireTuning.Group.SCORE).integer(FireParameter.CELL_COLUMNS), "the next run reads it");
				assertEquals(16, harness.panel.getPreview(FireTuning.Group.SCORE).tuning().integer(FireParameter.CELL_COLUMNS), "the preview follows");
				assertEquals(COLUMNS_BY_DEFAULT, running.integer(FireParameter.CELL_COLUMNS), "a run keeps the tuning it read at its start");
				assertEquals(COLUMNS_BY_DEFAULT, harness.store.load(FireTuning.Group.ARCADE).integer(FireParameter.CELL_COLUMNS), "the other group is not touched");

				UIScale opacity = (UIScale) harness.panel.getControl(FireTuning.Group.SCORE, FireParameter.OPACITY_MOST);
				userMoves(display, opacity, nativeInt(opacity, "getMaximum"));
				assertEquals(1.0, harness.store.load(FireTuning.Group.SCORE).value(FireParameter.OPACITY_MOST), 1e-9);
				assertEquals("100 %", harness.panel.getValueLabel(FireTuning.Group.SCORE, FireParameter.OPACITY_MOST).getText());
			} finally {
				harness.close();
			}
		});
	}

	/** The default number of columns of both groups ({@code FireField.COLUMNS}, not visible from this package). */
	private static final int COLUMNS_BY_DEFAULT = (int) FireParameter.CELL_COLUMNS.arcadeDefault();

	@Test
	public void defaultValuesResetsItsGroupOnly() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new HashMap<String, String>());
			try {
				userMoves(display, (UIScale) harness.panel.getControl(FireTuning.Group.SCORE, FireParameter.CELL_COLUMNS), 0);
				userMoves(display, (UIScale) harness.panel.getControl(FireTuning.Group.SCORE, FireParameter.SPARKS_LIFE), 0);
				userMoves(display, (UIScale) harness.panel.getControl(FireTuning.Group.ARCADE, FireParameter.CELL_COLUMNS), 0);
				userPresses(display, harness.panel.getDefaultsButton(FireTuning.Group.SCORE));
				assertEquals(FireTuning.SCORE_DEFAULTS, harness.store.load(FireTuning.Group.SCORE));
				assertEquals(FireTuning.SCORE_DEFAULTS, harness.panel.getPreview(FireTuning.Group.SCORE).tuning());
				for (FireParameter parameter : harness.panel.getParameters(FireTuning.Group.SCORE)) {
					assertEquals(FireSettingsPanel.shown(harness.messages, parameter, parameter.scoreDefault()),
							harness.panel.getValueLabel(FireTuning.Group.SCORE, parameter).getText(), parameter.key());
					String kept = harness.memory.values.get(FireTuningStore.key(FireTuning.Group.SCORE, parameter));
					assertTrue(kept == null || kept.isEmpty(), "the stored key is removed: " + parameter.key());
				}
				assertEquals(16, harness.store.load(FireTuning.Group.ARCADE).integer(FireParameter.CELL_COLUMNS), "Arcade's group is untouched");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void hidingThePanelStopsThePreviews() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new HashMap<String, String>());
			try {
				assertEquals(2, harness.ticks.size(), "one preview per group");
				for (Ticks ticker : harness.ticks) {
					assertFalse(ticker.running, "not shown yet: still");
				}
				harness.panel.setShown(true);
				for (Ticks ticker : harness.ticks) {
					assertTrue(ticker.running);
				}
				harness.panel.setShown(false);
				for (Ticks ticker : harness.ticks) {
					assertFalse(ticker.running, "hidden: the previews stop");
				}
			} finally {
				harness.close();
			}
		});
	}

	// ---------------------------------------------------------------- native toolkit

	private static String toolTip(UIControl control) {
		if (control instanceof UIScale) {
			return ((UIScale) control).getToolTipText();
		}
		if (control instanceof UIButton) {
			return ((UIButton) control).getToolTipText();
		}
		if (control instanceof UILabel) {
			return ((UILabel) control).getToolTipText();
		}
		return null;
	}

	/** The learner drags the native scale: selection changes, then SWT's selection event (as {@code RecognitionSettingsSwtTest}). */
	private static void userMoves(Object display, UIScale scale, int position) throws Exception {
		Object nativeScale = scale.getClass().getMethod("getControl").invoke(scale);
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		notifySelection(display, nativeScale);
	}

	private static void userPresses(Object display, UIButton button) throws Exception {
		notifySelection(display, button.getClass().getMethod("getControl").invoke(button));
	}

	private static void notifySelection(Object display, Object nativeControl) throws Exception {
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Method notify = Class.forName("org.eclipse.swt.widgets.Widget").getMethod("notifyListeners", int.class, eventClass);
		notify.invoke(nativeControl, SWT_SELECTION, eventClass.getConstructor().newInstance());
		flush(display);
	}

	private static int nativeInt(UIControl control, String method) throws Exception {
		Object nativeControl = control.getClass().getMethod("getControl").invoke(control);
		return (Integer) nativeControl.getClass().getMethod(method).invoke(nativeControl);
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 200 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}

	private static void withDisplay(DisplayTask task) throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			Class<?> environment = Class.forName("app.tuxguitar.ui.swt.SWTEnvironment");
			environment.getMethod("start", displayClass).invoke(environment.getMethod("getInstance").invoke(null), display);
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			task.run(display, factory);
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}
}
