package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The predefined approach lanes of the frets (spec 003 FR-066, SC-024): one lane for every fret cell of the neck, plus one before the nut for the open
 * strings. For the cell {@code n} between the wires that were painted,
 * <pre>
 * arrivalX(n) = (paintedWireX(n - 1) + paintedWireX(n)) / 2        arrivalY = paintedNeckBottomY
 * </pre>
 * and the lane joins its origin at the horizon to that point, <b>in one straight line</b>: it goes across the top edge of the neck without bending (owner,
 * 2026-10-11, Q34), the neck being see-through, and ends at the foot of the neck, the middle of the bottom edge of the cell, where the note is played. A note written at fret {@code n} uses this lane and no other, whatever its string: the string is
 * told by the colour and by the target on the neck, never by the geometry. The lane goes on under the neck down to the foot. The map is
 * built once for a camera snapshot and replaced as a whole when the camera, the viewport or the scale changes: a lane cannot outlive the wires it was built from.
 */
public final class FretArrivalLaneMap {

	/** One lane: where it begins, at the horizon, and where it ends, at the middle of the bottom edge of the cell (the foot of the neck). */
	public static final class Lane {
		private final int fret;
		private final ScreenPoint origin;
		private final ScreenPoint end;

		Lane(int fret, ScreenPoint origin, ScreenPoint end) {
			this.fret = fret;
			this.origin = origin;
			this.end = end;
		}

		public int fret() {
			return this.fret;
		}

		/** Where the lane begins: its point at the horizon, small. */
		public ScreenPoint origin() {
			return this.origin;
		}

		/** Where the lane ends: the middle of the bottom edge of the cell, the foot of the neck, at full size. */
		public ScreenPoint end() {
			return this.end;
		}

		/** Where the lane goes across a horizontal line, the top edge of the neck for instance: on the straight way from the origin to the end. */
		public ScreenPoint crossing(double y) {
			double share = (y - this.origin.y()) / (this.end.y() - this.origin.y());
			return new ScreenPoint(this.origin.x() + (this.end.x() - this.origin.x()) * share, y, this.origin.scale() + (this.end.scale() - this.origin.scale()) * share);
		}

		/** The point of the lane at a progress of the look-ahead: the origin for 0, the end for 1, on the straight way between, placed in perspective (see {@link IsometricProjection#depthShare}). */
		public ScreenPoint at(double progress) {
			double q = IsometricProjection.depthShare(progress);
			double scale = this.origin.scale() + (this.end.scale() - this.origin.scale()) * q;
			return new ScreenPoint(this.origin.x() + (this.end.x() - this.origin.x()) * q, this.origin.y() + (this.end.y() - this.origin.y()) * q, scale);
		}
	}

	private final PaintedFretGrid grid;
	private final Lane[] lanes;

	private FretArrivalLaneMap(PaintedFretGrid grid, Lane[] lanes) {
		this.grid = grid;
		this.lanes = lanes;
	}

	/** Builds the lanes from the grid as it is painted, for the projection of the same snapshot. */
	public static FretArrivalLaneMap fromPaintedFrets(PaintedFretGrid grid, IsometricProjection projection) {
		Lane[] lanes = new Lane[grid.wireCount()];
		for (int fret = 0; fret < lanes.length; fret++) {
			double arrivalX = (grid.cellLeftX(fret) + grid.cellRightX(fret)) / 2.0;
			ScreenPoint end = new ScreenPoint(arrivalX, grid.neckBottomY(), 1.0);
			lanes[fret] = new Lane(fret, projection.horizonOrigin(arrivalX), end);
		}
		return new FretArrivalLaneMap(grid, lanes);
	}

	public PaintedFretGrid grid() {
		return this.grid;
	}

	/** The lane of the fret: 0 is the lane before the nut. */
	public Lane laneForFret(int fret) {
		return this.lanes[Math.max(0, Math.min(fret, this.lanes.length - 1))];
	}

	public int size() {
		return this.lanes.length;
	}
}
