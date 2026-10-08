package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * One stable colour per string (spec 003 FR-025, visual-design.md): the same for the string's label, its incoming notes, trajectories, target rings and
 * the dots of a chord diagram, from the first frame to the impact. For six strings the approved palette, low to high: cobalt, cyan, teal, gold, violet,
 * magenta. Other counts take distinct colours from the same family, always keeping the low and the high end of the palette, so that a bass looks
 * like the ends of a guitar. The map is fixed for the run. Colours are plain RGB integers (0xRRGGBB); colour is never the only signal.
 */
public final class StringColours {

	public static final int COBALT = 0x3B6FE0;
	public static final int CYAN = 0x2CC4E0;
	public static final int TEAL = 0x1FA58F;
	public static final int GOLD = 0xE0A91F;
	public static final int VIOLET = 0x8C5CE0;
	public static final int MAGENTA = 0xE0479C;
	public static final int LIME = 0x8DC63F;
	public static final int ORANGE = 0xE86A2E;

	private static final int[] SIX = { COBALT, CYAN, TEAL, GOLD, VIOLET, MAGENTA };
	private static final int[] EIGHT = { COBALT, CYAN, TEAL, LIME, GOLD, ORANGE, VIOLET, MAGENTA };

	private final int[] lowToHigh;
	private final StringLayout layout;

	private StringColours(int[] lowToHigh) {
		this.lowToHigh = lowToHigh;
		this.layout = StringLayout.of(lowToHigh.length);
	}

	public static StringColours forStrings(int count) {
		if (count < 1) {
			throw new IllegalArgumentException("a track has at least one string: " + count);
		}
		int[] palette = (count <= 6 ? SIX : EIGHT);
		if (count > palette.length) {
			throw new IllegalArgumentException("no colour map for " + count + " strings");
		}
		int[] chosen = new int[count];
		for (int i = 0; i < count; i++) {
			int index = (count == 1 ? 0 : (int) Math.round(i * (palette.length - 1.0) / (count - 1.0)));
			chosen[i] = palette[index];
		}
		return new StringColours(chosen);
	}

	/** The colour of a TuxGuitar string number (1 is the highest string). */
	public int of(int tgString) {
		return this.lowToHigh[this.layout.rowOf(tgString)];
	}

	public int count() {
		return this.lowToHigh.length;
	}
}
