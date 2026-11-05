package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UITabFolder;
import app.tuxguitar.ui.widget.UITabItem;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the amp window in tabs (spec 025 T018, US3, FR-007 to FR-011, contracts/amp-window.md, data-model "Amp window tabs"): the check box,
 * the sounds with Save… and ⋯ and the delay above four tabs in the contract's order, each holding its controls; the window at most 600 pixels high at its
 * first opening and kept bounds brought within a 768-pixel screen; Add a pedal shows the tab of the pedal's section; the new names and a tooltip on every
 * control. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpWindowTabsSwtTest {

	private static final int SWT_SELECTION = 13;

	private static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();

		public String get(String key) {
			return this.values.get(key);
		}

		public void set(String key, String value) {
			this.values.put(key, value);
		}
	}

	/** The engine side, every call recorded. */
	private static final class Sound implements AmpWindow.Sound {
		final List<ChainSettings> chains = new ArrayList<ChainSettings>();
		final List<Boolean> switches = new ArrayList<Boolean>();
		double delay = Double.NaN;
		int scoreVolume = 10;
		double guitarVolume = -1;

		public List<String> outputs() {
			return Arrays.asList("", "Headphones");
		}

		public double estimatedDelayMillis() {
			return this.delay;
		}

		public void setEnabled(boolean enabled) {
			this.switches.add(enabled);
		}

		public void setOutput(String output) {
		}

		public void setGuitarVolume(double volume) {
			this.guitarVolume = volume;
		}

		public void setChain(ChainSettings chain) {
			this.chains.add(chain);
		}

		public int scoreVolume() {
			return this.scoreVolume;
		}

		public void setScoreVolume(int volume) {
			this.scoreVolume = volume;
		}
	}

	private static final class Harness {
		final Object display;
		final UIFactory factory;
		final UIWindow owner;
		final Memory memory = new Memory();
		final AmpSoundStore store = new AmpSoundStore(this.memory);
		final Sound sound = new Sound();
		final Properties english;
		List<UIRectangle> guards = Collections.emptyList();
		AmpWindow amp;

		Harness(Object display, UIFactory factory) throws Exception {
			this.display = display;
			this.factory = factory;
			this.english = english();
			this.owner = factory.createWindow();
			this.owner.setLayout(new UITableLayout());
			this.owner.setBounds(new UIRectangle(100, 100, 1000, 700));
			this.owner.open();
			this.amp = new AmpWindow(factory, this.owner, (key, arguments) -> text(this.english, key, arguments), this.store, this.sound, () -> this.guards,
					() -> new UIRectangle(400, 300, 300, 200), bounds -> bounds.getX() >= 0 && bounds.getY() >= 0 && bounds.getX() < 3000);
			flush(display);
		}

		void close() {
			this.amp.close();
			this.owner.dispose();
		}
	}

	private static Properties english() throws Exception {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/messages.properties")), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static String text(Properties properties, String key, String... arguments) {
		String value = properties.getProperty(key);
		if (value == null) {
			throw new AssertionError("missing text: " + key);
		}
		for (int i = 0; i < arguments.length; i++) {
			value = value.replace("{" + i + "}", arguments[i]);
		}
		return value;
	}

	/** Whether the control is inside that tab (its parents lead to the tab). */
	private static boolean inTab(UIControl control, UITabItem tab) {
		for (Object parent = control.getParent(); parent != null; parent = ((UIControl) parent).getParent()) {
			if (parent == tab) {
				return true;
			}
			if (!(parent instanceof UIControl)) {
				return false;
			}
		}
		return false;
	}

	private static boolean inAnyTab(UIControl control, UITabFolder tabs) {
		for (UITabItem tab : tabs.getTabs()) {
			if (inTab(control, tab)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void fourTabsInTheContractsOrderEachHoldingItsControls() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				UITabFolder tabs = harness.amp.getTabs();
				List<String> names = new ArrayList<String>();
				for (UITabItem tab : tabs.getTabs()) {
					names.add(tab.getText());
				}
				assertEquals(Arrays.asList("Pedals before the amp", "Amp", "Effects after the amp", "Output"), names);
				UITabItem before = tabs.getTabs().get(0);
				UITabItem amp = tabs.getTabs().get(1);
				UITabItem after = tabs.getTabs().get(2);
				UITabItem output = tabs.getTabs().get(3);
				// the Clean sound: a gate before the amp, a reverb after it
				assertTrue(inTab(harness.amp.getRows().getPedal(EffectKind.GATE).getSwitch(), before), "the gate in the first tab");
				assertTrue(inTab(harness.amp.getRows().getCharacter(), amp));
				for (String knob : AmpChainRows.KNOBS) {
					assertTrue(inTab(harness.amp.getRows().getScale(knob), amp), knob);
				}
				assertTrue(inTab(harness.amp.getRows().getCabinet(), amp), "the speaker simulation with the amp");
				assertTrue(inTab(harness.amp.getRows().getPedal(EffectKind.REVERB).getSwitch(), after), "the reverb in the third tab");
				assertTrue(inTab(harness.amp.getOutputs(), output));
				assertTrue(inTab(harness.amp.getGuitarVolume(), output));
				assertTrue(inTab(harness.amp.getScoreVolume(), output));
				for (UIControl top : new UIControl[] { harness.amp.getSwitch(), harness.amp.getSounds(), harness.amp.getSaveAs(), harness.amp.getMore(),
						harness.amp.getDelay(), harness.amp.getMessage(), harness.amp.getRows().getAddPedal() }) {
					assertFalse(inAnyTab(top, tabs), "outside the tabs: " + top);
				}
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theFirstOpeningIsAtMost600High() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				float height = harness.amp.getWindow().getBounds().getHeight();
				assertTrue(height <= 600f, "the window is " + height + " high");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void keptBoundsTallerThanTheScreenAreBroughtWithin768() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.memory.values.put("window.bounds", "150,20,520,1200");
				harness.amp.open();
				flush(display);
				UIRectangle bounds = harness.amp.getWindow().getBounds();
				assertTrue(bounds.getHeight() <= 768f, "the window is " + bounds.getHeight() + " high");
				assertEquals(150, bounds.getX(), 1, "kept where it was");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void addingAPedalShowsTheTabOfItsSectionAndKeepsTheWindowsSize() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				UITabFolder tabs = harness.amp.getTabs();
				UIRectangle size = harness.amp.getWindow().getBounds();
				userPicks(display, harness.amp.getRows().getAddPedal(), EffectKind.CHORUS);
				assertSame(tabs.getTabs().get(2), tabs.getSelectedTab(), "the effects after the amp are shown");
				assertTrue(inTab(harness.amp.getRows().getPedal(EffectKind.CHORUS).getSwitch(), tabs.getTabs().get(2)));
				userPicks(display, harness.amp.getRows().getAddPedal(), EffectKind.DISTORTION);
				assertSame(tabs.getTabs().get(0), tabs.getSelectedTab(), "the pedals before the amp are shown");
				assertTrue(inTab(harness.amp.getRows().getPedal(EffectKind.DISTORTION).getSwitch(), tabs.getTabs().get(0)));
				assertEquals(size.getHeight(), harness.amp.getWindow().getBounds().getHeight(), 1, "the tab scrolls, the window keeps its size");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theSwitchIsACheckBoxAndTheNamesAreClear() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				UICheckBox box = harness.amp.getSwitch();
				assertEquals("Hear my guitar through the amp", box.getText());
				assertEquals("Speaker simulation", harness.amp.getRows().getCabinet().getText());
				assertEquals("Save…", harness.amp.getSaveAs().getText());
				assertEquals("⋯", harness.amp.getMore().getText());
				List<String> tips = new ArrayList<String>(Arrays.asList(box.getToolTipText(), harness.amp.getRows().getCabinet().getToolTipText(),
						harness.amp.getSaveAs().getToolTipText(), harness.amp.getMore().getToolTipText(), harness.amp.getSoundsLabel().getToolTipText(),
						harness.amp.getOutputsLabel().getToolTipText(), harness.amp.getGuitarVolume().getToolTipText(), harness.amp.getScoreVolume().getToolTipText(),
						harness.amp.getDelay().getToolTipText()));
				for (String tip : tips) {
					assertTrue(tip != null && !tip.trim().isEmpty(), tips.toString());
				}
				assertTrue(harness.amp.getRows().getCabinet().getToolTipText().toLowerCase().contains("speaker"), "the tip says what it simulates");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theCheckBoxStartsTheSoundAsTheSwitchDid() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				userChecks(display, harness.amp.getSwitch(), true);
				assertEquals(Arrays.asList(Boolean.TRUE), harness.sound.switches);
				assertTrue(harness.store.settings().enabled(), "kept");
			} finally {
				harness.close();
			}
		});
	}

	private static void userChecks(Object display, UICheckBox box, boolean selected) throws Exception {
		Object nativeBox = box.getClass().getMethod("getControl").invoke(box);
		nativeBox.getClass().getMethod("setSelection", boolean.class).invoke(nativeBox, selected);
		notifySelection(display, nativeBox);
	}

	private static <T> void userPicks(Object display, UIDropDownSelect<T> select, T value) throws Exception {
		select.setSelectedValue(value);
		notifySelection(display, select.getClass().getMethod("getControl").invoke(select));
	}

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}



	private static void notifySelection(Object display, Object widget) throws Exception {
		Class<?> eventClass = Class.forName("org.eclipse.swt.widgets.Event");
		Object event = eventClass.getConstructor().newInstance();
		widget.getClass().getMethod("notifyListeners", int.class, eventClass).invoke(widget, SWT_SELECTION, event);
		flush(display);
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
}
