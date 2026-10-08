package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The part of the neck that is shown, as the distances from the nut (in millimetres) of its left and right edges. Panning moves both edges; showing a
 * wider part (a zoom out) moves them apart. Immutable.
 */
public final class Camera {

	private final double left;
	private final double right;

	public Camera(double left, double right) {
		if (!(right > left)) {
			throw new IllegalArgumentException("a camera shows a positive width: " + left + " to " + right);
		}
		this.left = left;
		this.right = right;
	}

	public double left() {
		return this.left;
	}

	public double right() {
		return this.right;
	}

	public double span() {
		return this.right - this.left;
	}

	public double centre() {
		return (this.left + this.right) / 2.0;
	}

	/** True when a position (in millimetres from the nut) lies in the shown part, with a margin inside each edge. */
	public boolean shows(double xMm, double marginMm) {
		return xMm >= this.left + marginMm && xMm <= this.right - marginMm;
	}
}
