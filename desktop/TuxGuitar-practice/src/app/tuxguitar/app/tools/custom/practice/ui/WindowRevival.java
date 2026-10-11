package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Brings back a preparation window that was only hidden (it is hidden while an Arcade run goes on, and after a result was shown).
 * What this does is what {@code PracticeWindow} does when the learner opens the window again.
 */
final class WindowRevival {

	private WindowRevival() {
	}

	/**
	 * @param window the hidden window
	 * @param content the control that holds the tabs
	 * @param refresh recomposes the controls from the settings (they may have changed while the window was hidden)
	 * @param refit sizes the window for its content
	 */
	static void revive(UIWindow window, UIControl content, Runnable refresh, Runnable refit) {
		window.setVisible(true);
		window.moveToTop();
		// what the controls show is recomposed from the settings, then the window is sized for it and painted again
		refresh.run();
		refit.run();
		content.redraw();
		window.redraw();
	}
}
