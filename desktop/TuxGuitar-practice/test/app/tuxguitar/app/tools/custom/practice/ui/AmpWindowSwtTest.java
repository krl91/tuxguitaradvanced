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
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIToggleButton;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the Amp and effects window (spec 022 T019, contracts/amp-window.md, FR-002, FR-016, FR-020): its controls, a slider sent to the engine
 * at once, a tooltip on every control, one window brought forward by a second open, the placement away from the run's notes, the bounds kept and used again,
 * and the window closing with its owner. The palette's amp button and the closing with the practice window in the application are checked by the smoke
 * check (T028): both need the running application. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpWindowSwtTest {

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
	public void theWindowShowsTheTopBlockAndTheAmpRow() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				assertTrue(harness.amp.isOpen());
				assertFalse(harness.amp.getSwitch().isSelected(), "off on a fresh store");
				assertEquals(2, harness.amp.getOutputs().getItemCount(), "the system's default and the outputs");
				assertNotNull(harness.amp.getGuitarVolume());
				assertNotNull(harness.amp.getScoreVolume());
				assertNotNull(harness.amp.getDelay());
				assertNotNull(harness.amp.getRows().getCharacter());
				for (String knob : AmpChainRows.KNOBS) {
					assertNotNull(harness.amp.getRows().getScale(knob), knob);
				}
				assertNotNull(harness.amp.getRows().getCabinet());
				assertEquals(ReadyMadeChains.CLEAN, harness.sound.chains.isEmpty() ? ReadyMadeChains.CLEAN : harness.sound.chains.get(0));
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void movingASliderSendsTheClampedValueAtOnceAndKeepsTheChain() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				UIScale gain = harness.amp.getRows().getScale("gain");
				userMoves(display, gain, gain.getMaximum());
				ChainSettings last = harness.sound.chains.get(harness.sound.chains.size() - 1);
				assertEquals(10.0, last.amp().gain(), 1e-9, "the top of the slider is 10");
				assertEquals(last, harness.store.currentChain(), "every change of the chain is kept");
				userMoves(display, gain, 0);
				assertEquals(0.0, harness.sound.chains.get(harness.sound.chains.size() - 1).amp().gain(), 1e-9);
				UIToggleButton cabinet = harness.amp.getRows().getCabinet();
				userToggles(display, cabinet, false);
				assertFalse(harness.sound.chains.get(harness.sound.chains.size() - 1).amp().cabinet(), "the cabinet switched off");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void theSwitchStartsTheSoundAndIsKept() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				userToggles(display, harness.amp.getSwitch(), true);
				assertEquals(Arrays.asList(Boolean.TRUE), harness.sound.switches);
				assertTrue(harness.store.settings().enabled(), "kept");
				userMoves(display, harness.amp.getGuitarVolume(), 50);
				assertEquals(0.5, harness.sound.guitarVolume, 1e-9);
				assertEquals(0.5, harness.store.settings().guitarVolume(), 1e-9);
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void everyControlHasATooltip() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				List<String> tips = new ArrayList<String>(Arrays.asList(harness.amp.getSwitch().getToolTipText(), harness.amp.getOutputsLabel().getToolTipText(),
						harness.amp.getGuitarVolume().getToolTipText(), harness.amp.getScoreVolume().getToolTipText(), harness.amp.getDelay().getToolTipText(),
						harness.amp.getRows().getCharacterLabel().getToolTipText(), harness.amp.getRows().getCabinet().getToolTipText()));
				for (String knob : AmpChainRows.KNOBS) {
					tips.add(harness.amp.getRows().getScale(knob).getToolTipText());
				}
				for (String tip : tips) {
					assertTrue(tip != null && !tip.trim().isEmpty(), tips.toString());
				}
				assertTrue(harness.amp.getDelay().getToolTipText().contains("Bluetooth"), "the delay's tooltip warns about Bluetooth headphones");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aSecondOpenBringsTheSameWindowForward() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				UIWindow first = harness.amp.getWindow();
				harness.amp.open();
				flush(display);
				assertSame(first, harness.amp.getWindow());
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void duringARunItOpensOnTheSideAwayFromTheNotes() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				// the notes on the left of the main window (100..1100): the window goes to the right half
				harness.guards = Arrays.asList(new UIRectangle(150, 300, 60, 40), new UIRectangle(250, 300, 60, 40));
				harness.amp.open();
				flush(display);
				UIRectangle right = harness.amp.getWindow().getBounds();
				assertTrue(right.getX() >= 600, "on the right: " + right);
				harness.amp.close();
				harness.memory.values.clear();
				harness.guards = Arrays.asList(new UIRectangle(900, 300, 60, 40));
				harness.amp.open();
				flush(display);
				UIRectangle left = harness.amp.getWindow().getBounds();
				assertTrue(left.getX() + left.getWidth() <= 600, "on the left: " + left);
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void withNoRunAndNoKeptBoundsItIsCentredOverThePracticeWindow() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				UIRectangle bounds = harness.amp.getWindow().getBounds();
				assertEquals(550, bounds.getX() + bounds.getWidth() / 2, 2, "centred over the practice window (400..700): " + bounds);
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void itsBoundsAreKeptAndUsedAtTheNextOpenWhenOnAScreen() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				harness.amp.getWindow().setBounds(new UIRectangle(150, 160, harness.amp.getWindow().getBounds().getWidth(),
						harness.amp.getWindow().getBounds().getHeight()));
				flush(display);
				harness.amp.close();
				int[] kept = harness.store.settings().windowBounds();
				assertNotNull(kept, "kept when it closes");
				assertEquals(150, kept[0]);
				assertEquals(160, kept[1]);
				harness.amp.open();
				flush(display);
				assertEquals(150, harness.amp.getWindow().getBounds().getX(), 1, "used again");
				harness.amp.close();
				// off every screen: placed again
				harness.memory.values.put("window.bounds", "-5000,-5000,400,300");
				harness.amp.open();
				flush(display);
				assertTrue(harness.amp.getWindow().getBounds().getX() >= 0, "not off the screens");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void itClosesWithItsOwnerAndKeepsItsBounds() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			harness.amp.open();
			flush(display);
			harness.owner.dispose();
			flush(display);
			assertFalse(harness.amp.isOpen());
			assertNotNull(harness.store.settings().windowBounds());
		});
	}

	@Test
	public void theDelayLineReadsTheEngineEstimate() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory);
			try {
				harness.amp.open();
				flush(display);
				harness.sound.delay = 14.4;
				harness.amp.soundChanged();
				assertTrue(harness.amp.getDelay().getText().contains("about 14 ms (estimate)"), harness.amp.getDelay().getText());
			} finally {
				harness.close();
			}
		});
	}

	// ---------------------------------------------------------------- native helpers

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	private static void userMoves(Object display, UIScale scale, int position) throws Exception {
		Object nativeScale = scale.getClass().getMethod("getControl").invoke(scale);
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		notifySelection(display, nativeScale);
	}

	private static void userToggles(Object display, UIToggleButton button, boolean selected) throws Exception {
		Object nativeButton = button.getClass().getMethod("getControl").invoke(button);
		nativeButton.getClass().getMethod("setSelection", boolean.class).invoke(nativeButton, selected);
		notifySelection(display, nativeButton);
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
