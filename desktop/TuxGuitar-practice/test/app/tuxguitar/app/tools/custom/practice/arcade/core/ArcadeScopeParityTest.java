package app.tuxguitar.app.tools.custom.practice.arcade.core;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Spec 014 (T013, FR-004, SC-003): the permissiveness is Arcade's and only Arcade's. The relaxation is a pure function (the effective settings are never
 * changed in place, so what Follow, Wait, the tuner and the calibration read is the same at every position), and it is applied in one place of the source: the
 * Arcade start of the practice window. Source check, as the window itself is built by no test.
 */
public class ArcadeScopeParityTest {

	private static final Path SOURCES = Paths.get("src/app/tuxguitar/app/tools/custom/practice");

	private static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
	public void theRelaxationDoesNotChangeTheSettingsItIsGiven() {
		RecognitionSettings effective = RecognitionSettings.defaults();
		RecognitionSettings before = effective;
		for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
			ArcadeLeniency.apply(position, effective);
			assertEquals(before, effective, "settings are immutable: what the other modes read is unchanged after " + position);
			assertEquals(before.toString(), effective.toString());
		}
	}

	@Test
	public void theCallsThatRelaxTheSettingsAndTheWindowAreInTheArcadeStartAndNowhereElse() throws IOException {
		List<String> callers = new ArrayList<String>();
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
				String text = read(file);
				if (file.getFileName().toString().equals("ArcadeLeniency.java")) {
					continue;
				}
				if (text.contains("ArcadeLeniency.apply(") || text.contains("ArcadeLeniency.windowMillis(")) {
					callers.add(file.getFileName().toString());
				}
			}
		}
		assertEquals(List.of("PracticeWindow.java"), callers, "only the practice window applies the leniency");
		String window = read(SOURCES.resolve("ui/PracticeWindow.java"));
		String arcade = body(window, "private void startArcade(");
		assertTrue(arcade.contains("ArcadeLeniency.apply(") && arcade.contains("ArcadeLeniency.windowMillis("), "startArcade applies both");
		String outside = window.replace(arcade, "");
		assertFalse(outside.contains("ArcadeLeniency.apply(") || outside.contains("ArcadeLeniency.windowMillis("), "no other method of the window applies the leniency");
		for (String other : new String[] { "private void startFollow(", "private void startSession()", "private void startSessionNow(" }) {
			if (window.contains(other)) {
				assertFalse(body(window, other).contains("ArcadeLeniency"), other + " does not know the leniency");
			}
		}
	}

	@Test
	public void theArcadeStartReadsThePositionFromTheRememberedChoices() throws IOException {
		String arcade = body(read(SOURCES.resolve("ui/PracticeWindow.java")), "private void startArcade(");
		assertTrue(arcade.contains("ArcadeChoices.of(") && arcade.contains(".leniency()"), "the position is the one the player chose and that is remembered");
	}
}
