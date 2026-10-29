package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.ui.PaletteDodge.Box;
import app.tuxguitar.app.tools.custom.practice.ui.PaletteDodge.Decision;

/** Spec 017: where the practice palette goes so that it never hides the note to play (T007, US1). */
public class PaletteDodgeTest {

	/** A main window of 1200 by 760 at (0, 60); the score area under the toolbars. */
	static final Box WINDOW = new Box(0, 60, 1200, 760);
	static final Box AREA = new Box(0, 100, 1200, 700);
	/** The palette at its opening place: the main window's top right, 24 from the right edge, 80 under the top. */
	static final Box OPENING = new Box(1200 - 360 - 24, 60 + 80, 360, 200);

	static Box note(double x, double y) {
		return new Box(x, y, 30, 40);
	}

	static Box placed(Box palette, Decision decision) {
		return new Box(decision.x(), decision.y(), palette.width(), palette.height());
	}

	static void assertInsideTheAreaAndFree(Box moved, Box current, Box next) {
		assertTrue(moved.x() >= AREA.x() + PaletteDodge.INSET - 1e-9 && moved.y() >= AREA.y() + PaletteDodge.INSET - 1e-9, "inside the score area, 16 from its edges");
		assertTrue(moved.right() <= AREA.right() - PaletteDodge.INSET + 1e-9 && moved.bottom() <= AREA.bottom() - PaletteDodge.INSET + 1e-9, "inside the score area, 16 from its edges");
		assertFalse(moved.meets(current, PaletteDodge.MARGIN), "the new place does not cover the note to play");
		assertFalse(moved.meets(next, PaletteDodge.MARGIN), "nor the next one");
	}

	@Test
	public void theNoteToPlayUnderThePaletteSendsItToAFreeCorner() {
		Box current = note(900, 200);
		Decision decision = new PaletteDodge().decide(OPENING, WINDOW, AREA, current, null, "e1");
		assertTrue(decision.isMove(), "FR-002: the palette moves aside");
		assertInsideTheAreaAndFree(placed(OPENING, decision), current, null);
	}

	@Test
	public void theNextNoteUnderThePaletteSendsItAsideAheadOfTime() {
		Box current = note(100, 500);
		Box next = note(900, 200);
		Decision decision = new PaletteDodge().decide(OPENING, WINDOW, AREA, current, next, "e1");
		assertTrue(decision.isMove(), "clarification 2026-10-30: ahead of time");
		assertInsideTheAreaAndFree(placed(OPENING, decision), current, next);
	}

	@Test
	public void nothingUnderThePaletteAndItStays() {
		assertFalse(new PaletteDodge().decide(OPENING, WINDOW, AREA, note(100, 500), note(200, 500), "e1").isMove(), "US1/AC4");
	}

	@Test
	public void aNoteWithinEightPixelsCountsAsUnderAndOneFurtherDoesNot() {
		double right = OPENING.right();
		assertTrue(new PaletteDodge().decide(OPENING, WINDOW, AREA, new Box(right + 7, 200, 10, 40), null, "e1").isMove(), "7 px away: under");
		assertFalse(new PaletteDodge().decide(OPENING, WINDOW, AREA, new Box(right + 9, 200, 10, 40), null, "e1").isMove(), "9 px away: not under");
	}

	@Test
	public void withoutTheScoreAreaOrWithoutANoteNothingIsDecided() {
		assertFalse(new PaletteDodge().decide(OPENING, WINDOW, null, note(900, 200), null, "e1").isMove(), "no screen mapping: nothing");
		assertFalse(new PaletteDodge().decide(OPENING, WINDOW, AREA, null, null, null).isMove(), "end of the run: nothing");
	}

	@Test
	public void theDodgeCanNeitherPauseTheRunNorChangeAVerdict() throws IOException {
		for (String name : new String[] { "PaletteDodge.java", "ScoreBoxes.java" }) {
			String source = read(name);
			assertFalse(source.contains("practice.session."), name + " uses nothing of the session (FR-006, SC-003)");
			assertFalse(source.contains("PracticeCommand"), name + " posts no command");
			assertFalse(source.contains(".post("), name + " posts nothing");
		}
	}

	@Test
	public void theCheckComesBackOftenEnoughToUncoverTheNoteWithinThreeTenthsOfASecond() {
		assertTrue(PaletteDodge.CHECK_MILLIS <= 100, "SC-001, research R4");
	}

	static String read(String name) throws IOException {
		for (String root : new String[] { "src", "TuxGuitar-practice/src", "desktop/TuxGuitar-practice/src" }) {
			Path path = Paths.get(root, "app/tuxguitar/app/tools/custom/practice/ui/" + name);
			if (Files.exists(path)) {
				return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			}
		}
		throw new IOException(name + " not found");
	}
}
