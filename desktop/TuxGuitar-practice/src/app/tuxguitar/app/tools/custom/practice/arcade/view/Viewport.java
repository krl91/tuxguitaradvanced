package app.tuxguitar.app.tools.custom.practice.arcade.view;

/** The size of the drawing area in pixels. */
public final class Viewport {

	private final double width;
	private final double height;

	public Viewport(double width, double height) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("an empty viewport: " + width + "x" + height);
		}
		this.width = width;
		this.height = height;
	}

	public double width() {
		return this.width;
	}

	public double height() {
		return this.height;
	}
}
