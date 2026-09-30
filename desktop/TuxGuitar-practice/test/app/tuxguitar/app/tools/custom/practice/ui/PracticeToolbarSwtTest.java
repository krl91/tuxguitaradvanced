package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.toolbar.UIToolBar;
import app.tuxguitar.ui.toolbar.UIToolCheckableItem;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UIWindow;

/**
 * Native SWT test of the transport-bar Practice button (spec 002, tasks T138): the real SWT toolkit
 * loads the icon of every skin and shows the button's enabled / checked state on a real tool bar
 * item. Needs a macOS graphical session and the main thread; runs only with the profile
 * {@code swt-gui-tests} (see {@link RecognitionSettingsSwtTest}). SWT is reached by reflection.
 */
@Tag("swt-gui")
public class PracticeToolbarSwtTest {

	private static final String[] SKINS = { "Breeze", "Breeze-dark", "Oxygen", "Oxygen-dark", "Symbolic", "Symbolic-dark" };

	@Test
	public void theIconLoadsInEverySkinAndTheItemShowsTheLauncherState() throws Exception {
		Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display");
		Object display = displayClass.getConstructor().newInstance();
		try {
			UIFactory factory = (UIFactory) Class.forName("app.tuxguitar.ui.swt.SWTFactory").getConstructor(displayClass).newInstance(display);
			UIWindow window = factory.createWindow();
			window.setLayout(new UITableLayout());
			UIPanel panel = factory.createPanel(window, false);
			panel.setLayout(new UITableLayout());
			((UITableLayout) window.getLayout()).set(panel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			UIToolBar toolBar = factory.createHorizontalToolBar(panel);
			((UITableLayout) panel.getLayout()).set(toolBar, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			UIToolCheckableItem item = toolBar.createCheckItem();

			for( String skin : SKINS ) {
				try( InputStream stream = Files.newInputStream(Paths.get("share/skins/" + skin + "/practice_toolbar.png")) ) {
					UIImage image = factory.createImage(stream);
					assertNotNull(image, skin);
					assertEquals(22f, image.getWidth(), skin);
					assertEquals(22f, image.getHeight(), skin);
					item.setImage(image);
				}
			}

			LauncherState inactive = LauncherState.of(false, true, true);
			item.setEnabled(inactive.isEnabled());
			item.setChecked(inactive.isChecked());
			assertTrue(item.isEnabled());
			assertFalse(item.isChecked());

			LauncherState active = LauncherState.of(true, true, true);
			item.setChecked(active.isChecked());
			assertTrue(item.isChecked());

			LauncherState unavailable = LauncherState.of(false, false, false);
			item.setChecked(unavailable.isChecked());
			item.setEnabled(unavailable.isEnabled());
			assertFalse(item.isEnabled());
			assertFalse(item.isChecked());
			item.setToolTipText(unavailable.tooltipKey("transport.practice"));
			assertEquals("transport.practice.no-song", item.getToolTipText());

			window.pack();
			window.dispose();
		} finally {
			displayClass.getMethod("dispose").invoke(display);
		}
	}
}
