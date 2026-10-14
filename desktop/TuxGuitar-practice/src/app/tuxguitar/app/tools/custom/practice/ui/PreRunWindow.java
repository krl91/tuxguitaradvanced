package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Locale;
import java.util.function.LongSupplier;

import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
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
	private State shown;
	private long strumSince;
	private boolean finished;
	private boolean tunerMissing;

	PreRunWindow(UIFactory factory, UIWindow parent, PreRunCheck check, PreRunSource source, Texts texts, LongSupplier clock, TunerOpener tuner, Runnable onFinished) {
		this.check = check;
		this.source = source;
		this.texts = texts;
		this.clock = clock;
		this.tuner = tuner;
		this.onFinished = onFinished;
		this.window = factory.createWindow(parent, false, true);
		this.window.setText(texts.get("practice.prerun.title"));
		UITableLayout layout = new UITableLayout();
		// what is hidden takes no room
		layout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.window.setLayout(layout);

		this.question = factory.createWrapLabel(this.window);
		this.question.setText(texts.get("practice.prerun.tuned.question"));
		layout.set(this.question, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.question, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		UITableLayout answersLayout = new UITableLayout(0f);
		UIPanel answers = factory.createPanel(this.window, false);
		answers.setLayout(answersLayout);
		layout.set(answers, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.yesButton = button(factory, answers, answersLayout, 1, "practice.prerun.tuned.yes");
		this.noButton = button(factory, answers, answersLayout, 2, "practice.prerun.tuned.no");

		this.ask = factory.createLabel(this.window);
		this.ask.setText(texts.get("practice.prerun.strum.ask"));
		layout.set(this.ask, 3, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		this.meter = factory.createProgressBar(this.window);
		this.meter.setMinimum(0);
		this.meter.setMaximum(METER_FLOOR_DB);
		layout.set(this.meter, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.sentence = factory.createWrapLabel(this.window);
		layout.set(this.sentence, 5, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.sentence, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		this.advice = factory.createWrapLabel(this.window);
		layout.set(this.advice, 6, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.advice, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = factory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 7, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.checkAgainButton = button(factory, buttons, buttonsLayout, 1, "practice.prerun.check-again");
		this.continueButton = button(factory, buttons, buttonsLayout, 2, "practice.prerun.continue");
		this.cancelButton = button(factory, buttons, buttonsLayout, 3, "practice.prerun.cancel");

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
		finish();
	}

	/** The Escape key: a cancel. */
	void escapePressed() {
		this.check.cancel();
		refresh();
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
}
