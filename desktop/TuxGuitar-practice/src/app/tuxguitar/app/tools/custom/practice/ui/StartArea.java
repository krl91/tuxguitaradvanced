package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Function;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * The line of a greyed start in the practice window's fixed bottom area, above the buttons, whatever the tab (spec 025, FR-002 to FR-004,
 * contracts/practice-window.md): it is a visible line and not a tooltip, since a disabled button shows no tooltip on macOS. Check again is offered
 * for the causes of the input level only.
 */
final class StartArea {

	/** The width the line wraps at, as the other lines of the window. */
	private static final float WRAP_WIDTH = InputSelectorPanel.WRAP_WIDTH;

	private final UIPanel panel;
	private final UILabel line;
	private final UIButton checkAgain;

	StartArea(UIFactory factory, UIContainer parent, Function<String, String> texts, Runnable checkAgainAction) {
		this.panel = factory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout(0f);
		this.panel.setLayout(layout);
		this.line = factory.createWrapLabel(this.panel);
		layout.set(this.line, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(this.line, UITableLayout.PACKED_WIDTH, WRAP_WIDTH);
		this.checkAgain = factory.createButton(this.panel);
		this.checkAgain.setText(texts.apply("practice.start.check-again"));
		this.checkAgain.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				checkAgainAction.run();
			}
		});
		layout.set(this.checkAgain, 1, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false);
		show(null);
	}

	UIPanel getControl() {
		return this.panel;
	}

	/** The line: the window writes the blocker's text in it. */
	UILabel getLine() {
		return this.line;
	}

	UIButton getCheckAgain() {
		return this.checkAgain;
	}

	/** Shows the line while the start is greyed (a blocker), with Check again when it applies; hides both when the start can be pressed. */
	void show(QuickStart.Blocker blocker) {
		this.line.setVisible(blocker != null);
		this.checkAgain.setVisible(blocker != null && blocker.checkAgain());
	}
}
