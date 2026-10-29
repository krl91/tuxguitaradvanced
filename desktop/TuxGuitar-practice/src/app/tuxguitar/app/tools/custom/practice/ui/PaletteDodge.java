package app.tuxguitar.app.tools.custom.practice.ui;

/**
 * Where the practice palette goes so that it never hides the note to play (spec 017): pure, no UI, no clock. Given the palette's box, the main
 * window's, the score area's and the boxes of the current and the next notes to play (screen pixels), it says whether the palette stays or jumps to
 * one of the four corners of the score area (FR-002), ahead of time (the next note counts too), never back to the place it left while the notes are
 * still near it, at most once per framed note, and never when the learner put it outside the main window (FR-007). The caller applies the decision;
 * this object remembers what it decided (research R5, R6).
 */
public final class PaletteDodge {

	/** The period of the check while a run goes: a scroll, a resize or the first picture is caught within 0.3 s (SC-001, research R4). */
	public static final long CHECK_MILLIS = 100;

	/** A note box nearer than this to the palette counts as under it. */
	static final double MARGIN = 8.0;

	/** The distance from the edges of the score area to the palette in a corner. */
	static final double INSET = 16.0;

	/** A palette further than this from where it was put last was moved by the learner. */
	static final double MOVED = 2.0;

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}
	}

	/** A box on the screen; immutable. */
	public static final class Box {
		private final double x;
		private final double y;
		private final double width;
		private final double height;

		public Box(double x, double y, double width, double height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		public double x() {
			return this.x;
		}

		public double y() {
			return this.y;
		}

		public double width() {
			return this.width;
		}

		public double height() {
			return this.height;
		}

		double right() {
			return this.x + this.width;
		}

		double bottom() {
			return this.y + this.height;
		}

		double centreX() {
			return this.x + this.width / 2.0;
		}

		/** True when the other box comes within {@code margin} of this one. */
		boolean meets(Box other, double margin) {
			return other != null && other.x < right() + margin && other.right() > this.x - margin && other.y < bottom() + margin && other.bottom() > this.y - margin;
		}

		boolean inside(Box other) {
			return this.x >= other.x && this.y >= other.y && right() <= other.right() && bottom() <= other.bottom();
		}

		/** The area this box shares with the other. */
		double overlap(Box other) {
			if (other == null) {
				return 0.0;
			}
			double w = Math.min(right(), other.right()) - Math.max(this.x, other.x);
			double h = Math.min(bottom(), other.bottom()) - Math.max(this.y, other.y);
			return w > 0.0 && h > 0.0 ? w * h : 0.0;
		}

		/** The distance between two boxes, 0 when they meet. */
		double gap(Box other) {
			double dx = Math.max(0.0, Math.max(this.x - other.right(), other.x - right()));
			double dy = Math.max(0.0, Math.max(this.y - other.bottom(), other.y - bottom()));
			return Math.hypot(dx, dy);
		}

		boolean verticallyOverlaps(Box other) {
			return other != null && other.y < bottom() && other.bottom() > this.y;
		}

		boolean near(Box other, double tolerance) {
			return Math.abs(this.x - other.x) <= tolerance && Math.abs(this.y - other.y) <= tolerance;
		}

		boolean sameSize(Box other) {
			return other != null && this.width == other.width && this.height == other.height;
		}
	}

	/** Stay, or jump to a corner. */
	public static final class Decision {
		static final Decision STAY = new Decision(null, 0.0, 0.0);

		private final Corner corner;
		private final double x;
		private final double y;

		private Decision(Corner corner, double x, double y) {
			this.corner = corner;
			this.x = x;
			this.y = y;
		}

		public boolean isMove() {
			return this.corner != null;
		}

		public Corner corner() {
			return this.corner;
		}

		public double x() {
			return this.x;
		}

		public double y() {
			return this.y;
		}
	}

	/** Where the palette was put last by this object (the opening place first); a palette elsewhere was moved by the learner. */
	private Box lastAuto;
	/** The place the last move left, avoided while the framed note is near it. */
	private Box left;
	/** The framed note of the last move: one move per framed note. */
	private Object movedFor;
	private Box areaAtMove;
	private Box paletteAtMove;
	/** The learner's last move put the palette outside the main window. */
	private boolean outside;

	/**
	 * @param palette the palette's box
	 * @param window the main window's box, or null
	 * @param area the score area's box, or null when unknown (nothing is decided)
	 * @param current the box of the notes to play now, or null
	 * @param next the box of the next notes to play, or null (always null while the run is paused)
	 * @param event the identity of the framed note (one move per framed note)
	 */
	public Decision decide(Box palette, Box window, Box area, Box current, Box next, Object event) {
		if (palette == null) {
			return Decision.STAY;
		}
		if (this.lastAuto == null) {
			this.lastAuto = palette;
		} else if (!palette.near(this.lastAuto, MOVED)) {
			// the learner moved it: that is its place now, and outside the main window it is never moved
			this.lastAuto = palette;
			this.left = null;
			this.movedFor = null;
			this.outside = (window != null && !palette.inside(window));
		}
		if (this.outside || area == null) {
			return Decision.STAY;
		}
		boolean covered = palette.meets(current, MARGIN);
		boolean soon = palette.meets(next, MARGIN);
		if (!covered && !soon) {
			return Decision.STAY;
		}
		boolean resized = (this.areaAtMove != null && (!area.sameSize(this.areaAtMove) || !palette.sameSize(this.paletteAtMove)));
		if (event != null && event == this.movedFor && !resized) {
			return Decision.STAY;
		}
		Corner best = null;
		Box bestBox = null;
		int bestRank = Integer.MAX_VALUE;
		double bestDistance = Double.MAX_VALUE;
		for (Corner corner : Corner.values()) {
			Box box = place(corner, area, palette);
			if (box.near(palette, MOVED) || box.meets(current, MARGIN) || box.meets(next, MARGIN) || backToTheLeftPlace(box, current, palette)) {
				continue;
			}
			int rank = ahead(corner, box, current, next) ? 1 : 0;
			double distance = Math.hypot(box.x() - palette.x(), box.y() - palette.y());
			if (rank < bestRank || (rank == bestRank && distance < bestDistance)) {
				best = corner;
				bestBox = box;
				bestRank = rank;
				bestDistance = distance;
			}
		}
		if (best == null) {
			// no free corner: the one that covers the least of the note to play, and only if it covers less than here (FR-005)
			double least = palette.overlap(current);
			for (Corner corner : Corner.values()) {
				Box box = place(corner, area, palette);
				double covering = box.overlap(current);
				if (!box.near(palette, MOVED) && covering < least) {
					least = covering;
					best = corner;
					bestBox = box;
				}
			}
			if (best == null) {
				this.movedFor = event;
				return Decision.STAY;
			}
		}
		this.left = palette;
		this.lastAuto = bestBox;
		this.movedFor = event;
		this.areaAtMove = area;
		this.paletteAtMove = palette;
		return new Decision(best, bestBox.x(), bestBox.y());
	}

	/** The palette's box in that corner of the area, at {@link #INSET} from its edges. */
	static Box place(Corner corner, Box area, Box palette) {
		double x = corner.right() ? area.right() - INSET - palette.width() : area.x() + INSET;
		double y = (corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT) ? area.bottom() - INSET - palette.height() : area.y() + INSET;
		return new Box(x, y, palette.width(), palette.height());
	}

	/** The place just left, while the framed note is still within one palette width of it: never go back there (US2). */
	private boolean backToTheLeftPlace(Box box, Box current, Box palette) {
		return this.left != null && current != null && box.overlap(this.left) > 0.0 && this.left.gap(current) < palette.width();
	}

	/** A corner on the line of the notes, on the side they head to (FR-003). */
	private static boolean ahead(Corner corner, Box box, Box current, Box next) {
		if (current == null || next == null || !current.verticallyOverlaps(next) || !box.verticallyOverlaps(current)) {
			return false;
		}
		double heading = next.centreX() - current.centreX();
		return (heading > 0.0 && corner.right() && box.centreX() > current.centreX()) || (heading < 0.0 && !corner.right() && box.centreX() < current.centreX());
	}
}
