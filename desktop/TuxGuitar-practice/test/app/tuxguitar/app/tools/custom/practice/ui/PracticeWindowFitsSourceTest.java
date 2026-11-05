package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The practice window uses the pieces that make it fit the screen (spec 025 T015, US2, FR-005, FR-006): every tab scrolls, and the folder is sized by
 * {@link PracticeLayout} from the usable height of the screen. The window is not built by any test, so this is checked on its source; the pieces themselves
 * are tested on SWT by {@code PracticeWindowFitsSwtTest}.
 */
public class PracticeWindowFitsSourceTest {

	private static String source() throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java");
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException("PracticeWindow.java not found");
	}

	private static String body(String text, String declaration) {
		int at = text.indexOf(declaration);
		assertTrue(at >= 0, declaration + " exists");
		int open = text.indexOf('{', at);
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			if (text.charAt(i) == '{') {
				depth++;
			} else if (text.charAt(i) == '}' && --depth == 0) {
				return text.substring(open, i + 1);
			}
		}
		throw new AssertionError("unbalanced " + declaration);
	}

	@Test
	public void everyTabScrolls() throws IOException {
		String tab = body(source(), "private UIPanel tab(");
		assertTrue(tab.contains("PracticeLayout.scrollingTab("), "a tab's panel is inside a scrolling panel");
		assertFalse(tab.contains("uiFactory.createPanel(item"), "no tab panel straight in the tab any more");
	}

	@Test
	public void theFolderIsSizedForTheScreen() throws IOException {
		String refit = body(source(), "private void refit()");
		assertTrue(refit.contains("PracticeLayout.sizeFolder("), "the folder's height comes from the practice layout");
		assertTrue(refit.contains("Screens.usableHeight("), "from the usable height of the screen");
		assertFalse(refit.contains("tallest + 48f"), "no longer the tallest tab, uncapped");
	}
}
