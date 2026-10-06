package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.system.keybindings.KeyBindingActionManager;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.session.ChordFeedback;
import app.tuxguitar.app.tools.custom.practice.session.Feedback;
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;
import app.tuxguitar.app.tools.custom.practice.session.PracticeCommand;
import app.tuxguitar.app.tools.custom.practice.session.PracticeParameters;
import app.tuxguitar.app.tools.custom.practice.session.PassageStatus;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSnapshot;
import app.tuxguitar.app.ui.TGApplication;
import app.tuxguitar.app.view.main.TGWindow;
import app.tuxguitar.app.view.util.TGDialogUtil;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;
import app.tuxguitar.util.TGContext;

/**
 * The compact, movable practice palette shown while practice runs (FR-076): the detected note
 * with cents and confidence, one status line, input health and the practice controls. The
 * expected event itself is framed in the score (FR-073). Practice shortcuts work while it has
 * focus. UI thread only.
 */
public class PracticePalette {

	public interface Controller {
		void post(PracticeCommand command);

		/** The Stop button: practice ends at once, without waiting behind queued analysis frames. */
		void stop();

		/** The learner closed the palette window: practice stops. */
		void closed();
	}

	private static final String[] NOTE_NAMES = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
	private static final float WIDTH = 360f;

	private final TGContext context;
	private final Controller controller;
	private TolerancePolicy displayTolerance;
	private UIWindow window;
	private UIRectangle lastBounds;
	private boolean closing;
	private UILabel verdictLabel;
	private UIFont verdictFont;
	private UIColor correctColor;
	private UIColor wrongColor;
	private UILabel detectedLabel;
	private UILabel statusLabel;
	private UILabel techniqueLabel;
	private UILabel outcomeLabel;
	private UILabel passageLabel;
	private UILabel inputLabel;
	private UIButton skipButton;
	private UIButton resumeButton;
	private UIButton restartButton;

	public PracticePalette(TGContext context, Controller controller, TolerancePolicy displayTolerance) {
		this.context = context;
		this.controller = controller;
		this.displayTolerance = displayTolerance;
	}

	public void open() {
		if( isOpen() ) {
			this.window.moveToTop();
			return;
		}
		UIFactory uiFactory = TGApplication.getInstance(this.context).getFactory();
		UIWindow parent = TGWindow.getInstance(this.context).getWindow();
		this.window = uiFactory.createWindow(parent, false, false);
		this.window.setText(TuxGuitar.getProperty(PracticePlugin.OPEN_ACTION_ID));
		this.window.setImage(TuxGuitar.getInstance().getIconManager().getAppIcon());
		UITableLayout layout = new UITableLayout();
		this.window.setLayout(layout);

		this.verdictLabel = uiFactory.createLabel(this.window);
		layout.set(this.verdictLabel, 1, 1, UITableLayout.ALIGN_CENTER, UITableLayout.ALIGN_CENTER, true, false);
		UIFont font = this.verdictLabel.getFont();
		this.verdictFont = uiFactory.createFont(font != null ? font.getName() : "Sans", 28f, true, false);
		this.verdictLabel.setFont(this.verdictFont);
		this.correctColor = uiFactory.createColor(0, 150, 0);
		this.wrongColor = uiFactory.createColor(210, 0, 0);
		this.detectedLabel = label(uiFactory, layout, 2);
		this.statusLabel = label(uiFactory, layout, 3);
		this.techniqueLabel = label(uiFactory, layout, 4);
		this.outcomeLabel = label(uiFactory, layout, 5);
		this.passageLabel = label(uiFactory, layout, 6);
		this.inputLabel = label(uiFactory, layout, 7);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = uiFactory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 8, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.skipButton = button(uiFactory, buttons, buttonsLayout, 1, "practice.skip", () -> this.controller.post(PracticeCommand.SKIP));
		this.resumeButton = button(uiFactory, buttons, buttonsLayout, 2, "practice.resume", () -> this.controller.post(PracticeCommand.RESUME));
		this.restartButton = button(uiFactory, buttons, buttonsLayout, 3, "practice.restart", () -> this.controller.post(PracticeCommand.RESTART));
		UIButton stopButton = button(uiFactory, buttons, buttonsLayout, 4, "practice.stop", () -> this.controller.stop());
		listenKeys(this.window, buttons, this.skipButton, this.resumeButton, this.restartButton, stopButton,
				this.detectedLabel, this.statusLabel, this.techniqueLabel, this.outcomeLabel, this.passageLabel, this.inputLabel);

		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				PracticePalette.this.verdictFont.dispose();
				PracticePalette.this.correctColor.dispose();
				PracticePalette.this.wrongColor.dispose();
				PracticePalette.this.window = null;
				if( !PracticePalette.this.closing ) {
					PracticePalette.this.controller.closed();
				}
			}
		});
		this.window.pack();
		placeWindow(parent);
		this.window.open();
		refit();
	}

	/** The tuning-state ranges shown for the detected note (the session's settings, T102). */
	public void setDisplayTolerance(TolerancePolicy displayTolerance) {
		this.displayTolerance = displayTolerance;
	}

	/** Closes the palette without stopping practice (the caller stops it). */
	public void close() {
		if( isOpen() ) {
			this.lastBounds = this.window.getBounds();
			this.closing = true;
			try {
				this.window.dispose();
			} finally {
				this.closing = false;
			}
		}
	}

	public boolean isOpen() {
		return (this.window != null && !this.window.isDisposed());
	}

	/** Renders a session snapshot and the current input assessment. */
	public void render(PracticeSnapshot snapshot, InputAssessment assessment) {
		if( !isOpen() ) {
			return;
		}
		PracticeSession.State state = snapshot.getState();
		renderVerdict(snapshot.getVerdict());
		this.detectedLabel.setText(detectedText(snapshot));
		this.statusLabel.setText(statusText(snapshot));
		this.techniqueLabel.setText(techniqueText(snapshot));
		this.outcomeLabel.setText(lastOutcomeText(snapshot));
		this.passageLabel.setText(passageText(snapshot.getPassageStatus()));
		setInput(assessment);
		this.skipButton.setEnabled(state == PracticeSession.State.RUNNING);
		this.resumeButton.setEnabled(state == PracticeSession.State.READY_TO_RESUME);
		this.restartButton.setEnabled(state == PracticeSession.State.COMPLETE);
		refit();
	}

	/**
	 * A large green check for an acceptance, a red cross for a confirmed wrong note or chord,
	 * nothing for silence, noise or while listening (owner requirement 2026-09-28). The symbol
	 * carries a word too, so colour is never the only cue (FR-070).
	 */
	private void renderVerdict(PracticeSession.Verdict verdict) {
		switch( verdict ) {
			case CORRECT:
				this.verdictLabel.setFgColor(this.correctColor);
				this.verdictLabel.setText(TuxGuitar.getProperty("practice.verdict.CORRECT"));
				break;
			case WRONG:
				this.verdictLabel.setFgColor(this.wrongColor);
				this.verdictLabel.setText(TuxGuitar.getProperty("practice.verdict.WRONG"));
				break;
			default:
				this.verdictLabel.setText(" ");
				break;
		}
	}

	public void setInput(InputAssessment assessment) {
		if( !isOpen() ) {
			return;
		}
		String reason = TuxGuitar.getProperty("practice.input.reason." + assessment.getReason().name());
		this.inputLabel.setText(TuxGuitar.getProperty("practice.input.status." + assessment.getStatus().name(), new String[] { reason }));
		refit();
	}

	/** Tuner-style reading with confidence: note, octave and cents from that note's own centre (FR-017). */
	private String detectedText(PracticeSnapshot snapshot) {
		PitchReading reading = snapshot.getReading();
		if( !reading.hasPitch() || !reading.isReliable() ) {
			return TuxGuitar.getProperty("practice.detected.none");
		}
		String note = reading.getNoteName() + reading.getOctave();
		String cents = Long.toString(Math.round(Math.abs(reading.getCents())));
		String text;
		switch( this.displayTolerance.tunerState(reading) ) {
			case FLAT:
				text = TuxGuitar.getProperty("practice.detected.flat", new String[] { note, cents });
				break;
			case SHARP:
				text = TuxGuitar.getProperty("practice.detected.sharp", new String[] { note, cents });
				break;
			default:
				text = TuxGuitar.getProperty("practice.detected.in-tune", new String[] { note });
				break;
		}
		String confidence = Long.toString(Math.round(snapshot.getConfidence() * 100));
		return text + " " + TuxGuitar.getProperty("practice.detected.confidence", new String[] { confidence });
	}

	/**
	 * The written techniques of the last finished note and what was heard of each: detected, not detected,
	 * uncertain, or not verifiable. It sits apart from the pitch result and never changes it (FR-030).
	 */
	private String techniqueText(PracticeSnapshot snapshot) {
		StringBuilder text = new StringBuilder();
		for( TechniqueResult result : snapshot.getTechniqueResults() ) {
			text.append(text.length() > 0 ? " · " : "").append(TuxGuitar.getProperty("practice.technique.name." + result.getTechnique().name()))
					.append(": ").append(TuxGuitar.getProperty("practice.technique.verdict." + result.getVerdict().name()));
		}
		return text.length() == 0 ? " " : text.toString();
	}

	/** The measured closest spacing of followed notes, in seconds with one decimal, in the language of the application. */
	static String speedLimitText() {
		return String.format(java.util.Locale.getDefault(), "%.1f", PracticeParameters.MEASURED_SPEED_LIMIT_SECONDS);
	}

	/** The feedback line for the current event, or the session state when there is none to give. */
	private String statusText(PracticeSnapshot snapshot) {
		PracticeSession.State state = snapshot.getState();
		PracticeEvent event = snapshot.getCurrentEvent();
		if( state == PracticeSession.State.RUNNING && event != null && snapshot.isSpeedLimit() ) {
			return TuxGuitar.getProperty("practice.speed-limit", new String[] { speedLimitText() });
		}
		if( state == PracticeSession.State.RUNNING && event != null ) {
			switch( event.getKind() ) {
				case EXPECTED_NOTE:
					return feedbackText(snapshot.getFeedback(), event);
				case EXPECTED_CHORD:
					return chordFeedbackText(snapshot.getChordFeedback(), event);
				case MULTI_NOTE:
					return TuxGuitar.getProperty("practice.multi-note");
				default:
					return "";
			}
		}
		String text = TuxGuitar.getProperty("practice.state." + state.name());
		if( state == PracticeSession.State.PAUSED ) {
			StringBuilder reasons = new StringBuilder();
			for( PracticeSession.PauseReason reason : snapshot.getPauseReasons() ) {
				reasons.append(reasons.length() > 0 ? ", " : "").append(TuxGuitar.getProperty("practice.pause." + reason.name()));
			}
			text += " " + reasons;
		}
		return text;
	}

	/**
	 * Chord feedback (FR-081, FR-082): what to strum, what was missing after a strum, and which
	 * pitches cannot be checked separately — never claimed as verified.
	 */
	private String chordFeedbackText(ChordFeedback feedback, PracticeEvent event) {
		String chord = chordText(event);
		String text;
		ChordFeedback.Kind kind = (feedback != null ? feedback.getKind() : ChordFeedback.Kind.WAITING);
		switch( kind ) {
			case UNCERTAIN:
				text = TuxGuitar.getProperty("practice.feedback.uncertain");
				break;
			case STRUM_AGAIN:
				text = TuxGuitar.getProperty("practice.chord.strum-again", new String[] { chord });
				break;
			case INCOMPLETE:
				text = TuxGuitar.getProperty("practice.chord.incomplete", new String[] { names(feedback.getMissing()), chord });
				break;
			default:
				text = TuxGuitar.getProperty("practice.chord.waiting", new String[] { chord });
				break;
		}
		// the notes audio cannot tell from the others are the ones the acceptance rule itself does not check separately (spec 004 FR-004)
		int[] notSeparable = PolyphonicAcceptance.notSeparable(event.getRequiredPitches());
		if( notSeparable.length > 0 ) {
			text += " " + TuxGuitar.getProperty("practice.chord.unverifiable", new String[] { names(notSeparable) });
		} else if( feedback != null && feedback.getUnverifiable().length > 0 ) {
			text += " " + TuxGuitar.getProperty("practice.chord.unverifiable", new String[] { names(feedback.getUnverifiable()) });
		}
		return text;
	}

	private static String names(int[] pitches) {
		StringBuilder text = new StringBuilder();
		for( int midi : pitches ) {
			text.append(text.length() > 0 ? " " : "").append(noteName(midi));
		}
		return text.toString();
	}

	/** One learner-worded feedback line with a text symbol, never colour alone (FR-018, FR-070). */
	private String feedbackText(Feedback feedback, PracticeEvent event) {
		String expected = noteName(event.getExpectedMidi());
		if( feedback == null ) {
			return TuxGuitar.getProperty("practice.feedback.waiting", new String[] { expected });
		}
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
			case WAITING:
			default:
				return TuxGuitar.getProperty(feedback.needsArticulation() ? "practice.feedback.play-again" : "practice.feedback.waiting",
						new String[] { expected });
		}
	}

	/**
	 * The latest result line: the accepted event, or a skipped / automatically passed event
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
		if( accepted == null ) {
			return "";
		}
		String what = (accepted.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ? chordText(accepted) : noteName(accepted.getExpectedMidi()));
		return TuxGuitar.getProperty("practice.feedback.correct", new String[] { what });
	}

	/**
	 * Passage line (FR-090): the current passage, attempt and errors, the last end-of-attempt
	 * decision, and the summary once complete. Session memory only.
	 */
	static String passageText(PassageStatus status) {
		if( status == null ) {
			return "";
		}
		StringBuilder text = new StringBuilder();
		PassageStatus.Summary summary = status.getSummary();
		if( summary != null ) {
			StringBuilder ranges = new StringBuilder();
			for( PassageStatus.MeasureRange range : summary.getNotPassed() ) {
				ranges.append(ranges.length() > 0 ? ", " : "").append(range.getFirst() == range.getLast()
						? Integer.toString(range.getFirst()) : range.getFirst() + "–" + range.getLast());
			}
			text.append(TuxGuitar.getProperty("practice.passage.summary", new String[] {
					Integer.toString(summary.getPassages()), Integer.toString(summary.getPassedFirstAttempt()),
					Integer.toString(summary.getPassedAfterRetry()), Integer.toString(summary.getNotPassed().size()),
					Integer.toString(summary.getRetries()) }));
			if( ranges.length() > 0 ) {
				text.append("\n").append(TuxGuitar.getProperty("practice.passage.summary.not-passed", new String[] { ranges.toString() }));
			}
			return text.toString();
		}
		if( status.getPassageNumber() > 0 ) {
			text.append(TuxGuitar.getProperty("practice.passage.current", new String[] {
					Integer.toString(status.getPassageNumber()), Integer.toString(status.getPassageCount()),
					Integer.toString(status.getFirstMeasure()), Integer.toString(status.getLastMeasure()),
					Integer.toString(status.getAttempt()), Integer.toString(status.getMaxAttempts()),
					Integer.toString(status.getErrors()), Integer.toString(status.getJudged()) }));
		}
		if( status.getDecision() != PassageStatus.Decision.NONE ) {
			text.append(text.length() > 0 ? "\n" : "").append(TuxGuitar.getProperty("practice.passage.decision." + status.getDecision().name(),
					new String[] { Integer.toString(status.getDecisionErrors()), Integer.toString(status.getDecisionJudged()),
					Integer.toString(status.getDecisionAttempt()), Integer.toString(status.getThresholdPercent()) }));
		}
		return text.toString();
	}

	/** The distinct pitches of an expected chord, e.g. "E2 B2 E3" (FR-078: duplicates once). */
	static String chordText(PracticeEvent event) {
		StringBuilder text = new StringBuilder();
		for( int midi : event.getRequiredPitches() ) {
			text.append(text.length() > 0 ? " " : "").append(noteName(midi));
		}
		return text.toString();
	}

	static String noteName(int midi) {
		return NOTE_NAMES[midi % 12] + (midi / 12 - 1);
	}

	/** Top right of the main window at first; afterwards where the learner left it. */
	private void placeWindow(UIWindow parent) {
		UIRectangle bounds = this.window.getBounds();
		if( this.lastBounds != null ) {
			bounds.getPosition().setX(this.lastBounds.getX());
			bounds.getPosition().setY(this.lastBounds.getY());
		} else {
			UIRectangle parentBounds = parent.getBounds();
			bounds.getPosition().setX(Math.max(0, parentBounds.getX() + parentBounds.getWidth() - bounds.getWidth() - 24f));
			bounds.getPosition().setY(parentBounds.getY() + 80f);
		}
		this.window.setBounds(bounds);
	}

	private UILabel label(UIFactory uiFactory, UITableLayout layout, int row) {
		UILabel label = uiFactory.createWrapLabel(this.window);
		layout.set(label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, WIDTH, null, null);
		layout.set(label, UITableLayout.PACKED_WIDTH, WIDTH);
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
		layout.set(button, 1, column, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, null, 25f, null);
		return button;
	}

	private void listenKeys(UIControl... controls) {
		KeyBindingActionManager keyBindings = KeyBindingActionManager.getInstance(this.context);
		for( UIControl control : controls ) {
			keyBindings.appendListenersTo(control);
		}
	}
}
