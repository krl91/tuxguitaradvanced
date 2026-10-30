package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Shape;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * The stage flames behind the picture, drawn like the classic fire of the Amiga demos (spec 018, replacing the tongues of spec 009): a field of square big
 * pixels along the foot of the picture ({@link FireField}, the top cell of each column cut to the height of its flame), each coloured from dark red to white by its heat and translucent ({@link FireColours}), all
 * decoration. Pure: the same inputs give the same items; the flicker comes from the flame clock, never from a random source. The colours are the fire's
 * own: no monochrome variant, no theme.
 *
 * <p>The reading guard (spec 009 FR-005, SC-002): a cell that comes near the box of a text fades to almost nothing and is fully there again a few pixels
 * away. The cells of the flame carry in {@code tgString} the code of the group of columns they belong to ({@code tongue * 4 + part + 1}), the columns
 * being grouped as the tongues and layers of spec 009 were counted (28 by 3 at the full tier, 14 by 2 at the balanced one, half of them discreet), so that a
 * reader of the scene still finds that many pieces of flame.
 */
final class FlameDrawing {

	/** The groups of columns at the full tier; the balanced tier and discreet have half of them each. */
	static final int TONGUES = 28;

	/** The tallest corner of the fire at level 1 is this share of the picture height (FR-001). */
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

	/** A spark lives this long. */
	static final long SPARK_LIFE = 1_000_000_000L;

	/** The most sparks in the air at once. */
	static final int MOST_SPARKS = 60;

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
		int parts = full ? 3 : 2;
		int groups = (full ? TONGUES : TONGUES / 2) / (discreet ? 2 : 1) * parts;
		FireField field = new FireField(viewport, tier, flames);
		double cell = field.cell();
		for (int row = 0; ; row++) {
			boolean any = false;
			for (int col = 0; col < field.columns(); col++) {
				int heat = field.heat(col, row);
				if (heat == 0) {
					continue;
				}
				any = true;
				double x1 = field.left(col);
				double y2 = field.top(row) + cell;
				double y1 = y2 - field.cellHeight(col, row);
				double[] corners = { x1, y1, x1 + cell, y1, x1 + cell, y2, x1, y2 };
				double[] hull = { x1, y1, x1 + cell, y2 };
				double alpha = faded(FireColours.alpha(heat, discreet), hull, texts);
				if (row < FireField.EMBER_ROWS) {
					items.add(new SceneItem(Shape.QUAD, Role.FLAME_EMBER, corners, FireColours.colour(heat), alpha, 0.0, null, -1, 0, null));
				} else {
					int group = (int) ((long) col * groups / field.columns());
					int code = group / parts * 4 + group % parts + 1;
					items.add(new SceneItem(Shape.QUAD, Role.FLAME_TONGUE, corners, FireColours.colour(heat), alpha, 0.0, null, -1, code, null));
				}
			}
			if (!any) {
				break;
			}
		}
		if (full && flames.motion()) {
			sparks(items, field, flames, discreet, texts);
		}
		return items;
	}

	/**
	 * The sparks of the right notes (spec 018 FR-005, research R7): each burst throws {@code 4 + min(streak, 12) / 2} single cells (60 % of them discreet)
	 * from the top of the fire near a corner; they rise, drift and fade out within {@link #SPARK_LIFE}. At most {@link #MOST_SPARKS} in the air, the newest
	 * first. Over a cell of the fire of opacity {@code a} a spark has at most {@code (0.60 - a) / (1 - a)}, so the two together never pass 60 % (FR-002).
	 */
	private static void sparks(List<SceneItem> items, FireField field, FlameState flames, boolean discreet, List<double[]> texts) {
		List<FlameState.Burst> bursts = flames.bursts();
		double cell = field.cell();
		int highest = (int) Math.floor(field.top(0) / cell);
		int thrown = 0;
		for (int b = bursts.size() - 1; b >= 0 && thrown < MOST_SPARKS; b--) {
			FlameState.Burst burst = bursts.get(b);
			long age = flames.flameClockNanos() - burst.clockNanos();
			if (age < 0L || age >= SPARK_LIFE) {
				continue;
			}
			double life = age / (double) SPARK_LIFE;
			int count = 4 + Math.min(burst.streak(), 12) / 2;
			if (discreet) {
				count = (int) Math.round(count * DISCREET);
			}
			for (int i = 0; i < count && thrown < MOST_SPARKS; i++) {
				long h = mix(burst.clockNanos() * 31L + i);
				boolean right = (h & 1L) != 0L;
				int from = (int) Math.floor(((h >>> 1) & 0xFFFF) / 65536.0 * 0.25 * field.columns());
				int start = right ? field.columns() - 1 - from : from;
				double rise = 6.0 + 10.0 * (((h >>> 17) & 0xFFFF) / 65536.0);
				double drift = (((h >>> 33) & 0xFFFF) / 65536.0 - 0.5) * 6.0;
				int col = Math.max(0, Math.min(field.columns() - 1, start + (int) Math.round(drift * life)));
				int row = Math.min(highest, FireField.EMBER_ROWS + field.tongueRows(start) + (int) Math.floor(rise * life));
				double alpha = FireColours.MOST * (1.0 - life) * (discreet ? DISCREET : 1.0);
				double under = FireColours.alpha(field.heat(col, row), discreet);
				if (under > 0.0) {
					alpha = Math.min(alpha, (FireColours.MOST - under) / (1.0 - under));
				}
				double x1 = field.left(col);
				double y1 = field.top(row);
				double[] hull = { x1, y1, x1 + cell, y1 + cell };
				alpha = faded(alpha, hull, texts);
				if (alpha > 1e-6) {
					double[] corners = { x1, y1, x1 + cell, y1, x1 + cell, y1 + cell, x1, y1 + cell };
					items.add(new SceneItem(Shape.QUAD, Role.FLAME_EMBER, corners, FireColours.colour(FireColours.STEPS - 1 - (int) (life * 8.0)), alpha, 0.0, null, -1, 0, null));
				}
				thrown++;
			}
		}
	}

	/** A 64-bit mix of a number: the same input, the same output. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** 1 at the corners and {@link #MIDDLE} in the middle of the width; {@code t} is the place across the width, 0 to 1. */
	static double weight(double t) {
		double d = Math.abs(2.0 * t - 1.0);
		return MIDDLE + (1.0 - MIDDLE) * d * d;
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
