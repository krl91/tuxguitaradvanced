package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * One shape or text of a scene, with what it is for. The scene is a plain list of these in paint order: the canvas only replays it, the tests read it,
 * nothing in it depends on a toolkit. Immutable.
 */
public final class SceneItem {

	public enum Shape {
		/** x1, y1, x2, y2. */
		LINE,
		/** Four corners, x1, y1 ... x4, y4. */
		QUAD,
		/** Centre x, centre y, radius x, radius y; filled. */
		DISC,
		/** Centre x, centre y, radius x, radius y; only the outline. */
		RING,
		/** Centre x, centre y, half width, half height; a filled rounded rectangle (FR-070). */
		ROUNDED,
		/** Centre x, centre y, half width, half height; only the outline of a rounded rectangle. */
		ROUNDED_RING,
		/** x0, y0, the two control points, x1, y1: a cubic curve, only the stroke (the arrow of a bend, FR-077). */
		CURVE,
		/** x (see {@link #align()}), middle y, font height in pixels. */
		TEXT
	}

	/** How a text sits on its x. */
	public enum Align {
		LEFT, CENTRE, RIGHT
	}

	public enum Role {
		FIELD_GUIDE, NECK_BODY, STRING, STRING_LABEL, FRET_WIRE, NUT, FRET_NUMBER, INLAY, HIT_LINE,
		MARKER, MARKER_FRET, TRAJECTORY, TARGET_RECT, TARGET_OUTLINE,
		TIMING_BAR, EVENT_TEXT,
		/** The open string (fret 0): a horizontal bar across the lanes, its bright cap at the left end and its word (FR-069). */
		OPEN_BAR, OPEN_CAP, OPEN_LABEL,
		/** The thread and the footprint of a fretted marker (FR-071), the highlighted number of the active fret on the ruler, the approach band (FR-072). */
		MARKER_THREAD, MARKER_FOOTPRINT, FRET_RULER_ACTIVE, APPROACH_BAND, NECK_LANE,
		/** The chord panel (FR-074): the translucent body, its luminous edge, a rounded rectangle per fretted position, a line per open string, the fret on a rectangle of the active chord, the name above the left corner, the arrow of the stroke and the remark. */
		CHORD_PANEL, CHORD_PANEL_EDGE, PANEL_NOTE, PANEL_OPEN_LINE, PANEL_FRET, PANEL_NAME, PANEL_STROKE, PANEL_REMARK,
		/** The bend of a note (FR-077): the dashed arrow, its head, the dashed ghost of the cell the bend reaches on the neck, the amount. */
		BEND_ARROW, BEND_HEAD, BEND_GHOST, BEND_LABEL,
		/** The technique marks of a note (FR-078): the dashed arc, its head, the dashed ghost of the destination cell, the dashed rail of a slide, the letters, the pill behind P.M. and T, the wave of a vibrato, the strokes of a tremolo picking. */
		TECH_ARROW, TECH_HEAD, TECH_GHOST, TECH_RAIL, TECH_LABEL, TECH_PILL, TECH_WAVE, TECH_TICK,
		BADGE, BADGE_SYMBOL, BADGE_TEXT,
		HUD_STREAK, HUD_SPEED, HUD_NEXT,
		/** Decoration, dropped first when the frames cost too much: never information. */
		GLOW, TRAIL, SHADOW, TARGET_HALO,
		/** The stage flames behind the picture (spec 009): decoration, never information. */
		FLAME_TONGUE, FLAME_EMBER;

		public boolean isDecoration() {
			return this == GLOW || this == TRAIL || this == SHADOW || this == TARGET_HALO || this == APPROACH_BAND
					|| this == FLAME_TONGUE || this == FLAME_EMBER;
		}
	}

	private final Shape shape;
	private final Role role;
	private final double[] values;
	private final int colour;
	private final double alpha;
	private final double lineWidth;
	private final String text;
	private final int eventIndex;
	private final int tgString;
	private final Align align;

	SceneItem(Shape shape, Role role, double[] values, int colour, double alpha, double lineWidth, String text, int eventIndex, int tgString, Align align) {
		this.shape = shape;
		this.role = role;
		this.values = values;
		this.colour = colour;
		this.alpha = alpha;
		this.lineWidth = lineWidth;
		this.text = text;
		this.eventIndex = eventIndex;
		this.tgString = tgString;
		this.align = align;
	}

	public Shape shape() {
		return this.shape;
	}

	public Role role() {
		return this.role;
	}

	public double value(int index) {
		return this.values[index];
	}

	public int valueCount() {
		return this.values.length;
	}

	/** The colour as 0xRRGGBB. */
	public int colour() {
		return this.colour;
	}

	/** 0 (invisible) to 1 (opaque). */
	public double alpha() {
		return this.alpha;
	}

	public double lineWidth() {
		return this.lineWidth;
	}

	/** The text of a TEXT item, or null. */
	public String text() {
		return this.text;
	}

	/** The index of the event this belongs to, or -1 for the neck and the HUD. */
	public int eventIndex() {
		return this.eventIndex;
	}

	/** The TuxGuitar string number this is tinted for, or 0. */
	public int tgString() {
		return this.tgString;
	}

	public Align align() {
		return this.align;
	}

	/** The colour channels equal (0xRRGGBB with R = G = B). */
	public boolean isGrey() {
		return ((this.colour >> 16) & 0xFF) == ((this.colour >> 8) & 0xFF) && ((this.colour >> 8) & 0xFF) == (this.colour & 0xFF);
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(this.shape + ":" + this.role);
		for (double value : this.values) {
			text.append(' ').append(String.format("%.1f", value));
		}
		if (this.text != null) {
			text.append(" \"").append(this.text).append('"');
		}
		return text.append(String.format(" #%06X a%.2f e%d s%d", this.colour, this.alpha, this.eventIndex, this.tgString)).toString();
	}
}
