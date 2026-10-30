package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Shape;

/**
 * Spec 018 (T006; FR-001, FR-002, SC-002): the fire of the reference scene is made of square big pixels on one grid (the top one of a column cut to the
 * height of its flame), none over another, each at most 60 % opaque, so the game shows through it everywhere; nothing is drawn where the fire is cold; the
 * fire is behind everything.
 */
public class FireTranslucencyTest {

	private static boolean isFlame(SceneItem item) {
		return item.role() == Role.FLAME_TONGUE || item.role() == Role.FLAME_EMBER;
	}

	@Test
	public void everyCellIsABigPixelOfTheGridAtMostSixtyPercentOpaqueAndNoneLiesOverAnother() {
		for (QualityTier tier : new QualityTier[] { QualityTier.FULL, QualityTier.BALANCED }) {
			Scene scene = ReferenceScenes.flameReading().scene(ReferenceScenes.SCREEN, tier);
			double cell = FireField.cell(ReferenceScenes.SCREEN, tier);
			Set<String> taken = new HashSet<String>();
			Set<Double> tops = new HashSet<Double>();
			Double originX = null;
			int cells = 0;
			for (SceneItem item : scene.items()) {
				if (!isFlame(item)) {
					continue;
				}
				cells++;
				assertEquals(Shape.QUAD, item.shape(), "a big pixel is a quad");
				double x1 = Math.min(item.value(0), item.value(4));
				double x2 = Math.max(item.value(0), item.value(4));
				double y1 = Math.min(item.value(1), item.value(5));
				double y2 = Math.max(item.value(1), item.value(5));
				assertEquals(item.value(0), item.value(6), 1e-9, "vertical left edge: " + item);
				assertEquals(item.value(2), item.value(4), 1e-9, "vertical right edge");
				assertEquals(item.value(1), item.value(3), 1e-9, "horizontal top edge");
				assertEquals(item.value(5), item.value(7), 1e-9, "horizontal bottom edge");
				assertEquals(cell, x2 - x1, 1e-9, tier + ": one cell wide");
				assertTrue(y2 - y1 > 0.0 && y2 - y1 <= cell + 1e-9, tier + ": at most one cell high (the top cell of a flame is cut to its height)");
				assertTrue(y2 - y1 >= cell - 1e-9 || !tops.contains(Math.rint(x1 * 1000.0)), tier + ": one cut cell per column, at its top");
				if (y2 - y1 < cell - 1e-9) {
					tops.add(Math.rint(x1 * 1000.0));
				}
				if (originX == null) {
					originX = x1;
				}
				double gx = (x1 - originX) / cell;
				double gy = (ReferenceScenes.SCREEN.height() - y2) / cell;
				assertEquals(Math.rint(gx), gx, 1e-6, "on the grid");
				assertEquals(Math.rint(gy), gy, 1e-6, "on the grid");
				assertTrue(taken.add(Math.rint(gx) + ":" + Math.rint(gy)), tier + ": two cells at the same place " + item);
				assertTrue(item.alpha() > 0.0, "nothing is drawn where the fire is cold");
				assertTrue(item.alpha() <= 0.60 + 1e-9, tier + ": at most 60 % opaque (FR-002, SC-002): " + item);
			}
			assertTrue(cells > 0);
		}
	}

	@Test
	public void theFireIsBehindEverything() {
		Scene scene = ReferenceScenes.flameReading().scene(ReferenceScenes.SCREEN, QualityTier.FULL);
		boolean other = false;
		for (SceneItem item : scene.items()) {
			if (isFlame(item)) {
				assertFalse(other, "a fire cell after another item");
			} else {
				other = true;
			}
		}
	}
}
