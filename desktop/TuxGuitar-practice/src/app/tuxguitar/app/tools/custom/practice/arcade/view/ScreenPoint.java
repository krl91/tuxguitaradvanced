package app.tuxguitar.app.tools.custom.practice.arcade.view;

/** A point on the screen, in pixels, and the scale of what is drawn there (1 on the neck and on the hit line, less in the distance). */
public final class ScreenPoint {

	private final double x;
	private final double y;
	private final double scale;

	public ScreenPoint(double x, double y, double scale) {
		this.x = x;
		this.y = y;
		this.scale = scale;
	}

	public double x() {
		return this.x;
	}

	public double y() {
		return this.y;
	}

	public double scale() {
		return this.scale;
	}
}
