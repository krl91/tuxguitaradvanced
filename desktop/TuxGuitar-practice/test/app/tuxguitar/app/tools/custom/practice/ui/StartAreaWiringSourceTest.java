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
 * The wiring of spec 025's US1, read from the source (the practice window is not built by any test): the line of a greyed start lives in the
 * window's bottom area, not in a tab; it follows the blocker rule and a running check; the level panel asks the re-check rule on each frame, and
 * the window tells it whether a run is going (FR-001 to FR-004, FR-013).
 */
public class StartAreaWiringSourceTest {

	private static String source(String file) throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/" + file);
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException(file + " not found");
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
	public void theLineIsInTheWindowsBottomAreaNotInATab() throws IOException {
		String text = source("PracticeWindow.java");
		assertTrue(text.contains("new StartArea(uiFactory, this.window,"), "the start area is a child of the window");
		assertTrue(text.contains("this.startLabel = this.startArea.getLine();"), "the start line is the area's line");
		String preparation = body(text, "private UITabFolder createPreparation(");
		assertFalse(preparation.contains("this.startLabel = "), "no start line in a tab any more");
	}

	@Test
	public void theLineFollowsTheBlockerRuleAndARunningCheck() throws IOException {
		String update = body(source("PracticeWindow.java"), "private void updateStartButton()");
		assertTrue(update.contains("QuickStart.blocker("), "the tested rule of the cause");
		assertTrue(update.contains("this.engine.isLevelCheckRunning()"), "a running check is said");
		assertTrue(update.contains("this.startArea.show("), "the line and Check again follow it");
	}

	@Test
	public void theLevelPanelAsksTheRecheckRuleWithTheRunState() throws IOException {
		String panel = source("LevelPanel.java");
		assertTrue(body(panel, "private void frameReceived(").contains("LevelRecheck.shouldCheck("), "each frame asks the rule");
		assertTrue(source("PracticeWindow.java").contains("this.levelPanel.setRunGoing("), "the window says when a run is going");
	}
}
