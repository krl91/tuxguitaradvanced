package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import app.tuxguitar.audio.engine.monitor.Knob;
import app.tuxguitar.audio.engine.monitor.PedalSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UIToggleButton;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the pedals' rows of the Amp and effects window (spec 022 T036, contracts/amp-window.md "Layout" 2, FR-005, FR-016, US2/AC3): Add
 * pedal… offers only the kinds missing and puts each in its own section; up and down stay within the section; remove takes the pedal out; every row has
 * its switch, its sliders and its tooltips; every change reaches the engine; a new window over the same settings shows the same chain. Runs only with the
 * profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class AmpPedalRowsSwtTest {

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
		final UIWindow owner;
		final Memory memory;
		final AmpSoundStore store;
		final Sound sound = new Sound();
		final AmpWindow amp;

		Harness(Object display, UIFactory factory, Memory memory) throws Exception {
			this.memory = memory;
			this.store = new AmpSoundStore(memory);
			Properties english = english();
			this.owner = factory.createWindow();
			this.owner.setLayout(new UITableLayout());
			this.owner.setBounds(new UIRectangle(100, 100, 1000, 700));
			this.owner.open();
			this.amp = new AmpWindow(factory, this.owner, (key, arguments) -> text(english, key, arguments), this.store, this.sound,
					() -> Collections.<UIRectangle>emptyList(), () -> new UIRectangle(400, 300, 300, 200), bounds -> true);
			this.amp.open();
			flush(display);
		}

		AmpChainRows rows() {
			return this.amp.getRows();
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

	private static List<EffectKind> offered(AmpChainRows rows) {
		List<EffectKind> kinds = rows.getOffered();
		assertEquals(kinds.size() + 1, rows.getAddPedal().getItemCount(), "the prompt and one item per kind offered");
		return kinds;
	}

	private static List<EffectKind> kinds(List<PedalSettings> pedals) {
		List<EffectKind> kinds = new ArrayList<EffectKind>();
		for (PedalSettings pedal : pedals) {
			kinds.add(pedal.kind());
		}
		return kinds;
	}

	@Test
	public void addPedalOffersOnlyTheMissingKindsAndPutsEachInItsSection() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new Memory());
			try {
				// a fresh store gives Clean: the gate before the amp, the reverb after it
				assertEquals(Arrays.asList(EffectKind.COMPRESSOR, EffectKind.OVERDRIVE, EffectKind.DISTORTION, EffectKind.CHORUS, EffectKind.ECHO),
						offered(harness.rows()));
				userPicks(display, harness.rows().getAddPedal(), EffectKind.ECHO);
				assertEquals(Arrays.asList(EffectKind.REVERB, EffectKind.ECHO), kinds(harness.sound.last().after()));
				userPicks(display, harness.rows().getAddPedal(), EffectKind.DISTORTION);
				assertEquals(Arrays.asList(EffectKind.GATE, EffectKind.DISTORTION), kinds(harness.sound.last().before()));
				assertEquals(PedalSettings.defaults(EffectKind.DISTORTION), harness.sound.last().pedal(EffectKind.DISTORTION), "a new pedal at its defaults");
				assertEquals(Arrays.asList(EffectKind.COMPRESSOR, EffectKind.OVERDRIVE, EffectKind.CHORUS), offered(harness.rows()));
				assertNull(harness.rows().getAddPedal().getSelectedValue(), "the list goes back to its prompt");
				assertNotNull(harness.rows().getPedal(EffectKind.ECHO));
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void upAndDownMoveAPedalOnlyWithinItsSection() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new Memory());
			try {
				userPicks(display, harness.rows().getAddPedal(), EffectKind.COMPRESSOR);
				userPicks(display, harness.rows().getAddPedal(), EffectKind.ECHO);
				assertFalse(harness.rows().getPedal(EffectKind.GATE).getUp().isEnabled(), "the first pedal before the amp cannot go up");
				assertFalse(harness.rows().getPedal(EffectKind.COMPRESSOR).getDown().isEnabled(), "the last pedal before the amp cannot go down");
				assertFalse(harness.rows().getPedal(EffectKind.REVERB).getUp().isEnabled(), "the first pedal after the amp cannot go up");
				userClicks(display, harness.rows().getPedal(EffectKind.GATE).getDown());
				assertEquals(Arrays.asList(EffectKind.COMPRESSOR, EffectKind.GATE), kinds(harness.sound.last().before()));
				assertEquals(Arrays.asList(EffectKind.REVERB, EffectKind.ECHO), kinds(harness.sound.last().after()), "the other section stays");
				userClicks(display, harness.rows().getPedal(EffectKind.ECHO).getUp());
				assertEquals(Arrays.asList(EffectKind.ECHO, EffectKind.REVERB), kinds(harness.sound.last().after()));
				assertEquals(Arrays.asList(EffectKind.COMPRESSOR, EffectKind.GATE), kinds(harness.sound.last().before()));
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void removeTakesThePedalOut() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new Memory());
			try {
				userClicks(display, harness.rows().getPedal(EffectKind.REVERB).getRemove());
				assertTrue(harness.sound.last().after().isEmpty());
				assertNull(harness.rows().getPedal(EffectKind.REVERB), "its row is gone");
				assertTrue(offered(harness.rows()).contains(EffectKind.REVERB), "it can be added again");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void everyRowHasItsSwitchSlidersAndTooltipsAndEveryChangeReachesTheEngine() throws Exception {
		withDisplay((display, factory) -> {
			Harness harness = new Harness(display, factory, new Memory());
			try {
				for (EffectKind kind : new ArrayList<EffectKind>(offered(harness.rows()))) {
					userPicks(display, harness.rows().getAddPedal(), kind);
				}
				assertTrue(offered(harness.rows()).isEmpty(), "every kind is in the chain");
				for (EffectKind kind : EffectKind.values()) {
					AmpPedalRow row = harness.rows().getPedal(kind);
					assertNotNull(row, kind.name());
					List<String> tips = new ArrayList<String>(Arrays.asList(row.getSwitch().getToolTipText(), row.getUp().getToolTipText(),
							row.getDown().getToolTipText(), row.getRemove().getToolTipText()));
					for (Knob knob : kind.knobs()) {
						assertNotNull(row.getScale(knob.key()), kind + " " + knob.key());
						tips.add(row.getScale(knob.key()).getToolTipText());
					}
					for (String tip : tips) {
						assertTrue(tip != null && !tip.trim().isEmpty(), kind + ": a control without a tooltip");
					}
					UIToggleButton toggle = row.getSwitch();
					userToggles(display, toggle, false);
					assertFalse(harness.sound.last().pedal(kind).enabled(), kind + " switched off");
					userToggles(display, toggle, true);
					assertTrue(harness.sound.last().pedal(kind).enabled(), kind + " switched on");
					for (Knob knob : kind.knobs()) {
						UIScale scale = row.getScale(knob.key());
						userMoves(display, scale, scale.getMaximum());
						assertEquals(knob.max(), harness.sound.last().pedal(kind).value(knob.key()), 1e-9, kind + " " + knob.key() + " at the top");
						userMoves(display, scale, 0);
						assertEquals(knob.min(), harness.sound.last().pedal(kind).value(knob.key()), 1e-9, kind + " " + knob.key() + " at the bottom");
					}
				}
				assertEquals(harness.sound.last(), harness.store.currentChain(), "every change of the chain is kept");
			} finally {
				harness.close();
			}
		});
	}

	@Test
	public void aNewWindowOverTheSameSettingsShowsTheSameChain() throws Exception {
		withDisplay((display, factory) -> {
			Memory memory = new Memory();
			Harness first = new Harness(display, factory, memory);
			ChainSettings chain;
			try {
				userPicks(display, first.rows().getAddPedal(), EffectKind.ECHO);
				UIScale time = first.rows().getPedal(EffectKind.ECHO).getScale("time");
				userMoves(display, time, time.getMaximum());
				userToggles(display, first.rows().getPedal(EffectKind.GATE).getSwitch(), false);
				chain = first.sound.last();
			} finally {
				first.close();
			}
			Harness second = new Harness(display, factory, memory);
			try {
				assertEquals(chain, second.rows().chain());
				AmpPedalRow echo = second.rows().getPedal(EffectKind.ECHO);
				assertEquals(echo.getScale("time").getMaximum(), echo.getScale("time").getValue(), "the echo's time is shown");
				assertFalse(second.rows().getPedal(EffectKind.GATE).getSwitch().isSelected(), "the gate is shown off");
			} finally {
				second.close();
			}
		});
	}

	// ---------------------------------------------------------------- native helpers

	private interface DisplayTask {
		void run(Object display, UIFactory factory) throws Exception;
	}

	private static Object control(Object widget) throws Exception {
		return widget.getClass().getMethod("getControl").invoke(widget);
	}

	private static <T> void userPicks(Object display, UIDropDownSelect<T> select, T value) throws Exception {
		select.setSelectedValue(value);
		notifySelection(display, control(select));
	}

	private static void userClicks(Object display, UIButton button) throws Exception {
		notifySelection(display, control(button));
	}

	private static void userMoves(Object display, UIScale scale, int position) throws Exception {
		Object nativeScale = control(scale);
		nativeScale.getClass().getMethod("setSelection", int.class).invoke(nativeScale, position);
		notifySelection(display, nativeScale);
	}

	private static void userToggles(Object display, UIToggleButton button, boolean selected) throws Exception {
		Object nativeButton = control(button);
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
