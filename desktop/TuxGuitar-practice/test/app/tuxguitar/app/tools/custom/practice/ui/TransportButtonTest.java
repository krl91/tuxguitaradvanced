package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;

/**
 * What each button of the transport bar starts (owner, 2026-10-15: "the note button must start Wait for the right note directly, the coloured one Arcade
 * directly"). The note button used to open the window with the last mode chosen, which could be Follow or Arcade. The mapping is one place; the plugin and the window
 * read it, and the source is checked for that (the window itself is built by no test yet: its journey SP-02 and SP-03 of spec 011 will drive it).
 */
public class TransportButtonTest {

	private static String read(String name) throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/" + name);
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException(name + " not found");
	}

	@Test
	public void theNoteButtonStartsWaitForTheRightNoteAndTheColouredButtonStartsArcade() {
		assertEquals(FollowSettings.Mode.WAIT, TransportButton.PRACTICE.mode());
		assertEquals(FollowSettings.Mode.ARCADE, TransportButton.ARCADE.mode());
	}

	@Test
	public void thePluginSetsTheModeOfTheNoteButtonBeforeItOpensTheWindowAndTheArcadeStartReadsItsOwnMapping() throws IOException {
		String plugin = read("PracticePlugin.java");
		int toggle = plugin.indexOf("void toggleWindow(");
		int set = plugin.indexOf("TransportButton.PRACTICE.mode()", toggle);
		int open = plugin.indexOf("openAndStart()", toggle);
		assertTrue(set > toggle && open > set, "the note button sets Wait before the window opens and starts");
		String window = read("PracticeWindow.java");
		int arcade = window.indexOf("public void openAndStartArcade()");
		assertTrue(window.indexOf("TransportButton.ARCADE.mode()", arcade) > arcade, "the Arcade button sets Arcade from the same mapping");
	}
}
