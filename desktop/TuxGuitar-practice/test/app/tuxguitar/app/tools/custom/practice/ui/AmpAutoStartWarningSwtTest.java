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
import java.util.Properties;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the warning when the sound would start on its own with the built-in microphone (spec 022 T069, FR-012): the practice window opening
 * with the sound on, or the input changing to it. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpAutoStartWarningSwtTest {

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
		final List<Boolean> switches = new ArrayList<Boolean>();
		boolean builtIn;

		public List<String> outputs() {
			return Arrays.asList("");
		}

		public double estimatedDelayMillis() {
			return Double.NaN;
		}

		public void setEnabled(boolean enabled) {
			this.switches.add(enabled);
		}

		public void setOutput(String output) {
		}

		public void setGuitarVolume(double volume) {
		}

		public void setChain(ChainSettings chain) {
		}

		public int scoreVolume() {
			return 10;
		}

		public void setScoreVolume(int volume) {
		}

		public boolean builtInMicrophone() {
			return this.builtIn;
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

	}

	@Test
	public void startingOnItsOwnWithTheBuiltInMicrophoneWarnsFirstAndTurnOnAnywayStartsIt() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.store.save(harness.store.settings().withEnabled(true));
				harness.sound.builtIn = true;
				harness.amp.warnBeforeStart();
				flush(display);
				assertTrue(harness.amp.isOpen(), "the window opens to show the warning");
				assertTrue(harness.amp.getWarning() != null && !harness.amp.getWarning().isDisposed(), "the warning shows");
				assertFalse(harness.amp.getSwitch().isSelected(), "the switch waits");
				assertTrue(harness.sound.switches.isEmpty(), "not started");
				press(display, harness.amp.getTurnOnAnyway());
				assertEquals(Arrays.asList(Boolean.TRUE), harness.sound.switches);
				assertTrue(harness.amp.getSwitch().isSelected());
				assertTrue(harness.store.settings().enabled());
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void cancelLeavesTheSoundOffAndTheSwitchOff() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.store.save(harness.store.settings().withEnabled(true));
				harness.sound.builtIn = true;
				harness.amp.open();
				flush(display);
				harness.amp.warnBeforeStart();
				flush(display);
				press(display, harness.amp.getWarningCancel());
				assertTrue(harness.sound.switches.isEmpty(), "still silent");
				assertFalse(harness.amp.getSwitch().isSelected());
				assertFalse(harness.store.settings().enabled(), "off at the next opening too");
			} finally {
				harness.close();
			}
		});
	}

	private static void press(Object display, UIButton button) throws Exception {
		notifySelection(display, button.getClass().getMethod("getControl").invoke(button));
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
