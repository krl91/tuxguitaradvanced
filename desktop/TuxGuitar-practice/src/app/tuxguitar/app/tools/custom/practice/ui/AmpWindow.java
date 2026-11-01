package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundSettings;
import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIToggleButton;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The Amp and effects window (spec 022, contracts/amp-window.md): the top block (the switch, the output, the two volumes, the delay), the amp and cabinet
 * rows, and a message line. One window: a second open brings it forward. It is owned by the main window, placed at its kept bounds when they are on a screen,
 * else away from the run's notes, else over the practice window; its bounds are kept when it closes, also when its owner closes it.
 */
final class AmpWindow {

	/** The texts of the window, from the language files. */
	interface Texts {
		String get(String key, String... arguments);
	}

	/** The engine side (one per practice window, {@link AmpSound} in the application). */
	interface Sound {
		/** The output devices by name, the system's default ({@code ""}) first. */
		List<String> outputs();

		/** NaN while no output is open. */
		double estimatedDelayMillis();

		void setEnabled(boolean enabled);

		void setOutput(String output);

		void setGuitarVolume(double volume);

		void setChain(ChainSettings chain);

		/** TuxGuitar's volume, 0 to 10. */
		int scoreVolume();

		void setScoreVolume(int volume);
	}

	private static final float MARGIN = 24f;
	private static final int VOLUME_POSITIONS = 100;

	private final UIFactory factory;
	private final UIWindow owner;
	private final Texts texts;
	private final AmpSoundStore store;
	private final Sound sound;
	private final Supplier<List<UIRectangle>> guards;
	private final Supplier<UIRectangle> practiceBounds;
	private final Predicate<UIRectangle> onScreen;
	private UIWindow window;
	private UIToggleButton enabled;
	private UILabel outputsLabel;
	private UIDropDownSelect<String> outputs;
	private UIScale guitarVolume;
	private UIScale scoreVolume;
	private UILabel delay;
	private UILabel message;
	private AmpChainRows rows;
	private boolean updating;

	/**
	 * @param guards the boxes on the screen of the run's framed and next notes; empty when no run is going on
	 * @param practiceBounds the practice window's bounds, or null when it is hidden
	 * @param onScreen whether bounds kept from an earlier launch are on one of the screens now
	 */
	AmpWindow(UIFactory factory, UIWindow owner, Texts texts, AmpSoundStore store, Sound sound, Supplier<List<UIRectangle>> guards,
			Supplier<UIRectangle> practiceBounds, Predicate<UIRectangle> onScreen) {
		this.factory = factory;
		this.owner = owner;
		this.texts = texts;
		this.store = store;
		this.sound = sound;
		this.guards = guards;
		this.practiceBounds = practiceBounds;
		this.onScreen = onScreen;
	}

	boolean isOpen() {
		return this.window != null && !this.window.isDisposed();
	}

	/** Opens the window, or brings it forward when it is open. */
	void open() {
		if (isOpen()) {
			this.window.moveToTop();
			return;
		}
		AmpSoundSettings settings = this.store.settings();
		this.window = this.factory.createWindow(this.owner, false, true);
		this.window.setText(this.texts.get(AmpTexts.TITLE));
		UITableLayout layout = new UITableLayout();
		this.window.setLayout(layout);

		UIPanel top = this.factory.createPanel(this.window, false);
		UITableLayout topLayout = new UITableLayout();
		top.setLayout(topLayout);
		layout.set(top, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, false);

		this.enabled = this.factory.createToggleButton(top);
		this.enabled.setText(this.texts.get(AmpTexts.ENABLED));
		this.enabled.setToolTipText(this.texts.get(AmpTexts.ENABLED + ".tip"));
		this.enabled.setSelected(settings.enabled());
		this.enabled.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpWindow.this.updating) {
					boolean on = AmpWindow.this.enabled.isSelected();
					AmpWindow.this.store.save(AmpWindow.this.store.settings().withEnabled(on));
					AmpWindow.this.sound.setEnabled(on);
				}
			}
		});
		topLayout.set(this.enabled, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false, 1, 3);

		this.outputsLabel = label(top, topLayout, 2, AmpTexts.OUTPUT);
		this.outputs = this.factory.createDropDownSelect(top);
		for (String name : this.sound.outputs()) {
			this.outputs.addItem(new UISelectItem<String>(name.isEmpty() ? this.texts.get(AmpTexts.OUTPUT_DEFAULT) : name, name));
		}
		this.outputs.setSelectedValue(settings.output());
		if (this.outputs.getSelectedValue() == null) {
			this.outputs.setSelectedValue("");
		}
		this.outputs.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				String name = AmpWindow.this.outputs.getSelectedValue();
				if (!AmpWindow.this.updating && name != null) {
					AmpWindow.this.store.save(AmpWindow.this.store.settings().withOutput(name));
					AmpWindow.this.sound.setOutput(name);
				}
			}
		});
		topLayout.set(this.outputs, 2, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		label(top, topLayout, 3, AmpTexts.GUITAR_VOLUME);
		this.guitarVolume = scale(top, topLayout, 3, AmpTexts.GUITAR_VOLUME, VOLUME_POSITIONS, (int) Math.round(settings.guitarVolume() * VOLUME_POSITIONS));
		this.guitarVolume.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpWindow.this.updating) {
					double volume = AmpWindow.this.guitarVolume.getValue() / (double) VOLUME_POSITIONS;
					AmpWindow.this.store.save(AmpWindow.this.store.settings().withGuitarVolume(volume));
					AmpWindow.this.sound.setGuitarVolume(volume);
				}
			}
		});

		label(top, topLayout, 4, AmpTexts.SCORE_VOLUME);
		this.scoreVolume = scale(top, topLayout, 4, AmpTexts.SCORE_VOLUME, 10, this.sound.scoreVolume());
		this.scoreVolume.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpWindow.this.updating) {
					AmpWindow.this.sound.setScoreVolume(AmpWindow.this.scoreVolume.getValue());
				}
			}
		});

		this.delay = this.factory.createLabel(top);
		this.delay.setToolTipText(this.texts.get(AmpTexts.DELAY + ".tip"));
		topLayout.set(this.delay, 5, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 3);

		this.rows = new AmpChainRows(this.factory, this.window, layout, 2, this.texts, this.store.currentChain(), chain -> {
			this.store.saveCurrentChain(chain);
			this.sound.setChain(chain);
		});

		this.message = this.factory.createLabel(this.window);
		layout.set(this.message, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				keepBounds();
				AmpWindow.this.window = null;
			}
		});
		showDelay();
		this.window.pack();
		this.window.setBounds(placement(settings.windowBounds()));
		this.window.open();
	}

	private UILabel label(UIPanel parent, UITableLayout layout, int row, String key) {
		UILabel label = this.factory.createLabel(parent);
		label.setText(this.texts.get(key));
		label.setToolTipText(this.texts.get(key + ".tip"));
		layout.set(label, row, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		return label;
	}

	private UIScale scale(UIPanel parent, UITableLayout layout, int row, String key, int maximum, int value) {
		UIScale scale = this.factory.createHorizontalScale(parent);
		scale.setMinimum(0);
		scale.setMaximum(maximum);
		scale.setIncrement(1);
		scale.setValue(value);
		scale.setToolTipText(this.texts.get(key + ".tip"));
		layout.set(scale, row, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(scale, UITableLayout.PACKED_WIDTH, 200f);
		return scale;
	}

	private UIRectangle placement(int[] kept) {
		UIRectangle size = this.window.getBounds();
		UIRectangle keptBounds = kept == null ? null : new UIRectangle(kept[0], kept[1], kept[2], kept[3]);
		return place(keptBounds, this.onScreen, this.owner.getBounds(), this.guards.get(), this.practiceBounds.get(), size.getWidth(), size.getHeight());
	}

	/**
	 * Where the window opens: at its kept bounds when they are on a screen; else, during a run, inside the main window on the side away from the run's notes
	 * (the side of the main window's middle where their centre is not); else centred over the practice window (over the main window when it is hidden).
	 */
	static UIRectangle place(UIRectangle kept, Predicate<UIRectangle> onScreen, UIRectangle main, List<UIRectangle> guards, UIRectangle practice, float width,
			float height) {
		if (kept != null && onScreen.test(kept)) {
			return kept;
		}
		if (guards != null && !guards.isEmpty()) {
			float centre = 0f;
			for (UIRectangle guard : guards) {
				centre += guard.getX() + guard.getWidth() / 2f;
			}
			centre /= guards.size();
			boolean notesOnTheLeft = centre < main.getX() + main.getWidth() / 2f;
			float x = notesOnTheLeft ? main.getX() + main.getWidth() - width - MARGIN : main.getX() + MARGIN;
			float y = main.getY() + Math.max(0f, (main.getHeight() - height) / 2f);
			return new UIRectangle(x, y, width, height);
		}
		UIRectangle over = practice != null ? practice : main;
		return new UIRectangle(over.getX() + (over.getWidth() - width) / 2f, Math.max(0f, over.getY() + (over.getHeight() - height) / 2f), width, height);
	}

	private void keepBounds() {
		if (this.window != null) {
			UIRectangle bounds = this.window.getBounds();
			int[] kept = { Math.round(bounds.getX()), Math.round(bounds.getY()), Math.round(bounds.getWidth()), Math.round(bounds.getHeight()) };
			this.store.save(this.store.settings().withWindowBounds(kept));
		}
	}

	void close() {
		if (isOpen()) {
			this.window.dispose();
		}
	}

	/** The engine's state or estimate changed (UI thread). */
	void soundChanged() {
		if (isOpen()) {
			showDelay();
		}
	}

	/** A sentence on the message line: a failure, input clipping (UI thread). */
	void message(String text) {
		if (isOpen()) {
			this.message.setText(text == null ? "" : text);
			this.window.layout();
		}
	}

	private void showDelay() {
		double millis = this.sound.estimatedDelayMillis();
		String value = Double.isNaN(millis) ? this.texts.get(AmpTexts.DELAY_UNKNOWN)
				: this.texts.get(AmpTexts.DELAY_VALUE, Long.toString(Math.round(millis)));
		this.delay.setText(this.texts.get(AmpTexts.DELAY) + " " + value);
	}

	UIWindow getWindow() {
		return this.window;
	}

	UIToggleButton getSwitch() {
		return this.enabled;
	}

	UIDropDownSelect<String> getOutputs() {
		return this.outputs;
	}

	UILabel getOutputsLabel() {
		return this.outputsLabel;
	}

	UIScale getGuitarVolume() {
		return this.guitarVolume;
	}

	UIScale getScoreVolume() {
		return this.scoreVolume;
	}

	UILabel getDelay() {
		return this.delay;
	}

	AmpChainRows getRows() {
		return this.rows;
	}
}
