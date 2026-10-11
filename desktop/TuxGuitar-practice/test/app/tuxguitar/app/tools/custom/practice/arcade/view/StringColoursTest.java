package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One stable, distinct colour per string (spec 003 T032, FR-025, visual-design.md): low to high red, yellow, light blue, orange, green, violet (FR-067, the owner's palette of 2026-10-11). */
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

		assertEquals(StringColours.RED, colours.of(6), "low E");
		assertEquals(StringColours.YELLOW, colours.of(5), "A");
		assertEquals(StringColours.LIGHT_BLUE, colours.of(4), "D");
		assertEquals(StringColours.ORANGE, colours.of(3), "G");
		assertEquals(StringColours.GREEN, colours.of(2), "B");
		assertEquals(StringColours.VIOLET, colours.of(1), "high e: violet is the highest string");
		assertEquals(0xE5453B, StringColours.RED);
		assertEquals(0xF5E642, StringColours.YELLOW);
		assertEquals(0x5CB8F0, StringColours.LIGHT_BLUE);
		assertEquals(0xF58A2E, StringColours.ORANGE);
		assertEquals(0x3DE0A0, StringColours.GREEN);
		assertEquals(0x9B59D0, StringColours.VIOLET);
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
		assertEquals(StringColours.RED, bass.of(4), "red is the lowest string of any track");
		assertEquals(StringColours.VIOLET, bass.of(1), "violet is the highest string of any track");
	}

	@Test
	public void redIsAlwaysTheLowestStringAndVioletTheHighestWhateverTheStringCount() {
		for (int count : new int[] { 4, 5, 6, 7, 8 }) {
			StringColours colours = StringColours.forStrings(count);
			assertEquals(StringColours.RED, colours.of(count), count + " strings: the lowest is red");
			assertEquals(StringColours.VIOLET, colours.of(1), count + " strings: the highest is violet");
		}
	}
}
