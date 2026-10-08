package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One stable, distinct colour per string (spec 003 T032, FR-025, visual-design.md): low to high cobalt, cyan, teal, gold, violet, magenta. */
public class StringColoursTest {

	private static double distance(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return Math.sqrt(dr * dr + dg * dg + db * db);
	}

	@Test
	public void theSixStringPaletteIsTheApprovedOneFromTheLowStringToTheHighOne() {
		StringColours colours = StringColours.forStrings(6);

		assertEquals(StringColours.COBALT, colours.of(6), "low E");
		assertEquals(StringColours.CYAN, colours.of(5), "A");
		assertEquals(StringColours.TEAL, colours.of(4), "D");
		assertEquals(StringColours.GOLD, colours.of(3), "G");
		assertEquals(StringColours.VIOLET, colours.of(2), "B");
		assertEquals(StringColours.MAGENTA, colours.of(1), "high e");
	}

	@Test
	public void everyStringHasItsOwnColourForAnyStringCountAndTheyAreWellApart() {
		for (int count : new int[] { 4, 5, 6, 7, 8 }) {
			StringColours colours = StringColours.forStrings(count);
			for (int a = 1; a <= count; a++) {
				for (int b = a + 1; b <= count; b++) {
					assertNotEquals(colours.of(a), colours.of(b), count + " strings: " + a + " and " + b);
					assertTrue(distance(colours.of(a), colours.of(b)) >= 60.0, count + " strings: strings " + a + " and " + b + " are too close");
				}
			}
		}
	}

	@Test
	public void theColourOfAStringIsTheSameWheneverItIsAskedAndForTheSameTrack() {
		StringColours first = StringColours.forStrings(4);
		StringColours second = StringColours.forStrings(4);
		for (int string = 1; string <= 4; string++) {
			assertEquals(first.of(string), first.of(string));
			assertEquals(first.of(string), second.of(string));
		}
	}

	@Test
	public void aFourStringBassKeepsTheEndsOfTheSixStringPalette() {
		StringColours bass = StringColours.forStrings(4);
		StringColours guitar = StringColours.forStrings(6);
		assertEquals(guitar.of(6), bass.of(4), "the low string is the same colour");
		assertEquals(guitar.of(1), bass.of(1), "the high string is the same colour");
	}
}
