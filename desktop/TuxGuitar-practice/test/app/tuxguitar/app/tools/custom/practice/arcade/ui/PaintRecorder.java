package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * A painter that keeps what the toolkit's keeps and lists what was really drawn with it: the straight segments and the circles, each with the alpha, the colours
 * and the width that were set when it was drawn, and the texts. The tests of what the renderers send to the painter (spec 003 FR-065, FR-066) read this, not
 * the scene.
 */
public final class PaintRecorder implements InvocationHandler {

	public static final class Segment {
		public final double x1;
		public final double y1;
		public final double x2;
		public final double y2;
		public final double width;
		public final int alpha;
		public final int colour;

		Segment(double x1, double y1, double x2, double y2, double width, int alpha, int colour) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.width = width;
			this.alpha = alpha;
			this.colour = colour;
		}
	}

	public static final class Circle {
		public final int style;
		public final double cx;
		public final double cy;
		public final double diameter;
		public final int alpha;
		public final int fill;
		public final int stroke;
		public final double lineWidth;

		Circle(int style, double cx, double cy, double diameter, int alpha, int fill, int stroke, double lineWidth) {
			this.style = style;
			this.cx = cx;
			this.cy = cy;
			this.diameter = diameter;
			this.alpha = alpha;
			this.fill = fill;
			this.stroke = stroke;
			this.lineWidth = lineWidth;
		}
	}

	public final List<Segment> segments = new ArrayList<Segment>();
	public final List<Circle> circles = new ArrayList<Circle>();
	public final List<String> texts = new ArrayList<String>();
	private int alpha = 255;
	private int fill;
	private int stroke;
	private double lineWidth = 1.0;
	private int style;
	private final double[] points = new double[8];
	private int pointCount;

	private static int rgb(UIColor colour) {
		return (colour.getRed() << 16) | (colour.getGreen() << 8) | colour.getBlue();
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) {
		switch (method.getName()) {
			case "setAlpha":
				this.alpha = (Integer) args[0];
				return null;
			case "setBackground":
				this.fill = rgb((UIColor) args[0]);
				return null;
			case "setForeground":
				this.stroke = rgb((UIColor) args[0]);
				return null;
			case "setLineWidth":
				this.lineWidth = (Float) args[0];
				return null;
			case "initPath":
				this.style = args.length == 0 ? UIPainter.PATH_DRAW : (Integer) args[0];
				this.pointCount = 0;
				return null;
			case "moveTo":
			case "lineTo":
				if (this.pointCount < 4) {
					this.points[2 * this.pointCount] = (Float) args[0];
					this.points[2 * this.pointCount + 1] = (Float) args[1];
					this.pointCount++;
				}
				return null;
			case "closePath":
				if (this.style == UIPainter.PATH_DRAW && this.pointCount == 2) {
					this.segments.add(new Segment(this.points[0], this.points[1], this.points[2], this.points[3], this.lineWidth, this.alpha, this.stroke));
				}
				this.pointCount = 0;
				return null;
			case "addCircle": {
				// UIPainter.addCircle(x, y, width): the centre and the diameter, as SWTPainter, JFXPainter and QTPainter all take it
				double x = (Float) args[0];
				double y = (Float) args[1];
				double w = (Float) args[2];
				this.circles.add(new Circle(this.style, x, y, w, this.alpha, this.fill, this.stroke, this.lineWidth));
				return null;
			}
			case "drawString":
				this.texts.add(args[0] + "@" + Math.round((Float) args[1]) + "," + Math.round((Float) args[2]));
				return null;
			case "getFMWidth":
			case "getFMHeight":
				return 10f;
			default:
				return method.getReturnType() == float.class ? (Object) 0f : null;
		}
	}

	public static UIFactory factory() {
		return (UIFactory) Proxy.newProxyInstance(UIFactory.class.getClassLoader(), new Class<?>[] { UIFactory.class }, (p, method, args) -> {
			if (method.getName().equals("createColor")) {
				final int r = (Integer) args[0];
				final int g = (Integer) args[1];
				final int b = (Integer) args[2];
				return Proxy.newProxyInstance(UIColor.class.getClassLoader(), new Class<?>[] { UIColor.class }, (q, m, a) -> {
					switch (m.getName()) {
						case "getRed":
							return r;
						case "getGreen":
							return g;
						case "getBlue":
							return b;
						default:
							return m.getReturnType() == boolean.class ? (Object) false : null;
					}
				});
			}
			if (method.getName().equals("createFont")) {
				return Proxy.newProxyInstance(UIFont.class.getClassLoader(), new Class<?>[] { UIFont.class }, (q, m, a) -> null);
			}
			return null;
		});
	}

	/** What a renderer sent to the painter for a scene. */
	public static PaintRecorder record(PainterRenderer renderer, Scene scene, QualityTier tier) {
		PaintRecorder recorder = new PaintRecorder();
		renderer.target((UIPainter) Proxy.newProxyInstance(UIPainter.class.getClassLoader(), new Class<?>[] { UIPainter.class }, recorder));
		renderer.render(scene, tier);
		return recorder;
	}

	public static PainterRenderer selected() {
		return new UiPainterRenderer(factory(), "Sans");
	}

	public static PainterRenderer fallback() {
		return UiPainterRenderer.fallback(factory(), "Sans");
	}
}
