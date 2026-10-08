package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The traditional chord diagram attached to the timing bar of a chord (spec 003 FR-028, visual-design.md): strings vertical from the low string on the
 * left to the high one on the right, frets horizontal, an open string marked above, a muted string marked only when the score says so, one dot per
 * written position. It answers <b>what</b>; the bar answers <b>when</b>; the verdict says what was heard. Positions are the tablature's instructions:
 * nothing here says that the audio verified a string or a fingering, and no finger number or chord name is made up.
 */
public final class ChordCartouche {

	/** What is shown above a string of the diagram. */
	public enum Mark {
		NONE,
		/** The string is played open (fret 0 written). */
		OPEN,
		/** The score writes a muted note on the string. */
		DEAD
	}

	/** A dot of the diagram: a string, its fret, and where it falls in the diagram. */
	public static final class Dot {
		private final int column;
		private final int row;
		private final int tgString;
		private final int fret;

		Dot(int column, int row, int tgString, int fret) {
			this.column = column;
			this.row = row;
			this.tgString = tgString;
			this.fret = fret;
		}

		/** From the left, 0 being the lowest string. */
		public int column() {
			return this.column;
		}

		/** From the top, 0 being the first fret of the window. */
		public int row() {
			return this.row;
		}

		public int tgString() {
			return this.tgString;
		}

		public int fret() {
			return this.fret;
		}
	}

	/** The number of fret spaces shown at least. */
	public static final int MIN_ROWS = 4;
	/** The smallest size the diagram keeps on the screen, in pixels, whatever the distance: perspective may shrink it, not below this. */
	public static final double MIN_WIDTH_PX = 54.0;
	public static final double MIN_HEIGHT_PX = 66.0;

	private final int strings;
	private final int firstFret;
	private final int rows;
	private final String name;
	private final ArcadeRenderEvent.Stroke stroke;
	private final boolean notCheckedSeparately;
	private final List<Dot> dots;
	private final Mark[] marks;

	private ChordCartouche(int strings, int firstFret, int rows, String name, ArcadeRenderEvent.Stroke stroke, boolean notCheckedSeparately, List<Dot> dots,
			Mark[] marks) {
		this.strings = strings;
		this.firstFret = firstFret;
		this.rows = rows;
		this.name = name;
		this.stroke = stroke;
		this.notCheckedSeparately = notCheckedSeparately;
		this.dots = Collections.unmodifiableList(dots);
		this.marks = marks;
	}

	public static ChordCartouche of(ArcadeRenderEvent chord, StringLayout layout) {
		int minFretted = Integer.MAX_VALUE;
		int maxFretted = 0;
		for (ArcadeRenderEvent.Position position : chord.positions()) {
			if (position.fret() > 0) {
				minFretted = Math.min(minFretted, position.fret());
				maxFretted = Math.max(maxFretted, position.fret());
			}
		}
		int first = 1;
		int rows = MIN_ROWS;
		if (maxFretted > 0) {
			first = (maxFretted <= MIN_ROWS ? 1 : minFretted);
			rows = Math.max(MIN_ROWS, maxFretted - first + 1);
			// the first fret written with the chord is a hint: used when it fits the written positions and the strings of the track
			int hint = chord.metadataFirstFret();
			if (hint > 0 && chord.metadataStrings() == layout.count() && hint <= minFretted && maxFretted <= hint + Math.max(rows, MIN_ROWS) - 1) {
				first = hint;
				rows = Math.max(MIN_ROWS, maxFretted - first + 1);
			}
		}
		List<Dot> dots = new ArrayList<Dot>();
		Mark[] marks = new Mark[layout.count()];
		java.util.Arrays.fill(marks, Mark.NONE);
		for (ArcadeRenderEvent.Position position : chord.positions()) {
			int column = layout.diagramColumnOf(position.tgString());
			if (position.fret() == 0) {
				marks[column] = Mark.OPEN;
			} else {
				dots.add(new Dot(column, position.fret() - first, position.tgString(), position.fret()));
			}
		}
		for (int dead : chord.deadStrings()) {
			if (dead >= 1 && dead <= layout.count()) {
				marks[layout.diagramColumnOf(dead)] = Mark.DEAD;
			}
		}
		return new ChordCartouche(layout.count(), first, rows, chord.chordName(), chord.stroke(), chord.hasDuplicatePitches(), dots, marks);
	}

	/** The number of vertical string lines: the track's own. */
	public int strings() {
		return this.strings;
	}

	/** The fret of the first row of the diagram. */
	public int firstFret() {
		return this.firstFret;
	}

	/** The number of fret spaces. */
	public int rows() {
		return this.rows;
	}

	/** The label at the side when the diagram does not start at the first fret ("5fr"), or null. */
	public String fretLabel() {
		return this.firstFret > 1 ? this.firstFret + "fr" : null;
	}

	/** The name written with the chord, or null: the view then shows a generic word, never a guessed name. */
	public String name() {
		return this.name;
	}

	public ArcadeRenderEvent.Stroke stroke() {
		return this.stroke;
	}

	/** True when two written notes share a pitch: the chord is said to be "not checked separately", never verified note by note. */
	public boolean notCheckedSeparately() {
		return this.notCheckedSeparately;
	}

	public List<Dot> dots() {
		return this.dots;
	}

	/** The mark above the string of a diagram column. */
	public Mark markAt(int column) {
		return this.marks[column];
	}
}
