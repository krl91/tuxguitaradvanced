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
		double p = Math.max(0.0, Math.min(1.0, progress));
		double away = 1.0 - p;
		double scale = 1.0 - (1.0 - FAR_SCALE) * away;
		double y = hitLineY() - (hitLineY() - horizonY()) * away;
		double centre = this.viewport.width() / 2.0;
		double x = centre + (neckX(xMm, camera) - centre) * scale;
		return new ScreenPoint(x, y, scale);
	}
}
