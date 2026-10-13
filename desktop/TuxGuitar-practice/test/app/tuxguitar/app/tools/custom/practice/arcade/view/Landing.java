package app.tuxguitar.app.tools.custom.practice.arcade.view;

/** Where the things that come down to the foot of the neck are, as the tests expect them: on the ground path that ends at the bottom of the neck (spec 003 Q37). */
final class Landing {

	private Landing() {
	}

	/** The point of the ground at a position of the neck and a progress, with the scale of its depth. */
	static ScreenPoint ground(IsometricProjection projection, Viewport viewport, Camera cam, double xMm, double progress) {
		double q = IsometricProjection.depthShare(progress);
		double scale = projection.farScale() + (1.0 - projection.farScale()) * q;
		double centre = viewport.width() / 2.0;
		double bottom = viewport.height() * IsometricProjection.NECK_NEAR_EDGE;
		double hor = projection.horizonY();
		return new ScreenPoint(centre + (projection.neckX(xMm, cam) - centre) * scale, hor + (bottom - hor) * q, scale);
	}

	/** The height of a string row above the ground at the foot, in pixels at full scale. */
	static double rise(IsometricProjection projection, Viewport viewport, int row, int strings) {
		return viewport.height() * IsometricProjection.NECK_NEAR_EDGE - projection.stringY(row, strings);
	}

	static double footY(Viewport viewport) {
		return viewport.height() * IsometricProjection.NECK_NEAR_EDGE;
	}
}
