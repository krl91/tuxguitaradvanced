package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintEvent;
import app.tuxguitar.ui.event.UIPaintListener;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.ui.widget.UIContainer;

/**
 * The picture of the tablature of one sound (spec 013, FR-021): the six strings as horizontal lines, the thinnest (string 1) on top, and the fret number of
 * each note on its string, 0 for an open string; the notes of a chord are one above the other in the same column. Each string is cut around its number so that
 * the number can be read. It draws lines and numbers only, with the colours of the window.
 */
final class TabDiagram {

	static final int STRINGS = 6;
	static final float WIDTH = 300f;
	static final float HEIGHT = 120f;
	private static final float MARGIN = 20f;
	/** half of the gap cut in a string around its number */
	private static final float HALF_GAP = 14f;

	/** One note of the sound: its string (1, the thinnest, to 6) and its fret (0, open). */
	static final class Mark {
		private final int string;
		private final int fret;

		Mark(int string, int fret) {
			this.string = string;
			this.fret = fret;
		}

		int getString() {
			return this.string;
		}

		int getFret() {
			return this.fret;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Mark && ((Mark) other).string == this.string && ((Mark) other).fret == this.fret;
		}

		@Override
		public int hashCode() {
			return this.string * 100 + this.fret;
		}

		@Override
		public String toString() {
			return "string " + this.string + " fret " + this.fret;
		}
	}

	/** A number to write, centred on (x, y). */
	static final class Label {
		final String text;
		final float x;
		final float y;

		Label(String text, float x, float y) {
			this.text = text;
			this.x = x;
			this.y = y;
		}
	}

	/** What to draw: the pieces of the strings, as {x1, y1, x2, y2}, and the numbers. */
	static final class Layout {
		final List<float[]> lines = new ArrayList<float[]>();
		final List<Label> labels = new ArrayList<Label>();
	}

	static List<Mark> marks(List<CalibrationPiece.PieceNote> notes) {
		List<Mark> marks = new ArrayList<Mark>();
		for (CalibrationPiece.PieceNote note : notes) {
			marks.add(new Mark(note.getString(), note.getFret()));
		}
		return marks;
	}

	/** The vertical position of a string, 1 on top. */
	static float stringY(int string, float height) {
		float top = height * 0.12f;
		float step = height * 0.76f / (STRINGS - 1);
		return top + (string - 1) * step;
	}

	static Layout layout(float width, float height, List<Mark> marks) {
		Layout layout = new Layout();
		float centre = width / 2f;
		for (int string = 1; string <= STRINGS; string++) {
			float y = stringY(string, height);
			Mark here = null;
			for (Mark mark : marks) {
				if (mark.getString() == string) {
					here = mark;
				}
			}
			if (here == null) {
				layout.lines.add(new float[] { MARGIN, y, width - MARGIN, y });
			} else {
				layout.lines.add(new float[] { MARGIN, y, centre - HALF_GAP, y });
				layout.lines.add(new float[] { centre + HALF_GAP, y, width - MARGIN, y });
				layout.labels.add(new Label(Integer.toString(here.getFret()), centre, y));
			}
		}
		return layout;
	}

	/** Draws a layout on a painter. */
	static final class Drawing {
		private final UIFactory factory;
		private final Supplier<UIColor> colour;
		private final Supplier<UIFont> base;
		private UIFont font;
		private UIFont madeFrom;

		Drawing(UIFactory factory, UIColor colour) {
			this(factory, () -> colour, () -> null);
		}

		/** @param colour the foreground of the window (null: the painter's own); @param base the font of the window, from which the numbers' font is made */
		Drawing(UIFactory factory, Supplier<UIColor> colour, Supplier<UIFont> base) {
			this.factory = factory;
			this.colour = colour;
			this.base = base;
		}

		/** The numbers are larger than the text of the window; where a larger font cannot be made, the font of the window is used as it is. */
		private UIFont numbersFont() {
			UIFont window = this.base.get();
			if (window != this.madeFrom) {
				this.madeFrom = window;
				this.font = window;
				if (window != null && this.factory != null) {
					try {
						this.font = this.factory.createFont(window.getName(), window.getHeight() * 1.6f, true, false);
					} catch (RuntimeException exception) {
						this.font = window;
					}
				}
			}
			return this.font;
		}

		void draw(UIPainter painter, float width, float height, List<Mark> marks) {
			Layout layout = layout(width, height, marks);
			painter.setAntialias(true);
			UIColor colour = this.colour.get();
			if (colour != null) {
				painter.setForeground(colour);
			}
			painter.setLineWidth(1.5f);
			for (float[] line : layout.lines) {
				painter.initPath(UIPainter.PATH_DRAW);
				painter.moveTo(line[0], line[1]);
				painter.lineTo(line[2], line[3]);
				painter.closePath();
			}
			UIFont numbers = numbersFont();
			if (numbers != null) {
				painter.setFont(numbers);
			}
			for (Label label : layout.labels) {
				float textWidth = painter.getFMWidth(label.text);
				float textHeight = painter.getFMHeight();
				painter.drawString(label.text, label.x - textWidth / 2f, label.y - textHeight / 2f);
			}
		}
	}

	private final UICanvas canvas;
	private final Drawing drawing;
	private List<Mark> shown;

	TabDiagram(UIFactory factory, UIContainer parent) {
		this.canvas = factory.createCanvas(parent, false);
		this.drawing = new Drawing(factory, this.canvas::getFgColor, this.canvas::getFont);
		this.canvas.addPaintListener(new UIPaintListener() {
			public void onPaint(UIPaintEvent event) {
				List<Mark> marks = TabDiagram.this.shown;
				if (marks != null) {
					TabDiagram.this.drawing.draw(event.getPainter(), TabDiagram.this.canvas.getBounds().getWidth(), TabDiagram.this.canvas.getBounds().getHeight(), marks);
				}
			}
		});
		this.canvas.setVisible(false);
	}

	UICanvas canvas() {
		return this.canvas;
	}

	/** The sound shown, or null when the picture is hidden. */
	List<Mark> shown() {
		return this.shown;
	}

	/** Shows the sound, or hides the picture for null. */
	void show(List<Mark> marks) {
		boolean changed = marks == null ? this.shown != null : !marks.equals(this.shown);
		this.shown = marks;
		this.canvas.setVisible(marks != null);
		if (changed && marks != null) {
			this.canvas.redraw();
		}
	}
}
