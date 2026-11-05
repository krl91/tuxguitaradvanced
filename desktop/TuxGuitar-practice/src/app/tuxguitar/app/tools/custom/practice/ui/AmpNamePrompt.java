package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIDisposeEvent;
import app.tuxguitar.ui.event.UIDisposeListener;
import app.tuxguitar.ui.event.UIModifyEvent;
import app.tuxguitar.ui.event.UIModifyListener;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UITextField;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The small window that asks for a sound's name, for Save… and Rename… of the amp window (spec 025, FR-009, clarification 3, research R6): the name, the line
 * that says why a name cannot be used (the messages of spec 022: a ready-made name, more than {@link AmpSoundStore#NAME_LENGTH} characters), OK and Cancel. OK
 * is enabled only for a name the caller accepts.
 */
final class AmpNamePrompt {

	private static final float FIELD_WIDTH = 240f;
	private static final float PROBLEM_WIDTH = 320f;

	private final UIFactory factory;
	private final AmpWindow.Texts texts;
	private UIWindow window;
	private UITextField name;
	private UILabel problem;
	private UIButton ok;
	private UIButton cancel;

	AmpNamePrompt(UIFactory factory, AmpWindow.Texts texts) {
		this.factory = factory;
		this.texts = texts;
	}

	boolean isOpen() {
		return this.window != null && !this.window.isDisposed();
	}

	/**
	 * Opens the prompt over {@code owner}, the field holding {@code initial}.
	 *
	 * @param problems the problem of a name for the store, null when none
	 * @param acceptable whether OK applies to that name
	 * @param done called with the trimmed name when OK is pressed, after the prompt closed
	 */
	void open(UIWindow owner, String title, String initial, Function<String, AmpSoundStore.NameProblem> problems, Predicate<String> acceptable,
			Consumer<String> done) {
		close();
		this.window = this.factory.createWindow(owner, true, false);
		this.window.setText(title);
		UITableLayout layout = new UITableLayout();
		this.window.setLayout(layout);
		UILabel label = this.factory.createLabel(this.window);
		label.setText(this.texts.get(AmpTexts.SOUND_NAME));
		label.setToolTipText(this.texts.get(AmpTexts.SOUND_NAME + ".tip"));
		layout.set(label, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.name = this.factory.createTextField(this.window);
		this.name.setText(initial);
		layout.set(this.name, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(this.name, UITableLayout.PACKED_WIDTH, FIELD_WIDTH);
		this.problem = this.factory.createWrapLabel(this.window);
		layout.set(this.problem, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 3);
		layout.set(this.problem, UITableLayout.PACKED_WIDTH, PROBLEM_WIDTH);
		this.cancel = button(layout, 1, AmpTexts.NAME_CANCEL, () -> close());
		this.ok = button(layout, 2, AmpTexts.NAME_OK, () -> {
			String chosen = this.name.getText().trim();
			close();
			done.accept(chosen);
		});
		this.name.addModifyListener(new UIModifyListener() {
			public void onModify(UIModifyEvent event) {
				check(problems, acceptable);
			}
		});
		this.window.addDisposeListener(new UIDisposeListener() {
			public void onDispose(UIDisposeEvent event) {
				AmpNamePrompt.this.window = null;
			}
		});
		check(problems, acceptable);
		this.window.pack();
		UIRectangle over = owner.getBounds();
		UIRectangle size = this.window.getBounds();
		this.window.setBounds(new UIRectangle(over.getX() + (over.getWidth() - size.getWidth()) / 2f, over.getY() + 60f, size.getWidth(), size.getHeight()));
		this.window.open();
	}

	/** OK enabled for an accepted name; a refused name says why, with spec 022's messages. */
	private void check(Function<String, AmpSoundStore.NameProblem> problems, Predicate<String> acceptable) {
		String text = this.name.getText();
		AmpSoundStore.NameProblem found = problems.apply(text);
		String refused = found == AmpSoundStore.NameProblem.READY_MADE ? AmpTexts.NAME_READY_MADE
				: found == AmpSoundStore.NameProblem.TOO_LONG ? AmpTexts.NAME_TOO_LONG : null;
		this.problem.setText(refused == null ? "" : this.texts.get(refused, Integer.toString(AmpSoundStore.NAME_LENGTH)));
		this.ok.setEnabled(acceptable.test(text));
		this.window.layout();
	}

	private UIButton button(UITableLayout layout, int column, String key, Runnable action) {
		UIButton button = this.factory.createButton(this.window);
		button.setText(this.texts.get(key));
		button.setToolTipText(this.texts.get(key + ".tip"));
		button.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				action.run();
			}
		});
		layout.set(button, 3, column + 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		return button;
	}

	void close() {
		if (isOpen()) {
			this.window.dispose();
		}
	}

	UIWindow getWindow() {
		return this.window;
	}

	UITextField getName() {
		return this.name;
	}

	UILabel getProblem() {
		return this.problem;
	}

	UIButton getOk() {
		return this.ok;
	}

	UIButton getCancel() {
		return this.cancel;
	}
}
