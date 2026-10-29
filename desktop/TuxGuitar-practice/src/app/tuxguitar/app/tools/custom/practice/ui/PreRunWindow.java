package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Locale;
import java.util.function.LongSupplier;

import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UIKeyEvent;
import app.tuxguitar.ui.event.UIKeyPressedListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIKey;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIProgressBar;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The small start window (spec 010): the strum asked for, the live level, what the strum said and what to do, and the ways on and out. A thin shell over
 * {@link PreRunCheck}, which holds the rules, and {@link PreRunSource}, which holds the audio. It gives the hand back once ({@code onFinished}), whether the
 * mode may begin or not (the state of the check says which), and never starts anything itself.
 */
final class PreRunWindow {

	/** The texts of the window, from the language files; the advice for the verdicts reuses the keys of the level panel. */
	interface Texts {
		String get(String key, String... arguments);
	}

	/** The way to the tuner (spec 010 R5): whether it exists, opening it (or bringing it forward), and telling when it is closed, by any means (user-interface thread). */
	interface TunerOpener {
		boolean available();

		void open();

		void toFront();

		/** The listener runs when a tuner opened through {@link #open()} is closed; null removes it. */
		void setClosedListener(Runnable listener);
	}

	/** Ten seconds without a strum: the learner may go on anyway (FR-008). */
	static final long GO_ON_AFTER_NANOS = 10_000_000_000L;

	private static final int METER_FLOOR_DB = 60;

	private final PreRunCheck check;
	private final PreRunSource source;
	private final Texts texts;
	private final LongSupplier clock;
	private final TunerOpener tuner;
	private final Runnable onFinished;
	private final UIWindow window;
	private final UIProgressBar meter;
	private final UILabel ask;
	private final UILabel question;
	private final UIButton yesButton;
	private final UIButton noButton;
	private final UILabel sentence;
	private final UILabel advice;
	private final UIButton continueButton;
	private final UIButton checkAgainButton;
	private final UIButton cancelButton;
	private final UILabel levelLabel;
	private final UIDropDownSelect<ArcadeLevel> levelSelect;
	/** The offer of the rhythm calibration after the level (spec 016), or null: then the mode begins as soon as the check is ready. */
	private final RhythmOffer offer;
	private final CalibrationFlow calibration;
	private final UILabel rhythmSentence;
	private final UILabel rhythmProgress;
	private final UIPanel rhythmButtons;
	private final UIButton calibrateNowButton;
	private final UIButton goOnWithoutButton;
	private final UIButton calibrationCancelButton;
	private State shown;
	private long strumSince;
	private boolean finished;
	private boolean tunerMissing;
	/** What the last layout was made for: the parts shown and their texts. The layout is redone only when it changes. */
	private String laidOutFor = "";

	PreRunWindow(UIFactory factory, UIWindow parent, PreRunCheck check, PreRunSource source, Texts texts, LongSupplier clock, TunerOpener tuner, Runnable onFinished) {
		this(factory, parent, check, source, texts, clock, tuner, onFinished, null);
	}

	/** @param levelChoice for an Arcade start (spec 014): the row that asks the level of the run is shown first; null for the other modes (no row) */
	PreRunWindow(UIFactory factory, UIWindow parent, PreRunCheck check, PreRunSource source, Texts texts, LongSupplier clock, TunerOpener tuner, Runnable onFinished,
			LevelChoice levelChoice) {
		this(factory, parent, check, source, texts, clock, tuner, onFinished, levelChoice, null, null);
	}

	/**
	 * @param offer the offer of the rhythm calibration once the check is ready (spec 016 FR-004), or null for none
	 * @param backend runs the calibration of the offer as the Mode tab runs it; null when there is no offer
	 */
	PreRunWindow(UIFactory factory, UIWindow parent, PreRunCheck check, PreRunSource source, Texts texts, LongSupplier clock, TunerOpener tuner, Runnable onFinished,
			LevelChoice levelChoice, RhythmOffer offer, FollowPanel.Backend backend) {
		this.check = check;
		this.offer = (backend != null ? offer : null);
		this.source = source;
		this.texts = texts;
		this.clock = clock;
		this.tuner = tuner;
		this.onFinished = onFinished;
		// without a parent (the preparation window was never shown) the native toolkit has no window to hang it on
		this.window = parent == null ? factory.createWindow() : factory.createWindow(parent, false, true);
		this.window.setText(texts.get("practice.prerun.title"));
		UITableLayout layout = new UITableLayout();
		// what is hidden takes no room
		layout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.window.setLayout(layout);

		// the level of an Arcade run (spec 014, FR-006): asked at every launch, on the first row; the other rows are one lower when it is there
		final int first = levelChoice != null ? 2 : 1;
		if (levelChoice != null) {
			UITableLayout levelLayout = new UITableLayout(0f);
			UIPanel levelRow = factory.createPanel(this.window, false);
			levelRow.setLayout(levelLayout);
			layout.set(levelRow, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			this.levelLabel = factory.createLabel(levelRow);
			this.levelLabel.setText(texts.get("practice.arcade.level.label"));
			levelLayout.set(this.levelLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			this.levelSelect = factory.createDropDownSelect(levelRow);
			for (ArcadeLevel level : ArcadeLevel.values()) {
				this.levelSelect.addItem(new UISelectItem<ArcadeLevel>(texts.get("practice.arcade.level." + level.number()), level));
			}
			this.levelSelect.setSelectedValue(levelChoice.level());
			levelLayout.set(this.levelSelect, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			this.levelSelect.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					ArcadeLevel chosen = PreRunWindow.this.levelSelect.getSelectedValue();
					if (chosen != null) {
						levelChoice.choose(chosen);
					}
				}
			});
		} else {
			this.levelLabel = null;
			this.levelSelect = null;
		}

		this.question = factory.createWrapLabel(this.window);
		this.question.setText(texts.get("practice.prerun.tuned.question"));
		layout.set(this.question, first, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.question, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		UITableLayout answersLayout = new UITableLayout(0f);
		UIPanel answers = factory.createPanel(this.window, false);
		answers.setLayout(answersLayout);
		layout.set(answers, first + 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.yesButton = button(factory, answers, answersLayout, 1, "practice.prerun.tuned.yes");
		this.noButton = button(factory, answers, answersLayout, 2, "practice.prerun.tuned.no");

		this.ask = factory.createLabel(this.window);
		this.ask.setText(texts.get("practice.prerun.strum.ask"));
		layout.set(this.ask, first + 2, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		this.meter = factory.createProgressBar(this.window);
		this.meter.setMinimum(0);
		this.meter.setMaximum(METER_FLOOR_DB);
		layout.set(this.meter, first + 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.sentence = factory.createWrapLabel(this.window);
		layout.set(this.sentence, first + 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.sentence, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		this.advice = factory.createWrapLabel(this.window);
		layout.set(this.advice, first + 5, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.advice, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = factory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, first + 6, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.checkAgainButton = button(factory, buttons, buttonsLayout, 1, "practice.prerun.check-again");
		this.continueButton = button(factory, buttons, buttonsLayout, 2, "practice.prerun.continue");
		this.cancelButton = button(factory, buttons, buttonsLayout, 3, "practice.prerun.cancel");

		// the offer of the rhythm calibration (spec 016): its sentence, the calibration's own sentence, and its buttons
		this.rhythmSentence = factory.createWrapLabel(this.window);
		this.rhythmSentence.setText(texts.get("practice.prerun.rhythm.sentence"));
		layout.set(this.rhythmSentence, first + 7, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.rhythmSentence, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		this.rhythmProgress = factory.createWrapLabel(this.window);
		this.rhythmProgress.setText("");
		layout.set(this.rhythmProgress, first + 8, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.rhythmProgress, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		UITableLayout rhythmLayout = new UITableLayout(0f);
		rhythmLayout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.rhythmButtons = factory.createPanel(this.window, false);
		this.rhythmButtons.setLayout(rhythmLayout);
		layout.set(this.rhythmButtons, first + 9, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.calibrateNowButton = button(factory, this.rhythmButtons, rhythmLayout, 1, "practice.prerun.rhythm.calibrate");
		this.goOnWithoutButton = button(factory, this.rhythmButtons, rhythmLayout, 2, "practice.prerun.rhythm.go-on");
		this.calibrationCancelButton = button(factory, this.rhythmButtons, rhythmLayout, 3, "practice.follow.calibration.cancel");
		this.calibration = (this.offer != null ? new CalibrationFlow(backend, new CalibrationFlow.View() {
			public void progress(String text) {
				PreRunWindow.this.rhythmProgress.setText(text);
			}

			public void refresh() {
				PreRunWindow.this.refresh();
			}

			public void relayout() {
				PreRunWindow.this.refresh();
			}

			public void ended(boolean calibrated) {
				PreRunWindow.this.offer.calibrationEnded(calibrated);
				PreRunWindow.this.refresh();
			}
		}) : null);
		this.calibrateNowButton.addSelectionListener(selected(this::calibrateNow));
		this.goOnWithoutButton.addSelectionListener(selected(() -> {
			this.offer.goOnWithout();
			refresh();
		}));
		this.calibrationCancelButton.addSelectionListener(selected(() -> this.calibration.cancel()));

		this.yesButton.addSelectionListener(selected(() -> {
			this.check.answerTuned(true);
			refresh();
		}));
		this.noButton.addSelectionListener(selected(this::notTuned));
		this.checkAgainButton.addSelectionListener(selected(() -> {
			this.check.checkAgain();
			refresh();
		}));
		this.continueButton.addSelectionListener(selected(() -> {
			this.check.continueAnyway();
			refresh();
		}));
		this.cancelButton.addSelectionListener(selected(() -> {
			this.check.cancel();
			cancelOffer();
			refresh();
		}));
		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				windowClosed();
			}
		});
		this.window.addKeyPressedListener(new UIKeyPressedListener() {
			public void onKeyPressed(UIKeyEvent event) {
				if (event.getKeyCombination().contains(UIKey.ESC)) {
					escapePressed();
				}
			}
		});
		this.tuner.setClosedListener(this::tunerClosed);
		this.check.setMeterReset(this.source::reset);
		this.source.setChangeListener(this::refresh);
		this.shown = null;
	}

	/** Calibrate now: one action, the clicks begin at once inside this window; when they cannot, the offer stays with the reason. */
	private void calibrateNow() {
		this.offer.calibrate();
		if (!this.calibration.start()) {
			this.offer.calibrationEnded(false);
		}
		refresh();
	}

	/** The start is cancelled during the offer: the clicks stop and nothing begins. */
	private void cancelOffer() {
		if (this.offer != null) {
			this.calibration.dispose();
			this.offer.cancel();
		}
	}

	/** What an Arcade start asks of the player and remembers: the level, level 1 the first time, then the last one chosen. */
	interface LevelChoice {
		ArcadeLevel level();

		void choose(ArcadeLevel level);
	}

	/** The row of the level, or null when this start is not an Arcade one. */
	UIDropDownSelect<ArcadeLevel> levelSelect() {
		return this.levelSelect;
	}

	UILabel levelLabel() {
		return this.levelLabel;
	}

	boolean hasLevelRow() {
		return this.levelSelect != null;
	}

	private UIButton button(UIFactory factory, UIPanel parent, UITableLayout layout, int column, String key) {
		UIButton button = factory.createButton(parent);
		button.setText(this.texts.get(key));
		layout.set(button, 1, column, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		return button;
	}

	private UISelectionListener selected(Runnable action) {
		return new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		};
	}

	void open() {
		this.window.pack();
		this.window.open();
	}

	/** The learner says the guitar is not in tune: the tuner opens and the start window gives way, unless there is no tuner (then the question stays, with a message). */
	private void notTuned() {
		if (!this.tuner.available()) {
			this.tunerMissing = true;
			refresh();
			return;
		}
		this.check.answerTuned(false);
		if (this.check.state() == State.TUNER_OPEN) {
			this.tuner.open();
		}
		refresh();
	}

	/** The tuner was closed, by any means: back to the question (the mode never begins by itself). */
	private void tunerClosed() {
		if (this.finished) {
			return;
		}
		this.check.tunerClosed();
		refresh();
	}

	/** Brings the pending start forward: the tuner while it is open, else this window. */
	void toFront() {
		if (this.check.state() == State.TUNER_OPEN) {
			this.tuner.toFront();
		} else if (!this.window.isDisposed()) {
			this.window.moveToTop();
		}
	}

	/** Reads the audio, lets the check act on a finished strum, and shows the state (user-interface thread). */
	void refresh() {
		if (this.finished) {
			return;
		}
		State state = this.check.state();
		if (state == State.STRUM) {
			StrumClipMeter.Reading reading = this.source.reading();
			if (reading.verdict() != StrumVerdict.NO_STRUM) {
				this.check.verdict(reading.verdict());
				state = this.check.state();
			}
		}
		if (state == State.READY && this.offer != null && this.offer.enter()) {
			showOffer();
			return;
		}
		if (state == State.READY || state == State.CANCELLED) {
			finish();
			return;
		}
		if (state != this.shown) {
			if (state == State.STRUM) {
				this.strumSince = this.clock.getAsLong();
			}
			this.shown = state;
		}
		boolean asking = state == State.TUNING;
		boolean strum = state == State.STRUM;
		boolean bad = state == State.LEVEL_BAD;
		this.window.setVisible(state != State.TUNER_OPEN);
		this.question.setText(this.texts.get("practice.prerun.tuned.question") + (asking && this.tunerMissing ? "\n" + this.texts.get("practice.prerun.tuner.unavailable") : ""));
		this.question.setVisible(asking);
		this.yesButton.setVisible(asking);
		this.noButton.setVisible(asking);
		this.ask.setVisible(strum || bad);
		if (!asking) {
			this.tunerMissing = false;
		}
		double level = this.source.levelDbfs();
		this.meter.setValue(Double.isNaN(level) ? 0 : (int) Math.round(Math.max(-METER_FLOOR_DB, Math.min(0.0, level)) + METER_FLOOR_DB));
		this.meter.setVisible(strum || bad);
		if (bad) {
			StrumClipMeter.Reading reading = this.source.reading();
			StrumVerdict verdict = this.check.lastVerdict();
			this.sentence.setText(this.texts.get("practice.prerun.verdict." + verdict.name()) + " "
					+ this.texts.get("practice.prerun.strum.heard", String.format(Locale.ROOT, "%.0f", reading.rmsDbfs())));
			this.advice.setText(adviceFor(verdict));
		} else {
			this.sentence.setText(strum ? this.texts.get("practice.prerun.verdict.NO_STRUM") : "");
			this.advice.setText("");
		}
		this.sentence.setVisible(strum || bad);
		this.advice.setVisible(bad);
		this.checkAgainButton.setVisible(bad);
		this.continueButton.setVisible(bad || (strum && this.clock.getAsLong() - this.strumSince >= GO_ON_AFTER_NANOS));
		this.cancelButton.setVisible(true);
		this.rhythmSentence.setVisible(false);
		this.rhythmProgress.setVisible(false);
		this.rhythmButtons.setVisible(false);
		// a control made visible after the window was laid out has no size until the layout is redone (found by the owner: an empty window with only its buttons)
		String layoutKey = state + "|" + this.tunerMissing + "|" + this.sentence.getText() + "|" + this.advice.getText() + "|" + this.continueButton.isVisible() + "|" + this.checkAgainButton.isVisible();
		if (!layoutKey.equals(this.laidOutFor) && state != State.TUNER_OPEN) {
			this.laidOutFor = layoutKey;
			WindowFit.refit(this.window, null);
		}
	}

	/** The step of the rhythm calibration (spec 016): the strum is over, its parts give way to the offer, then to the clicks of the calibration. */
	private void showOffer() {
		boolean calibrating = this.offer.state() == RhythmOffer.State.CALIBRATING;
		this.window.setVisible(true);
		this.question.setVisible(false);
		this.yesButton.setVisible(false);
		this.noButton.setVisible(false);
		this.ask.setVisible(false);
		this.meter.setVisible(false);
		this.sentence.setVisible(false);
		this.advice.setVisible(false);
		this.checkAgainButton.setVisible(false);
		this.continueButton.setVisible(false);
		this.cancelButton.setVisible(true);
		this.rhythmSentence.setVisible(true);
		this.rhythmProgress.setVisible(!this.rhythmProgress.getText().isEmpty());
		this.rhythmButtons.setVisible(true);
		this.calibrateNowButton.setVisible(!calibrating);
		this.goOnWithoutButton.setVisible(!calibrating);
		this.calibrationCancelButton.setVisible(calibrating);
		String layoutKey = "RHYTHM|" + calibrating + "|" + this.rhythmProgress.getText();
		if (!layoutKey.equals(this.laidOutFor)) {
			this.laidOutFor = layoutKey;
			WindowFit.refit(this.window, null);
		}
	}

	/** What to do about a bad strum: the advice of the level panel, and the boost warning when the application's own gain is raised. */
	private String adviceFor(StrumVerdict verdict) {
		String key = verdict == StrumVerdict.CLIPPING ? "CLIPPING" : verdict == StrumVerdict.TOO_LOUD ? "TOO_HIGH" : "TOO_LOW";
		String advice = this.texts.get("practice.level.guidance." + key);
		if (verdict == StrumVerdict.CLIPPING && this.source.boostActive()) {
			advice += "\n" + this.texts.get("practice.gain.warning.BOOST_CAUSES_CLIPPING");
		}
		return advice;
	}

	/** The close button of the window: a cancel, unless the mode may already begin. */
	void windowClosed() {
		if (this.finished) {
			return;
		}
		if (this.check.state() != State.READY) {
			this.check.cancel();
		}
		cancelOffer();
		finish();
	}

	/** The Escape key: a cancel. */
	void escapePressed() {
		this.check.cancel();
		cancelOffer();
		refresh();
	}

	/** True when the mode may begin: the check is ready and the offer of the rhythm, if any, allows it. */
	boolean mayBegin() {
		return this.check.state() == State.READY && (this.offer == null || this.offer.allowsStart());
	}

	/** Gives the hand back once: the audio listeners are released, the window closes and the flow is told. */
	private void finish() {
		if (this.finished) {
			return;
		}
		this.finished = true;
		this.tuner.setClosedListener(null);
		this.source.setChangeListener(null);
		this.source.close();
		if (!this.window.isDisposed()) {
			this.window.dispose();
		}
		this.onFinished.run();
	}

	UIWindow windowWidget() {
		return this.window;
	}

	UILabel questionLabel() {
		return this.question;
	}

	UIButton yesButton() {
		return this.yesButton;
	}

	UIButton noButton() {
		return this.noButton;
	}

	UIProgressBar meterBar() {
		return this.meter;
	}

	UILabel sentenceLabel() {
		return this.sentence;
	}

	UILabel adviceLabel() {
		return this.advice;
	}

	UIButton continueButton() {
		return this.continueButton;
	}

	UIButton checkAgainButton() {
		return this.checkAgainButton;
	}

	UIButton cancelButton() {
		return this.cancelButton;
	}

	UILabel rhythmSentenceLabel() {
		return this.rhythmSentence;
	}

	UILabel rhythmProgressLabel() {
		return this.rhythmProgress;
	}

	UIButton calibrateNowButton() {
		return this.calibrateNowButton;
	}

	UIButton goOnWithoutButton() {
		return this.goOnWithoutButton;
	}

	UIButton calibrationCancelButton() {
		return this.calibrationCancelButton;
	}
}
