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
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.menu.UIMenuActionItem;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the sound's name prompt and the ⋯ menu (spec 025 T019, US3, FR-009, clarification 3, contracts/amp-window.md): Save… opens a small
 * window with the name; an empty name, a ready-made one or one of 41 characters keeps OK disabled, the last two with today's messages; OK saves. The ⋯ menu
 * offers Rename…, Delete and Restore, enabled under today's rules; Rename… opens the prompt with the current name. Runs only with the profile
 * {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpNamePromptSwtTest {

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

	@Test
	public void saveOpensThePromptAndTheNameRulesAreTodays() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				AmpNamePrompt prompt = harness.amp.getNamePrompt();
				assertFalse(prompt.isOpen(), "no prompt before Save…");
				press(display, harness.amp.getSaveAs());
				assertTrue(prompt.isOpen(), "Save… opens the prompt");
				assertEquals("", prompt.getName().getText());
				assertFalse(prompt.getOk().isEnabled(), "no name typed");
				type(display, prompt, "lead");
				assertFalse(prompt.getOk().isEnabled(), "a ready-made name");
				assertEquals(text(harness.english, AmpTexts.NAME_READY_MADE), prompt.getProblem().getText());
				type(display, prompt, "x".repeat(AmpSoundStore.NAME_LENGTH + 1));
				assertFalse(prompt.getOk().isEnabled(), "too long");
				assertEquals(text(harness.english, AmpTexts.NAME_TOO_LONG, Integer.toString(AmpSoundStore.NAME_LENGTH)), prompt.getProblem().getText());
				type(display, prompt, "Mine");
				assertEquals("", prompt.getProblem().getText(), "a good name: no message");
				press(display, prompt.getOk());
				assertFalse(prompt.isOpen(), "OK closes the prompt");
				assertEquals("Mine", harness.store.current().name());
				assertEquals("Mine", harness.amp.getSounds().getSelectedValue());
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void cancelSavesNothing() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				AmpNamePrompt prompt = harness.amp.getNamePrompt();
				press(display, harness.amp.getSaveAs());
				type(display, prompt, "Mine");
				press(display, prompt.getCancel());
				assertFalse(prompt.isOpen());
				assertEquals("Clean", harness.store.current().name(), "nothing saved");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theMenuOffersRenameDeleteAndRestoreUnderTodaysRules() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				assertNotNull(harness.amp.getMore());
				assertEquals("Rename…", harness.amp.getRename().getText());
				assertEquals("Delete", harness.amp.getDelete().getText());
				assertEquals("Restore", harness.amp.getRestore().getText());
				// a ready-made sound, unchanged: nothing applies
				assertFalse(harness.amp.getRename().isEnabled());
				assertFalse(harness.amp.getDelete().isEnabled());
				assertFalse(harness.amp.getRestore().isEnabled());
				// changed: Restore applies
				UIScale gain = harness.amp.getRows().getScale("gain");
				userMoves(display, gain, gain.getMaximum());
				assertTrue(harness.amp.getRestore().isEnabled());
				choose(display, harness.amp.getRestore());
				assertEquals(ReadyMadeChains.CLEAN, harness.store.currentChain(), "restored");
				// one of the learner's sounds: Rename… and Delete apply
				press(display, harness.amp.getSaveAs());
				type(display, harness.amp.getNamePrompt(), "Mine");
				press(display, harness.amp.getNamePrompt().getOk());
				assertTrue(harness.amp.getRename().isEnabled());
				assertTrue(harness.amp.getDelete().isEnabled());
				choose(display, harness.amp.getRename());
				AmpNamePrompt prompt = harness.amp.getNamePrompt();
				assertTrue(prompt.isOpen(), "Rename… opens the prompt");
				assertEquals("Mine", prompt.getName().getText(), "with the current name");
				assertFalse(prompt.getOk().isEnabled(), "the same name: nothing to rename");
				type(display, prompt, "Yours");
				press(display, prompt.getOk());
				assertEquals("Yours", harness.store.current().name());
				choose(display, harness.amp.getDelete());
				assertEquals("Clean", harness.store.current().name(), "deleted, back to Clean");
			} finally {
				harness.close();
			}
		});
	}

	private static void press(Object display, UIButton button) throws Exception {
		assertTrue(button.isEnabled(), "the button is enabled");
		notifySelection(display, button.getClass().getMethod("getControl").invoke(button));
	}

	private static void choose(Object display, UIMenuActionItem item) throws Exception {
		assertTrue(item.isEnabled(), "the menu item is enabled");
		notifySelection(display, item.getClass().getMethod("getControl").invoke(item));
	}

	private static void type(Object display, AmpNamePrompt prompt, String name) throws Exception {
		prompt.getName().setText(name);
		flush(display);
	}

	private static void userMoves(Object display, UIScale scale, int position) throws Exception {
		Object nativeScale = scale.getClass().getMethod("getControl").invoke(scale);
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		notifySelection(display, nativeScale);
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
