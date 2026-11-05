package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScrollBarPanel;
import app.tuxguitar.ui.widget.UITabFolder;
import app.tuxguitar.ui.widget.UITabItem;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the practice window's height (spec 025 T016, US2, FR-005, FR-006, SC-002): the real {@link PracticeLayout} sizes a real tab folder whose
 * tabs scroll, in a window built as the practice window is (the tabs, then the bottom row with the start button), and {@link WindowFit} fits it. A tab taller
 * than the screen leaves the window within 768 pixels (and within a smaller screen), the button row stays inside the window, and the tab's last control can
 * be scrolled into view. {@code PracticeWindow} itself needs the whole application and no test builds it; that it uses these pieces is checked on its source
 * by {@code PracticeWindowFitsSourceTest}. Runs only with the profile {@code swt-gui-tests}.
 */
@Tag("swt-gui")
public class PracticeWindowFitsSwtTest {

	private static final class Built {
		UIWindow window;
		UIButton start;
		UIScrollBarPanel tallScroll;
		UIPanel tallContent;
		UILabel last;
		List<UIPanel> tabs = new ArrayList<UIPanel>();
	}

	private static Built build(UIFactory factory, int rows) {
		Built built = new Built();
		// a child of a main window, as the practice window is
		built.window = factory.createWindow(factory.createWindow(), false, true);
		UITableLayout windowLayout = new UITableLayout();
		built.window.setLayout(windowLayout);
		UITabFolder folder = factory.createTabFolder(built.window, false);
		windowLayout.set(folder, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 520f, null, null);

		UITabItem tall = folder.createTab();
		tall.setText("Input");
		built.tallContent = PracticeLayout.scrollingTab(factory, tall);
		built.tallScroll = (UIScrollBarPanel) built.tallContent.getParent();
		UITableLayout tallLayout = new UITableLayout();
		built.tallContent.setLayout(tallLayout);
		for (int row = 1; row <= rows; row++) {
			UILabel label = factory.createLabel(built.tallContent);
			label.setText("Row " + row + " of the tall tab");
			tallLayout.set(label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			built.last = label;
		}
		built.tabs.add(built.tallContent);

		UITabItem small = folder.createTab();
		small.setText("Track");
		UIPanel smallContent = PracticeLayout.scrollingTab(factory, small);
		UITableLayout smallLayout = new UITableLayout();
		smallContent.setLayout(smallLayout);
		UILabel one = factory.createLabel(smallContent);
		one.setText("One line");
		smallLayout.set(one, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		built.tabs.add(smallContent);

		UIPanel buttons = factory.createPanel(built.window, false);
		UITableLayout buttonsLayout = new UITableLayout(0f);
		buttons.setLayout(buttonsLayout);
		windowLayout.set(buttons, 2, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, false);
		built.start = factory.createButton(buttons);
		built.start.setText("Start");
		buttonsLayout.set(built.start, 1, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_CENTER, false, false);
		return built;
	}

	@Test
	public void aTabTallerThanTheScreenScrollsAndTheWindowFits768() throws Exception {
		check(768f, 768f);
	}

	@Test
	public void aSmallerScreenIsFittedToo() throws Exception {
		check(600f, 600f);
	}

	@Test
	public void anUnknownScreenIsTakenAs768() throws Exception {
		check(0f, 768f);
	}

	private void check(float usable, float limit) throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			Built built = build(factory, 60);
			PracticeLayout.sizeFolder(built.window, built.window.getChildren().get(0), built.tabs, usable);
			WindowFit.refit(built.window, null);
			// opened as the practice window opens: refitted, packed, shown, refitted again (PracticeWindow.showWindow, TGDialogUtil.OPEN_STYLE_PACK)
			built.window.pack();
			built.window.open();
			flush(display);
			PracticeLayout.sizeFolder(built.window, built.window.getChildren().get(0), built.tabs, usable);
			WindowFit.refit(built.window, null);
			flush(display);

			float height = built.window.getBounds().getHeight();
			assertTrue(height <= limit, "the window is " + height + " high, more than " + limit);
			float buttonBottom = absoluteBottom(built.start);
			float windowBottom = built.window.getBounds().getY() + height;
			assertTrue(buttonBottom <= windowBottom, "the start button (bottom at " + buttonBottom + ") is inside the window (bottom at " + windowBottom + ")");

			// the tab's last row is out of view, and comes into view at the end of the scroll
			int maximum = built.tallScroll.getVScroll().getMaximum();
			assertTrue(maximum > 0, "the tall tab scrolls");
			built.tallScroll.getVScroll().setValue(maximum);
			built.tallScroll.layout();
			flush(display);
			float lastBottom = built.last.getBounds().getY() + built.last.getBounds().getHeight() + built.tallContent.getBounds().getY();
			float visible = built.tallScroll.getBounds().getHeight();
			assertTrue(lastBottom <= visible + 1f, "the last row (bottom at " + lastBottom + ") is in view (" + visible + ")");
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}

	/** The bottom of a control on the screen, read from SWT. */
	private static float absoluteBottom(Object uiControl) throws Exception {
		Object control = uiControl.getClass().getMethod("getControl").invoke(uiControl);
		Object size = control.getClass().getMethod("getSize").invoke(control);
		int height = size.getClass().getField("y").getInt(size);
		Class<?> pointClass = Class.forName("org.eclipse.swt.graphics.Point");
		Object origin = control.getClass().getMethod("toDisplay", int.class, int.class).invoke(control, 0, 0);
		return pointClass.getField("y").getInt(origin) + height;
	}

	private static void flush(Object display) throws Exception {
		Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
		for (int i = 0; i < 50 && (Boolean) readAndDispatch.invoke(display); i++) {
			// drain the pending events
		}
	}
}
