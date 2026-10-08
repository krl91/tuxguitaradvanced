package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The projection of the model onto the screen (spec 003 FR-025, visual-design.md). The toolkit paints 2D paths and has no depth, so the depth is
 * computed here: the neck is in the lower foreground and its far edge carries the fixed hit line; an event comes from the horizon, perpendicular to the
 * strings, and is at the hit line exactly when its progress is 1. Distance shrinks what is drawn towards the middle of the screen. Everything is a
 * function of the viewport, the camera and the progress: no state, no accumulation, so pause, restart and a change of speed cannot drift.
 */
public final class IsometricProjection {

	/** The share of the height at which the hit line lies, and the horizon, and the near edge of the neck. */
	static final double HIT_LINE = 0.60;
	static final double HORIZON = 0.14;
	static final double NECK_NEAR_EDGE = 0.90;
	/** The scale of what is drawn at the horizon. */
	static final double FAR_SCALE = 0.35;
	/** The margin left and right of the shown part of the neck, as a share of the width. */
	static final double SIDE_MARGIN = 0.05;

	private final Viewport viewport;

	public IsometricProjection(Viewport viewport) {
		this.viewport = viewport;
	}

	public Viewport viewport() {
		return this.viewport;
	}

	public double hitLineY() {
		return this.viewport.height() * HIT_LINE;
	}

	public double horizonY() {
		return this.viewport.height() * HORIZON;
	}

	/** The screen x of a position along the neck, on the neck plane and on the hit line, for the camera. */
	public double neckX(double xMm, Camera camera) {
		double margin = this.viewport.width() * SIDE_MARGIN;
		double usable = this.viewport.width() - 2.0 * margin;
		return margin + (xMm - camera.left()) / camera.span() * usable;
	}

	/** The screen y of a string row on the neck (row 0 is the lowest string, at the bottom; the highest is nearest the hit line). */
	public double stringY(int row, int stringCount) {
		double top = this.viewport.height() * (HIT_LINE + 0.05);
		double bottom = this.viewport.height() * (NECK_NEAR_EDGE - 0.03);
		if (stringCount == 1) {
			return (top + bottom) / 2.0;
		}
		return top + (stringCount - 1 - row) * (bottom - top) / (stringCount - 1);
	}

	/** A point on the neck plane: the column of a position along the neck and the row of a string. */
	public ScreenPoint neck(double xMm, int row, int stringCount, Camera camera) {
		return new ScreenPoint(neckX(xMm, camera), stringY(row, stringCount), 1.0);
	}

	/**
	 * A point of an event on its way: the column of the fret it targets and its progress, 0 at the horizon and exactly 1 at the hit line. Between the
	 * two, the distance to the hit line is proportional to the time left, and the scale falls linearly with it.
	 */
	public ScreenPoint approach(double xMm, double progress, Camera camera) {
		double q = depthShare(progress);
		double away = 1.0 - q;
		double scale = 1.0 - (1.0 - FAR_SCALE) * away;
		double y = hitLineY() - (hitLineY() - horizonY()) * away;
		double centre = this.viewport.width() / 2.0;
		double x = centre + (neckX(xMm, camera) - centre) * scale;
		return new ScreenPoint(x, y, scale);
	}

	/**
	 * How far along its lane an event is when it has gone the given share of its look-ahead: a perspective, not a straight line in time. The depth of the event
	 * is {@code 1 + DEPTH * (time left / look-ahead)} and its distance from the horizon is proportional to the inverse of the depth, so that the last seconds
	 * before the onset take much of the lane and the far ones are close together: an event can be seen coming from far without the near ones being packed.
	 * It is 0 for 0, exactly 1 for 1, and strictly increasing; the same for the lane of a note, the bar of a chord and the lines across the field.
	 */
	public static double depthShare(double progress) {
		double p = Math.max(0.0, Math.min(1.0, progress));
		double left = 1.0 - p;
		double far = 1.0 / (1.0 + DEPTH);
		return (1.0 / (1.0 + DEPTH * left) - far) / (1.0 - far);
	}

	/** The depth of an event at its horizon, in times its depth at the hit line: the strength of the perspective of {@link #depthShare}. */
	static final double DEPTH = 2.0;

	/** The y of the top edge of the neck: the line of the arrival of the events, which is the hit line. */
	public double neckTopY() {
		return hitLineY();
	}

	/**
	 * Where a lane that ends at the given x on the top edge of the neck begins at the horizon: the end drawn towards the middle of the screen by the scale of
	 * the horizon, as everything is. The same construction as {@link #approach}, so that a lane and the way of an event along a column are one line.
	 */
	public ScreenPoint horizonOrigin(double endX) {
		double centre = this.viewport.width() / 2.0;
		return new ScreenPoint(centre + (endX - centre) * FAR_SCALE, horizonY(), FAR_SCALE);
	}
}
