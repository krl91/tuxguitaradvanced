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
import app.tuxguitar.audio.engine.monitor.MonitorNotice;
import app.tuxguitar.audio.engine.monitor.MonitorState;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIToggleButton;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the safety of the Amp and effects window (spec 022 T050, FR-012, FR-013, FR-014, US4): the microphone warning before the sound starts,
 * the message line for the engine's events, and a fresh store silent. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpSafetySwtTest {

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

		String message() {
			return this.amp.getMessage().getText();
		}

		String say(String key, String... arguments) {
			return text(this.english, key, arguments);
		}
	}

	@Test
	public void withTheBuiltInMicrophoneTheSoundStartsOnlyAfterTurnOnAnyway() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.sound.builtIn = true;
				harness.amp.open();
				flush(display);
				userToggles(display, harness.amp.getSwitch(), true);
				assertTrue(harness.sound.switches.isEmpty(), "not started yet");
				assertTrue(harness.amp.getWarning() != null && !harness.amp.getWarning().isDisposed(), "the warning shows");
				assertFalse(harness.amp.getSwitch().isSelected(), "the switch waits");
				press(display, harness.amp.getWarningCancel());
				assertTrue(harness.sound.switches.isEmpty(), "cancelled: still silent");
				assertFalse(harness.store.settings().enabled());
				userToggles(display, harness.amp.getSwitch(), true);
				press(display, harness.amp.getTurnOnAnyway());
				assertEquals(Arrays.asList(Boolean.TRUE), harness.sound.switches, "started after Turn on anyway");
				assertTrue(harness.amp.getSwitch().isSelected());
				assertTrue(harness.store.settings().enabled());
				assertTrue(harness.amp.getWarning() == null || harness.amp.getWarning().isDisposed(), "the warning closed");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void anotherInputStartsAtOnce() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				userToggles(display, harness.amp.getSwitch(), true);
				assertEquals(Arrays.asList(Boolean.TRUE), harness.sound.switches);
				assertTrue(harness.amp.getWarning() == null, "no warning");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theMessageLineTellsTheEnginesEvents() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				userToggles(display, harness.amp.getSwitch(), true);
				harness.amp.stateChanged(MonitorState.ON, null);
				harness.amp.notice(MonitorNotice.INPUT_CLIPPING);
				assertEquals(harness.say(AmpTexts.CLIPPING), harness.message());
				assertTrue(harness.message().contains("gain"), "the advice of the gain control");
				harness.amp.notice(MonitorNotice.LIGHTER_REVERB_OFF);
				assertEquals(harness.say(AmpTexts.LIGHTER_REVERB_OFF), harness.message());
				harness.amp.stateChanged(MonitorState.MUTED_FEEDBACK, null);
				assertEquals(harness.say(AmpTexts.FEEDBACK), harness.message());
				assertFalse(harness.amp.getSwitch().isSelected(), "muted: the switch is off, to turn on again");
				assertFalse(harness.store.settings().enabled());
				userToggles(display, harness.amp.getSwitch(), true);
				harness.amp.stateChanged(MonitorState.STARTING, null);
				harness.amp.stateChanged(MonitorState.ON, null);
				harness.amp.stateChanged(MonitorState.MUTED_OVERLOAD, null);
				assertEquals(harness.say(AmpTexts.OVERLOAD), harness.message());
				userToggles(display, harness.amp.getSwitch(), true);
				harness.amp.stateChanged(MonitorState.STARTING, null);
				harness.amp.stateChanged(MonitorState.ON, null);
				harness.amp.stateChanged(MonitorState.FAILED, "unplugged");
				assertEquals(harness.say(AmpTexts.DEVICE_LOST, "unplugged"), harness.message(), "a device lost while playing");
				assertFalse(harness.amp.getSwitch().isSelected());
				harness.amp.stateChanged(MonitorState.STARTING, null);
				harness.amp.stateChanged(MonitorState.FAILED, "refused");
				assertEquals(harness.say(AmpTexts.FAILED, "refused"), harness.message(), "a device refused at the start");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aFreshStoreOpensSilent() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				assertFalse(harness.amp.getSwitch().isSelected());
				assertTrue(harness.sound.switches.isEmpty());
			} finally {
				harness.close();
			}
		});
	}

	private static void press(Object display, UIButton button) throws Exception {
		notifySelection(display, button.getClass().getMethod("getControl").invoke(button));
	}

	private static void userToggles(Object display, UIToggleButton button, boolean selected) throws Exception {
		Object nativeButton = button.getClass().getMethod("getControl").invoke(button);
		nativeButton.getClass().getMethod("setSelection", boolean.class).invoke(nativeButton, selected);
		notifySelection(display, nativeButton);
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
