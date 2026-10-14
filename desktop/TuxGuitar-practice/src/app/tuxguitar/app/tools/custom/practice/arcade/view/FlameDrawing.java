package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Shape;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * The stage flames behind the picture (spec 009, research R1, R2, R5, R8): tongues that taper to a tip, in three layers (dark red, orange, yellow core),
 * plus embers and a low glow, all made of the quads and discs the scene already has and all decoration. Pure: the same inputs give the same items; the
 * sway comes from the flame clock and fixed phases, never from a random source. The colours are the flames' own: no monochrome variant, no theme.
 *
 * <p>The reading guard (FR-005, SC-002): a part of a flame that comes near the box of a text fades to almost nothing and is fully there again a few pixels
 * away. A tongue that meets a text is cut into thin horizontal strips so that only the part near the text fades, not the whole column.
 * The strips of a tongue layer share its code in {@code tgString} ({@code tongue * 4 + layer + 1}), so a tongue is still one tongue to a reader of the scene.
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

	/** A text is surrounded by this margin, in pixels, in which the flames are at their faintest (SC-002). */
	static final double WIDEN = 4.0;

	/** The most opacity a flame has where it meets a text, far under the 12 % of SC-002 so that the text keeps its contrast. */
	static final double NEAR_TEXT = 0.002;

	/** The distance, in pixels, over which the flames come back to their own opacity: a fade and not an edge. */
	static final double RAMP = 28.0;

	private static final int DARK_RED = 0xB7210F;
	private static final int ORANGE = 0xF26A12;
	private static final int YELLOW = 0xFFD23C;
	private static final int EMBER = 0xFF8A2A;
	private static final int GLOW = 0xFF5A14;

	/** Per layer from the outside: share of the height, share of the width, opacity. */
	private static final double[][] LAYERS = { { 1.0, 1.0, 0.38 }, { 0.72, 0.62, 0.50 }, { 0.45, 0.30, 0.62 } };

	/** The yellow core is never used where the flames are weak, in the middle of the width: it is orange there (R8 d). */
	private static final double YELLOW_FROM_WEIGHT = 0.5;

	/** The longest piece of a tongue near a text, in pixels. */
	private static final double STRIP = 22.0;

	private FlameDrawing() {
	}

	/**
	 * The flame items of a picture, to be put before every other item.
	 *
	 * @param texts the boxes {x1, y1, x2, y2} of the texts of the picture
	 */
	static List<SceneItem> items(Viewport viewport, QualityTier tier, FlameState flames, List<double[]> texts) {
		List<SceneItem> items = new ArrayList<SceneItem>();
		if (flames == null || flames.setting() == FlameSetting.OFF || tier == QualityTier.PERFORMANCE) {
			return items;
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
		double inset = baseWidth / 2.0;
		if (full) {
			glow(items, viewport, flames.level(), factor, texts);
		}
		for (int i = 0; i < tongues; i++) {
			double t = tongues == 1 ? 0.5 : i / span;
			double weight = weight(t);
			double height = flames.level() * factor * maxHeight * weight;
			if (height > 0.0) {
				double x = inset + t * (width - 2.0 * inset);
				for (int layer = 0; layer < layers; layer++) {
					tongue(items, i, x, bottom, baseWidth, height, weight, factor, layer, seconds, texts);
				}
			}
		}
		for (int i = 0; i < tongues; i++) {
			double t = tongues == 1 ? 0.5 : i / span;
			double x = inset + t * (width - 2.0 * inset);
			ember(items, i, x, bottom, viewport.height(), weight(t) * factor, flames.level(), seconds, texts);
		}
		return items;
	}

	/** 1 at the corners and {@link #MIDDLE} in the middle of the width; {@code t} is the place across the width, 0 to 1. */
	static double weight(double t) {
		double d = Math.abs(2.0 * t - 1.0);
		return MIDDLE + (1.0 - MIDDLE) * d * d;
	}

	private static void tongue(List<SceneItem> items, int index, double x, double bottom, double baseWidth, double height, double weight, double factor, int layer,
			double seconds, List<double[]> texts) {
		double[] shape = LAYERS[layer];
		double top = bottom - height * shape[0];
		double half = baseWidth / 2.0 * shape[1];
		double sway = Math.sin(2.0 * Math.PI * (seconds * (0.9 + 0.13 * (index % 5))) + index * 1.7 + layer * 0.6) * baseWidth * 0.35 * shape[0];
		double tipHalf = half * 0.18;
		double alpha = shape[2] * weight * factor;
		int colour = layer == 0 ? DARK_RED : layer == 1 ? ORANGE : (weight >= YELLOW_FROM_WEIGHT ? YELLOW : ORANGE);
		int code = index * 4 + layer + 1;
		double[] left = { x - half, x + sway - tipHalf };
		double[] right = { x + half, x + sway + tipHalf };
		// whole when no text is near, else in thin strips: the strips near a text fade and the others do not
		double[] box = { x - half - RAMP, top - RAMP, x + half + RAMP + Math.abs(sway), bottom + RAMP };
		int strips = near(box, texts) ? (int) Math.max(1, Math.min(24, Math.ceil((bottom - top) / STRIP))) : 1;
		for (int s = 0; s < strips; s++) {
			double t0 = s / (double) strips;
			double t1 = (s + 1) / (double) strips;
			double y0 = bottom + (top - bottom) * t0;
			double y1 = bottom + (top - bottom) * t1;
			double[] corners = { lerp(left[0], left[1], t0), y0, lerp(right[0], right[1], t0), y0, lerp(right[0], right[1], t1), y1, lerp(left[0], left[1], t1), y1 };
			double[] hull = { Math.min(corners[0], corners[6]), Math.min(y0, y1), Math.max(corners[2], corners[4]), Math.max(y0, y1) };
			items.add(new SceneItem(Shape.QUAD, Role.FLAME_TONGUE, corners, colour, faded(alpha, hull, texts), 0.0, null, -1, code, null));
		}
	}

	private static void ember(List<SceneItem> items, int index, double x, double bottom, double height, double weight, double level, double seconds, List<double[]> texts) {
		double rise = (seconds * (0.28 + 0.04 * (index % 4)) + index * 0.37) % 1.0;
		double y = bottom - 6.0 - rise * height * 0.06 * (0.4 + level);
		double radius = 2.0 + (index % 3);
		double[] hull = { x - radius, y - radius, x + radius, y + radius };
		items.add(new SceneItem(Shape.DISC, Role.FLAME_EMBER, new double[] { x, y, radius, radius }, EMBER, faded(0.55 * weight * (1.0 - 0.7 * rise), hull, texts), 0.0, null, -1,
				0, null));
	}

	/** A low glow along the foot of the picture, under the numbers of the neck; the middle one is as weak as the middle of the flames. */
	private static void glow(List<SceneItem> items, Viewport viewport, double level, double factor, List<double[]> texts) {
		double ry = viewport.height() * 0.06;
		double rx = viewport.width() * 0.18;
		for (double share : new double[] { 0.18, 0.50, 0.82 }) {
			double x = share * viewport.width();
			double alpha = 0.10 * factor * weight(share) * (0.3 + 0.7 * level);
			double[] hull = { x - rx, viewport.height() - 2.0 * ry, x + rx, viewport.height() };
			items.add(new SceneItem(Shape.DISC, Role.FLAME_EMBER, new double[] { x, viewport.height() - ry, rx, ry }, GLOW, faded(alpha, hull, texts), 0.0, null, -1, 0, null));
		}
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** True when the box is within the fade distance of any text. */
	private static boolean near(double[] box, List<double[]> texts) {
		for (double[] text : texts) {
			if (gap(box, text) < RAMP) {
				return true;
			}
		}
		return false;
	}

	/** The distance between two boxes {x1, y1, x2, y2}, 0 when they meet. */
	private static double gap(double[] a, double[] b) {
		double dx = Math.max(0.0, Math.max(a[0] - b[2], b[0] - a[2]));
		double dy = Math.max(0.0, Math.max(a[1] - b[3], b[1] - a[3]));
		return Math.hypot(dx, dy);
	}

	/** The opacity of a piece of flame with this box: its own far from every text, {@link #NEAR_TEXT} at the margin of one, and a smooth way between. */
	static double faded(double alpha, double[] hull, List<double[]> texts) {
		double result = alpha;
		for (double[] text : texts) {
			double[] wide = { text[0] - WIDEN, text[1] - WIDEN, text[2] + WIDEN, text[3] + WIDEN };
			double distance = gap(hull, wide);
			if (distance < RAMP && result > NEAR_TEXT) {
				double f = distance / RAMP;
				f = f * f * (3.0 - 2.0 * f);
				result = NEAR_TEXT + (result - NEAR_TEXT) * f;
			}
		}
		return result;
	}

	/** The boxes of the texts of a list of items: the room of every word, and the verdict badges. */
	static List<double[]> textBoxes(List<SceneItem> items) {
		List<double[]> boxes = new ArrayList<double[]>();
		for (SceneItem item : items) {
			if (item.shape() == Shape.TEXT && item.text() != null && !item.text().trim().isEmpty()) {
				double width = item.text().length() * item.value(2) * 0.66;
				double x = item.value(0);
				double left = item.align() == SceneItem.Align.CENTRE ? x - width / 2.0 : item.align() == SceneItem.Align.RIGHT ? x - width : x;
				boxes.add(new double[] { left, item.value(1) - item.value(2) * 0.6, left + width, item.value(1) + item.value(2) * 0.6 });
			} else if (item.role() == Role.BADGE) {
				double x1 = Double.MAX_VALUE;
				double y1 = Double.MAX_VALUE;
				double x2 = -Double.MAX_VALUE;
				double y2 = -Double.MAX_VALUE;
				for (int i = 0; i < 8; i += 2) {
					x1 = Math.min(x1, item.value(i));
					x2 = Math.max(x2, item.value(i));
					y1 = Math.min(y1, item.value(i + 1));
					y2 = Math.max(y2, item.value(i + 1));
				}
				boxes.add(new double[] { x1, y1, x2, y2 });
			}
		}
		return boxes;
	}
}
