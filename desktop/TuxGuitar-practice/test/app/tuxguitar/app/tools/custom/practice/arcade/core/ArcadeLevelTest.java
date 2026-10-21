package app.tuxguitar.app.tools.custom.practice.arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * Spec 014 (T004, FR-006): the five levels of Arcade with the share of the notes and of the chords each keeps; a number outside 1 to 5 is brought to the
 * nearest level.
 */
public class ArcadeLevelTest {

	@Test
	public void theFiveLevelsKeepTheStatedSharesOfTheNotesAndOfTheChords() {
		int[][] expected = { { 1, 30, 0 }, { 2, 30, 30 }, { 3, 50, 50 }, { 4, 75, 75 }, { 5, 100, 100 } };
		for (int[] row : expected) {
			ArcadeLevel level = ArcadeLevel.of(row[0]);
			assertEquals(row[0], level.number());
			assertEquals(row[1], level.notePercent(), "notes of level " + row[0]);
			assertEquals(row[2], level.chordPercent(), "chords of level " + row[0]);
		}
	}

	@Test
	public void thereAreFiveLevelsInOrderAndTheLowestIsLevelOne() {
		assertEquals(5, ArcadeLevel.values().length);
		for (int i = 0; i < 5; i++) {
			assertEquals(i + 1, ArcadeLevel.values()[i].number());
		}
		assertSame(ArcadeLevel.of(1), ArcadeLevel.LOWEST);
		assertSame(ArcadeLevel.of(5), ArcadeLevel.HIGHEST);
	}

	@Test
	public void aNumberOutsideOneToFiveIsBroughtToTheNearestLevel() {
		assertSame(ArcadeLevel.of(1), ArcadeLevel.of(0));
		assertSame(ArcadeLevel.of(1), ArcadeLevel.of(-7));
		assertSame(ArcadeLevel.of(5), ArcadeLevel.of(6));
		assertSame(ArcadeLevel.of(5), ArcadeLevel.of(Integer.MAX_VALUE));
	}

	@Test
	public void levelFiveKeepsEverythingAndOnlyLevelFive() {
		for (ArcadeLevel level : ArcadeLevel.values()) {
			assertEquals(level == ArcadeLevel.HIGHEST, level.keepsEverything(), "level " + level.number());
		}
	}
}
