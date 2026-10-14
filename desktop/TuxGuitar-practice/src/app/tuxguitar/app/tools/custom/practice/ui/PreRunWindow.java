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

	/** Ten seconds without a strum: the learner may go on anyway (FR-008). */
	static final long GO_ON_AFTER_NANOS = 10_000_000_000L;

	private static final int METER_FLOOR_DB = 60;

	private final PreRunCheck check;
	private final PreRunSource source;
	private final Texts texts;
	private final LongSupplier clock;
	private final Runnable onFinished;
	private final UIWindow window;
	private final UIProgressBar meter;
	private final UILabel sentence;
	private final UILabel advice;
	private final UIButton continueButton;
	private final UIButton checkAgainButton;
	private final UIButton cancelButton;
	private State shown;
	private long strumSince;
	private boolean finished;

	PreRunWindow(UIFactory factory, UIWindow parent, PreRunCheck check, PreRunSource source, Texts texts, LongSupplier clock, Runnable onFinished) {
		this.check = check;
		this.source = source;
		this.texts = texts;
		this.clock = clock;
		this.onFinished = onFinished;
		this.window = factory.createWindow(parent, false, true);
		this.window.setText(texts.get("practice.prerun.title"));
		UITableLayout layout = new UITableLayout();
		// what is hidden takes no room
		layout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.window.setLayout(layout);

		UILabel ask = factory.createLabel(this.window);
		ask.setText(texts.get("practice.prerun.strum.ask"));
		layout.set(ask, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		this.meter = factory.createProgressBar(this.window);
		this.meter.setMinimum(0);
		this.meter.setMaximum(METER_FLOOR_DB);
		layout.set(this.meter, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.sentence = factory.createWrapLabel(this.window);
		layout.set(this.sentence, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.sentence, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		this.advice = factory.createWrapLabel(this.window);
		layout.set(this.advice, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.advice, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttons = factory.createPanel(this.window, false);
		buttons.setLayout(buttonsLayout);
		layout.set(buttons, 5, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.checkAgainButton = button(factory, buttons, buttonsLayout, 1, "practice.prerun.check-again");
		this.continueButton = button(factory, buttons, buttonsLayout, 2, "practice.prerun.continue");
		this.cancelButton = button(factory, buttons, buttonsLayout, 3, "practice.prerun.cancel");

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

	void toFront() {
		if (!this.window.isDisposed()) {
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
		boolean strum = state == State.STRUM;
		boolean bad = state == State.LEVEL_BAD;
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
		this.source.setChangeListener(null);
		this.source.close();
		if (!this.window.isDisposed()) {
			this.window.dispose();
		}
		this.onFinished.run();
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
