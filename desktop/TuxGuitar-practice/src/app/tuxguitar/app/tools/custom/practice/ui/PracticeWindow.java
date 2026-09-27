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
import app.tuxguitar.app.tools.custom.practice.session.Feedback;
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
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
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
 * Non-modal Interactive Practice window: the preparation step (FR-002, FR-003, FR-008, FR-038)
 * and the running view (FR-028, FR-029, FR-032, FR-033). Practice shortcuts work while it has
 * focus. Session state lives on the session thread; this window only renders snapshots.
 */
public class PracticeWindow {

	/**
	 * Provisional, configurable defaults approved 2026-09-26; acceptance widened from 30 to 35
	 * cents by T076 (fretted notes on the real-guitar clips read up to +30.5 cents at p90).
	 */
	public static final double CONFIDENCE_GATE = 0.80;
	public static final int STABILITY_HOPS = 20;
	public static final double TUNER_IN_TUNE_CENTS = 5.0;
	public static final double PRACTICE_ACCEPT_CENTS = 35.0;

	private static final String CONSUMER_ID = "practice";
	private static final String[] NOTE_NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

	private final TGContext context;
	private final PracticePlugin plugin;
	private final PracticeTunerContext shared;
	private final PracticeHighlight highlight;
	private final TolerancePolicy displayTolerance = new TolerancePolicy(TUNER_IN_TUNE_CENTS, PRACTICE_ACCEPT_CENTS);
	private AudioEngine engine;

	private UIWindow window;
	private UIPanel preparation;
	private UIPanel running;
	private InputSelectorPanel inputSelector;
	private InputSelectorPanel runningInputSelector;
	private LevelPanel levelPanel;
	private UILabel headphoneLabel;
	private UIDropDownSelect<PracticeTrackCandidate> tracks;
	private UILabel trackNotice;
	private UILabel tuningLabel;
	private UILabel unusualLabel;
	private UILabel startLabel;
	private UILabel mismatchLabel;
	private PracticeTunerContext.Listener sharedListener;
	private PracticeSequence preparedSequence;
	private UIButton startButton;
	private UILabel expectedLabel;
	private UILabel instructionLabel;
	private UILabel detectedLabel;
	private UILabel feedbackLabel;
	private UILabel acceptedLabel;
	private UILabel stateLabel;
	private UILabel runningInputStatus;
	private UIButton skipButton;
	private UIButton stopButton;
	private UIButton resumeButton;
	private UIButton restartButton;

	private TGSong song;
	private InputAssessment assessment = InputAssessment.UNCHECKED;
	private CurrentInputSelection.Listener selectionListener;
	private SessionLoop<PracticeSnapshot> loop;
	private PracticeSession session;
	private SessionEvents events;
	private AnalysisService.Listener frameListener;

	public PracticeWindow(TGContext context, PracticePlugin plugin) {
		this.context = context;
		this.plugin = plugin;
		this.shared = PracticeTunerContext.getInstance(context);
		this.highlight = new PracticeHighlight(context);
	}

	/** Opens the window, or brings it to the front when it is already open. */
	public void open() {
		if( this.isOpen() ) {
			this.window.moveToTop();
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
		this.running = createRunning(uiFactory);
		windowLayout.set(this.running, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 480f, null, null);

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
				PracticeWindow.this.runningInputSelector.dispose();
				PracticeWindow.this.levelPanel.disconnect();
				PracticeWindow.this.engine.getSelection().removeListener(PracticeWindow.this.selectionListener);
				PracticeWindow.this.engine.release(CONSUMER_ID);
				PracticeWindow.this.shared.removeListener(PracticeWindow.this.sharedListener);
				PracticeWindow.this.shared.setPracticeTrack(null);
				PracticeWindow.this.window = null;
			}
		});

		this.selectionListener = snapshot -> TGSynchronizer.getInstance(this.context).executeLater(() -> inputChanged(snapshot));
		this.engine.getSelection().addListener(this.selectionListener);
		this.levelPanel.connect();
		this.sharedListener = changed -> TGSynchronizer.getInstance(this.context).executeLater(() -> updateMismatch());
		this.shared.addListener(this.sharedListener);
		this.engine.acquire(CONSUMER_ID);
		this.assessment = this.engine.getSelection().snapshot().getAssessment();

		showPreparation();
		TGDialogUtil.openDialog(this.window, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
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
		this.levelPanel = new LevelPanel(this.context, this.engine, uiFactory, panel);
		layout.set(this.levelPanel.getControl(), 6, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.headphoneLabel = label(uiFactory, panel, layout, 8);

		UITableLayout startLayout = new UITableLayout(0f);
		UIPanel startPanel = uiFactory.createPanel(panel, false);
		startPanel.setLayout(startLayout);
		layout.set(startPanel, 10, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		button(uiFactory, startPanel, startLayout, 1, "practice.tune-guitar", () -> openTuner());
		this.startButton = button(uiFactory, startPanel, startLayout, 2, "practice.start", () -> startSession());
		listenKeys(panel, this.tracks, startPanel, this.startButton);
		return panel;
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
		if( candidates.getPreselected() != null ) {
			this.tracks.setSelectedValue(candidates.getPreselected());
		} else if( !candidates.getCandidates().isEmpty() ) {
			this.tracks.setSelectedValue(candidates.getCandidates().get(0));
		}
		this.trackNotice.setText(candidates.getCandidates().isEmpty() ? noCandidateText(candidates.getExclusions()) : "");
		updateTuning();
		inputChanged(this.engine.getSelection().snapshot());
		setVisible(this.preparation, true);
		setVisible(this.running, false);
	}

	private void updateTuning() {
		PracticeTrackCandidate candidate = this.tracks.getSelectedValue();
		this.shared.setPracticeTrack(candidate != null ? candidate.getTrack() : null);
		if( candidate == null ) {
			this.tuningLabel.setText("");
			this.unusualLabel.setText("");
			this.startLabel.setText("");
			this.preparedSequence = null;
			updateStartButton();
			return;
		}
		TrackTuning tuning = tuning(candidate.getTrack());
		Caret caret = TuxGuitar.getInstance().getTablatureEditor().getTablature().getCaret();
		this.preparedSequence = PracticeSequenceBuilder.build(this.song, candidate.getTrack(), tuning, caret.getMeasure(), caret.getPosition());
		this.startLabel.setText(startText(this.preparedSequence));
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
		this.window.layout();
	}

	/** Input or assessment changed (UI thread). The session itself is informed by SessionEvents. */
	private void inputChanged(CurrentInputSelection.Snapshot snapshot) {
		this.assessment = snapshot.getAssessment();
		if( !isOpen() ) {
			return;
		}
		String reason = TuxGuitar.getProperty("practice.input.reason." + this.assessment.getReason().name());
		this.runningInputStatus.setText(TuxGuitar.getProperty("practice.input.status." + this.assessment.getStatus().name(), new String[] { reason }));
		this.levelPanel.setAssessment(this.assessment);
		AudioInputDescriptor input = snapshot.getInput();
		boolean headphones = (input != null && input.getKind() != AudioInputDescriptor.Kind.INSTRUMENT);
		this.headphoneLabel.setText(headphones ? TuxGuitar.getProperty("practice.headphones") : "");
		updateStartButton();
		this.window.layout();
	}

	private void updateStartButton() {
		InputAssessment.Status status = this.assessment.getStatus();
		this.startButton.setEnabled(this.tracks.getSelectedValue() != null
				&& (status == InputAssessment.Status.SUITABLE || status == InputAssessment.Status.SUBOPTIMAL));
	}

	// ---------------------------------------------------------------- running view (FR-028/029)

	private UIPanel createRunning(UIFactory uiFactory) {
		UIPanel panel = uiFactory.createPanel(this.window, false);
		UITableLayout layout = new UITableLayout();
		panel.setLayout(layout);
		this.expectedLabel = label(uiFactory, panel, layout, 1);
		this.instructionLabel = label(uiFactory, panel, layout, 2);
		this.detectedLabel = label(uiFactory, panel, layout, 3);
		this.feedbackLabel = label(uiFactory, panel, layout, 4);
		this.acceptedLabel = label(uiFactory, panel, layout, 5);
		this.stateLabel = label(uiFactory, panel, layout, 6);
		this.runningInputStatus = label(uiFactory, panel, layout, 7);
		this.runningInputSelector = new InputSelectorPanel(this.context, this.engine, uiFactory, panel);
		layout.set(this.runningInputSelector.getControl(), 9, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(panel, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 8, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		button(uiFactory, buttons, buttonsLayout, 5, "practice.tune-guitar", () -> openTuner());
		this.skipButton = button(uiFactory, buttons, buttonsLayout, 1, "practice.skip", () -> post(PracticeCommand.SKIP));
		this.resumeButton = button(uiFactory, buttons, buttonsLayout, 2, "practice.resume", () -> post(PracticeCommand.RESUME));
		this.restartButton = button(uiFactory, buttons, buttonsLayout, 3, "practice.restart", () -> post(PracticeCommand.RESTART));
		this.stopButton = button(uiFactory, buttons, buttonsLayout, 4, "practice.stop", () -> post(PracticeCommand.STOP));
		listenKeys(panel, buttons, this.skipButton, this.resumeButton, this.restartButton, this.stopButton);
		return panel;
	}

	private void startSession() {
		PracticeTrackCandidate candidate = this.tracks.getSelectedValue();
		if( candidate == null ) {
			return;
		}
		PracticeTrackSelection selection = new PracticeTrackSelection(this.song, candidate, this.shared);
		selection.accept();
		PracticeSequence sequence = this.preparedSequence;
		long hopNanos = TarsosFastYinEstimator.DEFAULT_HOP_SIZE * 1_000_000_000L / 48000L;
		this.session = new PracticeSession(selection, sequence, new NoteInterpreter(CONFIDENCE_GATE),
				new TolerancePolicy(TUNER_IN_TUNE_CENTS, PRACTICE_ACCEPT_CENTS),
				STABILITY_HOPS * hopNanos, ArticulationDetector.DECISION_DELAY_HOPS * hopNanos);
		this.session.setAssessment(this.assessment);
		this.loop = new SessionLoop<PracticeSnapshot>(this.context, this.session, this::render);
		this.events = new SessionEvents(this.context, this.loop, this.session, this.engine.getSelection(), this.shared);
		final SessionLoop<PracticeSnapshot> sessionLoop = this.loop;
		this.frameListener = frame -> sessionLoop.postFrame(frame);

		this.loop.start();
		this.events.connect();
		this.engine.subscribe(this.frameListener);
		this.plugin.setCommandSink(sessionLoop::postCommand);
		this.loop.postCommand(PracticeCommand.START);

		setVisible(this.preparation, false);
		setVisible(this.running, true);
		this.window.layout();
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
		PracticeEvent event = snapshot.getCurrentEvent();
		if( event != null && event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			this.expectedLabel.setText(TuxGuitar.getProperty("practice.expected", new String[] { noteName(event.getExpectedMidi()) }));
			this.instructionLabel.setText(TuxGuitar.getProperty("practice.instruction",
					new String[] { Integer.toString(event.getStringNumber()), Integer.toString(event.getFret()) }));
		} else if( event != null && event.getKind() == PracticeEvent.Kind.MULTI_NOTE ) {
			this.expectedLabel.setText(TuxGuitar.getProperty("practice.multi-note"));
			this.instructionLabel.setText("");
		} else {
			this.expectedLabel.setText("");
			this.instructionLabel.setText("");
		}
		this.detectedLabel.setText(detectedText(snapshot.getReading()));
		this.feedbackLabel.setText(feedbackText(snapshot.getFeedback(), event));
		this.acceptedLabel.setText(lastOutcomeText(snapshot));
		String stateText = TuxGuitar.getProperty("practice.state." + state.name());
		if( state == PracticeSession.State.PAUSED ) {
			StringBuilder reasons = new StringBuilder();
			for( PracticeSession.PauseReason reason : snapshot.getPauseReasons() ) {
				reasons.append(reasons.length() > 0 ? ", " : "").append(TuxGuitar.getProperty("practice.pause." + reason.name()));
			}
			stateText += " " + reasons;
		}
		this.stateLabel.setText(stateText);
		this.resumeButton.setEnabled(state == PracticeSession.State.READY_TO_RESUME);
		this.restartButton.setEnabled(state == PracticeSession.State.COMPLETE);
		this.skipButton.setEnabled(state == PracticeSession.State.RUNNING);

		if( state == PracticeSession.State.STOPPED ) {
			teardownSession();
			showPreparation();
		} else if( state == PracticeSession.State.COMPLETE || event == null ) {
			this.highlight.hide();
		} else {
			this.highlight.show(event.getBeat());
		}
		this.window.layout();
	}

	/**
	 * The latest result line: the accepted note, or a skipped / automatically passed event
	 * (FR-016, FR-030), whichever happened last. Never shows a skipped event as correct.
	 */
	private String lastOutcomeText(PracticeSnapshot snapshot) {
		PracticeEvent accepted = snapshot.getLastAccepted();
		PracticeEvent passed = snapshot.getLastPassed();
		if( passed != null && (accepted == null || passed.getSequenceIndex() > accepted.getSequenceIndex()) ) {
			if( snapshot.getLastPassedOutcome() == PracticeSession.Outcome.AUTO_PASSED ) {
				return TuxGuitar.getProperty("practice.auto-passed", new String[] {
						TuxGuitar.getProperty("practice.auto-pass." + passed.getAutoPassReason().name()) });
			}
			return TuxGuitar.getProperty("practice.skipped");
		}
		return (accepted != null ? TuxGuitar.getProperty("practice.feedback.correct", new String[] { noteName(accepted.getExpectedMidi()) }) : "");
	}

	/** Tuner-style reading: note, octave and cents from that note's own centre (FR-017). */
	private String detectedText(PitchReading reading) {
		if( !reading.hasPitch() || !reading.isReliable() ) {
			return TuxGuitar.getProperty("practice.detected.none");
		}
		String note = reading.getNoteName() + reading.getOctave();
		String cents = Long.toString(Math.round(Math.abs(reading.getCents())));
		switch( this.displayTolerance.tunerState(reading) ) {
			case FLAT:
				return TuxGuitar.getProperty("practice.detected.flat", new String[] { note, cents });
			case SHARP:
				return TuxGuitar.getProperty("practice.detected.sharp", new String[] { note, cents });
			default:
				return TuxGuitar.getProperty("practice.detected.in-tune", new String[] { note });
		}
	}

	/** One learner-worded feedback line with a text symbol, never colour alone (FR-018, FR-070). */
	private String feedbackText(Feedback feedback, PracticeEvent event) {
		if( feedback == null || event == null || event.getKind() != PracticeEvent.Kind.EXPECTED_NOTE ) {
			return "";
		}
		String expected = noteName(event.getExpectedMidi());
		switch( feedback.getKind() ) {
			case UNCERTAIN:
				return TuxGuitar.getProperty("practice.feedback.uncertain");
			case INCORRECT: {
				String detected = noteName(feedback.getDetectedMidi());
				if( feedback.isOctaveOnly() ) {
					String direction = TuxGuitar.getProperty(feedback.getDetectedMidi() > event.getExpectedMidi()
							? "practice.feedback.octave.higher" : "practice.feedback.octave.lower");
					return TuxGuitar.getProperty("practice.feedback.octave", new String[] { detected, expected, direction });
				}
				if( feedback.getDetectedMidi() == event.getExpectedMidi() ) {
					return TuxGuitar.getProperty("practice.feedback.out-of-tune", new String[] { expected });
				}
				return TuxGuitar.getProperty("practice.feedback.incorrect", new String[] { detected, expected });
			}
			case CORRECT:
				return "";
			case WAITING:
			default:
				return TuxGuitar.getProperty(feedback.needsArticulation() ? "practice.feedback.play-again" : "practice.feedback.waiting",
						new String[] { expected });
		}
	}

	private void teardownSession() {
		this.highlight.hide();
		if( this.loop != null ) {
			this.plugin.setCommandSink(null);
			this.engine.unsubscribe(this.frameListener);
			this.events.disconnect();
			this.loop.stop();
			this.loop = null;
			this.session = null;
			this.events = null;
			this.frameListener = null;
		}
	}

	/** Opens the tuner through its action id: no dependency on the tuner plugin (FR-047, FR-058). */
	private void openTuner() {
		new TGActionProcessor(this.context, "tuner.tuner").process();
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
		this.window.layout();
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
		UILabel label = uiFactory.createLabel(panel);
		layout.set(label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		return label;
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
