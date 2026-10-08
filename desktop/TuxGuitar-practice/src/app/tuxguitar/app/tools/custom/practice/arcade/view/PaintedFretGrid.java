package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The fret grid of the neck as it is painted for one camera and viewport (spec 003 FR-066): the screen x of every fret wire (the nut is wire 0), the x of the
 * left edge of the lane before the nut, and the y of the top edge of the neck. The neck is painted from this and the lanes of the notes are built from this,
 * so that a lane can only end where the wires and the edge that were painted are. Nothing in it depends on a string or on an event.
 */
public final class PaintedFretGrid {

	private final double[] wireX;
	private final boolean[] painted;
	private final double preNutX;
	private final double neckTopY;

	private PaintedFretGrid(double[] wireX, boolean[] painted, double preNutX, double neckTopY) {
		this.wireX = wireX;
		this.painted = painted;
		this.preNutX = preNutX;
		this.neckTopY = neckTopY;
	}

	/** The grid of the neck for the camera snapshot: a wire is painted when it is in the shown part. */
	public static PaintedFretGrid of(NeckModel neck, IsometricProjection projection, Camera camera) {
		int count = neck.fretCount() + 1;
		double[] wireX = new double[count];
		boolean[] painted = new boolean[count];
		for (int n = 0; n < count; n++) {
			wireX[n] = projection.neckX(neck.fretX(n), camera);
			// a wire is painted when it is in the shown part of the neck: the camera keeps both wires of the cell of every target in it
			painted[n] = camera.shows(neck.fretX(n), 0.0);
		}
		return new PaintedFretGrid(wireX, painted, projection.neckX(neck.cellLeftX(0), camera), projection.neckTopY());
	}

	/** The number of wires, the nut included: frets 0 to the last. */
	public int wireCount() {
		return this.wireX.length;
	}

	/** The screen x of wire {@code n}. */
	public double wireX(int n) {
		return this.wireX[n];
	}

	/** True when wire {@code n} is in the shown part of the neck, so that it is painted. */
	public boolean isPainted(int n) {
		return this.painted[n];
	}

	/** The screen x of the left edge of the lane before the nut, where the open strings are. */
	public double preNutX() {
		return this.preNutX;
	}

	/** The screen y of the top edge of the neck. */
	public double neckTopY() {
		return this.neckTopY;
	}

	/** The screen x of the left edge of the cell of fret {@code fret}: the wire before it, or the edge of the lane before the nut. */
	public double cellLeftX(int fret) {
		return fret <= 0 ? this.preNutX : this.wireX[Math.min(fret - 1, this.wireX.length - 1)];
	}

	/** The screen x of the right edge of the cell of fret {@code fret}: its own wire, or the nut for the open strings. */
	public double cellRightX(int fret) {
		return this.wireX[Math.max(0, Math.min(fret, this.wireX.length - 1))];
	}
}
