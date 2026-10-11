package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Arrays;
import java.util.Map;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.system.keybindings.KeyBindingActionManager;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeStarter;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeController;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeResultWindow;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeRunPort;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeSceneLabels;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeSession;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeTexts;
import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeWindow;
import app.tuxguitar.app.tools.custom.practice.model.ArcadeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidate;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.FollowEvents;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.FollowStarter;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.app.tools.custom.practice.session.PassageSettings;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;
import app.tuxguitar.app.tools.custom.practice.session.SessionEvents;
import app.tuxguitar.app.tools.custom.practice.session.SessionLoop;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.component.tab.Caret;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.editor.action.TGActionProcessor;
import app.tuxguitar.song.helpers.tuning.TuningManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UITabFolder;
import app.tuxguitar.ui.widget.UITabItem;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * Non-modal Interactive Practice window: the preparation step (FR-002, FR-003, FR-008, FR-038);
 * while practice runs it is hidden and the compact {@link PracticePalette} and the score frames
 * take over (FR-073–FR-076). Practice shortcuts work while it has
 * focus. Session state lives on the session thread; this window only renders snapshots.
 */
public class PracticeWindow {

	/**
	 * Provisional, configurable defaults approved 2026-09-26; acceptance widened from 30 to 35
	 * cents by T076 (fretted notes on the real-guitar clips read up to +30.5 cents at p90).
	 */

	private static final String CONSUMER_ID = "practice";
	private static final String[] NOTE_NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

	private final TGContext context;
	private final PracticePlugin plugin;
	private final PracticeTunerContext shared;
	private final PracticeHighlight highlight;
	private AudioEngine engine;

	private UIWindow window;
	private UITabFolder preparation;
	private final java.util.List<UIPanel> tabPanels = new java.util.ArrayList<UIPanel>();
	private PracticePalette palette;
	private InputSelectorPanel inputSelector;
	private LevelPanel levelPanel;
	private UILabel headphoneLabel;
	private UILabel delayLabel;
	private UIDropDownSelect<PracticeTrackCandidate> tracks;
	private UILabel trackNotice;
	private UILabel tuningLabel;
	private UILabel unusualLabel;
	private UILabel startLabel;
	private UILabel mismatchLabel;
	private PracticeTunerContext.Listener sharedListener;
	private RecognitionSettingsStore.Listener settingsListener;
	private PracticeSequence preparedSequence;
	private UIButton startButton;
	private UIDropDownSelect<Integer> passageMeasures;
	private UIDropDownSelect<Integer> passageThreshold;

	private TGSong song;
	private InputAssessment assessment = InputAssessment.UNCHECKED;
	private CurrentInputSelection.Listener selectionListener;
	private SessionLoop<PracticeSnapshot> loop;
	private PracticeSession session;
	private SessionEvents events;
	/** A one-click start is waiting for the input to become usable (see {@link QuickStart}). */
	private boolean autoStart;
	private boolean quickStartRequested;
	/** False while a one-click start has not needed the preparation window (it exists but was never shown). */
	private boolean windowShown;
	private static final long QUICK_START_PATIENCE_MILLIS = 2500L;
	/** The "starting at measure..." text, to which the quick-start waiting hint is added while it applies. */
	private String startBase = "";
	private AnalysisService.Listener frameListener;
	// ---- the tempo-following mode (spec 002 T096-T099): a run beside the wait-mode session, never together with it
	private FollowPanel followPanel;
	private EngineFollowAdapter followAdapter;
	private FollowStarter.Plan followPlan;
	private AnalysisService.Listener followFrameListener;
	private FollowSession.Snapshot followLast;
	private int followShownEvent = -1;
	private int followShownExtras;
	// ---- Arcade (spec 003 Increment D): the same run as the tempo-following mode (followPlan), seen on the isometric view
	private ArcadeController arcadeController;
	private ArcadeSession arcadeSession;
	private ArcadeWindow arcadeWindow;
	private ArcadeResultWindow arcadeResult;
	private PracticeTrackCandidate arcadeCandidate;
	private PracticeSequence arcadeFull;
	/** The whole piece as the preparation gave it: the button Restart of the game starts again from its first event, whatever replayed section it is in. */
	private PracticeSequence arcadeWhole;
	private static final ArcadeTexts ARCADE_TEXTS = (key, arguments) -> arguments.length == 0 ? TuxGuitar.getProperty(key) : TuxGuitar.getProperty(key, arguments);

	public PracticeWindow(TGContext context, PracticePlugin plugin) {
		this.context = context;
		this.plugin = plugin;
		this.shared = PracticeTunerContext.getInstance(context);
		this.highlight = new PracticeHighlight(context);
		this.palette = new PracticePalette(context, new PracticePalette.Controller() {
			public void post(PracticeCommand command) {
				PracticeWindow.this.post(command);
			}

			public void stop() {
				PracticeWindow.this.stopNow();
			}

			public void closed() {
				PracticeWindow.this.post(PracticeCommand.STOP);
			}
		}, PracticeParameters.of(RecognitionSettingsStore.getInstance(context).get()).tolerance());
	}

	/**
	 * The transport-bar button: opens the window and, when the track under the score cursor can be
	 * practised, starts practising it as soon as the input is usable (no Start press needed). When
	 * the window is already open it is only brought to the front.
	 */
	public void openAndStart() {
		this.quickStartRequested = !isOpen();
		open();
		this.quickStartRequested = false;
	}

	/**
	 * The coloured button of the transport bar (owner, 2026-10-11): Arcade on the track under the cursor right now, with the input as it is (the last
	 * one used, or the remembered default), without going through the choices of the preparation window.
	 */
	public void openAndStartArcade() {
		FollowSettings.getInstance(this.context).setMode(FollowSettings.Mode.ARCADE);
		if( isOpen() ) {
			if( this.arcadeWindow != null && this.arcadeWindow.isOpen() ) {
				this.arcadeWindow.getWindow().moveToTop();
				return;
			}
			open();
			modeChanged();
			startSession();
			return;
		}
		openAndStart();
	}

	/** Opens the window, or brings it to the front when it is already open. */
	public void open() {
		if( this.isOpen() ) {
			if( this.palette.isOpen() ) {
				this.palette.open();
			} else if( this.arcadeWindow != null && this.arcadeWindow.isOpen() ) {
				// an Arcade run is going on: the game comes to the front, the preparation stays hidden
				this.arcadeWindow.getWindow().moveToTop();
			} else {
				// hidden after an Arcade run whose result was shown: it comes back (showWindow shows it again and brings it to the front)
				showWindow();
			}
			return;
		}
		this.engine = AudioEngine.getInstance(this.context);
		this.followAdapter = new EngineFollowAdapter(this.context, this.engine);
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UIWindow uiParent = TGWindow.getInstance(this.context).getWindow();
		UITableLayout windowLayout = new UITableLayout();

		this.window = uiFactory.createWindow(uiParent, false, true);
		this.window.setLayout(windowLayout);
		this.window.setImage(TuxGuitar.getInstance().getIconManager().getAppIcon());
		this.window.setText(TuxGuitar.getProperty(PracticePlugin.OPEN_ACTION_ID));

		// the preparation is in tabs, so that the window is not taller than a screen: the track, the input, the mode, the repeated passages
		this.preparation = createPreparation(uiFactory);
		windowLayout.set(this.preparation, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 520f, null, null);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		windowLayout.set(buttons, 3, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, false);
		// Start and Close are always in view, whatever the tab
		this.startButton = button(uiFactory, buttons, buttonsLayout, 1, "practice.start", () -> startSession());
		UIButton closeButton = button(uiFactory, buttons, buttonsLayout, 2, "close", () -> close());
		buttonsLayout.set(closeButton, UITableLayout.MARGIN_RIGHT, 0f);
		listenKeys(buttons, closeButton, this.startButton);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				teardownSession();
				PracticeWindow.this.followPanel.dispose();
				PracticeWindow.this.inputSelector.dispose();
				PracticeWindow.this.levelPanel.disconnect();
				PracticeWindow.this.engine.getSelection().removeListener(PracticeWindow.this.selectionListener);
				PracticeWindow.this.engine.release(CONSUMER_ID);
				PracticeWindow.this.shared.removeListener(PracticeWindow.this.sharedListener);
				PracticeWindow.this.shared.setPracticeTrack(null);
				RecognitionSettingsStore.getInstance(PracticeWindow.this.context).removeListener(PracticeWindow.this.settingsListener);
				PracticeWindow.this.autoStart = false;
				PracticeWindow.this.window = null;
				PracticeWindow.this.plugin.windowStateChanged(PracticeWindow.this.context);
			}
		});

		this.selectionListener = snapshot -> TGSynchronizer.getInstance(this.context).executeLater(() -> inputChanged(snapshot));
		this.engine.getSelection().addListener(this.selectionListener);
		this.levelPanel.connect();
		this.sharedListener = changed -> TGSynchronizer.getInstance(this.context).executeLater(() -> updateMismatch());
		this.shared.addListener(this.sharedListener);
		// the Configuration window may reset the passage choice while this window is open (T102)
		this.settingsListener = (previous, current) -> TGSynchronizer.getInstance(this.context).executeLater(() -> {
			if( isOpen() ) {
				refreshPassageSettings();
			}
		});
		RecognitionSettingsStore.getInstance(this.context).addListener(this.settingsListener);
		this.engine.acquire(CONSUMER_ID);
		this.assessment = this.engine.getSelection().snapshot().getAssessment();

		this.windowShown = false;
		showPreparation();
		if( this.autoStart ) {
			// one click on the Practice button: straight to the practice, the configuration window only appears if the learner has to act
			watchQuickStart();
		} else {
			showWindow();
		}
		this.plugin.windowStateChanged(this.context);
	}

	/** Shows the preparation window (once opened it is only hidden and shown again). */
	private void showWindow() {
		if( !isOpen() ) {
			return;
		}
		if( !this.windowShown ) {
			this.windowShown = true;
			refit();
			TGDialogUtil.openDialog(this.window, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
			refit();
		} else {
			// back from a hidden state (the Arcade run): shown, recomposed and painted again
			WindowRevival.revive(this.window, this.preparation, this.followPanel::refresh, this::refit);
		}
	}

	/** A one-click start that cannot go on by itself (no usable input after a short wait) shows the window so the learner can choose the input. */
	private void watchQuickStart() {
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(QUICK_START_PATIENCE_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			TGSynchronizer.getInstance(this.context).executeLater(() -> {
				if( isOpen() && !this.windowShown && this.session == null ) {
					showWindow();
				}
			});
		}, "practice-quick-start");
		watchdog.setDaemon(true);
		watchdog.start();
	}

	public void close() {
		if( this.isOpen() ) {
			this.window.dispose();
		}
	}

	public boolean isOpen() {
		return (this.window != null && !this.window.isDisposed());
	}

	// ---------------------------------------------------------------- preparation (FR-002)

	private UITabFolder createPreparation(UIFactory uiFactory) {
		UITabFolder folder = uiFactory.createTabFolder(this.window, false);

		// ---- tab 1: the track
		UITableLayout layout = new UITableLayout();
		UIPanel panel = tab(uiFactory, folder, "practice.tab.track", layout);
		UILabel trackLabel = uiFactory.createLabel(panel);
		trackLabel.setText(TuxGuitar.getProperty("practice.track"));
		layout.set(trackLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.tracks = uiFactory.createDropDownSelect(panel);
		layout.set(this.tracks, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.tracks.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				// the learner chose a track: no automatic start any more
				PracticeWindow.this.autoStart = false;
				updateTuning();
			}
		});
		this.trackNotice = label(uiFactory, panel, layout, 2);
		this.tuningLabel = label(uiFactory, panel, layout, 3);
		this.unusualLabel = label(uiFactory, panel, layout, 4);
		this.startLabel = label(uiFactory, panel, layout, 5);
		this.mismatchLabel = label(uiFactory, panel, layout, 6);

		// ---- tab 2: the input and its level
		UITableLayout inputLayout = new UITableLayout();
		UIPanel inputPanel = tab(uiFactory, folder, "practice.tab.input", inputLayout);
		this.inputSelector = new InputSelectorPanel(this.context, this.engine, uiFactory, inputPanel);
		inputLayout.set(this.inputSelector.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.inputSelector.setContentListener(this::refit);
		this.levelPanel = new LevelPanel(this.context, this.engine, uiFactory, inputPanel);
		this.levelPanel.setContentListener(this::refit);
		inputLayout.set(this.levelPanel.getControl(), 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.headphoneLabel = label(uiFactory, inputPanel, inputLayout, 3);

		// ---- tab 3: the mode, and for the tempo-following modes the tempo, the tolerance and the rhythm calibration (spec 002 T097, T098)
		UITableLayout modeLayout = new UITableLayout();
		UIPanel modePanel = tab(uiFactory, folder, "practice.tab.mode", modeLayout);
		this.followPanel = new FollowPanel(uiFactory, modePanel, FollowSettings.getInstance(this.context), this.followAdapter);
		modeLayout.set(this.followPanel.getControl(), 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.followPanel.setContentListener(this::refit);
		this.followPanel.setModeListener(this::modeChanged);
		// what recognition measured: said as measured, with no more precision (spec 004 T009, SC-003, FR-008)
		this.delayLabel = label(uiFactory, modePanel, modeLayout, 2);
		this.delayLabel.setText(delayInfo());

		// ---- tab 4: the repeated passages of the wait mode
		UITableLayout passageLayout = new UITableLayout();
		UIPanel passagePanel = tab(uiFactory, folder, "practice.tab.passages", passageLayout);
		createPassageSettings(uiFactory, passagePanel, passageLayout);

		// a tab that was hidden is measured only once it is shown (its controls count as invisible before): the window is refitted when the learner changes tab
		folder.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				refit();
			}
		});

		listenKeys(panel, this.tracks, this.passageMeasures, this.passageThreshold);
		return folder;
	}

	/** One tab of the preparation: its text and a panel with its own layout. */
	private UIPanel tab(UIFactory uiFactory, UITabFolder folder, String key, UITableLayout layout) {
		UITabItem item = folder.createTab();
		item.setText(TuxGuitar.getProperty(key));
		UIPanel panel = uiFactory.createPanel(item, false);
		panel.setLayout(layout);
		tabPanels.add(panel);
		return panel;
	}

	/**
	 * Passage retries (FR-091): Off or 1/2/4 measures, and the error threshold, remembered in
	 * {@link PassageSettings} for the application run only.
	 */
	private void createPassageSettings(UIFactory uiFactory, UIPanel panel, UITableLayout layout) {
		final PassageSettings settings = PassageSettings.getInstance(this.context);
		UILabel measuresLabel = uiFactory.createLabel(panel);
		measuresLabel.setText(TuxGuitar.getProperty("practice.passages"));
		layout.set(measuresLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.passageMeasures = uiFactory.createDropDownSelect(panel);
		layout.set(this.passageMeasures, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		for( int measures : PassageSettings.MEASURE_OPTIONS ) {
			this.passageMeasures.addItem(new UISelectItem<Integer>(passageMeasuresText(measures), measures));
		}
		this.passageMeasures.setSelectedValue(settings.getMeasures());

		UILabel thresholdLabel = uiFactory.createLabel(panel);
		thresholdLabel.setText(TuxGuitar.getProperty("practice.passages.threshold"));
		layout.set(thresholdLabel, 2, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.passageThreshold = uiFactory.createDropDownSelect(panel);
		layout.set(this.passageThreshold, 2, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		for( int percent : PassageSettings.THRESHOLD_PERCENT_OPTIONS ) {
			this.passageThreshold.addItem(new UISelectItem<Integer>(
					TuxGuitar.getProperty("practice.passages.threshold.value", new String[] { Integer.toString(percent) }), percent));
		}
		this.passageThreshold.setSelectedValue(settings.getThresholdPercent());
		this.passageThreshold.setEnabled(!settings.isOff());
		UILabel hint = label(uiFactory, panel, layout, 3);
		hint.setText(TuxGuitar.getProperty("practice.passages.hint"));

		this.passageMeasures.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				Integer measures = PracticeWindow.this.passageMeasures.getSelectedValue();
				if( measures != null ) {
					settings.setMeasures(measures);
					PracticeWindow.this.passageThreshold.setEnabled(!settings.isOff());
				}
			}
		});
		this.passageThreshold.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				Integer percent = PracticeWindow.this.passageThreshold.getSelectedValue();
				if( percent != null ) {
					settings.setThresholdPercent(percent);
				}
			}
		});
	}

	/** Shows the current run choice, which the Configuration window may have reset (T102). */
	private void refreshPassageSettings() {
		PassageSettings settings = PassageSettings.getInstance(this.context);
		this.passageMeasures.setSelectedValue(settings.getMeasures());
		this.passageThreshold.setSelectedValue(settings.getThresholdPercent());
		this.passageThreshold.setEnabled(!settings.isOff());
	}

	private static String passageMeasuresText(int measures) {
		if( measures == PassageSettings.OFF ) {
			return TuxGuitar.getProperty("practice.passages.off");
		}
		if( measures == 1 ) {
			return TuxGuitar.getProperty("practice.passages.measure");
		}
		return TuxGuitar.getProperty("practice.passages.measures", new String[] { Integer.toString(measures) });
	}

	private void showPreparation() {
		this.song = TuxGuitar.getInstance().getDocumentManager().getSong();
		TGTrack caretTrack = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret().getTrack();
		boolean arcade = FollowSettings.getInstance(this.context).isArcade();
		java.util.List<PracticeTrackCandidate> candidateList;
		PracticeTrackCandidate preselected;
		Map<TGTrack, String> exclusions = new java.util.LinkedHashMap<TGTrack, String>();
		if( arcade ) {
			// Arcade: any pitched track, whatever its number of strings; the cursor's track is shown, and nothing starts on it alone
			ArcadeTrackCandidates arcadeCandidates = ArcadeTrackCandidates.evaluate(this.song, caretTrack);
			candidateList = arcadeCandidates.getCandidates();
			preselected = arcadeCandidates.getPreselected();
			for( Map.Entry<TGTrack, ArcadeTrackCandidates.Exclusion> entry : arcadeCandidates.getExclusions().entrySet() ) {
				exclusions.put(entry.getKey(), entry.getValue().name());
			}
		} else {
			PracticeTrackCandidates candidates = PracticeTrackCandidates.evaluate(this.song, caretTrack);
			candidateList = candidates.getCandidates();
			preselected = candidates.getPreselected();
			for( Map.Entry<TGTrack, PracticeTrackCandidates.Exclusion> entry : candidates.getExclusions().entrySet() ) {
				exclusions.put(entry.getKey(), entry.getValue().name());
			}
		}
		this.tracks.removeItems();
		for( PracticeTrackCandidate candidate : candidateList ) {
			String text = candidate.getTrackNumber() + ". " + candidate.getTrackName();
			if( candidate.hasGuitarHint() ) {
				text += " (" + TuxGuitar.getProperty("practice.track.guitar-hint") + ")";
			}
			this.tracks.addItem(new UISelectItem<PracticeTrackCandidate>(text, candidate));
		}
		boolean cursorTrackCanBePractised = false;
		for( PracticeTrackCandidate candidate : candidateList ) {
			cursorTrackCanBePractised |= (caretTrack != null && candidate.getTrack() == caretTrack);
		}
		this.autoStart = QuickStart.requested(this.quickStartRequested, cursorTrackCanBePractised);
		if( preselected != null ) {
			this.tracks.setSelectedValue(preselected);
		} else if( !candidateList.isEmpty() ) {
			this.tracks.setSelectedValue(candidateList.get(0));
		}
		this.trackNotice.setText(candidateList.isEmpty() ? noCandidateText(exclusions) : "");
		updateTuning();
		inputChanged(this.engine.getSelection().snapshot());
		refreshPassageSettings();
		setVisible(this.preparation, true);
	}

	private void updateTuning() {
		PracticeTrackCandidate candidate = this.tracks.getSelectedValue();
		this.shared.setPracticeTrack(candidate != null ? candidate.getTrack() : null);
		if( candidate == null ) {
			this.tuningLabel.setText("");
			this.unusualLabel.setText("");
			this.startBase = "";
			this.startLabel.setText("");
			this.preparedSequence = null;
			updateStartButton();
			return;
		}
		TrackTuning tuning = tuning(candidate.getTrack());
		Caret caret = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret();
		this.preparedSequence = PracticeSequenceBuilder.build(this.song, candidate.getTrack(), tuning, caret.getMeasure(), caret.getPosition());
		this.startBase = startText(this.preparedSequence);
		this.startLabel.setText(this.startBase);
		String strings = noteNames(tuning.getOpenStringMidi());
		String text = TuxGuitar.getProperty("practice.tuning", new String[] { strings });
		switch( tuning.getRealization() ) {
			case CAPO_EQUIVALENT:
				text += " " + TuxGuitar.getProperty("practice.tuning.capo", new String[] { Integer.toString(tuning.getOffset()) });
				break;
			case TUNE_DOWN:
				text += " " + TuxGuitar.getProperty("practice.tuning.down", new String[] { Integer.toString(-tuning.getOffset()), noteNames(tuning.getPhysicalTargetMidi()) });
				break;
			default:
				break;
		}
		this.tuningLabel.setText(text);
		String unusual = tuning.isUnusualSetup() ? TuxGuitar.getProperty("practice.tuning.unusual") : "";
		if( FollowSettings.getInstance(this.context).isArcade() && lowestBelowG1(tuning.getOpenStringMidi()) ) {
			// the notes below G1 (49 Hz) are never recognised by the chain (research R11): said, not hidden
			unusual = (unusual.isEmpty() ? "" : unusual + "\n") + TuxGuitar.getProperty("arcade.bass.limit");
		}
		this.unusualLabel.setText(unusual);
		updateMismatch();
		updateStartButton();
		refit();
	}

	/** Input or assessment changed (UI thread). The session itself is informed by SessionEvents. */
	private void inputChanged(CurrentInputSelection.Snapshot snapshot) {
		this.assessment = snapshot.getAssessment();
		if( !isOpen() ) {
			return;
		}
		this.palette.setInput(this.assessment);
		this.levelPanel.setAssessment(this.assessment);
		if( !this.windowShown && this.session == null && this.assessment.getStatus() == InputAssessment.Status.UNUSABLE ) {
			showWindow();
		}
		this.followPanel.refresh();
		AudioInputDescriptor input = snapshot.getInput();
		boolean headphones = (input != null && input.getKind() != AudioInputDescriptor.Kind.INSTRUMENT);
		this.headphoneLabel.setText(headphones ? TuxGuitar.getProperty("practice.headphones") : "");
		updateStartButton();
		refit();
	}

	/** The measured delay and the closest notes that are followed, rounded so as not to claim more precision than measured. */
	static String delayInfo() {
		java.util.Locale locale = java.util.Locale.getDefault();
		String typical = String.format(locale, "%.1f", Math.round(PracticeParameters.MEASURED_DELAY_MEDIAN_MILLIS / 100.0) / 10.0);
		String most = String.format(locale, "%.1f", Math.ceil(PracticeParameters.MEASURED_DELAY_P90_MILLIS / 50.0) * 50.0 / 1000.0);
		String closest = String.format(locale, "%.1f", PracticeParameters.MEASURED_SPEED_LIMIT_SECONDS);
		return TuxGuitar.getProperty("practice.delay.info", new String[] { typical, most, closest });
	}

	private void updateStartButton() {
		boolean canStart = QuickStart.canStart(this.tracks.getSelectedValue() != null, this.assessment.getStatus());
		this.startButton.setEnabled(canStart);
		FollowSettings modes = FollowSettings.getInstance(this.context);
		this.startButton.setText(modes.isArcade() ? TuxGuitar.getProperty("practice.start.arcade", new String[] { TuxGuitar.getProperty("arcade.name") })
				: TuxGuitar.getProperty(modes.isFollow() ? "practice.start.follow" : "practice.start"));
		// a one-click start is waiting for the guitar: say so instead of leaving a greyed button unexplained
		boolean waiting = (this.autoStart && !canStart && this.tracks.getSelectedValue() != null);
		this.startLabel.setText(waiting ? this.startBase + "\n" + TuxGuitar.getProperty("practice.quickstart.waiting") : this.startBase);
		if( QuickStart.startNow(this.autoStart, canStart) ) {
			// after the window is shown: the start hides it
			TGSynchronizer.getInstance(this.context).executeLater(() -> {
				if( isOpen() && this.autoStart && this.session == null
						&& QuickStart.canStart(this.tracks.getSelectedValue() != null, this.assessment.getStatus()) ) {
					this.autoStart = false;
					startSession();
				}
			});
		}
	}

	// ---------------------------------------------------------------- running practice (FR-073–FR-076)

	private void startSession() {
		PracticeTrackCandidate candidate = this.tracks.getSelectedValue();
		if( candidate == null ) {
			return;
		}
		if( FollowSettings.getInstance(this.context).isArcade() ) {
			this.arcadeWhole = this.preparedSequence;
			startArcade(candidate, this.preparedSequence);
			return;
		}
		if( FollowSettings.getInstance(this.context).isFollow() ) {
			startFollow(candidate);
			return;
		}
		PracticeTrackSelection selection = new PracticeTrackSelection(this.song, candidate, this.shared);
		selection.accept();
		PracticeSequence sequence = this.preparedSequence;
		// the validated audio-recognition settings (T102), fixed for this session
		RecognitionSettings recognition = RecognitionSettingsStore.getInstance(this.context).get();
		PracticeParameters parameters = PracticeParameters.of(recognition);
		this.palette.setDisplayTolerance(parameters.tolerance());
		this.session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(),
				parameters.stabilityNanos(), parameters.articulationLatencyNanos(),
				new ChordMatcher(parameters.chordParameters()), PassageSettings.getInstance(this.context).toPolicy());
		this.session.setAssessment(this.assessment);
		// the written techniques are judged beside the pitch, never instead of it (spec 002); off: every technique is "not verifiable"
		this.session.setTechniqueJudge(new TechniqueJudge(TechniqueCatalog.defaults(), recognition.get(RecognitionParameters.NOTE_TECHNIQUE)));
		// notes and chords are also judged from what each attack adds to the sound (spec 004): works over strings that keep ringing
		this.session.setPolyphonicAcceptance(new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		this.engine.setAttackCandidates(AttackCandidates.of(sequence));
		if( PracticeParameters.EARLY_STABILITY_ENABLED ) {
			this.session.setEarlyStability(PracticeParameters.EARLY_STABILITY_MARGIN, PracticeParameters.EARLY_STABILITY_NANOS);
			this.session.setExpectedPitchSink(this.engine::setExpectedPitches);
		}
		this.loop = new SessionLoop<PracticeSnapshot>(this.context, this.session, this::render);
		this.events = new SessionEvents(this.context, this.loop, this.session, this.engine.getSelection(), this.shared);
		final SessionLoop<PracticeSnapshot> sessionLoop = this.loop;
		this.frameListener = frame -> sessionLoop.postFrame(frame);

		this.loop.start();
		this.events.connect();
		// chords are judged on the polyphonic spectrum, computed only while it is needed
		this.engine.setSpectrumEnabled(hasChords(sequence));
		this.engine.subscribe(this.frameListener);
		this.plugin.setCommandSink(sessionLoop::postCommand);
		this.loop.postCommand(PracticeCommand.START);

		// the score is the practice interface: only the compact palette stays on screen (FR-076)
		this.palette.open();
		setVisible(this.window, false);
	}

	private static boolean hasChords(PracticeSequence sequence) {
		for( PracticeEvent event : sequence.getEvents() ) {
			if( event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
				return true;
			}
		}
		return false;
	}

	/** Ends practice immediately on the UI thread (Stop button): nothing queued can delay or undo it. */
	private void stopNow() {
		if( !isOpen() || (this.session == null && this.followPlan == null) ) {
			return;
		}
		teardownSession();
		showPreparation();
		showWindow();
		refit();
	}

	private void post(PracticeCommand command) {
		if( this.followPlan != null ) {
			this.followPlan.run().postCommand(command);
		} else if( this.loop != null ) {
			this.loop.postCommand(command);
		}
	}

	/** Renders a session snapshot (UI thread). */
	private void render(PracticeSnapshot snapshot) {
		if( !isOpen() || this.session == null ) {
			return;
		}
		PracticeSession.State state = snapshot.getState();
		if( state == PracticeSession.State.STOPPED ) {
			teardownSession();
			showPreparation();
			showWindow();
			refit();
			return;
		}
		this.palette.render(snapshot, this.assessment);
		// the current event framed red (none once complete), the just-accepted one flashed green
		this.highlight.show(state == PracticeSession.State.COMPLETE ? null : snapshot.getCurrentEvent(), snapshot.getLastAccepted(),
				snapshot.getAcceptedCount());
	}

	// ---------------------------------------------------------------- Arcade (spec 003, Increment D)

	/** The learner chose another mode: the tracks that can be played are not the same, so the list is made again. */
	private void modeChanged() {
		if( !isOpen() ) {
			return;
		}
		showPreparation();
		refit();
	}

	/**
	 * Starts an Arcade run of the chosen track from the given sequence: the same run as the tempo-following mode (the chosen track is the only one silent in
	 * the player, the others are the backing), seen on the isometric view in its own window. The preparation window is hidden while it runs.
	 */
	private void startArcade(PracticeTrackCandidate candidate, PracticeSequence full) {
		RecognitionSettings recognition = RecognitionSettingsStore.getInstance(this.context).get();
		PracticeParameters parameters = PracticeParameters.of(recognition);
		this.arcadeCandidate = candidate;
		this.arcadeFull = full;
		final TrackTuning arcadeTuning = TrackTuning.ofAnyStrings(candidate.getTrack());
		FollowStarter.Plan plan;
		try {
			plan = ArcadeStarter.prepareFrom(this.context, this.song, candidate.getTrack(), full, FollowSettings.getInstance(this.context), parameters,
					new TechniqueJudge(TechniqueCatalog.defaults(), recognition.get(RecognitionParameters.NOTE_TECHNIQUE)),
					() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()), this.followAdapter, this::renderArcade,
					runnable -> TGSynchronizer.getInstance(this.context).executeLater(runnable), this::arcadeRunEnded,
					(loop, handler) -> new FollowEvents(this.context, loop, handler, this.engine.getSelection(), this.shared),
					() -> TGSynchronizer.getInstance(this.context).executeLater(this::restartArcade));
		} catch( IllegalArgumentException exception ) {
			this.followPanel.message(TuxGuitar.getProperty("arcade.start.failed"));
			refit();
			return;
		}
		this.followPlan = plan;
		this.followLast = null;
		// the floating measure (FR-076): the picture holds what comes in the length of one measure of the track
		long measureNanos = ArcadeSession.measureNanos(plan.timeline(), plan.timeline().targetTicks(0),
				this.song.getMeasureHeader(plan.sequence().getEvents().get(0).getMeasureIndex()).getLength());
		this.arcadeSession = ArcadeSession.of(plan, arcadeTuning, new ArcadeSceneLabels(ARCADE_TEXTS), false,
				FollowSettings.getInstance(this.context).getMarkerPercent() / 100.0, measureNanos);
		this.engine.setAttackCandidates(AttackCandidates.of(plan.sequence()));
		this.engine.setSpectrumEnabled(hasChords(plan.sequence()));
		final FollowRun run = plan.run();
		this.followFrameListener = run::postFrame;
		this.engine.subscribe(this.followFrameListener);
		this.plugin.setCommandSink(run::postCommand);
		final ArcadeSession session = this.arcadeSession;
		this.arcadeController = new ArcadeController(ArcadeRunPort.of(run), session, new ArcadeController.Listener() {
			public void stateChanged(ArcadeController.State state) {
				if( PracticeWindow.this.arcadeWindow != null ) {
					PracticeWindow.this.arcadeWindow.setPaused(state == ArcadeController.State.PAUSED);
				}
			}

			public void ended(FollowRun.Ended reason, ArcadeResult result) {
				PracticeWindow.this.arcadeEnded(reason, result);
			}

			public void startFailed(FollowPlayback.Result result) {
				PracticeWindow.this.arcadeStartFailed(result);
			}
		});
		final ArcadeController controller = this.arcadeController;
		this.arcadeWindow = new ArcadeWindow(TGApplication.getInstance(this.context).getFactory(), TGWindow.getInstance(this.context).getWindow(),
				session.frameSource(this.followAdapter.player().clock(), ArcadeWindow.newQuality(), () -> plan.percent()),
				task -> TGSynchronizer.getInstance(this.context).executeLater(task), ARCADE_TEXTS, new ArcadeWindow.Actions() {
					public void pause() {
						controller.pause();
					}

					public void resume() {
						controller.resume();
					}

					public void stop() {
						controller.stop();
					}

					public void restart() {
						TGSynchronizer.getInstance(PracticeWindow.this.context).executeLater(PracticeWindow.this::restartArcadeFromStart);
					}
				});
		controller.start();
		if( controller.state() == ArcadeController.State.RUNNING ) {
			this.arcadeWindow.open();
			setVisible(this.window, false);
		}
	}

	/** A snapshot of the run (UI thread). */
	private void renderArcade(FollowSession.Snapshot snapshot) {
		ArcadeSession session = this.arcadeSession;
		if( session != null ) {
			session.accept(snapshot);
		}
	}

	/** The run ended, for any cause (UI thread): the controller says it once. */
	private void arcadeRunEnded(FollowRun.Ended reason) {
		ArcadeController controller = this.arcadeController;
		if( controller != null ) {
			controller.ended(reason);
		}
	}

	private void arcadeStartFailed(FollowPlayback.Result result) {
		teardownSession();
		showPreparation();
		showWindow();
		this.followPanel.message(TuxGuitar.getProperty(result == FollowPlayback.Result.ALREADY_PLAYING ? "arcade.start.player-busy" : "arcade.start.failed"));
		refit();
	}

	/**
	 * The end of an Arcade run (UI thread). The window of the run is closed and everything is given back; the result is shown once when the whole passage was
	 * judged and the song came to its end, otherwise the preparation window comes back, with the reason for a play of the learner, a stopped player or an error.
	 */
	private void arcadeEnded(FollowRun.Ended reason, ArcadeResult result) {
		final PracticeSequence full = this.arcadeFull;
		final PracticeTrackCandidate candidate = this.arcadeCandidate;
		teardownSession();
		if( !isOpen() ) {
			return;
		}
		if( result != null ) {
			showPreparation();
			int first = 1;
			int last = 1;
			if( full != null && !full.isEmpty() ) {
				first = full.getEvents().get(full.getStartIndex()).getMeasureIndex() + 1;
				last = full.getEvents().get(full.getEvents().size() - 1).getMeasureIndex() + 1;
			}
			this.arcadeResult = new ArcadeResultWindow(TGApplication.getInstance(this.context).getFactory(), TGWindow.getInstance(this.context).getWindow(),
					ARCADE_TEXTS, result, first, last, measure -> replayArcade(candidate, full, measure));
			this.arcadeResult.open();
			return;
		}
		showPreparation();
		showWindow();
		if( reason == FollowRun.Ended.MANUAL_PLAY || reason == FollowRun.Ended.FAILED || reason == FollowRun.Ended.PLAYER_STOPPED ) {
			this.followPanel.message(TuxGuitar.getProperty(reason == FollowRun.Ended.MANUAL_PLAY ? "arcade.end.manual-play"
					: reason == FollowRun.Ended.FAILED ? "arcade.end.failed" : "arcade.end.player-stopped"));
		}
		refit();
	}

	/** The replay of a section: the same settings, from the first event of the chosen measure (US5.3). */
	private void replayArcade(PracticeTrackCandidate candidate, PracticeSequence full, int measureNumber) {
		if( !isOpen() || candidate == null || full == null ) {
			return;
		}
		int index = full.getStartIndex();
		for( int i = 0; i < full.getEvents().size(); i++ ) {
			if( full.getEvents().get(i).getMeasureIndex() >= measureNumber - 1 ) {
				index = i;
				break;
			}
		}
		startArcade(candidate, full.startingAt(index));
	}

	/** Restart of the learner: this run ends and a new one begins from the same place, once. */
	private void restartArcade() {
		PracticeTrackCandidate candidate = this.arcadeCandidate;
		PracticeSequence full = this.arcadeFull;
		if( !isOpen() || this.followPlan == null || candidate == null || full == null ) {
			return;
		}
		teardownSession();
		startArcade(candidate, full);
	}

	/** The button Restart of the game window: the same track and settings, from the beginning of the piece. */
	private void restartArcadeFromStart() {
		PracticeTrackCandidate candidate = this.arcadeCandidate;
		PracticeSequence whole = this.arcadeWhole != null ? this.arcadeWhole : this.arcadeFull;
		if( !isOpen() || candidate == null || whole == null ) {
			return;
		}
		teardownSession();
		startArcade(candidate, whole);
	}

	/** Gives the window and the controller of the run back; the run itself is stopped by {@link #teardownFollow}. */
	private void teardownArcade() {
		ArcadeWindow window = this.arcadeWindow;
		this.arcadeWindow = null;
		this.arcadeController = null;
		this.arcadeSession = null;
		if( window != null ) {
			window.close();
		}
		if( this.arcadeResult != null ) {
			this.arcadeResult.close();
			this.arcadeResult = null;
		}
	}

	// ---------------------------------------------------------------- the tempo-following mode (spec 002 T096-T099)

	/**
	 * Starts a run of the tempo-following mode: the score plays on the player (the practised track silent in the player only), after a count-in,
	 * and each note is judged on its time. The wait-mode session is not touched: the two are never running together.
	 */
	private void startFollow(PracticeTrackCandidate candidate) {
		PracticeTrackSelection selection = new PracticeTrackSelection(this.song, candidate, this.shared);
		selection.accept();
		RecognitionSettings recognition = RecognitionSettingsStore.getInstance(this.context).get();
		PracticeParameters parameters = PracticeParameters.of(recognition);
		this.palette.setDisplayTolerance(parameters.tolerance());
		FollowStarter.Plan plan = FollowStarter.prepare(this.context, this.song, this.preparedSequence, candidate.getTrack().getNumber(),
				FollowSettings.getInstance(this.context), parameters,
				new TechniqueJudge(TechniqueCatalog.defaults(), recognition.get(RecognitionParameters.NOTE_TECHNIQUE)),
				() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()), this.followAdapter, this::renderFollow,
				runnable -> TGSynchronizer.getInstance(this.context).executeLater(runnable), this::followEnded,
				(loop, handler) -> new FollowEvents(this.context, loop, handler, this.engine.getSelection(), this.shared),
				() -> TGSynchronizer.getInstance(this.context).executeLater(this::restartFollow));
		this.followPlan = plan;
		this.followLast = null;
		this.followShownEvent = -1;
		this.followShownExtras = 0;
		this.engine.setAttackCandidates(AttackCandidates.of(plan.sequence()));
		this.engine.setSpectrumEnabled(hasChords(plan.sequence()));
		final FollowRun run = plan.run();
		this.followFrameListener = run::postFrame;
		this.engine.subscribe(this.followFrameListener);
		this.plugin.setCommandSink(run::postCommand);
		this.palette.setFollowMode(true);
		FollowPlayback.Result result = run.start();
		if( result != FollowPlayback.Result.STARTED ) {
			teardownFollow();
			this.followPanel.message(TuxGuitar.getProperty(result == FollowPlayback.Result.ALREADY_PLAYING ? "practice.follow.player-busy" : "practice.follow.start-failed"));
			refit();
			return;
		}
		// the score is the practice interface: only the compact palette stays on screen (FR-076)
		this.palette.open();
		setVisible(this.window, false);
	}

	/** A snapshot of the run (UI thread). */
	private void renderFollow(FollowSession.Snapshot snapshot) {
		FollowStarter.Plan plan = this.followPlan;
		if( !isOpen() || plan == null ) {
			return;
		}
		this.followLast = snapshot;
		FollowView view = FollowView.of(snapshot, plan.sequence().getEvents(), this.followShownEvent, this.followShownExtras, PracticeWindow::followText);
		this.followShownEvent = view.latestEvent();
		this.followShownExtras = view.latestExtras();
		this.palette.renderFollow(view, snapshot.state(), this.assessment);
		int open = snapshot.openEvent();
		boolean running = (snapshot.state() == FollowSession.State.FOLLOWING || snapshot.state() == FollowSession.State.COUNT_IN);
		PracticeEvent current = (running && open >= 0 ? plan.sequence().getEvents().get(open) : null);
		PracticeEvent lastRight = null;
		int right = 0;
		for( FollowSession.EventResult result : snapshot.results() ) {
			if( result.status() == FollowSession.Status.MATCHED ) {
				lastRight = plan.sequence().getEvents().get(result.eventIndex());
				right++;
			}
		}
		this.highlight.show(current, lastRight, right);
	}

	private static String followText(String key, String... arguments) {
		return arguments.length == 0 ? TuxGuitar.getProperty(key) : TuxGuitar.getProperty(key, arguments);
	}

	/**
	 * The run ended without the window asking (UI thread). The end of the song after a complete run keeps the palette and its summary; a play of the
	 * learner, a stopped player or a failure bring the preparation window back with the reason. Follow is never restarted by itself.
	 */
	private void followEnded(FollowRun.Ended reason) {
		if( !isOpen() || this.followPlan == null || reason == FollowRun.Ended.STOPPED ) {
			return;
		}
		boolean complete = (this.followLast != null && this.followLast.state() == FollowSession.State.COMPLETE);
		if( reason == FollowRun.Ended.PLAYER_STOPPED && complete ) {
			releaseFollowHooks();
			return;
		}
		teardownSession();
		showPreparation();
		showWindow();
		if( reason != FollowRun.Ended.COMMAND ) {
			String key = (reason == FollowRun.Ended.MANUAL_PLAY ? "practice.follow.ended.manual-play"
					: reason == FollowRun.Ended.FAILED ? "practice.follow.ended.failed" : "practice.follow.ended.player-stopped");
			this.followPanel.message(TuxGuitar.getProperty(key));
		}
		refit();
	}

	/** Restart of the learner: this run ends and a new one begins, once. */
	private void restartFollow() {
		if( !isOpen() || this.followPlan == null ) {
			return;
		}
		PracticeTrackCandidate candidate = this.tracks.getSelectedValue();
		teardownFollow();
		if( candidate != null ) {
			startFollow(candidate);
		}
	}

	/** Stops listening to the engine for the run; the run itself is untouched. */
	private void releaseFollowHooks() {
		if( this.followFrameListener != null ) {
			this.engine.unsubscribe(this.followFrameListener);
			this.followFrameListener = null;
			this.engine.setSpectrumEnabled(false);
			this.engine.setAttackCandidates(null);
		}
		this.plugin.setCommandSink(null);
	}

	/** Ends the run if there is one, gives everything back, and forgets it: calling it again does nothing. */
	private void teardownFollow() {
		teardownArcade();
		FollowStarter.Plan plan = this.followPlan;
		if( plan == null ) {
			return;
		}
		this.followPlan = null;
		this.followLast = null;
		plan.run().stop();
		releaseFollowHooks();
		this.palette.setFollowMode(false);
	}

	private void teardownSession() {
		teardownFollow();
		this.highlight.hide();
		if( this.loop != null ) {
			this.plugin.setCommandSink(null);
			this.engine.unsubscribe(this.frameListener);
			this.engine.setSpectrumEnabled(false);
			this.engine.setExpectedPitches(null);
			this.engine.setAttackCandidates(null);
			this.events.disconnect();
			this.loop.stop();
			this.loop = null;
			this.session = null;
			this.events = null;
			this.frameListener = null;
		}
		this.palette.close();
	}

	/** Non-blocking warning when the tuner guides another tuning than the Practice Track (FR-062). */
	private void updateMismatch() {
		if( !isOpen() ) {
			return;
		}
		PracticeTrackCandidate candidate = this.tracks.getSelectedValue();
		int[] chosen = this.shared.getTunerChosenTargets();
		boolean differs = (candidate != null && chosen != null && !Arrays.equals(chosen, tuning(candidate.getTrack()).getPhysicalTargetMidi()));
		this.mismatchLabel.setText(differs ? TuxGuitar.getProperty("practice.tuner-mismatch", new String[] { noteNames(chosen) }) : "");
		refit();
	}

	// ---------------------------------------------------------------- helpers

	/** The resolved starting position shown in the preparation step (FR-004). */
	private static String startText(PracticeSequence sequence) {
		if( sequence.isEmpty() ) {
			return "";
		}
		PracticeEvent first = sequence.getEvents().get(sequence.getStartIndex());
		String measure = Integer.toString(first.getMeasureIndex() + 1);
		if( sequence.getStartIndex() == 0 ) {
			return TuxGuitar.getProperty("practice.start-position.beginning", new String[] { measure });
		}
		return TuxGuitar.getProperty("practice.start-position.cursor", new String[] { measure });
	}

	/** True when a string of the track is tuned below G1 (MIDI 31): its open note and the first frets cannot be judged. */
	private static boolean lowestBelowG1(int[] openStringMidi) {
		for( int midi : openStringMidi ) {
			if( midi < 31 ) {
				return true;
			}
		}
		return false;
	}

	private TrackTuning tuning(TGTrack track) {
		if( FollowSettings.getInstance(this.context).isArcade() ) {
			return TrackTuning.ofAnyStrings(track);
		}
		return TrackTuning.of(track, TrackTuning.builtInSixStringMinimum(TuningManager.getInstance(this.context).getTgTuningsGroup()));
	}

	private static String noCandidateText(Map<TGTrack, String> exclusions) {
		StringBuilder text = new StringBuilder(TuxGuitar.getProperty("practice.track.none"));
		for( Map.Entry<TGTrack, String> entry : exclusions.entrySet() ) {
			text.append("\n").append(entry.getKey().getNumber()).append(". ").append(entry.getKey().getName()).append(": ")
					.append(TuxGuitar.getProperty("practice.exclusion." + entry.getValue()));
		}
		return text.toString();
	}

	private static String noteNames(int[] midi) {
		StringBuilder text = new StringBuilder();
		for( int i = midi.length - 1 ; i >= 0 ; i -- ) {
			text.append(noteName(midi[i])).append(i > 0 ? " " : "");
		}
		return text.toString();
	}

	private static String noteName(int midi) {
		return NOTE_NAMES[midi % 12] + (midi / 12 - 1);
	}

	private static UILabel label(UIFactory uiFactory, UIPanel panel, UITableLayout layout, int row) {
		UILabel label = uiFactory.createWrapLabel(panel);
		layout.set(label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(label, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		return label;
	}

	/** Applies the packed/minimum size after a content or state change (all controls stay visible). */
	private void refit() {
		if( isOpen() ) {
			// the folder asks for the height of its tallest tab, so that no tab needs the window to be resized by hand
			// and the width of its widest one: the window is never too narrow for a tab that is not the one shown
			float tallest = 0f;
			float widest = 0f;
			for( UIPanel tabPanel : this.tabPanels ) {
				if( !tabPanel.isDisposed() ) {
					tabPanel.computePackedSize(null, null);
					tallest = Math.max(tallest, tabPanel.getPackedSize().getHeight());
					widest = Math.max(widest, tabPanel.getPackedSize().getWidth());
				}
			}
			((UITableLayout) this.window.getLayout()).set(this.preparation, UITableLayout.PACKED_HEIGHT, tallest + 48f);
			((UITableLayout) this.window.getLayout()).set(this.preparation, UITableLayout.PACKED_WIDTH, widest + 24f);
			WindowFit.refit(this.window, TGWindow.getInstance(this.context).getWindow().getBounds());
		}
	}

	private static UIButton button(UIFactory uiFactory, UIPanel panel, UITableLayout layout, int column, String key, Runnable action) {
		UIButton button = uiFactory.createButton(panel);
		button.setText(TuxGuitar.getProperty(key));
		button.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		});
		layout.set(button, 1, column, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 80f, 25f, null);
		return button;
	}

	private void listenKeys(UIControl... controls) {
		KeyBindingActionManager keyBindings = KeyBindingActionManager.getInstance(this.context);
		for( UIControl control : controls ) {
			keyBindings.appendListenersTo(control);
		}
	}

	private static void setVisible(UIControl control, boolean visible) {
		control.setVisible(visible);
	}
}
