package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameItemsAccess;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ReferenceScenes;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * Spec 018 (T031, research R10): the cells of the demo fire are filled in batches of one colour and one opacity, so that the real painter keeps the full
 * tier with the fine grid, neighbours on a row joined into one rectangle; every flame item is still covered once, with its colour and its opacity, and a
 * spark lying on a cell is filled after it.
 */
public class FlameCellsPaintTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final long CLOCK = 5_000_000_000L;
	private static final long MS = 1_000_000L;

	/** One filled rectangle, with the colour and the alpha the painter had then, in painting order. */
	private static final class Fill {
		final double x1;
		final double y1;
		final double x2;
		final double y2;
		final String colour;
		final int alpha;
		final int path;

		Fill(double x1, double y1, double x2, double y2, String colour, int alpha, int path) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.colour = colour;
			this.alpha = alpha;
			this.path = path;
		}

		boolean covers(SceneItem quad) {
			return quad.value(0) >= this.x1 - 0.01 && quad.value(4) <= this.x2 + 0.01 && Math.abs(quad.value(1) - this.y1) < 0.01 && Math.abs(quad.value(5) - this.y2) < 0.01;
		}

		double area() {
			return (this.x2 - this.x1) * (this.y2 - this.y1);
		}
	}

	/** Paints a scene with the selected or the fallback path and returns its rectangles and its number of paths. */
	private static final class Painted {
		final List<Fill> fills = new ArrayList<Fill>();
		int paths;

		Painted(Scene scene, boolean fallback) {
			String[] colour = { null };
			int[] alpha = { 255 };
			UIFactory factory = (UIFactory) Proxy.newProxyInstance(UIFactory.class.getClassLoader(), new Class<?>[] { UIFactory.class }, (p, m, a) -> {
				if (m.getName().equals("createColor")) {
					String name = String.format("#%02X%02X%02X", (Integer) a[0], (Integer) a[1], (Integer) a[2]);
					return Proxy.newProxyInstance(UIColor.class.getClassLoader(), new Class<?>[] { UIColor.class },
							(q, n, b) -> n.getName().equals("toString") ? name : n.getReturnType() == boolean.class ? (Object) false : null);
				}
				if (m.getName().equals("createFont")) {
					return Proxy.newProxyInstance(UIFont.class.getClassLoader(), new Class<?>[] { UIFont.class }, (q, n, b) -> n.getReturnType() == boolean.class ? (Object) false : null);
				}
				return null;
			});
			UIPainter painter = (UIPainter) Proxy.newProxyInstance(UIPainter.class.getClassLoader(), new Class<?>[] { UIPainter.class }, (p, m, a) -> {
				switch (m.getName()) {
					case "setBackground":
						colour[0] = String.valueOf(a[0]);
						return null;
					case "setAlpha":
						alpha[0] = (Integer) a[0];
						return null;
					case "initPath":
						this.paths++;
						return null;
					case "addRectangle":
						this.fills.add(new Fill((Float) a[0], (Float) a[1], (Float) a[0] + (Float) a[2], (Float) a[1] + (Float) a[3], colour[0], alpha[0], this.paths));
						return null;
					case "getFMWidth":
					case "getFMHeight":
						return 10f;
					default:
						return m.getReturnType() == float.class ? (Object) 0f : null;
				}
			});
			UiPainterRenderer renderer = fallback ? UiPainterRenderer.fallback(factory, "Sans") : new UiPainterRenderer(factory, "Sans");
			renderer.target(painter);
			renderer.render(scene, QualityTier.FULL);
		}
	}

	private static boolean flame(SceneItem item) {
		return item.role() == SceneItem.Role.FLAME_TONGUE || item.role() == SceneItem.Role.FLAME_EMBER;
	}

	private static Scene withSparks() {
		List<FlameState.Burst> bursts = new ArrayList<FlameState.Burst>();
		for (int i = 0; i < 12; i++) {
			bursts.add(new FlameState.Burst(CLOCK - (950 - i * 80) * MS, 12));
		}
		return FlameItemsAccess.fireAlone(SCREEN, QualityTier.FULL, new FlameState(FlameSetting.FULL, 1.0, CLOCK, true, bursts));
	}

	private static String colourOf(SceneItem item) {
		return String.format("#%06X", item.colour() & 0xFFFFFF);
	}

	private static int alphaOf(SceneItem item) {
		return (int) Math.round(Math.max(0.0, Math.min(1.0, item.alpha())) * 255.0);
	}

	/** The fills that cover the item with its colour and its opacity, in painting order. */
	private static List<Fill> fillsOf(Painted painted, SceneItem item) {
		List<Fill> found = new ArrayList<Fill>();
		for (Fill fill : painted.fills) {
			if (fill.covers(item) && fill.colour.equals(colourOf(item)) && fill.alpha == alphaOf(item)) {
				found.add(fill);
			}
		}
		return found;
	}

	private static List<SceneItem> flames(Scene scene) {
		List<SceneItem> flames = new ArrayList<SceneItem>();
		for (SceneItem item : scene.items()) {
			if (flame(item)) {
				flames.add(item);
			}
		}
		return flames;
	}

	/** Every flame item is covered by a rectangle of its colour and its opacity, and the rectangles cover nothing else, nothing twice. */
	private static void assertEveryFlameItemFilledOnce(Scene scene) {
		List<SceneItem> flames = flames(scene);
		assertTrue(flames.size() > 1000, "a full fire: " + flames.size());
		Painted painted = new Painted(scene, false);
		double items = 0.0;
		for (SceneItem item : flames) {
			assertFalse(fillsOf(painted, item).isEmpty(), "filled with its colour and its opacity: " + item);
			items += (item.value(4) - item.value(0)) * (item.value(5) - item.value(1));
		}
		double filled = 0.0;
		for (Fill fill : painted.fills) {
			filled += fill.area();
		}
		assertEquals(items, filled, items * 1e-6, "the rectangles cover the flame items exactly");
		assertTrue(painted.fills.size() < flames.size(), "neighbours of one colour on a row are joined: " + painted.fills.size() + " rectangles for " + flames.size() + " items");
	}

	@Test
	public void everyCellOfTheFireIsFilledOnceWithItsColourAndItsOpacity() {
		assertEveryFlameItemFilledOnce(ReferenceScenes.flameReading().scene(ReferenceScenes.SCREEN, QualityTier.FULL));
		assertEveryFlameItemFilledOnce(withSparks());
	}

	@Test
	public void theCellsAreFilledInBatchesFarFewerPathsThanItems() {
		Scene scene = ReferenceScenes.flameReading().scene(ReferenceScenes.SCREEN, QualityTier.FULL);
		int flames = 0;
		int others = 0;
		for (SceneItem item : scene.items()) {
			if (flame(item)) {
				flames++;
			} else if (item.shape() != SceneItem.Shape.TEXT) {
				others++;
			}
		}
		Painted painted = new Painted(scene, false);
		int flamePaths = painted.paths - others;
		assertTrue(flamePaths > 0 && flamePaths * 5 < flames, "the fire takes " + flamePaths + " paths for " + flames + " cells");
	}

	@Test
	public void whereTwoCellsMeetTheLaterOneIsFilledInALaterPath() {
		Scene scene = withSparks();
		Painted painted = new Painted(scene, false);
		Map<String, Integer> corner = new HashMap<String, Integer>();
		List<SceneItem> items = scene.items();
		int checked = 0;
		for (int i = 0; i < items.size(); i++) {
			SceneItem item = items.get(i);
			// cells of one grid meet only where they share their bottom-left corner (a spark on a cell, or on the cut top of a column)
			String key = Math.round(item.value(0) * 100) + ":" + Math.round(item.value(5) * 100);
			Integer earlier = corner.put(key, i);
			if (earlier != null) {
				List<Fill> under = fillsOf(painted, items.get(earlier));
				List<Fill> over = fillsOf(painted, item);
				assertTrue(over.get(over.size() - 1).path > under.get(0).path, "painted in the order of the scene where two cells meet: " + key);
				checked++;
			}
		}
		assertTrue(checked > 0, "some sparks lie on cells of the fire");
	}

	@Test
	public void theFallbackStillFillsNoCellOfTheFire() {
		assertEquals(0, new Painted(withSparks(), true).fills.size());
	}
}
