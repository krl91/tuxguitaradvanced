package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The start path of the window, read from its source (spec 010 T022, invariants 1, 11, 13): the window is not built by any test, so the structure is
 * checked on the text. A new way to start a mode that skips the check, or a restart that asks again, fails here.
 */
public class PracticeWindowStartPathSourceTest {

	private static String source() throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java");
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException("PracticeWindow.java not found");
	}

	/** The body of a method: from the first brace after its declaration to the matching one. */
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
	public void startSessionOnlyAsksTheFlowAndTheRunIsStartedFromInsideItsCallback() throws IOException {
		String text = source();
		String start = body(text, "private void startSession()");
		assertTrue(start.contains(".begin("), "startSession goes through the start flow");
		for (String forbidden : new String[] { "startArcade(", "startFollow(", "new PracticeSession(" }) {
			assertFalse(start.contains(forbidden), "startSession must not start a mode itself: " + forbidden);
		}
		int begin = start.indexOf(".begin(");
		int calls = 0;
		Matcher matcher = Pattern.compile("startSessionNow\\(").matcher(text);
		while (matcher.find()) {
			calls++;
		}
		// the declaration and the one call inside the begin( of startSession
		assertEquals(2, calls, "startSessionNow is declared once and called once");
		assertTrue(start.indexOf("startSessionNow(", begin) > begin, "and the call is inside the begin( call");
		String now = body(text, "private void startSessionNow(");
		assertTrue(now.contains("startArcade(") && now.contains("startFollow(") && now.contains("new PracticeSession("), "the old body lives in startSessionNow");
	}

	@Test
	public void theCallersOfStartSessionAreTheThreeKnownOnes() throws IOException {
		String text = source();
		List<String> callers = new ArrayList<String>();
		Matcher matcher = Pattern.compile("(?<![A-Za-z])startSession\\(\\)").matcher(text);
		while (matcher.find()) {
			int line = text.substring(0, matcher.start()).split("\n", -1).length;
			callers.add(text.split("\n", -1)[line - 1].trim());
		}
		// the declaration, the Start button, the quick start and openAndStartArcade
		assertEquals(4, callers.size(), callers.toString());
	}

	@Test
	public void restartsAndReplaysNeverGoThroughTheStartFlow() throws IOException {
		String text = source();
		for (String method : new String[] { "private void restartArcade()", "private void restartArcadeFromStart()", "private void replayArcade(", "private void restartFollow()" }) {
			String body = body(text, method);
			assertFalse(body.contains("startFlow") || body.contains("startSession("), method + " does not ask again (FR-013)");
		}
	}

	@Test
	public void aPendingStartIsCancelledWhenTheWindowTheModeOrTheTrackChangeAndIgnoresTheTransportBar() throws IOException {
		String text = source();
		assertTrue(body(text, "private void modeChanged()").contains("startFlow.cancelPending()"));
		assertTrue(text.contains("PracticeWindow.this.startFlow.cancelPending()") || text.contains("this.startFlow.cancelPending();\n\t\t\t\t\tteardownSession"), "the window's dispose listener cancels");
		String arcade = body(text, "public void openAndStartArcade()");
		int pending = arcade.indexOf("startFlow.pending()");
		assertTrue(pending >= 0, "the transport-bar button checks for a pending start");
		assertTrue(pending < arcade.indexOf("setMode("), "before the mode is set");
		assertTrue(pending < arcade.indexOf("modeChanged()"), "and before modeChanged()");
		assertTrue(text.contains("this.startFlow.pending()"), "a lost input does not show the preparation over a pending start");
	}

	@Test
	public void theQuickStartWatchdogAndTheCancelUseTheRulesTestedInQuickStartTest() throws IOException {
		String text = source();
		String watch = body(text, "private void watchQuickStart()");
		assertTrue(watch.contains("QuickStart.showAfterPatience(") && watch.contains("this.startFlow.pending()"), "the watchdog asks the tested rule, with the pending start");
		String cancelled = body(text, "private void startCancelled()");
		assertTrue(cancelled.contains("QuickStart.closeAfterCancelledStart(") && cancelled.contains("this.disposing"), "a cancelled one-click start asks the tested rule");
	}
}
