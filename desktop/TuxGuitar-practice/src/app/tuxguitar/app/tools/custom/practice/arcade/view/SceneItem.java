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
		/** x (see {@link #align()}), middle y, font height in pixels. */
		TEXT
	}

	/** How a text sits on its x. */
	public enum Align {
		LEFT, CENTRE, RIGHT
	}

	public enum Role {
		FIELD_GUIDE, NECK_BODY, STRING, STRING_LABEL, FRET_WIRE, NUT, FRET_NUMBER, INLAY, HIT_LINE,
		MARKER, MARKER_FRET, CHORD_BAR, TRAJECTORY, TARGET_DISK, TARGET_OUTLINE,
		TIMING_BAR, STEM, EVENT_TEXT, CHORD_CHIP,
		CARTOUCHE_FRAME, CARTOUCHE_HEADER, CARTOUCHE_STRING, CARTOUCHE_FRET, CARTOUCHE_DOT, CARTOUCHE_MARK, CARTOUCHE_FRET_LABEL, CARTOUCHE_NAME, CARTOUCHE_STROKE,
		CARTOUCHE_NOTE,
		BADGE, BADGE_SYMBOL, BADGE_TEXT,
		HUD_STREAK, HUD_SPEED, HUD_NEXT,
		/** Decoration, dropped first when the frames cost too much: never information. */
		GLOW, TRAIL, SHADOW, TARGET_HALO;

		public boolean isDecoration() {
			return this == GLOW || this == TRAIL || this == SHADOW || this == TARGET_HALO;
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
