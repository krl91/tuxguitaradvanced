package app.tuxguitar.app.tools.custom.tunernext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.system.config.TGConfigKeys;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.app.system.keybindings.KeyBindingActionManager;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.editor.event.TGUpdateEvent;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.helpers.tuning.TuningManager;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIToggleButton;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGMusicKeyUtils;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The evolved guitar tuner (FR-046–FR-060) on the shared audio engine: chromatic and guided
 * six-string modes, note + octave + cents, flat / in tune / sharp with an animated needle, Hz
 * readout, rough scale, clickable string targets, live input level; input problems instead of
 * confident readings.
 */
public class TunerWindow {

	/** Provisional, configurable defaults approved 2026-09-26 (refined in tasks T076). */

	private static final String CONSUMER_ID = "tuner";
	private static final long UI_INTERVAL_NANOS = 33_000_000L;

	private final TGContext context;
	private final TunerSessionState state;
	/** Audio-recognition settings (T102): in-tune range, hold, confidence; read on the UI thread. */
	private final TunerRecognition recognition;
	private final AtomicReference<AnalysisFrame> latestFrame = new AtomicReference<AnalysisFrame>();
	private final AtomicBoolean uiPending = new AtomicBoolean();
	private volatile long lastUiNanos;
	private AudioEngine engine;
	private AnalysisService.Listener frameListener;
	private CurrentInputSelection.Listener selectionListener;
	private PracticeTunerContext shared;
	private PracticeTunerContext.Listener sharedListener;
	private TGEventListener updateListener;

	private UIWindow window;
	private UIDropDownSelect<Boolean> mode;
	private UIDropDownSelect<TunerTuning> tunings;
	private UILabel tuningSource;
	private final List<UIToggleButton> stringButtons = new ArrayList<UIToggleButton>();
	private UILabel noteLabel;
	private UILabel stateLabel;
	private UILabel frequencyLabel;
	private UILabel inputLabel;
	private UILabel levelLabel;
	private TunerGainControl gainControl;
	private TunerNeedle needle;
	private TunerScale scale;
	private UIFont noteFont;
	private TunerTuning tuning;
	private int selectedString;

	public TunerWindow(TGContext context) {
		this.context = context;
		this.state = TunerSessionState.getInstance(context);
		this.recognition = new TunerRecognition(RecognitionSettingsStore.getInstance(context));
	}

	public void open() {
		if( isOpen() ) {
			this.window.moveToTop();
			return;
		}
		this.engine = AudioEngine.getInstance(this.context);
		this.engine.selectDefaultInputIfNone();
		this.stringButtons.clear();
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UITableLayout layout = new UITableLayout();
		this.window = uiFactory.createWindow(TGWindow.getInstance(this.context).getWindow(), false, true);
		this.window.setLayout(layout);
		this.window.setImage(TuxGuitar.getInstance().getIconManager().getAppIcon());
		this.window.setText(TuxGuitar.getProperty("tuner.tuner"));

		UIPanel top = panel(uiFactory, layout, 1);
		UITableLayout topLayout = (UITableLayout) top.getLayout();
		this.mode = uiFactory.createDropDownSelect(top);
		this.mode.addItem(new UISelectItem<Boolean>(TuxGuitar.getProperty("tunernext.mode.guided"), Boolean.TRUE));
		this.mode.addItem(new UISelectItem<Boolean>(TuxGuitar.getProperty("tunernext.mode.chromatic"), Boolean.FALSE));
		this.mode.setSelectedValue(Boolean.TRUE);
		this.mode.addSelectionListener(event -> updateTargets());
		topLayout.set(this.mode, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.tunings = uiFactory.createDropDownSelect(top);
		topLayout.set(this.tunings, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		UIButton custom = button(uiFactory, top, topLayout, 1, 3, "tunernext.custom", () -> new TunerCustomDialog(this.context, this.window, this.tuning, chosen -> {
			this.state.choose(chosen);
			loadTunings();
		}).open());
		this.tuningSource = uiFactory.createLabel(top);
		topLayout.set(this.tuningSource, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 3);
		this.tunings.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TunerTuning chosen = TunerWindow.this.tunings.getSelectedValue();
				if( chosen != null && chosen != TunerWindow.this.tuning ) {
					TunerWindow.this.state.choose(chosen);
					TunerWindow.this.tuning = chosen;
					updateTargets();
				}
			}
		});

		UIPanel strings = panel(uiFactory, layout, 2);
		for( int i = 0 ; i < TunerTuning.STRING_COUNT ; i ++ ) {
			final int stringNumber = TunerTuning.STRING_COUNT - i;
			UIToggleButton button = uiFactory.createToggleButton(strings);
			button.addSelectionListener(event -> selectString(stringNumber));
			((UITableLayout) strings.getLayout()).set(button, 1, i + 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			this.stringButtons.add(0, button);
		}

		UIPanel reading = panel(uiFactory, layout, 3);
		UITableLayout readingLayout = (UITableLayout) reading.getLayout();
		this.noteFont = uiFactory.createFont(TGConfigManager.getInstance(this.context).getFontModelConfigValue(TGConfigKeys.MATRIX_FONT).getName(), 28, true, false);
		this.noteLabel = uiFactory.createLabel(reading);
		this.noteLabel.setFont(this.noteFont);
		readingLayout.set(this.noteLabel, 1, 1, UITableLayout.ALIGN_CENTER, UITableLayout.ALIGN_CENTER, true, false);
		this.stateLabel = uiFactory.createLabel(reading);
		readingLayout.set(this.stateLabel, 2, 1, UITableLayout.ALIGN_CENTER, UITableLayout.ALIGN_CENTER, true, false);
		this.frequencyLabel = uiFactory.createLabel(reading);
		readingLayout.set(this.frequencyLabel, 3, 1, UITableLayout.ALIGN_CENTER, UITableLayout.ALIGN_CENTER, true, false);

		this.needle = new TunerNeedle(this.context, uiFactory, this.window);
		layout.set(this.needle.getControl(), 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 400f, 160f, null);
		this.scale = new TunerScale(this.context, uiFactory, this.window);
		layout.set(this.scale.getControl(), 5, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, false, 1, 1, 400f, 70f, null);

		UIPanel bottom = panel(uiFactory, layout, 6);
		UITableLayout bottomLayout = (UITableLayout) bottom.getLayout();
		this.inputLabel = uiFactory.createLabel(bottom);
		bottomLayout.set(this.inputLabel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.levelLabel = uiFactory.createLabel(bottom);
		bottomLayout.set(this.levelLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		button(uiFactory, bottom, bottomLayout, 1, 2, "tunernext.settings", () -> new TunerSettingsDialog(this.context, this.window, this.engine).open());
		button(uiFactory, bottom, bottomLayout, 1, 3, "close", () -> close());

		// the input gain, shared with the practice window (spec 002, FR-060)
		this.gainControl = new TunerGainControl(this.engine, uiFactory, this.window);
		layout.set(this.gainControl.getControl(), 7, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		KeyBindingActionManager keyBindings = KeyBindingActionManager.getInstance(this.context);
		keyBindings.appendListenersTo(top);
		keyBindings.appendListenersTo(reading);
		keyBindings.appendListenersTo(bottom);
		keyBindings.appendListenersTo(custom);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				TunerWindow.this.engine.unsubscribe(TunerWindow.this.frameListener);
				TunerWindow.this.engine.getSelection().removeListener(TunerWindow.this.selectionListener);
				TunerWindow.this.engine.release(CONSUMER_ID);
				TunerWindow.this.shared.removeListener(TunerWindow.this.sharedListener);
				TGEditorManager.getInstance(TunerWindow.this.context).removeUpdateListener(TunerWindow.this.updateListener);
				TunerWindow.this.shared.setTunerOpen(false);
				TunerWindow.this.noteFont.dispose();
				TunerWindow.this.window = null;
			}
		});

		this.frameListener = this::frameReceived;
		this.selectionListener = snapshot -> TGSynchronizer.getInstance(this.context).executeLater(this::updateInput);
		this.engine.getSelection().addListener(this.selectionListener);
		this.engine.subscribe(this.frameListener);
		this.engine.acquire(CONSUMER_ID);

		// FR-052: follow the Practice Track, or the selected guitar-confirmed track, until an explicit choice
		this.shared = PracticeTunerContext.getInstance(this.context);
		this.sharedListener = changed -> TGSynchronizer.getInstance(this.context).executeLater(this::refreshTrackTuning);
		this.shared.addListener(this.sharedListener);
		this.updateListener = event -> {
			if( TGUpdateEvent.EVENT_TYPE.equals(event.getEventType()) ) {
				TGSynchronizer.getInstance(this.context).executeLater(this::refreshTrackTuning);
			}
		};
		TGEditorManager.getInstance(this.context).addUpdateListener(this.updateListener);
		this.shared.setTunerOpen(true);

		loadTunings();
		updateInput();
		render(null);
		TGDialogUtil.openDialog(this.window, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
	}

	public void close() {
		if( isOpen() ) {
			this.window.dispose();
		}
	}

	public boolean isOpen() {
		return (this.window != null && !this.window.isDisposed());
	}

	/** Re-evaluates a track-based tuning when the practice track or the selected track changed. */
	private void refreshTrackTuning() {
		if( isOpen() && this.state.getExplicitChoice() == null ) {
			TunerTuning next = this.state.effectiveTuning(practiceTrackTuning(), confirmedSelectedTrackTuning());
			if( !next.sameTargets(this.tuning.getTargets()) || next.getSource() != this.tuning.getSource() ) {
				loadTunings();
			}
		}
	}

	private TunerTuning practiceTrackTuning() {
		TGTrack track = this.shared.getPracticeTrack();
		return (track != null && track.stringCount() == TunerTuning.STRING_COUNT ? TunerTuning.track(track) : null);
	}

	/** The track selected in the score, only if the learner confirmed it as a guitar part (FR-052 b). */
	private TunerTuning confirmedSelectedTrackTuning() {
		TGSong song = TuxGuitar.getInstance().getDocumentManager().getSong();
		TGTrack track = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret().getTrack();
		if( song == null || track == null || track.stringCount() != TunerTuning.STRING_COUNT || !this.shared.isConfirmed(song, track) ) {
			return null;
		}
		return TunerTuning.track(track);
	}

	/** Lists the presets and shows the effective tuning (FR-052, FR-055). */
	private void loadTunings() {
		this.tuning = this.state.effectiveTuning(practiceTrackTuning(), confirmedSelectedTrackTuning());
		List<TunerTuning> presets = TunerTuning.builtInPresets(TuningManager.getInstance(this.context).getTgTuningsGroup());
		this.tunings.removeItems();
		boolean listed = false;
		for( TunerTuning preset : presets ) {
			boolean current = (this.tuning.getSource() == TunerTuning.Source.PRESET && preset.getName().equals(this.tuning.getName()));
			TunerTuning item = (current ? this.tuning : preset);
			this.tunings.addItem(new UISelectItem<TunerTuning>(preset.getName(), item));
			listed |= current;
		}
		if( !listed ) {
			this.tunings.addItem(new UISelectItem<TunerTuning>(sourceText(this.tuning), this.tuning));
		}
		this.tunings.setSelectedValue(this.tuning);
		updateTargets();
	}

	private void updateTargets() {
		this.recognition.clearHold();
		boolean guided = Boolean.TRUE.equals(this.mode.getSelectedValue());
		int[] targets = this.tuning.getTargets();
		for( int i = 0 ; i < this.stringButtons.size() ; i ++ ) {
			UIToggleButton button = this.stringButtons.get(i);
			button.setText((i + 1) + ": " + TGMusicKeyUtils.sharpNoteFullName(targets[i]));
			button.setEnabled(guided);
			button.setSelected(guided && this.selectedString == i + 1);
		}
		this.tuningSource.setText(guided ? sourceText(this.tuning) : TuxGuitar.getProperty("tunernext.chromatic-hint"));
		this.window.layout();
	}

	private void selectString(int stringNumber) {
		this.selectedString = (this.selectedString == stringNumber ? 0 : stringNumber);
		updateTargets();
	}

	private String sourceText(TunerTuning tuning) {
		switch( tuning.getSource() ) {
			case TRACK:
				return TuxGuitar.getProperty("tunernext.source.track", new String[] { tuning.getName() });
			case CUSTOM:
				return TuxGuitar.getProperty("tunernext.source.custom");
			default:
				return TuxGuitar.getProperty("tunernext.source.preset", new String[] { tuning.getName() });
		}
	}

	private void updateInput() {
		this.recognition.clearHold();
		if( !isOpen() ) {
			return;
		}
		// a new input resets the gain: show it
		this.gainControl.refresh();
		CurrentInputSelection.Snapshot snapshot = this.engine.getSelection().snapshot();
		AudioInputDescriptor input = snapshot.getInput();
		this.inputLabel.setText(TuxGuitar.getProperty("tunernext.input", new String[] {
				input != null ? input.getDisplayName() : TuxGuitar.getProperty("tunernext.input.none") }));
		this.window.layout();
	}

	/** Analysis thread: keeps the latest frame and schedules at most ~30 UI updates per second. */
	private void frameReceived(AnalysisFrame frame) {
		this.latestFrame.set(frame);
		long now = System.nanoTime();
		if( now - this.lastUiNanos >= UI_INTERVAL_NANOS && this.uiPending.compareAndSet(false, true) ) {
			this.lastUiNanos = now;
			TGSynchronizer.getInstance(this.context).executeLater(() -> {
				this.uiPending.set(false);
				render(this.latestFrame.get());
			});
		}
	}

	private void render(AnalysisFrame frame) {
		if( !isOpen() ) {
			return;
		}
		if( frame == null ) {
			this.noteLabel.setText("-");
			this.stateLabel.setText(TuxGuitar.getProperty("tunernext.state.NO_READING"));
			this.frequencyLabel.setText("");
			this.needle.setPosition(0, false);
			this.scale.setPitch(-1, 0);
			return;
		}
		boolean guided = Boolean.TRUE.equals(this.mode.getSelectedValue());
		TunerHold.Shown held = this.recognition.show(frame, this.engine.getSelection().snapshot().getAssessment(),
				guided ? this.tuning.getTargets() : null, guided ? this.selectedString : 0);
		TunerDisplay display = held.getDisplay();
		if( display.isTuning() ) {
			PitchReading shown = display.getReading();
			this.noteLabel.setText(shown.getNoteName() + shown.getOctave());
			String cents = Long.toString(Math.round(Math.abs(display.getDeviationCents())));
			String target = (guided ? TGMusicKeyUtils.sharpNoteFullName(this.tuning.getTargets()[display.getTargetString() - 1]) : shown.getNoteName() + shown.getOctave());
			String state = TuxGuitar.getProperty("tunernext.state." + display.getState().name(), new String[] { cents, target });
			if( held.isHeld() ) {
				// stale reading: said in text, not only by the grey needle (FR-070)
				state = TuxGuitar.getProperty("tunernext.held", new String[] { state,
						TuxGuitar.getProperty("tunernext.held.reason." + held.getLiveState().name()) });
			}
			this.stateLabel.setText(state);
			this.frequencyLabel.setText(String.format("%.1f Hz", display.getFrequencyHz()));
			this.scale.setPitch(shown.getMidi(), shown.getCents());
			for( int i = 0 ; guided && this.selectedString == 0 && i < this.stringButtons.size() ; i ++ ) {
				this.stringButtons.get(i).setSelected(i + 1 == display.getTargetString());
			}
		} else {
			this.noteLabel.setText("-");
			this.stateLabel.setText(TuxGuitar.getProperty("tunernext.state." + display.getState().name()));
			this.frequencyLabel.setText("");
			this.scale.setPitch(-1, 0);
		}
		this.needle.setPosition(display.getNeedlePosition(), display.isTuning(), held.isHeld());
		this.levelLabel.setText(TuxGuitar.getProperty("tunernext.level." + frame.getLevelState().name()));
		if( this.gainControl.showWarning(frame) ) {
			this.window.layout();
		}
	}

	private UIPanel panel(UIFactory uiFactory, UITableLayout parentLayout, int row) {
		UIPanel panel = uiFactory.createPanel(this.window, false);
		panel.setLayout(new UITableLayout());
		parentLayout.set(panel, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		return panel;
	}

	private static UIButton button(UIFactory uiFactory, UIPanel parent, UITableLayout layout, int row, int column, String key, Runnable action) {
		UIButton button = uiFactory.createButton(parent);
		button.setText(TuxGuitar.getProperty(key));
		button.addSelectionListener(event -> action.run());
		layout.set(button, row, column, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false, 1, 1, 80f, 25f, null);
		return button;
	}
}
