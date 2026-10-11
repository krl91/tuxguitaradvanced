package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * One stable colour per string (spec 003 FR-025, FR-067, visual-design.md): the same for the string's label, its incoming notes, trajectories, target
 * disks and the dots of a chord diagram, from the first frame to the impact. For six strings the owner's palette of 2026-10-11, low to high: red,
 * yellow, light blue, orange, green, violet (violet is the highest string). Other counts take distinct colours from the same family, always keeping red
 * for the lowest and violet for the highest string. The map is fixed for the run. Colours are plain RGB integers (0xRRGGBB); colour is never the only signal.
 */
public final class StringColours {

	public static final int RED = 0xE5453B;
	public static final int YELLOW = 0xF5E642;
	public static final int LIGHT_BLUE = 0x5CB8F0;
	public static final int ORANGE = 0xF58A2E;
	public static final int GREEN = 0x3DE0A0;
	public static final int VIOLET = 0x9B59D0;
	private static final int COBALT = 0x3B6FE0;
	private static final int MAGENTA = 0xE0479C;

	private static final int[] SIX = { RED, YELLOW, LIGHT_BLUE, ORANGE, GREEN, VIOLET };
	private static final int[] EIGHT = { RED, YELLOW, COBALT, LIGHT_BLUE, ORANGE, GREEN, MAGENTA, VIOLET };

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
