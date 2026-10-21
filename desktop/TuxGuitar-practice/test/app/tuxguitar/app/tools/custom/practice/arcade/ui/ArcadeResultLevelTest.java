package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;

/**
 * Spec 014 (T031, FR-010): the result names the level and the leniency position used, so that two runs can be compared fairly. The lines are built from the two
 * choices and the texts; the window shows them first (its overload) and the practice window gives it the choices of the run that ended.
 */
public class ArcadeResultLevelTest {

	private static final ArcadeTexts KEYS = (key, arguments) -> key + (arguments.length == 0 ? "" : java.util.Arrays.toString(arguments));

	@Test
	public void theTwoLinesNameTheLevelWithItsSharesAndThePosition() {
		List<ArcadeResultText.Line> lines = ArcadeResultText.settings(ArcadeLevel.of(3), ArcadeLeniencyPosition.FUN, KEYS);
		assertEquals(2, lines.size());
		assertEquals("arcade.result.level", lines.get(0).label());
		assertEquals("arcade.result.level.value[3, 50, 50]", lines.get(0).value(), "the number and the two shares of the level");
		assertEquals("arcade.result.leniency", lines.get(1).label());
		assertEquals("arcade.leniency.fun", lines.get(1).value());
	}

	@Test
	public void everyLevelAndEveryPositionHasItsLine() {
		for (ArcadeLevel level : ArcadeLevel.values()) {
			for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
				List<ArcadeResultText.Line> lines = ArcadeResultText.settings(level, position, KEYS);
				assertTrue(lines.get(0).value().contains(Integer.toString(level.number())), level + " " + position);
				assertTrue(lines.get(1).value().endsWith(position.name().toLowerCase(java.util.Locale.ROOT)));
			}
		}
	}

	@Test
	public void thePracticeWindowGivesTheResultWindowTheChoicesOfTheRunThatEnded() throws IOException {
		Path path = Paths.get("src/app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java");
		if (!Files.exists(path)) {
			path = Paths.get("desktop/TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/ui/PracticeWindow.java");
		}
		String window = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		int at = window.indexOf("private void arcadeEnded(");
		String ended = window.substring(at, window.indexOf("\n	}\n", at));
		assertTrue(ended.contains("ArcadeResultText.settings(this.arcadeLevel, this.arcadeLeniency"), "the level and the position of the run that ended, kept in fields at its start");
		assertTrue(window.contains("this.arcadeLeniency = leniency;"), "the position is kept when the run starts");
	}
}
