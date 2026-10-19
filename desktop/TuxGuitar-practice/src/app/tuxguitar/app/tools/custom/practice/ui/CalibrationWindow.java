package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController.RefusalCause;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController.ResultKind;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController.Snapshot;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.calibration.RecordingQuality;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UICloseEvent;
import app.tuxguitar.ui.event.UICloseListener;
import app.tuxguitar.ui.event.UIKeyEvent;
import app.tuxguitar.ui.event.UIKeyPressedListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIKey;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UICheckBox;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIProgressBar;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The one window of the calibration (spec 013, FR-003): its content changes at each step of {@link CalibrationController}: a title with the step, a text, a
 * detail, a bar and up to three buttons. It is modal, so it blocks the application, above all during the offline analysis (FR-006); it gives way to the
 * tuner while the tuner is open. Closing it, at any step, asks "Stop the calibration?" and only a Yes stops (nothing saved). It does nothing but show the
 * controller's snapshot and pass the player's answers to the controller; each step is its own view, so that separate windows would be easy to go back to.
 */
final class CalibrationWindow {

	/** The texts of the window, from the language files (the full key, {@code practice.profile.*}, is given). */
	interface Texts {
		String get(String key, String... arguments);
	}

	/** Makes the controller, given the listener it must publish its snapshots to. */
	interface ControllerFactory {
		CalibrationController create(CalibrationController.Listener listener);
	}

	private static final String P = "practice.profile.";
	/** the numbered steps: tuning, advice, piece, listening, quality, analysis, result */
	private static final int STEPS = 7;
	private static final float WRAP_WIDTH = 460f;

	/** What one step shows. */
	private static final class View {
		String step = "";
		String body = "";
		String detail = "";
		/** the bar: the maximum (0 or less hides it) and the value */
		int barMaximum;
		int barValue;
		/** the box "Keep a copy of this recording for diagnosis" is shown (the quality step only) */
		boolean keepBox;
		final String[] labels = new String[3];
		final Runnable[] actions = new Runnable[3];

		View button(int index, String label, Runnable action) {
			this.labels[index] = label;
			this.actions[index] = action;
			return this;
		}
	}

	private final Texts texts;
	private final boolean microphone;
	private final List<String> pieceLines;
	private final Executor uiThread;
	private final Consumer<State> onFinished;
	private final UIWindow window;
	private final UILabel stepLabel;
	private final UILabel bodyLabel;
	private final UILabel detailLabel;
	private final UIProgressBar bar;
	private final UICheckBox keepCopy;
	private final UIButton[] buttons = new UIButton[3];
	private final CalibrationController controller;
	private View current;
	private State lastState;
	private boolean asking;
	private boolean finished;
	private String laidOutFor = "";

	CalibrationWindow(UIFactory factory, UIWindow parent, Texts texts, boolean microphone, List<String> pieceLines, ControllerFactory controllers, Executor uiThread,
			Consumer<State> onFinished) {
		this.texts = texts;
		this.microphone = microphone;
		this.pieceLines = pieceLines;
		this.uiThread = uiThread;
		this.onFinished = onFinished;
		// modal: the application waits, above all during the analysis; without a parent the native toolkit has no window to hang it on
		this.window = parent == null ? factory.createWindow() : factory.createWindow(parent, true, true);
		this.window.setText(texts.get(P + "title"));
		UITableLayout layout = new UITableLayout();
		layout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.window.setLayout(layout);

		this.stepLabel = factory.createLabel(this.window);
		layout.set(this.stepLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);
		this.bodyLabel = factory.createWrapLabel(this.window);
		layout.set(this.bodyLabel, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.bodyLabel, UITableLayout.PACKED_WIDTH, WRAP_WIDTH);
		this.detailLabel = factory.createWrapLabel(this.window);
		layout.set(this.detailLabel, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.detailLabel, UITableLayout.PACKED_WIDTH, WRAP_WIDTH);
		this.bar = factory.createProgressBar(this.window);
		this.bar.setMinimum(0);
		layout.set(this.bar, 4, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		// FR-020: unticked at every entry of the quality step, shown in that step only
		this.keepCopy = factory.createCheckBox(this.window);
		this.keepCopy.setText(texts.get(P + "quality.keep-copy"));
		this.keepCopy.setVisible(false);
		layout.set(this.keepCopy, 5, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, false);

		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel row = factory.createPanel(this.window, false);
		row.setLayout(buttonsLayout);
		layout.set(row, 6, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		for (int i = 0; i < this.buttons.length; i++) {
			final int index = i;
			this.buttons[i] = factory.createButton(row);
			buttonsLayout.set(this.buttons[i], 1, i + 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			this.buttons[i].addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					pressed(index);
				}
			});
		}
		this.window.addCloseListener(new UICloseListener() {
			public void onClose(UICloseEvent event) {
				closeRequested();
			}
		});
		this.window.addKeyPressedListener(new UIKeyPressedListener() {
			public void onKeyPressed(UIKeyEvent event) {
				if (event.getKeyCombination().contains(UIKey.ESC)) {
					closeRequested();
				}
			}
		});
		this.controller = controllers.create(snapshot -> this.uiThread.execute(this::refresh));
		refresh();
	}

	void open() {
		this.window.pack();
		this.window.open();
	}

	// ---------------------------------------------------------------- what the tests look at

	UIWindow window() {
		return this.window;
	}

	UILabel stepLabel() {
		return this.stepLabel;
	}

	UILabel bodyLabel() {
		return this.bodyLabel;
	}

	UILabel detailLabel() {
		return this.detailLabel;
	}

	UIProgressBar progressBar() {
		return this.bar;
	}

	UICheckBox keepCopyBox() {
		return this.keepCopy;
	}

	UIButton button(int index) {
		return this.buttons[index];
	}

	// ---------------------------------------------------------------- the cross, the buttons

	private void pressed(int index) {
		Runnable action = this.current == null ? null : this.current.actions[index];
		if (action != null) {
			action.run();
		}
	}

	/** Closes the window without going through its close listener (which would ask again, and close again: found on the built application). */
	private void dispose() {
		if (!this.window.isDisposed()) {
			this.window.dispose();
		}
	}

	/** The cross or Escape: asks "Stop the calibration?" at every step; the window does not close by itself. */
	void closeRequested() {
		if (this.finished) {
			dispose();
			return;
		}
		this.asking = true;
		refresh();
	}

	private View stopQuestion() {
		View view = new View();
		view.body = this.texts.get(P + "stop.question");
		view.detail = this.texts.get(P + "stop.note");
		view.button(0, this.texts.get(P + "stop.yes"), () -> {
			this.asking = false;
			this.controller.cancel();
			refresh();
		});
		view.button(1, this.texts.get(P + "stop.no"), () -> {
			this.asking = false;
			refresh();
		});
		return view;
	}

	// ---------------------------------------------------------------- showing a step

	void refresh() {
		if (this.finished) {
			return;
		}
		Snapshot snapshot = this.controller.getSnapshot();
		State state = snapshot.getState();
		if (state == State.DONE || state == State.CANCELLED) {
			this.finished = true;
			dispose();
			this.onFinished.accept(state);
			return;
		}
		// the tuner is open: the modal window gives way until it is closed
		this.window.setVisible(state != State.TUNER_OPEN);
		if (state == State.QUALITY && this.lastState != State.QUALITY) {
			this.keepCopy.setSelected(false);
		}
		this.lastState = state;
		View view = this.asking ? stopQuestion() : view(snapshot);
		if (!this.asking) {
			addCopyLine(view, snapshot);
		}
		apply(view);
	}

	/** FR-020: where the diagnostic copy was written, or that it could not be, added to the text of the steps that follow the quality question. */
	private void addCopyLine(View view, Snapshot s) {
		String line = s.getDiagnosticPath() != null ? t("quality.copy.written", s.getDiagnosticPath()) : s.isDiagnosticFailed() ? t("quality.copy.failed") : "";
		if (!line.isEmpty()) {
			view.detail = view.detail.isEmpty() ? line : view.detail + "\n" + line;
		}
	}

	private void apply(View view) {
		this.current = view;
		this.stepLabel.setText(view.step);
		this.stepLabel.setVisible(!view.step.isEmpty());
		this.bodyLabel.setText(view.body);
		this.detailLabel.setText(view.detail);
		this.detailLabel.setVisible(!view.detail.isEmpty());
		this.keepCopy.setVisible(view.keepBox);
		this.bar.setVisible(view.barMaximum > 0);
		if (view.barMaximum > 0) {
			this.bar.setMaximum(view.barMaximum);
			this.bar.setValue(Math.min(view.barValue, view.barMaximum));
		}
		for (int i = 0; i < this.buttons.length; i++) {
			boolean shown = view.labels[i] != null;
			this.buttons[i].setVisible(shown);
			if (shown) {
				this.buttons[i].setText(view.labels[i]);
			}
		}
		// the layout is redone only when what is shown changes in size
		String layoutKey = view.step + "|" + view.body + "|" + view.detail + "|" + (view.barMaximum > 0) + "|" + String.valueOf(view.labels[0]) + String.valueOf(view.labels[1])
				+ String.valueOf(view.labels[2]);
		if (!layoutKey.equals(this.laidOutFor)) {
			this.laidOutFor = layoutKey;
			WindowFit.refit(this.window, null);
		}
	}

	private String t(String key, String... arguments) {
		return this.texts.get(P + key, arguments);
	}

	private String step(int number) {
		return number <= 0 ? "" : t("step", Integer.toString(number), Integer.toString(STEPS));
	}

	private View view(Snapshot s) {
		View v = new View();
		CalibrationController c = this.controller;
		String cancel = t("cancel");
		switch (s.getState()) {
		case OFFER:
			v.body = t(s.hasProfile() ? "offer.again" : "offer.new");
			v.button(0, t(s.hasProfile() ? "offer.calibrate-again" : "offer.calibrate"), c::calibrate);
			v.button(1, t(s.hasProfile() ? "offer.default-replaces" : "offer.default"), c::useDefault);
			v.button(2, cancel, c::cancel);
			return v;
		case TUNING:
		case TUNER_OPEN:
			v.step = step(1);
			// the question and the answers of the start window are reused (spec 010)
			v.body = this.texts.get("practice.prerun.tuned.question") + (s.isTunerUnavailable() ? " " + t("tuner.unavailable") : "");
			v.button(0, this.texts.get("practice.prerun.tuned.yes"), () -> c.answerTuning(true));
			v.button(1, this.texts.get("practice.prerun.tuned.no"), () -> c.answerTuning(false));
			v.button(2, cancel, c::cancel);
			return v;
		case ADVICE:
			v.step = step(2);
			v.body = t("advice") + (this.microphone ? " " + t("advice.microphone") : "");
			v.button(0, t("ok"), c::ok);
			v.button(1, cancel, c::cancel);
			return v;
		case PIECE:
			v.step = step(3);
			v.body = t("piece.intro");
			v.detail = numbered(0);
			v.button(0, t("ok"), c::ok);
			v.button(1, cancel, c::cancel);
			return v;
		case LISTENING:
			v.step = step(4);
			v.body = t("listening", Integer.toString(s.getHeard()), Integer.toString(s.getExpected()));
			v.detail = s.getHeard() < this.pieceLines.size() ? t("listening.next", (s.getHeard() + 1) + ". " + this.pieceLines.get(s.getHeard())) : "";
			v.barMaximum = s.getExpected();
			v.barValue = s.getHeard();
			v.button(0, cancel, c::cancel);
			return v;
		case QUALITY:
			v.step = step(5);
			v.body = t("quality.question");
			v.detail = measures(s.getQuality());
			v.keepBox = true;
			v.button(0, t("quality.yes"), () -> c.answerQuality(true, this.keepCopy.isSelected()));
			v.button(1, t("quality.no"), () -> c.answerQuality(false, this.keepCopy.isSelected()));
			v.button(2, cancel, c::cancel);
			return v;
		case REFUSED:
			v.step = step(4);
			v.body = refusal(s);
			v.detail = t("refused.choose");
			v.button(0, t("record-again"), c::recordAgain);
			v.button(1, t(s.hasProfile() ? "offer.default-replaces" : "offer.default"), c::useDefault);
			v.button(2, t("stop"), c::stop);
			return v;
		case ANALYSING:
			v.step = step(6);
			v.body = t("analysing", Integer.toString(s.getProgressDone()), Integer.toString(s.getProgressBudget()));
			v.barMaximum = Math.max(1, s.getProgressBudget());
			v.barValue = s.getProgressDone();
			v.button(0, cancel, c::cancel);
			return v;
		case RESULT:
			v.step = step(7);
			v.body = s.isSaveFailed() ? t("result.save-failed") : t("result.saved");
			v.detail = rates(s);
			v.button(0, t("ok"), c::ok);
			return v;
		case CHOICE:
			v.step = step(7);
			v.body = s.getResultKind() == ResultKind.BETTER_UNDER_60
					? t("choice.BETTER_UNDER_60", percent(s.getFoundRate().totalPercent())) : t("choice.NOT_BETTER");
			v.detail = rates(s);
			v.button(0, t("choice.use-found"), c::useFound);
			v.button(1, t("record-again"), c::recordAgain);
			v.button(2, t(s.hasProfile() ? "offer.default-replaces" : "offer.default"), c::useDefault);
			return v;
		default:
			return v;
		}
	}

	private String numbered(int from) {
		StringBuilder lines = new StringBuilder();
		for (int i = from; i < this.pieceLines.size(); i++) {
			lines.append(i + 1).append(". ").append(this.pieceLines.get(i)).append(i + 1 < this.pieceLines.size() ? "\n" : "");
		}
		return lines.toString();
	}

	private String measures(RecordingQuality q) {
		if (q == null) {
			return "";
		}
		String word = t("quality.noise." + q.getNoiseWord().name().toLowerCase(Locale.ROOT));
		return t("quality.sounds", Integer.toString(q.getHeard()), Integer.toString(q.getExpected())) + "\n"
				+ t("quality.peak", oneDecimal(q.getPeakDbfs())) + "\n"
				+ t("quality.noise", oneDecimal(q.getNoiseDbfs()), word) + "\n"
				+ t(q.isClipped() ? "quality.clipping" : "quality.clipping.none");
	}

	private String refusal(Snapshot s) {
		RefusalCause cause = s.getRefusalCause();
		if (cause == null) {
			return "";
		}
		if (cause == RefusalCause.WRONG_COUNT) {
			return t("refused.WRONG_COUNT", Integer.toString(s.getHeard()), Integer.toString(s.getExpected()));
		}
		return t("refused." + cause.name());
	}

	/** The rates with the default settings, then with the profile: the notes, the chords, the total, and the reserve and limit sentences. */
	private String rates(Snapshot s) {
		RecognitionRate found = s.getFoundRate();
		RecognitionRate base = s.getDefaultRate();
		if (found == null || base == null) {
			return "";
		}
		List<String> lines = new ArrayList<String>();
		lines.add(t("result.rates"));
		if (found.getNotesTotal() > 0) {
			lines.add(t("result.rate.notes", percent(base.notesPercent()), percent(found.notesPercent())));
		}
		if (found.getChordsTotal() > 0) {
			lines.add(t("result.rate.chords", percent(base.chordsPercent()), percent(found.chordsPercent())));
		}
		lines.add(t("result.rate.total", percent(base.totalPercent()), percent(found.totalPercent())));
		lines.add(t("result.reserve"));
		if (s.isStoppedAtLimit()) {
			lines.add(t("result.limit"));
		}
		return String.join("\n", lines);
	}

	private static String percent(double value) {
		return Long.toString(Math.round(value));
	}

	private static String oneDecimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
