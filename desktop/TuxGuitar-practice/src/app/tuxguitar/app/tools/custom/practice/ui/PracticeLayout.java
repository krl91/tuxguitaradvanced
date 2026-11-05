package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.List;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UIScrollBarPanelLayout;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIControl;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScrollBarPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * The height of the practice window's tabs (spec 025, FR-005, FR-006, clarification 4, data-model "Practice tab height"): the window fits a 768-pixel
 * screen, so the folder is as high as its tallest tab when that fits, otherwise what the screen leaves after the rest of the window, and the tab scrolls.
 */
final class PracticeLayout {

	/** The screen every window must fit (the owner's answer: a 768-pixel screen), taken when the screen is unknown or larger. */
	static final float REFERENCE_HEIGHT = 768f;

	/** What the folder adds around its tabs' content (the tab strip and the borders), as the window has always counted it. */
	static final float FOLDER_CHROME = 48f;

	/** What the folder adds to its widest tab. */
	static final float FOLDER_SIDES = 24f;

	/** The window's title bar, not in its packed size. */
	static final float TITLE_BAR = 28f;

	/** One row of controls and the tab strip: the folder never gets lower, however small the screen. */
	static final float MIN_TAB_HEIGHT = 120f;

	private PracticeLayout() {
	}

	/**
	 * The folder's height: the tallest tab when it fits, else what the usable height leaves after {@code others} (the title bar, the notice, the bottom
	 * area and the buttons), never below {@link #MIN_TAB_HEIGHT}; a usable height unknown (zero or less) or above the reference is taken as the reference.
	 */
	static float tabHeight(float tallest, float usable, float others) {
		float screen = (usable <= 0f || usable > REFERENCE_HEIGHT) ? REFERENCE_HEIGHT : usable;
		float room = Math.max(MIN_TAB_HEIGHT, screen - others);
		return Math.min(tallest, room);
	}

	/**
	 * A tab whose content scrolls when the folder is lower than it (the pattern of the Advanced tab, spec 019): a vertical scrolling panel in the tab, as wide as
	 * its content and asking for no height of its own, and the panel returned, where the tab's controls go.
	 */
	static UIPanel scrollingTab(UIFactory factory, UIContainer tab) {
		final UIScrollBarPanel scroll = factory.createScrollBarPanel(tab, true, false, false);
		scroll.setLayout(new UIScrollBarPanelLayout(false, true, true, false, false, true));
		scroll.getVScroll().setIncrement(16);
		scroll.getVScroll().addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				scroll.layout();
			}
		});
		return factory.createPanel(scroll, false);
	}

	/**
	 * Sets the folder's packed size: as wide as its widest tab, and as high as {@link #tabHeight} allows on a screen of {@code usable} pixels, with what the
	 * rest of the window takes measured on the window itself.
	 */
	static void sizeFolder(UIWindow window, UIControl folder, List<UIPanel> tabs, float usable) {
		float tallest = 0f;
		float widest = 0f;
		for (UIPanel tab : tabs) {
			if (!tab.isDisposed()) {
				tab.computePackedSize(null, null);
				tallest = Math.max(tallest, tab.getPackedSize().getHeight());
				widest = Math.max(widest, tab.getPackedSize().getWidth());
			}
		}
		UITableLayout layout = (UITableLayout) window.getLayout();
		float wanted = tallest + FOLDER_CHROME;
		layout.set(folder, UITableLayout.PACKED_WIDTH, widest + FOLDER_SIDES);
		layout.set(folder, UITableLayout.PACKED_HEIGHT, wanted);
		window.computePackedSize(null, null);
		float others = window.getPackedSize().getHeight() - wanted + TITLE_BAR;
		layout.set(folder, UITableLayout.PACKED_HEIGHT, tabHeight(wanted, usable, others));
	}
}
