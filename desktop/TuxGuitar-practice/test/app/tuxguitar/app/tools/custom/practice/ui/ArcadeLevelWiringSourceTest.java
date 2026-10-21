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
 * Spec 014 (T020, T021, FR-006, FR-008, analysis I1, research R8-bis): where the level is applied in the practice window, read from its source (the window is built
 * by no test). The filter runs once, on the fresh-start path ({@code startSessionNow}), on the prepared sequence, which itself stays unfiltered so that a new
 * start at another level filters the original; the whole piece kept for the replays is the filtered one; {@code startArcade}, the replays and the restarts never
 * filter, because they are given the sequence that was already filtered.
 */
public class ArcadeLevelWiringSourceTest {

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
	public void theFilterIsAppliedOnTheFreshStartPathWithTheLevelThePlayerChose() throws IOException {
		String now = body(source(), "private void startSessionNow(");
		assertTrue(now.contains("ArcadeLevelFilter.apply("), "the fresh start filters");
		assertTrue(now.contains("ArcadeChoices.of(") && now.contains(".level()"), "with the level that is remembered (the start window stored the player's choice)");
		assertTrue(now.contains("startArcade("), "and still starts the run from there (PracticeWindowStartPathSourceTest)");
	}

	@Test
	public void startArcadeTheReplaysAndTheRestartsNeverFilterBecauseTheyAreGivenTheFilteredSequence() throws IOException {
		String text = source();
		for (String method : new String[] { "private void startArcade(", "private void replayArcade(", "private void restartArcade()", "private void restartArcadeFromStart()",
				"private void arcadeEnded(" }) {
			assertFalse(body(text, method).contains("ArcadeLevelFilter"), method + " must not filter a second time");
		}
	}

	@Test
	public void thePreparedSequenceStaysUnfilteredAndTheWholeKeptForTheReplaysIsTheFilteredOne() throws IOException {
		String text = source();
		for (String line : text.split("\n")) {
			if (line.contains("this.preparedSequence =")) {
				assertFalse(line.contains("ArcadeLevelFilter"), "the prepared sequence is never replaced by a filtered one: " + line.trim());
			}
		}
		boolean wholeIsFiltered = false;
		for (String line : text.split("\n")) {
			if (line.contains("this.arcadeWhole =") && line.contains("ArcadeLevelFilter")) {
				wholeIsFiltered = true;
			}
		}
		assertTrue(wholeIsFiltered, "the whole piece the replays start from is filtered at the same level as the run");
	}
}
