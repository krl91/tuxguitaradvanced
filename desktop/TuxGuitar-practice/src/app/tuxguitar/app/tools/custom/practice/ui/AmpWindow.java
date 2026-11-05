package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundSettings;
import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.app.tools.custom.practice.session.ScoreKey;
import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.MonitorNotice;
import app.tuxguitar.audio.engine.monitor.MonitorState;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.audio.engine.monitor.EffectKind;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.menu.UIMenuActionItem;
import app.tuxguitar.ui.menu.UIPopupMenu;
import app.tuxguitar.ui.resource.UIPosition;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UITabFolder;
import app.tuxguitar.ui.widget.UITabItem;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The Amp and effects window (spec 022; laid out by spec 025, contracts/amp-window.md): above, the check box that turns the sound on, the delay, and the
 * sounds with Save… and ⋯ (Rename…, Delete, Restore); four scrolling tabs (the pedals before the amp, the amp, the effects after it, the output and the
 * volumes); under them Add a pedal and a message line. At its first opening it is at most {@link #FIRST_HEIGHT} pixels high, and it is never taller than
 * the screen. The sound of the score in front is picked when the score changes, and the learner's pick is remembered for it (FR-019). One window: a second open brings it forward. It is owned by the main window, placed at its kept bounds when they are on a screen,
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

		/** Whether the practice input is the computer's built-in microphone: turning the sound on warns first (FR-012). */
		default boolean builtInMicrophone() {
			return false;
		}
	}

	private static final float MARGIN = 24f;
	/** The height of the window at its first opening (spec 025, FR-010). */
	static final float FIRST_HEIGHT = 600f;
	/** The tabs, in their order. */
	private static final String[] TABS = { AmpTexts.TAB_BEFORE, AmpTexts.TAB_AMP, AmpTexts.TAB_AFTER, AmpTexts.TAB_OUTPUT };
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
	private UICheckBox enabled;
	private UILabel soundsLabel;
	private UIDropDownSelect<String> sounds;
	private UIButton saveAs;
	private UIButton more;
	private UIPopupMenu menu;
	private UIMenuActionItem rename;
	private UIMenuActionItem delete;
	private UIMenuActionItem restore;
	private final AmpNamePrompt namePrompt;
	private UITabFolder tabs;
	private final List<UIPanel> tabPanels = new ArrayList<UIPanel>();
	private Optional<ScoreKey> score = Optional.empty();
	private UILabel outputsLabel;
	private UIDropDownSelect<String> outputs;
	private UIScale guitarVolume;
	private UIScale scoreVolume;
	private UILabel delay;
	private UILabel message;
	private AmpChainRows rows;
	private boolean updating;
	private UIWindow warning;
	private UIButton turnOnAnyway;
	private UIButton warningCancel;
	private MonitorState lastState = MonitorState.OFF;

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
		this.namePrompt = new AmpNamePrompt(factory, texts);
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

		this.enabled = this.factory.createCheckBox(top);
		this.enabled.setText(this.texts.get(AmpTexts.ENABLED));
		this.enabled.setToolTipText(this.texts.get(AmpTexts.ENABLED + ".tip"));
		this.enabled.setSelected(settings.enabled());
		this.enabled.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpWindow.this.updating) {
					boolean on = AmpWindow.this.enabled.isSelected();
					if (on && AmpWindow.this.sound.builtInMicrophone()) {
						setSwitch(false);
						warn();
					} else {
						turn(on);
					}
				}
			}
		});
		topLayout.set(this.enabled, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 3);

		createSounds(top, topLayout, 2);

		// on its own line: beside the check box it would make the window too wide to open beside the run's notes
		this.delay = this.factory.createLabel(top);
		this.delay.setToolTipText(this.texts.get(AmpTexts.DELAY + ".tip"));
		topLayout.set(this.delay, 3, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false, 1, 3);

		// the four tabs, each scrolling when the window is lower than it (spec 025, FR-007, FR-010)
		this.tabs = this.factory.createTabFolder(this.window, false);
		layout.set(this.tabs, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
		this.tabPanels.clear();
		List<AmpChainRows.Place> places = new ArrayList<AmpChainRows.Place>();
		for (String key : TABS) {
			UITabItem item = this.tabs.createTab();
			item.setText(this.texts.get(key));
			UIPanel panel = PracticeLayout.scrollingTab(this.factory, item);
			UITableLayout panelLayout = new UITableLayout();
			panel.setLayout(panelLayout);
			this.tabPanels.add(panel);
			places.add(new AmpChainRows.Place(panel, panelLayout, 1));
		}

		UIPanel output = this.tabPanels.get(3);
		UITableLayout outputLayout = (UITableLayout) output.getLayout();
		this.outputsLabel = label(output, outputLayout, 1, AmpTexts.OUTPUT);
		this.outputs = this.factory.createDropDownSelect(output);
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
		outputLayout.set(this.outputs, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		label(output, outputLayout, 2, AmpTexts.GUITAR_VOLUME);
		this.guitarVolume = scale(output, outputLayout, 2, AmpTexts.GUITAR_VOLUME, VOLUME_POSITIONS, (int) Math.round(settings.guitarVolume() * VOLUME_POSITIONS));
		this.guitarVolume.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpWindow.this.updating) {
					double volume = AmpWindow.this.guitarVolume.getValue() / (double) VOLUME_POSITIONS;
					AmpWindow.this.store.save(AmpWindow.this.store.settings().withGuitarVolume(volume));
					AmpWindow.this.sound.setGuitarVolume(volume);
				}
			}
		});

		label(output, outputLayout, 3, AmpTexts.SCORE_VOLUME);
		this.scoreVolume = scale(output, outputLayout, 3, AmpTexts.SCORE_VOLUME, 10, this.sound.scoreVolume());
		this.scoreVolume.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!AmpWindow.this.updating) {
					AmpWindow.this.sound.setScoreVolume(AmpWindow.this.scoreVolume.getValue());
				}
			}
		});

		this.rows = new AmpChainRows(this.factory, places.get(0), places.get(1), places.get(2), new AmpChainRows.Place(this.window, layout, 3), this.texts,
				this.store.currentChain(), chain -> {
					this.store.saveCurrentChain(chain);
					this.sound.setChain(chain);
					showSounds();
				}, this::relayout, section -> this.tabs.setSelectedIndex(section == EffectKind.Section.BEFORE ? 0 : 2));

		this.message = this.factory.createLabel(this.window);
		layout.set(this.message, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				keepBounds();
				AmpWindow.this.window = null;
			}
		});
		showDelay();
		showSounds();
		// at most 600 pixels high at the first opening; bounds kept from an earlier launch never taller than the screen holding them (spec 025, FR-010)
		int[] kept = settings.windowBounds();
		PracticeLayout.sizeFolder(this.window, this.tabs, this.tabPanels, kept == null ? FIRST_HEIGHT : Screens.usableHeight(this.owner.getBounds()));
		this.window.pack();
		this.window.setBounds(withinScreen(placement(kept)));
		this.window.open();
	}

	/** The sound list, Save… and ⋯ with Rename…, Delete and Restore (contracts/amp-window.md "Layout"); a name is asked in a small window. */
	private void createSounds(UIPanel top, UITableLayout topLayout, int row) {
		this.soundsLabel = label(top, topLayout, row, AmpTexts.SOUND);
		UIPanel panel = this.factory.createPanel(top, false);
		UITableLayout layout = new UITableLayout(0f);
		panel.setLayout(layout);
		topLayout.set(panel, row, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.sounds = this.factory.createDropDownSelect(panel);
		this.sounds.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				String name = AmpWindow.this.sounds.getSelectedValue();
				if (!AmpWindow.this.updating && name != null) {
					pick(name);
				}
			}
		});
		layout.set(this.sounds, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.saveAs = button(panel, layout, 2, AmpTexts.SAVE, () -> this.namePrompt.open(this.window, this.texts.get(AmpTexts.SAVE_TITLE), "",
				name -> this.store.nameProblem(name, null), name -> {
					AmpSoundStore.NameProblem problem = this.store.nameProblem(name, null);
					return problem == null || problem == AmpSoundStore.NameProblem.TAKEN;
				}, name -> {
					this.store.saveAs(name, this.rows.chain());
					this.store.setCurrent(name);
					this.store.remember(this.score, this.store.current().name());
					showSounds();
				}));
		this.more = button(panel, layout, 3, AmpTexts.MORE, () -> openMenu());
		this.menu = this.factory.createPopupMenu(this.window);
		this.rename = item(AmpTexts.RENAME, () -> {
			String current = this.store.current().name();
			this.namePrompt.open(this.window, this.texts.get(AmpTexts.RENAME_TITLE), current, name -> this.store.nameProblem(name, current),
					name -> this.store.nameProblem(name, current) == null && !name.trim().equals(current), name -> {
						this.store.rename(current, name);
						this.store.remember(this.score, name);
						showSounds();
					});
		});
		this.delete = item(AmpTexts.DELETE, () -> {
			this.store.delete(this.store.current().name());
			use(ReadyMadeChains.all().get(0).name());
		});
		this.restore = item(AmpTexts.RESTORE, () -> {
			this.store.restore(this.store.current().name());
			ChainSettings chain = this.store.currentChain();
			this.rows.show(chain);
			this.sound.setChain(chain);
			showSounds();
		});
	}

	private UIMenuActionItem item(String key, Runnable action) {
		UIMenuActionItem item = this.menu.createActionItem();
		item.setText(this.texts.get(key));
		item.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		});
		return item;
	}

	/** The ⋯ menu, under its button. */
	private void openMenu() {
		float x = 0f;
		float y = 0f;
		for (UIControl control = this.more; control != null && control != this.window; control = control.getParent()) {
			x += control.getBounds().getX();
			y += control.getBounds().getY();
		}
		UIRectangle window = this.window.getBounds();
		float title = window.getHeight() - this.window.getChildArea().getHeight();
		this.menu.open(new UIPosition(window.getX() + x, window.getY() + title + y + this.more.getBounds().getHeight()));
	}

	private UIButton button(UIPanel panel, UITableLayout layout, int column, String key, Runnable action) {
		UIButton button = this.factory.createButton(panel);
		button.setText(this.texts.get(key));
		button.setToolTipText(this.texts.get(key + ".tip"));
		button.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		});
		layout.set(button, 1, column, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		return button;
	}

	/** The learner picked a sound: its chain is heard at once, and it is remembered for the score in front. */
	private void pick(String name) {
		use(name);
		this.store.remember(this.score, this.store.current().name());
	}

	/** Makes a sound the current one: the store, the engine, and the rows and list when the window is open. */
	private void use(String name) {
		this.store.setCurrent(name);
		ChainSettings chain = this.store.currentChain();
		this.sound.setChain(chain);
		if (isOpen()) {
			this.rows.show(chain);
			showSounds();
		}
	}

	/**
	 * The score in front changed (or the practice window opened on it): its remembered sound, else the last one, becomes current before any run. When it is
	 * already the current sound, its unsaved changes are kept.
	 */
	void scoreChanged(Optional<ScoreKey> key) {
		this.score = key;
		String name = this.store.soundFor(key).name();
		if (!name.equals(this.store.current().name())) {
			use(name);
		}
	}

	/** The list from the store, the current sound selected and marked "(changed)" when it is. */
	private void showSounds() {
		if (!isOpen()) {
			return;
		}
		app.tuxguitar.app.tools.custom.practice.session.Sound currentSound = this.store.current();
		boolean currentChanged = this.store.currentChanged();
		this.updating = true;
		try {
			this.sounds.removeItems();
			for (app.tuxguitar.app.tools.custom.practice.session.Sound sound : this.store.sounds()) {
				boolean changed = sound.changed() || (sound.name().equals(currentSound.name()) && currentChanged);
				this.sounds.addItem(new UISelectItem<String>(changed ? sound.name() + " " + this.texts.get(AmpTexts.CHANGED) : sound.name(), sound.name()));
			}
			this.sounds.setSelectedValue(currentSound.name());
		} finally {
			this.updating = false;
		}
		showButtons();
	}

	/** Rename…, Delete and Restore enabled only when they apply (spec 022's rules; the name rules are the prompt's). */
	private void showButtons() {
		if (!isOpen()) {
			return;
		}
		app.tuxguitar.app.tools.custom.practice.session.Sound current = this.store.current();
		this.rename.setEnabled(!current.readyMade());
		this.delete.setEnabled(!current.readyMade());
		this.restore.setEnabled(current.readyMade() && current.changed());
	}

	private void turn(boolean on) {
		setSwitch(on);
		this.store.save(this.store.settings().withEnabled(on));
		this.sound.setEnabled(on);
	}

	private void setSwitch(boolean on) {
		this.updating = true;
		try {
			this.enabled.setSelected(on);
		} finally {
			this.updating = false;
		}
	}

	/** The headphones warning before the sound starts with the built-in microphone (FR-012): the sound starts only after Turn on anyway. */
	private void warn() {
		if (this.warning != null && !this.warning.isDisposed()) {
			this.warning.moveToTop();
			return;
		}
		this.warning = this.factory.createWindow(this.window, true, false);
		this.warning.setText(this.texts.get(AmpTexts.WARNING_TITLE));
		UITableLayout layout = new UITableLayout();
		this.warning.setLayout(layout);
		UILabel text = this.factory.createLabel(this.warning);
		text.setText(this.texts.get(AmpTexts.WARNING_TEXT));
		layout.set(text, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(text, UITableLayout.PACKED_WIDTH, 360f);
		this.warningCancel = warningButton(layout, 1, AmpTexts.WARNING_CANCEL, () -> {
			this.warning.dispose();
			setSwitch(false);
			this.store.save(this.store.settings().withEnabled(false));
		});
		this.turnOnAnyway = warningButton(layout, 2, AmpTexts.TURN_ON_ANYWAY, () -> {
			this.warning.dispose();
			turn(true);
		});
		this.warning.pack();
		UIRectangle over = this.window.getBounds();
		UIRectangle size = this.warning.getBounds();
		this.warning.setBounds(new UIRectangle(over.getX() + (over.getWidth() - size.getWidth()) / 2f, over.getY() + 40f, size.getWidth(), size.getHeight()));
		this.warning.open();
	}

	private UIButton warningButton(UITableLayout layout, int column, String key, Runnable action) {
		UIButton button = this.factory.createButton(this.warning);
		button.setText(this.texts.get(key));
		button.setToolTipText(this.texts.get(key + ".tip"));
		button.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		});
		layout.set(button, 2, column, column == 1 ? UITableLayout.ALIGN_RIGHT : UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		return button;
	}

	/**
	 * The sound would start on its own with the built-in microphone as input: the practice window opened with the sound on, or the input changed to it while
	 * it played (FR-012). The window opens with the switch off and the warning; the sound starts only after Turn on anyway.
	 */
	void warnBeforeStart() {
		open();
		setSwitch(false);
		warn();
	}

	/**
	 * The engine's state changed (UI thread). A howl, an overload or a lost device stops the sound with a message and turns the switch off, so the learner
	 * turns it on again; a device refused at the start says why.
	 */
	void stateChanged(MonitorState state, String reason) {
		MonitorState before = this.lastState;
		this.lastState = state;
		String text = null;
		if (state == MonitorState.MUTED_FEEDBACK) {
			text = this.texts.get(AmpTexts.FEEDBACK);
		} else if (state == MonitorState.MUTED_OVERLOAD) {
			text = this.texts.get(AmpTexts.OVERLOAD);
		} else if (state == MonitorState.FAILED) {
			text = this.texts.get(before == MonitorState.ON ? AmpTexts.DEVICE_LOST : AmpTexts.FAILED, reason == null ? "" : reason);
		} else if (state == MonitorState.STARTING) {
			message("");
		}
		if (text != null) {
			this.store.save(this.store.settings().withEnabled(false));
			if (isOpen()) {
				setSwitch(false);
			}
			message(text);
		}
		soundChanged();
	}

	/** A notice of the engine: input clipping, or the chain made lighter (UI thread). */
	void notice(MonitorNotice notice) {
		message(this.texts.get(AmpTexts.notice(notice)));
	}

	/** The pedals' rows changed: the tabs are laid out again, the window keeps its size (a tab taller than the window scrolls, spec 025). */
	private void relayout() {
		if (isOpen()) {
			for (UIPanel panel : this.tabPanels) {
				panel.computePackedSize(null, null);
			}
			this.window.layout();
		}
	}

	/** Bounds no taller than the screen holding them (768 pixels when it is not known). */
	private static UIRectangle withinScreen(UIRectangle bounds) {
		float screen = PracticeLayout.screen(Screens.usableHeight(bounds));
		return bounds.getHeight() <= screen ? bounds : new UIRectangle(bounds.getX(), bounds.getY(), bounds.getWidth(), screen);
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
		this.namePrompt.close();
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

	UICheckBox getSwitch() {
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

	UILabel getMessage() {
		return this.message;
	}

	UIWindow getWarning() {
		return this.warning;
	}

	UIButton getTurnOnAnyway() {
		return this.turnOnAnyway;
	}

	UIButton getWarningCancel() {
		return this.warningCancel;
	}

	UILabel getDelay() {
		return this.delay;
	}

	UILabel getSoundsLabel() {
		return this.soundsLabel;
	}

	UIDropDownSelect<String> getSounds() {
		return this.sounds;
	}

	UIButton getSaveAs() {
		return this.saveAs;
	}

	UIButton getMore() {
		return this.more;
	}

	UIMenuActionItem getRename() {
		return this.rename;
	}

	UIMenuActionItem getDelete() {
		return this.delete;
	}

	UIMenuActionItem getRestore() {
		return this.restore;
	}

	AmpNamePrompt getNamePrompt() {
		return this.namePrompt;
	}

	UITabFolder getTabs() {
		return this.tabs;
	}

	AmpChainRows getRows() {
		return this.rows;
	}
}
