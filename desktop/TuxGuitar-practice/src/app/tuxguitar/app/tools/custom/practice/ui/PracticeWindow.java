package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Arrays;
import java.util.Map;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.system.keybindings.KeyBindingActionManager;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidate;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.AttackCandidates;
import app.tuxguitar.app.tools.custom.practice.session.ChordMatcher;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
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
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
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
	private UIPanel preparation;
	private PracticePalette palette;
	private InputSelectorPanel inputSelector;
	private LevelPanel levelPanel;
	private UILabel headphoneLabel;
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

	/** Opens the window, or brings it to the front when it is already open. */
	public void open() {
		if( this.isOpen() ) {
			if( this.palette.isOpen() ) {
				this.palette.open();
			} else {
				this.window.moveToTop();
			}
			return;
		}
		this.engine = AudioEngine.getInstance(this.context);
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UIWindow uiParent = TGWindow.getInstance(this.context).getWindow();
		UITableLayout windowLayout = new UITableLayout();

		this.window = uiFactory.createWindow(uiParent, false, true);
		this.window.setLayout(windowLayout);
		this.window.setImage(TuxGuitar.getInstance().getIconManager().getAppIcon());
		this.window.setText(TuxGuitar.getProperty(PracticePlugin.OPEN_ACTION_ID));

		this.preparation = createPreparation(uiFactory);
		windowLayout.set(this.preparation, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 480f, null, null);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		windowLayout.set(buttons, 3, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, false);
		UIButton closeButton = button(uiFactory, buttons, buttonsLayout, 1, "close", () -> close());
		buttonsLayout.set(closeButton, UITableLayout.MARGIN_RIGHT, 0f);
		listenKeys(buttons, closeButton);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				teardownSession();
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
			TGDialogUtil.openDialog(this.window, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
		} else {
			setVisible(this.window, true);
			this.window.moveToTop();
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

	private UIPanel createPreparation(UIFactory uiFactory) {
		UIPanel panel = uiFactory.createPanel(this.window, false);
		UITableLayout layout = new UITableLayout();
		panel.setLayout(layout);

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
		this.startLabel = label(uiFactory, panel, layout, 7);
		this.mismatchLabel = label(uiFactory, panel, layout, 9);

		this.inputSelector = new InputSelectorPanel(this.context, this.engine, uiFactory, panel);
		layout.set(this.inputSelector.getControl(), 5, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.inputSelector.setContentListener(this::refit);
		this.levelPanel = new LevelPanel(this.context, this.engine, uiFactory, panel);
		this.levelPanel.setContentListener(this::refit);
		layout.set(this.levelPanel.getControl(), 6, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.headphoneLabel = label(uiFactory, panel, layout, 8);

		UITableLayout startLayout = new UITableLayout(0f);
		UIPanel startPanel = uiFactory.createPanel(panel, false);
		startPanel.setLayout(startLayout);
		createPassageSettings(uiFactory, panel, layout);

		layout.set(startPanel, 13, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.startButton = button(uiFactory, startPanel, startLayout, 1, "practice.start", () -> startSession());
		listenKeys(panel, this.tracks, this.passageMeasures, this.passageThreshold, startPanel, this.startButton);
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
		layout.set(measuresLabel, 10, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.passageMeasures = uiFactory.createDropDownSelect(panel);
		layout.set(this.passageMeasures, 10, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		for( int measures : PassageSettings.MEASURE_OPTIONS ) {
			this.passageMeasures.addItem(new UISelectItem<Integer>(passageMeasuresText(measures), measures));
		}
		this.passageMeasures.setSelectedValue(settings.getMeasures());

		UILabel thresholdLabel = uiFactory.createLabel(panel);
		thresholdLabel.setText(TuxGuitar.getProperty("practice.passages.threshold"));
		layout.set(thresholdLabel, 11, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.passageThreshold = uiFactory.createDropDownSelect(panel);
		layout.set(this.passageThreshold, 11, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		for( int percent : PassageSettings.THRESHOLD_PERCENT_OPTIONS ) {
			this.passageThreshold.addItem(new UISelectItem<Integer>(
					TuxGuitar.getProperty("practice.passages.threshold.value", new String[] { Integer.toString(percent) }), percent));
		}
		this.passageThreshold.setSelectedValue(settings.getThresholdPercent());
		this.passageThreshold.setEnabled(!settings.isOff());
		UILabel hint = label(uiFactory, panel, layout, 12);
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
		PracticeTrackCandidates candidates = PracticeTrackCandidates.evaluate(this.song, caretTrack);
		this.tracks.removeItems();
		for( PracticeTrackCandidate candidate : candidates.getCandidates() ) {
			String text = candidate.getTrackNumber() + ". " + candidate.getTrackName();
			if( candidate.hasGuitarHint() ) {
				text += " (" + TuxGuitar.getProperty("practice.track.guitar-hint") + ")";
			}
			this.tracks.addItem(new UISelectItem<PracticeTrackCandidate>(text, candidate));
		}
		boolean cursorTrackCanBePractised = false;
		for( PracticeTrackCandidate candidate : candidates.getCandidates() ) {
			cursorTrackCanBePractised |= (caretTrack != null && candidate.getTrack() == caretTrack);
		}
		this.autoStart = QuickStart.requested(this.quickStartRequested, cursorTrackCanBePractised);
		if( candidates.getPreselected() != null ) {
			this.tracks.setSelectedValue(candidates.getPreselected());
		} else if( !candidates.getCandidates().isEmpty() ) {
			this.tracks.setSelectedValue(candidates.getCandidates().get(0));
		}
		this.trackNotice.setText(candidates.getCandidates().isEmpty() ? noCandidateText(candidates.getExclusions()) : "");
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
		this.unusualLabel.setText(tuning.isUnusualSetup() ? TuxGuitar.getProperty("practice.tuning.unusual") : "");
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
		AudioInputDescriptor input = snapshot.getInput();
		boolean headphones = (input != null && input.getKind() != AudioInputDescriptor.Kind.INSTRUMENT);
		this.headphoneLabel.setText(headphones ? TuxGuitar.getProperty("practice.headphones") : "");
		updateStartButton();
		refit();
	}

	private void updateStartButton() {
		boolean canStart = QuickStart.canStart(this.tracks.getSelectedValue() != null, this.assessment.getStatus());
		this.startButton.setEnabled(canStart);
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
		PracticeTrackSelection selection = new PracticeTrackSelection(this.song, candidate, this.shared);
		selection.accept();
		PracticeSequence sequence = this.preparedSequence;
		// the validated audio-recognition settings (T102), fixed for this session
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettingsStore.getInstance(this.context).get());
		this.palette.setDisplayTolerance(parameters.tolerance());
		this.session = new PracticeSession(selection, sequence, parameters.noteInterpreter(), parameters.tolerance(),
				parameters.stabilityNanos(), parameters.articulationLatencyNanos(),
				new ChordMatcher(parameters.chordParameters()), PassageSettings.getInstance(this.context).toPolicy());
		this.session.setAssessment(this.assessment);
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
		if( !isOpen() || this.session == null ) {
			return;
		}
		teardownSession();
		showPreparation();
		showWindow();
		refit();
	}

	private void post(PracticeCommand command) {
		if( this.loop != null ) {
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

	private void teardownSession() {
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

	private TrackTuning tuning(TGTrack track) {
		return TrackTuning.of(track, TrackTuning.builtInSixStringMinimum(TuningManager.getInstance(this.context).getTgTuningsGroup()));
	}

	private static String noCandidateText(Map<TGTrack, PracticeTrackCandidates.Exclusion> exclusions) {
		StringBuilder text = new StringBuilder(TuxGuitar.getProperty("practice.track.none"));
		for( Map.Entry<TGTrack, PracticeTrackCandidates.Exclusion> entry : exclusions.entrySet() ) {
			text.append("\n").append(entry.getKey().getNumber()).append(". ").append(entry.getKey().getName()).append(": ")
					.append(TuxGuitar.getProperty("practice.exclusion." + entry.getValue().name()));
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
