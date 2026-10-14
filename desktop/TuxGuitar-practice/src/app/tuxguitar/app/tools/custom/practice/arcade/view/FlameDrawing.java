package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Shape;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * The stage flames behind the picture (spec 009, research R1, R2, R5, R8): tongues that taper to a tip, in three layers (dark red, orange, yellow core),
 * plus embers and a low glow, all made of the quads and discs the scene already has and all decoration. Pure: the same inputs give the same items; the
 * sway comes from the flame clock and fixed phases, never from a random source. The colours are the flames' own: no monochrome variant, no theme.
 */
final class FlameDrawing {

	/** Tongues at the full tier; the balanced tier has half of them. */
	static final int TONGUES = 28;

	/** The tallest corner tongue at level 1 is this share of the picture height (FR-001). */
	static final double CORNER_HEIGHT = 0.55;

	/** The weight of the middle of the width against the corners, and the growth towards them: {@code MIDDLE + (1 - MIDDLE) * d^2} (FR-001, R8). */
	static final double MIDDLE = 0.20;

	/** Discreet is this share of the height and of the opacity of full (FR-008). */
	static final double DISCREET = 0.6;

	private static final int DARK_RED = 0xB7210F;
	private static final int ORANGE = 0xF26A12;
	private static final int YELLOW = 0xFFD23C;
	private static final int EMBER = 0xFF8A2A;
	private static final int GLOW = 0xFF5A14;

	/** Per layer from the outside: share of the height, share of the width, opacity. */
	private static final double[][] LAYERS = { { 1.0, 1.0, 0.38 }, { 0.72, 0.62, 0.50 }, { 0.45, 0.30, 0.62 } };

	private FlameDrawing() {
	}

	/** Adds the flames to {@code items}, which must be empty or hold only what is to be painted after them. */
	static void draw(List<SceneItem> items, Viewport viewport, QualityTier tier, FlameState flames) {
		if (flames == null || flames.setting() == FlameSetting.OFF || tier == QualityTier.PERFORMANCE) {
			return;
		}
		boolean full = tier == QualityTier.FULL;
		boolean discreet = flames.setting() == FlameSetting.DISCREET;
		int tongues = (full ? TONGUES : TONGUES / 2) / (discreet ? 2 : 1);
		int layers = full ? 3 : 2;
		double factor = discreet ? DISCREET : 1.0;
		double width = viewport.width();
		double bottom = viewport.height();
		double maxHeight = CORNER_HEIGHT * viewport.height();
		double seconds = flames.motion() ? flames.flameClockNanos() / 1e9 : 0.0;
		double span = Math.max(1.0, tongues - 1.0);
		double baseWidth = width / span * 1.6;
		if (full) {
			glow(items, viewport, flames.level(), factor);
		}
		for (int i = 0; i < tongues; i++) {
			double x = (tongues == 1 ? 0.5 : i / span) * width;
			double weight = weight(x, width);
			double height = flames.level() * factor * maxHeight * weight;
			if (height > 0.0) {
				for (int layer = 0; layer < layers; layer++) {
					tongue(items, i, x, bottom, baseWidth, height, weight * factor, layer, seconds);
				}
			}
		}
		for (int i = 0; i < tongues; i++) {
			double x = (tongues == 1 ? 0.5 : i / span) * width;
			ember(items, i, x, bottom, width, viewport.height(), weight(x, width) * factor, flames.level(), seconds);
		}
	}

	/** 1 at the corners and {@link #MIDDLE} in the middle of the width. */
	static double weight(double x, double width) {
		double d = Math.abs(x - width / 2.0) / (width / 2.0);
		return MIDDLE + (1.0 - MIDDLE) * d * d;
	}

	private static void tongue(List<SceneItem> items, int index, double x, double bottom, double baseWidth, double height, double weight, int layer, double seconds) {
		double[] shape = LAYERS[layer];
		double top = bottom - height * shape[0];
		double half = baseWidth / 2.0 * shape[1];
		double sway = Math.sin(2.0 * Math.PI * (seconds * (0.9 + 0.13 * (index % 5))) + index * 1.7 + layer * 0.6) * baseWidth * 0.35 * shape[0];
		double tipHalf = half * 0.18;
		double[] corners = { x - half, bottom, x + half, bottom, x + sway + tipHalf, top, x + sway - tipHalf, top };
		int colour = layer == 0 ? DARK_RED : layer == 1 ? ORANGE : YELLOW;
		items.add(new SceneItem(Shape.QUAD, Role.FLAME_TONGUE, corners, colour, shape[2] * weight, 0.0, null, -1, 0, null));
	}

	private static void ember(List<SceneItem> items, int index, double x, double bottom, double width, double height, double weight, double level, double seconds) {
		double rise = (seconds * (0.28 + 0.04 * (index % 4)) + index * 0.37) % 1.0;
		double y = bottom - 6.0 - rise * height * 0.06 * (0.4 + level);
		double radius = 2.0 + (index % 3);
		items.add(new SceneItem(Shape.DISC, Role.FLAME_EMBER, new double[] { x, y, radius, radius }, EMBER, 0.55 * weight * (1.0 - 0.7 * rise), 0.0, null, -1, 0, null));
	}

	private static void glow(List<SceneItem> items, Viewport viewport, double level, double factor) {
		double[] at = { 0.10, 0.50, 0.90 };
		for (double share : at) {
			double x = share * viewport.width();
			double alpha = 0.10 * factor * weight(x, viewport.width()) * (0.3 + 0.7 * level);
			items.add(new SceneItem(Shape.DISC, Role.FLAME_EMBER, new double[] { x, viewport.height(), viewport.width() * 0.25, viewport.height() * 0.12 }, GLOW, alpha, 0.0, null, -1, 0, null));
		}
	}
}
