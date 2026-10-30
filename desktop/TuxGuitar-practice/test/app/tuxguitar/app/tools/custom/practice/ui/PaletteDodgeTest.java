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

	// ---- US2, the palette stays calm (T013)

	@Test
	public void itAvoidsTheCornerTheNotesAreHeadingTo() {
		// a tall, narrow score: the top left corner is the nearest, but the notes of the top line run towards it
		Box area = new Box(0, 100, 900, 1400);
		Box window = new Box(0, 60, 900, 1460);
		Box palette = PaletteDodge.place(PaletteDodge.Corner.TOP_RIGHT, area, new Box(0, 0, 360, 200));
		Box current = note(600, 150);
		Box next = note(540, 150);
		Decision decision = new PaletteDodge().decide(palette, window, area, current, next, "e1");
		assertTrue(decision.isMove());
		assertTrue(decision.corner() == PaletteDodge.Corner.BOTTOM_LEFT || decision.corner() == PaletteDodge.Corner.BOTTOM_RIGHT,
				"FR-003: not the corner the notes head to (" + decision.corner() + ")");
	}

	@Test
	public void itNeverGoesBackToThePlaceItLeftWhileTheNotesAreStillNearIt() {
		PaletteDodge dodge = new PaletteDodge();
		Box topLeft = PaletteDodge.place(PaletteDodge.Corner.TOP_LEFT, AREA, new Box(0, 0, 360, 200));
		Decision first = dodge.decide(topLeft, WINDOW, AREA, note(300, 300), null, "e1");
		assertTrue(first.isMove());
		assertTrue(first.corner() == PaletteDodge.Corner.BOTTOM_LEFT, "the nearest free corner: " + first.corner());
		// the notes come down under the new place, still within a palette width of the place it left
		Box bottomLeft = placed(topLeft, first);
		Decision second = dodge.decide(bottomLeft, WINDOW, AREA, note(300, 560), null, "e2");
		assertTrue(second.isMove());
		assertTrue(second.corner() != PaletteDodge.Corner.TOP_LEFT, "SC-002: never back to the place it left (" + second.corner() + ")");
	}

	@Test
	public void withoutAFreeCornerItTakesTheOneThatCoversTheLeastAndStaysThere() {
		Box area = new Box(0, 100, 400, 260);
		Box window = new Box(0, 60, 400, 300);
		Box palette = new Box(20, 130, 360, 200);
		Box current = new Box(0, 100, 200, 260);
		PaletteDodge dodge = new PaletteDodge();
		Decision first = dodge.decide(palette, window, area, current, null, "e1");
		assertTrue(first.isMove(), "FR-005: the place that covers the least");
		assertTrue(placed(palette, first).overlap(current) < palette.overlap(current));
		Box moved = placed(palette, first);
		assertFalse(dodge.decide(moved, window, area, current, null, "e1").isMove(), "and it stays");
		assertFalse(dodge.decide(moved, window, area, current, null, "e2").isMove(), "no back and forth on the next notes");
	}

	@Test
	public void oneMovePerFramedNoteUnlessTheScoreOrThePaletteChangesSize() {
		PaletteDodge dodge = new PaletteDodge();
		Decision first = dodge.decide(OPENING, WINDOW, AREA, note(900, 200), null, "e1");
		assertTrue(first.isMove());
		Box moved = placed(OPENING, first);
		Box underTheNewPlace = note(moved.x() + 10, moved.y() + 10);
		assertFalse(dodge.decide(moved, WINDOW, AREA, underTheNewPlace, null, "e1").isMove(), "FR-004: once for the same framed note");
		Box resized = new Box(0, 100, 1200, 650);
		assertTrue(dodge.decide(moved, WINDOW, resized, underTheNewPlace, null, "e1").isMove(), "a resized score allows a new move");
	}

	// ---- US3, the learner's place (T015)

	@Test
	public void aPlaceTheLearnerChoseIsKeptUntilItIsInTheWay() {
		PaletteDodge dodge = new PaletteDodge();
		assertFalse(dodge.decide(OPENING, WINDOW, AREA, note(100, 500), null, "e1").isMove());
		Box dragged = new Box(400, 300, 360, 200);
		assertFalse(dodge.decide(dragged, WINDOW, AREA, note(100, 700), null, "e1").isMove(), "US3/AC1: it stays where the learner put it");
		assertTrue(dodge.decide(dragged, WINDOW, AREA, note(500, 350), null, "e2").isMove(), "and moves aside when it is in the way");
	}

	@Test
	public void aPaletteTheLearnerPutOutsideTheMainWindowIsNeverMoved() {
		PaletteDodge dodge = new PaletteDodge();
		dodge.decide(OPENING, WINDOW, AREA, null, null, null);
		Box beside = new Box(1250, 140, 360, 200);
		assertFalse(dodge.decide(beside, WINDOW, AREA, note(1300, 200), null, "e1").isMove(), "FR-007: outside, never moved");
		assertFalse(dodge.decide(beside, WINDOW, AREA, note(1300, 200), null, "e2").isMove());
		Box backInside = new Box(816, 140, 360, 200);
		assertTrue(dodge.decide(backInside, WINDOW, AREA, note(900, 200), null, "e3").isMove(), "back inside by the learner: it moves aside again");
	}

	@Test
	public void aPaletteLeftOutsideBecauseTheMainWindowMovedStillMovesAside() {
		PaletteDodge dodge = new PaletteDodge();
		dodge.decide(OPENING, WINDOW, AREA, null, null, null);
		Box movedWindow = new Box(500, 60, 1200, 760);
		Box movedArea = new Box(500, 100, 1200, 700);
		assertTrue(dodge.decide(OPENING, movedWindow, movedArea, note(900, 200), null, "e1").isMove(), "untouched by the learner: not outside");
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
