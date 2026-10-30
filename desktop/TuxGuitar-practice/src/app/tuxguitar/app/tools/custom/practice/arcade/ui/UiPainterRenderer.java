package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * The baseline renderer (spec 003 T041): replays a scene on the toolkit's {@link UIPainter}, the 2D path painter that the SWT, JavaFX and Qt toolkits all
 * have. The colours and fonts it needs are created once and reused, and released by {@link #dispose()}. The painter belongs to one paint event: the canvas
 * hands it over with {@link #target(UIPainter)} before each render. The tier changes only the antialiasing of the shapes here: the decoration is already absent from the scene of the cheaper tiers, and no
 * lower resolution is used (the measurements of T045 show that the density budget of the scene, not the number of pixels, is what the cost depends on).
 */
public final class UiPainterRenderer extends PainterRenderer {

	private final UIFactory factory;
	private final String fontName;
	private final Map<Integer, UIColor> colours = new HashMap<Integer, UIColor>();
	private final Map<Integer, UIFont> fonts = new HashMap<Integer, UIFont>();
	private final boolean minimal;
	private final FlameCells flameCells = new FlameCells();
	private UIPainter painter;

	public UiPainterRenderer(UIFactory factory, String fontName) {
		this(factory, fontName, false);
	}

	private UiPainterRenderer(UIFactory factory, String fontName, boolean minimal) {
		this.factory = factory;
		this.fontName = fontName == null ? "Sans" : fontName;
		this.minimal = minimal;
	}

	/**
	 * The fallback path (spec 003 FR-057, T046): the same toolkit painter used the cheapest way. It draws the same information at the same places and
	 * with the same texts as the selected path, and nothing else: no decoration, no depth cues of the field, no inlays, no transparency (every item is
	 * opaque, which saves the blending of each) and no antialiasing of the shapes. It is used when it is forced, and when the selected path fails.
	 */
	public static UiPainterRenderer fallback(UIFactory factory, String fontName) {
		return new UiPainterRenderer(factory, fontName, true);
	}

	@Override
	public String name() {
		return this.minimal ? "fallback (toolkit painter, minimal)" : "toolkit painter";
	}

	@Override
	public void target(UIPainter painter) {
		this.painter = painter;
	}

	/** True for what the minimal path leaves out: decoration, the depth cues of the field and the inlays of the neck. */
	private static boolean leftOutByFallback(SceneItem item) {
		return item.role().isDecoration() || item.role() == SceneItem.Role.FIELD_GUIDE || item.role() == SceneItem.Role.NECK_LANE || item.role() == SceneItem.Role.INLAY;
	}

	private UIColor colour(int rgb) {
		UIColor colour = this.colours.get(rgb);
		if (colour == null) {
			colour = this.factory.createColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
			this.colours.put(rgb, colour);
		}
		return colour;
	}

	private UIFont font(double size) {
		int key = Math.max(6, (int) Math.round(size));
		UIFont font = this.fonts.get(key);
		if (font == null) {
			font = this.factory.createFont(this.fontName, key, true, false);
			this.fonts.put(key, font);
		}
		return font;
	}

	@Override
	public void render(Scene scene, QualityTier tier) {
		UIPainter p = this.painter;
		if (this.minimal) {
			p.setAlpha(255);
		}
		for (SceneItem item : scene.items()) {
			if (this.minimal && leftOutByFallback(item)) {
				continue;
			}
			if (!this.minimal && isFlameCell(item)) {
				this.flameCells.add(p, item);
				continue;
			}
			this.flameCells.paint(p);
			if (!this.minimal) {
				p.setAlpha((int) Math.round(Math.max(0.0, Math.min(1.0, item.alpha())) * 255.0));
			}
			p.setAntialias(item.shape() == SceneItem.Shape.TEXT || (!this.minimal && tier.antialias()));
			// the arrow of a bend, the ghost of its cell, the arc, the ghost and the rail of a technique are dashed (FR-077, FR-078): the dashes are made of
			// the path itself (a stroke of short segments), because the dash style of the native painter is not honoured when a path is drawn on macOS
			boolean dashed = isDashed(item.role());
			switch (item.shape()) {
				case LINE:
					p.setForeground(colour(item.colour()));
					p.setLineWidth((float) item.lineWidth());
					p.initPath(UIPainter.PATH_DRAW);
					if (dashed) {
						dashes(p, new double[] { item.value(0), item.value(1), item.value(2), item.value(3) }, false, item.lineWidth());
					} else {
						p.moveTo((float) item.value(0), (float) item.value(1));
						p.lineTo((float) item.value(2), (float) item.value(3));
					}
					p.closePath();
					break;
				case CURVE:
					p.setForeground(colour(item.colour()));
					p.setLineWidth((float) item.lineWidth());
					p.initPath(UIPainter.PATH_DRAW);
					if (dashed) {
						dashes(p, cubicPoints(item), false, item.lineWidth());
					} else {
						p.moveTo((float) item.value(0), (float) item.value(1));
						p.cubicTo((float) item.value(2), (float) item.value(3), (float) item.value(4), (float) item.value(5), (float) item.value(6), (float) item.value(7));
					}
					p.closePath();
					break;
				case QUAD:
					p.setBackground(colour(item.colour()));
					p.initPath(UIPainter.PATH_FILL);
					p.moveTo((float) item.value(0), (float) item.value(1));
					p.lineTo((float) item.value(2), (float) item.value(3));
					p.lineTo((float) item.value(4), (float) item.value(5));
					p.lineTo((float) item.value(6), (float) item.value(7));
					p.closePath();
					break;
				case DISC:
					p.setBackground(colour(item.colour()));
					p.initPath(UIPainter.PATH_FILL);
					p.addCircle((float) item.value(0), (float) item.value(1), (float) (2.0 * item.value(2)));
					p.closePath();
					break;
				case RING:
					p.setForeground(colour(item.colour()));
					p.setLineWidth((float) item.lineWidth());
					p.initPath(UIPainter.PATH_DRAW);
					p.addCircle((float) item.value(0), (float) item.value(1), (float) (2.0 * item.value(2)));
					p.closePath();
					break;
				case ROUNDED:
					p.setBackground(colour(item.colour()));
					p.initPath(UIPainter.PATH_FILL);
					roundedPath(p, item);
					p.closePath();
					break;
				case ROUNDED_RING:
					p.setForeground(colour(item.colour()));
					p.setLineWidth((float) item.lineWidth());
					p.initPath(UIPainter.PATH_DRAW);
					if (dashed) {
						dashes(p, roundedPoints(item), true, item.lineWidth());
						p.closePath();
						break;
					}
					roundedPath(p, item);
					// the last side is drawn explicitly: a stroked path is not closed by every painter
					p.lineTo((float) (item.value(0) + (item.value(2) - Math.max(0.0, Math.min(item.value(2), item.value(3)) * 0.45))), (float) (item.value(1) - item.value(3)));
					p.closePath();
					break;
				case TEXT:
					p.setFont(font(item.value(2)));
					p.setForeground(colour(item.colour()));
					float width = p.getFMWidth(item.text());
					float height = p.getFMHeight();
					float x = (float) item.value(0) - (item.align() == SceneItem.Align.CENTRE ? width / 2f : (item.align() == SceneItem.Align.RIGHT ? width : 0f));
					p.drawString(item.text(), x, (float) item.value(1) - height / 2f);
					break;
				default:
					break;
			}
		}
		this.flameCells.paint(p);
		p.setAlpha(255);
	}

	/** A cell of the demo fire: a flame item drawn as an upright rectangle (spec 018 research R10). */
	static boolean isFlameCell(SceneItem item) {
		if (item.shape() != SceneItem.Shape.QUAD || (item.role() != SceneItem.Role.FLAME_TONGUE && item.role() != SceneItem.Role.FLAME_EMBER)) {
			return false;
		}
		return item.value(0) == item.value(6) && item.value(2) == item.value(4) && item.value(1) == item.value(3) && item.value(5) == item.value(7);
	}

	/**
	 * The cells of the demo fire, filled in batches (spec 018 research R10): thousands of big pixels, one native path each, cost more than the frame budget
	 * of the full tier, so the cells of one colour and one painter alpha are filled as one path of rectangles, neighbours on a row joined into one. The cells lie on one grid ({@code FireField}),
	 * so two of them meet only when they share their bottom-left corner: when a cell lands on a pending one, what is pending is filled first, which keeps
	 * the order of the scene where it shows (a spark over a cell) and never puts two rectangles of one path on each other (the even-odd rule would hollow
	 * them). Any other item fills what is pending before it is drawn.
	 */
	private final class FlameCells {

		/** The pending rectangles by colour and alpha, in the order the groups appeared: x, y, width, height for each. */
		private final Map<Long, float[]> groups = new LinkedHashMap<Long, float[]>();
		private final Map<Long, Integer> sizes = new HashMap<Long, Integer>();
		private final Set<Long> corners = new HashSet<Long>();

		void add(UIPainter p, SceneItem item) {
			long corner = (Math.round(item.value(0) * 8.0) << 32) ^ (Math.round(item.value(5) * 8.0) & 0xFFFFFFFFL);
			if (!this.corners.add(corner)) {
				paint(p);
				this.corners.add(corner);
			}
			int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, item.alpha())) * 255.0);
			long key = ((long) (item.colour() & 0xFFFFFF) << 8) | alpha;
			float[] rectangles = this.groups.get(key);
			int size = this.sizes.getOrDefault(key, 0);
			if (rectangles == null || size + 4 > rectangles.length) {
				float[] larger = new float[Math.max(64, size * 2)];
				if (rectangles != null) {
					System.arraycopy(rectangles, 0, larger, 0, size);
				}
				rectangles = larger;
				this.groups.put(key, rectangles);
			}
			float x = (float) item.value(0);
			float y = (float) item.value(1);
			float width = (float) (item.value(2) - item.value(0));
			float height = (float) (item.value(5) - item.value(1));
			if (size >= 4 && rectangles[size - 3] == y && rectangles[size - 1] == height && Math.abs(rectangles[size - 4] + rectangles[size - 2] - x) < 0.01f) {
				// the left neighbour on the same row, same colour and alpha: one wider rectangle
				rectangles[size - 2] += width;
				return;
			}
			rectangles[size] = x;
			rectangles[size + 1] = y;
			rectangles[size + 2] = width;
			rectangles[size + 3] = height;
			this.sizes.put(key, size + 4);
		}

		/** Fills what is pending, one path per colour and alpha. */
		void paint(UIPainter p) {
			if (this.corners.isEmpty()) {
				return;
			}
			List<Long> keys = new ArrayList<Long>(this.groups.keySet());
			for (Long key : keys) {
				int size = this.sizes.getOrDefault(key, 0);
				if (size == 0) {
					continue;
				}
				float[] rectangles = this.groups.get(key);
				p.setAlpha((int) (key & 0xFF));
				p.setBackground(colour((int) (key >>> 8)));
				p.initPath(UIPainter.PATH_FILL);
				// hard pixels, as in the demos, and much cheaper: after initPath, which turns the antialiasing on in the SWT painter
				p.setAntialias(false);
				for (int i = 0; i < size; i += 4) {
					p.addRectangle(rectangles[i], rectangles[i + 1], rectangles[i + 2], rectangles[i + 3]);
				}
				p.closePath();
				this.sizes.put(key, 0);
			}
			this.corners.clear();
		}
	}


	/** The roles that are drawn dashed (FR-077, FR-078). */
	static boolean isDashed(SceneItem.Role role) {
		return role == SceneItem.Role.BEND_ARROW || role == SceneItem.Role.BEND_GHOST || role == SceneItem.Role.TECH_ARROW || role == SceneItem.Role.TECH_GHOST
				|| role == SceneItem.Role.TECH_RAIL;
	}

	/** The points of a cubic curve, sampled finely enough for the dashes to follow it. */
	static double[] cubicPoints(SceneItem item) {
		int steps = 28;
		double[] points = new double[2 * (steps + 1)];
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps;
			double u = 1.0 - t;
			double b0 = u * u * u;
			double b1 = 3 * u * u * t;
			double b2 = 3 * u * t * t;
			double b3 = t * t * t;
			points[2 * i] = b0 * item.value(0) + b1 * item.value(2) + b2 * item.value(4) + b3 * item.value(6);
			points[2 * i + 1] = b0 * item.value(1) + b1 * item.value(3) + b2 * item.value(5) + b3 * item.value(7);
		}
		return points;
	}

	/** The outline of a rounded rectangle as the points of {@link #roundedPath}, in order (the last side is the closing one). */
	static double[] roundedPoints(SceneItem item) {
		double cx = item.value(0);
		double cy = item.value(1);
		double rx = item.value(2);
		double ry = item.value(3);
		double r = Math.max(0.0, Math.min(rx, ry) * 0.45);
		double[][] corner = { { 1, -1 }, { 1, 1 }, { -1, 1 }, { -1, -1 } };
		double[] start = { -90, 0, 90, 180 };
		double[] points = new double[24];
		int n = 0;
		for (int c = 0; c < 4; c++) {
			double ccx = cx + corner[c][0] * (rx - r);
			double ccy = cy + corner[c][1] * (ry - r);
			for (int k = 0; k <= 2; k++) {
				double angle = Math.toRadians(start[c] + 45.0 * k);
				points[n++] = ccx + r * Math.cos(angle);
				points[n++] = ccy + r * Math.sin(angle);
			}
		}
		return points;
	}

	/**
	 * Strokes a polyline as dashes inside the current path: short segments (each its own moveTo and lineTo) whose length follows the width of the line, so that
	 * the dashes are the same on every painter.
	 */
	static void dashes(UIPainter p, double[] points, boolean closed, double width) {
		double on = Math.max(4.0, width * 2.4);
		double off = Math.max(3.0, width * 1.8);
		int count = points.length / 2;
		int segments = closed ? count : count - 1;
		boolean drawing = true;
		double left = on;
		for (int i = 0; i < segments; i++) {
			double x0 = points[2 * i];
			double y0 = points[2 * i + 1];
			double x1 = points[2 * ((i + 1) % count)];
			double y1 = points[2 * ((i + 1) % count) + 1];
			double length = Math.hypot(x1 - x0, y1 - y0);
			double at = 0.0;
			while (at < length) {
				double step = Math.min(left, length - at);
				if (drawing) {
					p.moveTo((float) (x0 + (x1 - x0) * at / length), (float) (y0 + (y1 - y0) * at / length));
					p.lineTo((float) (x0 + (x1 - x0) * (at + step) / length), (float) (y0 + (y1 - y0) * (at + step) / length));
				}
				at += step;
				left -= step;
				if (left <= 1e-9) {
					drawing = !drawing;
					left = drawing ? on : off;
				}
			}
		}
	}

	/** The outline of a rounded rectangle as straight segments (three per corner), so that every painter and the tests see the same shape. */
	static void roundedPath(UIPainter p, SceneItem item) {
		double cx = item.value(0);
		double cy = item.value(1);
		double rx = item.value(2);
		double ry = item.value(3);
		double r = Math.max(0.0, Math.min(rx, ry) * 0.45);
		double[][] corner = { { 1, -1 }, { 1, 1 }, { -1, 1 }, { -1, -1 } };
		double[] start = { -90, 0, 90, 180 };
		boolean first = true;
		for (int c = 0; c < 4; c++) {
			double ccx = cx + corner[c][0] * (rx - r);
			double ccy = cy + corner[c][1] * (ry - r);
			for (int k = 0; k <= 2; k++) {
				double angle = Math.toRadians(start[c] + 45.0 * k);
				float x = (float) (ccx + r * Math.cos(angle));
				float y = (float) (ccy + r * Math.sin(angle));
				if (first) {
					p.moveTo(x, y);
					first = false;
				} else {
					p.lineTo(x, y);
				}
			}
		}
	}

	@Override
	public void dispose() {
		for (UIColor colour : this.colours.values()) {
			colour.dispose();
		}
		for (UIFont font : this.fonts.values()) {
			font.dispose();
		}
		this.colours.clear();
		this.fonts.clear();
		this.painter = null;
	}
}
