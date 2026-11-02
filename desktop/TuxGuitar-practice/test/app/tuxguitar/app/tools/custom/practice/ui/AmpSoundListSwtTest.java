package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.ScoreKey;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the sound list of the Amp and effects window (spec 022 T044, contracts/amp-window.md, FR-008, FR-009, FR-019, US3): ready-made then
 * own sounds, a pick sent at once, "(changed)", the buttons enabled only when they apply, and each score's remembered sound before any run. Runs only with
 * the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpSoundListSwtTest {

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

	private static final class Sound implements AmpWindow.Sound {
		final List<ChainSettings> chains = new ArrayList<ChainSettings>();

		public List<String> outputs() {
			return Arrays.asList("");
		}

		public double estimatedDelayMillis() {
			return Double.NaN;
		}

		public void setEnabled(boolean enabled) {
		}

		public void setOutput(String output) {
		}

		public void setGuitarVolume(double volume) {
		}

		public void setChain(ChainSettings chain) {
			this.chains.add(chain);
		}

		public int scoreVolume() {
			return 10;
		}

		public void setScoreVolume(int volume) {
		}

		ChainSettings last() {
			return this.chains.get(this.chains.size() - 1);
		}
	}

	private static final class Harness {
		final Object display;
		final UIWindow owner;
		final Memory memory = new Memory();
		final AmpSoundStore store = new AmpSoundStore(this.memory);
		final Sound sound = new Sound();
		final Properties english;
		final AmpWindow amp;

		Harness(Object display, UIFactory factory) throws Exception {
			this.display = display;
			this.english = english();
			this.owner = factory.createWindow();
			this.owner.setLayout(new UITableLayout());
			this.owner.setBounds(new UIRectangle(100, 100, 1000, 700));
			this.owner.open();
			this.amp = new AmpWindow(factory, this.owner, (key, arguments) -> text(this.english, key, arguments), this.store, this.sound,
					() -> Collections.<UIRectangle>emptyList(), () -> null, bounds -> true);
			flush(display);
		}

		void close() {
			this.amp.close();
			this.owner.dispose();
		}

		List<String> items() throws Exception {
			UIDropDownSelect<String> select = this.amp.getSounds();
			Object combo = select.getClass().getMethod("getControl").invoke(select);
			return Arrays.asList((String[]) combo.getClass().getMethod("getItems").invoke(combo));
		}

		void pick(String name) throws Exception {
			UIDropDownSelect<String> select = this.amp.getSounds();
			select.setSelectedValue(name);
			notifySelection(this.display, select.getClass().getMethod("getControl").invoke(select));
		}

		void type(String name) throws Exception {
			this.amp.getSoundName().setText(name);
			flush(this.display);
		}

		void press(UIButton button) throws Exception {
			assertTrue(button.isEnabled(), "the button is enabled");
			notifySelection(this.display, button.getClass().getMethod("getControl").invoke(button));
		}
	}

	@Test
	public void theListShowsTheReadyMadeSoundsThenOwnSoundsAndAPickIsSentAtOnce() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.store.saveAs("blues", ReadyMadeChains.CRUNCH);
				harness.amp.open();
				flush(display);
				assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo", "blues"), harness.items());
				assertEquals("Clean", harness.amp.getSounds().getSelectedValue());
				harness.pick("Lead");
				assertEquals(ReadyMadeChains.LEAD, harness.sound.last(), "sent to the engine at once");
				assertEquals("Lead", harness.store.current().name());
				assertEquals(ReadyMadeChains.LEAD.amp().gain(), harness.amp.getRows().chain().amp().gain(), 1e-9, "the rows show the sound");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aChangeShowsChangedAndRestoreBringsTheReadyMadeSoundBack() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				assertFalse(harness.amp.getRestore().isEnabled(), "nothing to restore");
				assertFalse(harness.amp.getDelete().isEnabled(), "a ready-made sound cannot be deleted");
				assertFalse(harness.amp.getRename().isEnabled(), "nor renamed");
				UIScale gain = harness.amp.getRows().getScale("gain");
				userMoves(display, gain, gain.getMaximum());
				assertEquals("Clean " + harness.english.getProperty(AmpTexts.CHANGED), harness.items().get(0));
				assertTrue(harness.amp.getRestore().isEnabled());
				harness.press(harness.amp.getRestore());
				assertEquals("Clean", harness.items().get(0));
				assertEquals(ReadyMadeChains.CLEAN, harness.sound.last(), "the shipped chain is heard again");
				assertFalse(harness.amp.getRestore().isEnabled());
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void saveAsRenameAndDeleteAreEnabledOnlyWhenTheyApply() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				assertFalse(harness.amp.getSaveAs().isEnabled(), "no name typed");
				harness.type("lead");
				assertFalse(harness.amp.getSaveAs().isEnabled(), "a ready-made name");
				harness.type("Mine");
				harness.press(harness.amp.getSaveAs());
				assertEquals("Mine", harness.store.current().name());
				assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo", "Mine"), harness.items());
				assertEquals("Mine", harness.amp.getSounds().getSelectedValue());
				assertTrue(harness.amp.getDelete().isEnabled());
				assertFalse(harness.amp.getRename().isEnabled(), "no new name typed");
				harness.type("Yours");
				harness.press(harness.amp.getRename());
				assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo", "Yours"), harness.items());
				harness.press(harness.amp.getDelete());
				assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo"), harness.items());
				assertEquals("Clean", harness.store.current().name(), "back to Clean");
				assertEquals(ReadyMadeChains.CLEAN, harness.sound.last());
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void eachScoreGetsItsRememberedSoundBeforeAnyRun() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				Optional<ScoreKey> a = ScoreKey.of("Song A", "Band", "a.gp");
				Optional<ScoreKey> b = ScoreKey.of("Song B", "Band", "b.gp");
				harness.amp.scoreChanged(a);
				harness.amp.open();
				flush(display);
				harness.pick("Lead");
				harness.amp.scoreChanged(b);
				harness.pick("Crunch");
				harness.amp.close();
				flush(display);
				harness.amp.scoreChanged(a);
				assertEquals("Lead", harness.store.current().name(), "score A");
				assertEquals(ReadyMadeChains.LEAD, harness.sound.last(), "heard before any run, the window closed");
				harness.amp.scoreChanged(b);
				assertEquals("Crunch", harness.store.current().name(), "score B");
				assertEquals(ReadyMadeChains.CRUNCH, harness.sound.last());
				harness.amp.open();
				flush(display);
				assertEquals("Crunch", harness.amp.getSounds().getSelectedValue());
			} finally {
				harness.close();
			}
		});
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

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	private static void userMoves(Object display, UIScale scale, int position) throws Exception {
		Object nativeScale = scale.getClass().getMethod("getControl").invoke(scale);
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		notifySelection(display, nativeScale);
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
