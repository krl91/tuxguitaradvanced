package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UIScrollBarPanelLayout;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIScrollBarPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * A window whose content is taller than the screen holds scrolls instead of growing past the screen (the preparation window of the practice, which the mode of
 * Arcade makes taller): on the real toolkit the window is no taller than its share of the reference area, and the scroll bar has something to scroll.
 */
@Tag("swt-gui")
public class WindowFitScrollSwtTest {

	@Test
	public void aTallPreparationScrollsAndTheWindowStaysWithinTheScreen() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			Class<?> environment = Class.forName("app.tuxguitar.ui.swt.SWTEnvironment");
			environment.getMethod("start", displayClass).invoke(environment.getMethod("getInstance").invoke(null), display);
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			UITableLayout windowLayout = new UITableLayout();
			window.setLayout(windowLayout);
			UIScrollBarPanel scroll = factory.createScrollBarPanel(window, true, false, false);
			scroll.setLayout(new UIScrollBarPanelLayout(false, true, true, false, false, true));
			windowLayout.set(scroll, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, 480f, 200f, null);
			UIPanel content = factory.createPanel(scroll, false);
			UITableLayout layout = new UITableLayout();
			content.setLayout(layout);
			for (int row = 1; row <= 40; row++) {
				UILabel label = factory.createLabel(content);
				label.setText("line " + row);
				layout.set(label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 1, null, 50f, null);
			}
			window.open();
			UIRectangle area = new UIRectangle(0, 0, 1400, 760);
			content.computePackedSize(null, null);
			assertTrue(content.getPackedSize().getHeight() > 1500, "the content is much taller than the screen: " + content.getPackedSize().getHeight());

			WindowFit.refit(window, area, content.getPackedSize());
			Method readAndDispatch = display.getClass().getMethod("readAndDispatch");
			for (int i = 0; i < 100 && (Boolean) readAndDispatch.invoke(display); i++) {
				// drain
			}

			assertTrue(window.getBounds().getHeight() <= area.getHeight() * WindowFit.SCREEN_SHARE + 1.0, "the window is not taller than the screen: " + window.getBounds().getHeight());
			assertTrue(window.getBounds().getHeight() > 300, "but it is not squeezed: " + window.getBounds().getHeight());
			assertTrue(scroll.getVScroll().getMaximum() > 500, "there is something to scroll: " + scroll.getVScroll().getMaximum());
			scroll.getVScroll().setValue(scroll.getVScroll().getMaximum());
			scroll.layout();
			assertEquals(scroll.getVScroll().getMaximum(), scroll.getVScroll().getValue(), "the scroll bar goes to the end");
			window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}
}
