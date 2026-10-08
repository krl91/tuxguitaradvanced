package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** String numbers and physical rows (spec 003 T030, visual-design.md): string 1 of TuxGuitar is the high string, drawn at the top, one mapping function for all. */
public class StringLayoutTest {

	@Test
	public void theHighStringIsAtTheTopForFourSixAndSevenStrings() {
		for (int count : new int[] { 4, 6, 7 }) {
			StringLayout layout = StringLayout.of(count);
			assertEquals(count, layout.count());
			assertEquals(count - 1, layout.rowOf(1), "string 1 is the highest string, at the top");
			assertEquals(0, layout.rowOf(count), "the last string is the lowest, at the bottom");
		}
	}

	@Test
	public void theMappingIsItsOwnInverseAndNeverSwapsTwoStrings() {
		for (int count : new int[] { 4, 6, 7, 8 }) {
			StringLayout layout = StringLayout.of(count);
			boolean[] seen = new boolean[count];
			for (int string = 1; string <= count; string++) {
				int row = layout.rowOf(string);
				assertEquals(string, layout.tgStringAtRow(row));
				seen[row] = true;
			}
			for (boolean row : seen) {
				assertEquals(true, row, "every row is used once");
			}
		}
	}

	@Test
	public void aChordDiagramReadsFromTheLowStringOnTheLeftToTheHighOneOnTheRight() {
		StringLayout layout = StringLayout.of(6);
		int[] columns = new int[6];
		for (int string = 1; string <= 6; string++) {
			columns[string - 1] = layout.rowOf(string);
		}
		assertArrayEquals(new int[] { 5, 4, 3, 2, 1, 0 }, new int[] { columns[0], columns[1], columns[2], columns[3], columns[4], columns[5] });
		assertEquals(0, layout.diagramColumnOf(6), "low E at the left");
		assertEquals(5, layout.diagramColumnOf(1), "high e at the right");
	}

	@Test
	public void aStringOutsideTheTrackIsRefused() {
		StringLayout layout = StringLayout.of(4);
		assertThrows(IllegalArgumentException.class, () -> layout.rowOf(0));
		assertThrows(IllegalArgumentException.class, () -> layout.rowOf(5));
		assertThrows(IllegalArgumentException.class, () -> StringLayout.of(0));
	}
}
