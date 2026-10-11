package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ReferenceScenes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Theme;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * What the renderers really send to the painter for the target on the neck (spec 003 FR-065, SC-023): not the numbers of the scene, but the drawing calls
 * with the alpha, the colour, the width and the circle that were set when they were made. A painter that keeps its state records each filled circle and each
 * outlined circle with it; the tests look for the disk and the outline of every target of the scene among them.
 */
public class TargetPrimitivesTest {

	/** One circle sent to the painter, with the state of the painter when it was drawn: {@code UIPainter.addCircle(x, y, width)} takes the CENTRE and the diameter (SWT, JavaFX and Qt alike). */
	private static final class Circle {
		final int style;
		final double x;
		final double y;
		final double width;
		final int alpha;
		final int background;
		final int foreground;
		final double lineWidth;

		Circle(int style, double x, double y, double width, int alpha, int background, int foreground, double lineWidth) {
			this.style = style;
			this.x = x;
			this.y = y;
			this.width = width;
			this.alpha = alpha;
			this.background = background;
			this.foreground = foreground;
			this.lineWidth = lineWidth;
		}
	}

	/** A painter that keeps the state a real one keeps, and lists the circles. */
	private static final class StatefulPainter implements InvocationHandler {
		final List<Circle> circles = new ArrayList<Circle>();
		int alpha = 255;
		int background;
		int foreground;
		double lineWidth = 1.0;
		int style;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			switch (method.getName()) {
				case "setAlpha":
					this.alpha = (Integer) args[0];
					return null;
				case "setBackground":
					this.background = rgb((UIColor) args[0]);
					return null;
				case "setForeground":
					this.foreground = rgb((UIColor) args[0]);
					return null;
				case "setLineWidth":
					this.lineWidth = (Float) args[0];
					return null;
				case "initPath":
					this.style = args.length == 0 ? UIPainter.PATH_DRAW : (Integer) args[0];
					return null;
				case "addCircle":
					this.circles.add(new Circle(this.style, (Float) args[0], (Float) args[1], (Float) args[2], this.alpha, this.background, this.foreground, this.lineWidth));
					return null;
				case "getFMWidth":
				case "getFMHeight":
					return 10f;
				default:
					return method.getReturnType() == float.class ? (Object) 0f : null;
			}
		}
	}

	private static int rgb(UIColor colour) {
		return (colour.getRed() << 16) | (colour.getGreen() << 8) | colour.getBlue();
	}

	private static UIFactory factory() {
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

	private static StatefulPainter paint(PainterRenderer renderer, Scene scene, QualityTier tier) {
		StatefulPainter state = new StatefulPainter();
		renderer.target((UIPainter) Proxy.newProxyInstance(UIPainter.class.getClassLoader(), new Class<?>[] { UIPainter.class }, state));
		renderer.render(scene, tier);
		return state;
	}

	private static Circle find(StatefulPainter painter, int style, double cx, double cy, double diameter) {
		for (Circle circle : painter.circles) {
			if (circle.style == style && Math.abs(circle.x - cx) < 0.01 && Math.abs(circle.y - cy) < 0.01
					&& Math.abs(circle.width - diameter) < 0.01) {
				return circle;
			}
		}
		return null;
	}

	private void check(PainterRenderer renderer, Scene scene, QualityTier tier, String what) {
		PaintRecorder painter = PaintRecorder.record(renderer, scene, tier);
		int targets = 0;
		for (SceneItem item : scene.withRole(Role.TARGET_RECT)) {
			targets++;
			double height = 2.0 * item.value(3);
			PaintRecorder.Polygon core = painter.roundedAt(item.value(0), item.value(1), 2.0 * item.value(2));
			assertNotNull(core, what + ": the filled rounded rectangle was sent to the painter, centred");
			assertEquals(255, core.alpha, what + ": fully opaque when it is drawn");
			assertEquals(item.colour(), core.fill, what + ": in the colour of its string");
			assertEquals(2.0 * item.value(2), core.maxX() - core.minX(), 1e-3, what + ": as wide as the scene says");
			assertEquals(height, core.maxY() - core.minY(), 1e-3, what + ": as high as the scene says");
			assertTrue(height >= 24.0 - 1e-9 && height <= 42.0 + 1e-9 || height >= 48.0 - 1e-9, what + ": a height of the rule");
			SceneItem outline = null;
			for (SceneItem candidate : scene.forEvent(item.eventIndex(), Role.TARGET_OUTLINE)) {
				if (Math.abs(candidate.value(0) - item.value(0)) < 1e-9 && Math.abs(candidate.value(1) - item.value(1)) < 1e-9) {
					outline = candidate;
				}
			}
			assertNotNull(outline);
			PaintRecorder.Polygon ring = null;
			for (PaintRecorder.Polygon candidate : painter.outlines) {
				if (Math.abs((candidate.minX() + candidate.maxX()) / 2.0 - outline.value(0)) < 0.01 && Math.abs((candidate.minY() + candidate.maxY()) / 2.0 - outline.value(1)) < 0.01) {
					ring = candidate;
				}
			}
			assertNotNull(ring, what + ": the outline was sent too");
			assertEquals(255, ring.alpha);
			assertEquals(outline.colour(), ring.fill, what + ": the contrasting colour");
			assertTrue(ring.lineWidth >= 2.0 && ring.lineWidth <= 4.0, what + ": two to four pixels: " + ring.lineWidth);
		}
		assertTrue(targets > 0 || !scene.withRole(Role.OPEN_BAR).isEmpty(), what + ": the scene has a target, or the bar of an open string (which has none: FR-069)");
	}

	@Test
	public void theSelectedAndTheFallbackRenderersSendAnOpaqueCentredCorrectlyColouredCoreAndItsOutlineInEveryTierThemeAndSize() {
		SongFixture song = new SongFixture(3);
		song.quarter(1, 0, 3, 5);
		song.chord(1, 1, new int[] { 6, 5, 4, 2 }, new int[] { 3, 5, 5, 4 });
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		for (Viewport viewport : new Viewport[] { new Viewport(640, 360), new Viewport(1280, 720), new Viewport(2560, 1440) }) {
			for (double uiScale : new double[] { 1.0, 2.0 }) {
				for (Theme theme : Theme.values()) {
					for (boolean mono : new boolean[] { false, true }) {
						for (QualityTier tier : QualityTier.values()) {
							for (int stage = 0; stage < 2; stage++) {
								long now = stage == 0 ? fixture.onset(0) - 700 * SceneFixture.MS : fixture.onset(1) - 300 * SceneFixture.MS;
								Scene scene = fixture.scene(mono).withTheme(theme).withUiScale(uiScale).build(now, viewport, stage == 0 ? null
										: app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess.completedBefore(1, fixture.events.size()), 100, tier);
								String what = viewport.width() + "px x" + uiScale + " " + theme + (mono ? " mono " : " ") + tier + " stage " + stage;
								check(new UiPainterRenderer(factory(), "Sans"), scene, tier, "selected " + what);
								check(UiPainterRenderer.fallback(factory(), "Sans"), scene, tier, "fallback " + what);
							}
						}
					}
				}
			}
		}
	}

	@Test
	public void everyReferenceSceneKeepsItsTargetOpaqueThroughTheGuardedRendererToo() {
		for (ReferenceScenes.Reference reference : ReferenceScenes.all()) {
			Scene scene = reference.scene(false, QualityTier.PERFORMANCE);
			GuardedRenderer guarded = new GuardedRenderer(new UiPainterRenderer(factory(), "Sans"), UiPainterRenderer.fallback(factory(), "Sans"), false);
			check(guarded, scene, QualityTier.PERFORMANCE, reference.name);
		}
	}
}
