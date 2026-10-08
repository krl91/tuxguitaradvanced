package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * Where each string of a track is drawn (spec 003 FR-027, visual-design.md). TuxGuitar numbers the strings from 1, the highest, to N, the lowest; the
 * approved neck draws the low string at the bottom and a chord diagram reads from the low string on the left to the high one on the right. Every
 * conversion goes through this one class, so that no widget reverses the strings on its own.
 */
public final class StringLayout {

	private final int count;

	private StringLayout(int count) {
		this.count = count;
	}

	/** @param count the number of strings of the track (4 for a standard bass, 6 for a guitar...): the track's own definition, never an assumption */
	public static StringLayout of(int count) {
		if (count < 1) {
			throw new IllegalArgumentException("a track has at least one string: " + count);
		}
		return new StringLayout(count);
	}

	public int count() {
		return this.count;
	}

	/** The row of a string on the neck, counted from the bottom: 0 for the lowest string, {@code count - 1} for the highest (string 1). */
	public int rowOf(int tgString) {
		check(tgString);
		return this.count - tgString;
	}

	/** The TuxGuitar string number drawn at a row counted from the bottom. */
	public int tgStringAtRow(int row) {
		if (row < 0 || row >= this.count) {
			throw new IllegalArgumentException("no such row: " + row);
		}
		return this.count - row;
	}

	/** The column of a string in a chord diagram, counted from the left: the low string first. */
	public int diagramColumnOf(int tgString) {
		return rowOf(tgString);
	}

	private void check(int tgString) {
		if (tgString < 1 || tgString > this.count) {
			throw new IllegalArgumentException("string " + tgString + " is not one of the " + this.count + " strings of the track");
		}
	}
}
