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
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the sound file items of the Amp and effects window (spec 027 T044, FR-023, contracts/sound-file.md): the ⋯ menu offers *Export…*
 * and *Import…*, enabled whatever the current sound, with their texts and tips. Runs only with the profile {@code swt-gui-tests} (T065, Mac unlocked).
 */
@Tag("swt-gui")
public class AmpSoundFileSwtTest {

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
	}

	@Test
	public void theMoreMenuOffersExportAndImport() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				for (String name : Arrays.asList("Clean", "Crunch")) {
					harness.amp.getSounds().setSelectedValue(name);
					flush(display);
					assertEquals("Export…", harness.amp.getExport().getText());
					assertEquals("Import…", harness.amp.getImport().getText());
					assertTrue(harness.amp.getExport().isEnabled(), "export is enabled on " + name);
					assertTrue(harness.amp.getImport().isEnabled(), "import is enabled on " + name);
				}
				assertFalse(text(harness.english, "practice.amp.export.tip").isEmpty());
				assertFalse(text(harness.english, "practice.amp.import.tip").isEmpty());
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
