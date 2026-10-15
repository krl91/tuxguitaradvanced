package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;

/** A small modal Yes / No question; the callback runs only on Yes. */
final class ConfirmWindow {

	private ConfirmWindow() {
	}

	static void ask(UIFactory factory, UIWindow parent, String title, String question, String yesText, String noText, Runnable yes) {
		final UIWindow window = parent == null ? factory.createWindow() : factory.createWindow(parent, true, false);
		window.setText(title);
		UITableLayout layout = new UITableLayout();
		window.setLayout(layout);
		UILabel label = factory.createWrapLabel(window);
		label.setText(question);
		layout.set(label, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		layout.set(label, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		UITableLayout row = new UITableLayout(0f);
		UIPanel buttons = factory.createPanel(window, false);
		buttons.setLayout(row);
		layout.set(buttons, 2, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, true, false);
		UIButton yesButton = factory.createButton(buttons);
		yesButton.setText(yesText);
		row.set(yesButton, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		yesButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				window.close();
				yes.run();
			}
		});
		UIButton noButton = factory.createButton(buttons);
		noButton.setText(noText);
		row.set(noButton, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, false, false);
		noButton.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				window.close();
			}
		});
		window.pack();
		window.open();
	}
}
