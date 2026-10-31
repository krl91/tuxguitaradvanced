package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/** Spec 019 (T017, FR-001, FR-002, FR-004, FR-005, SC-003): the scene of the fire at the bottom of the score, with the score's defaults. */
public class ScoreFireSceneTest {

	private static final List<double[]> NO_GUARD = Collections.emptyList();

	private static FlameState flames(FlameSetting setting, double level) {
		return new FlameState(setting, level, 2_500_000_000L, true, null, FireTuning.SCORE_DEFAULTS);
	}

	private static List<SceneItem> items(Viewport viewport, QualityTier tier, FlameState flames, List<double[]> guard) {
		return ScoreFireScene.scene(viewport, tier, flames, guard).items();
	}

	private static double top(SceneItem item) {
		return item.value(1);
	}

	private static double left(SceneItem item) {
		return item.value(0);
	}

	@Test
	public void nothingWhenOffOrAtThePerformanceTier() {
		Viewport viewport = new Viewport(1280, 800);
		assertTrue(items(viewport, QualityTier.FULL, null, NO_GUARD).isEmpty());
		assertTrue(items(viewport, QualityTier.FULL, flames(FlameSetting.OFF, 1.0), NO_GUARD).isEmpty());
		assertTrue(items(viewport, QualityTier.PERFORMANCE, flames(FlameSetting.FULL, 1.0), NO_GUARD).isEmpty());
	}

	@Test
	public void levelZeroGivesTheEmberRowsOnly() {
		Viewport viewport = new Viewport(1280, 800);
		double cell = FireField.cell(viewport, QualityTier.FULL, FireTuning.SCORE_DEFAULTS);
		List<SceneItem> items = items(viewport, QualityTier.FULL, flames(FlameSetting.FULL, 0.0), NO_GUARD);
		assertFalse(items.isEmpty());
		for (SceneItem item : items) {
			assertEquals(SceneItem.Role.FLAME_EMBER, item.role());
			assertTrue(top(item) >= viewport.height() - 2 * cell - 1e-9, "on the bottom two rows");
		}
	}

	@Test
	public void atFullHeightTheCornersReachAThirdOfTheScoreAndTheMiddleStaysLow() {
		for (Viewport viewport : new Viewport[] { new Viewport(1280, 800), new Viewport(800, 600), new Viewport(1500, 500), new Viewport(600, 300) }) {
			double cell = FireField.cell(viewport, QualityTier.FULL, FireTuning.SCORE_DEFAULTS);
			List<SceneItem> items = items(viewport, QualityTier.FULL, flames(FlameSetting.FULL, 1.0), NO_GUARD);
			double third = viewport.height() - viewport.height() / 3.0;
			double highestCorner = Double.MAX_VALUE;
			double emberTop = viewport.height() - 2 * cell;
			double cornerTongue = 0.0;
			double middleTongue = 0.0;
			for (SceneItem item : items) {
				assertTrue(top(item) >= third - 1e-6, viewport + ": never above a third of the score (FR-004)");
				double x = left(item);
				boolean corner = x < cell * 1.5 || x > viewport.width() - cell * 2.5;
				boolean middle = Math.abs(x + cell / 2.0 - viewport.width() / 2.0) <= viewport.width() / 20.0;
				if (corner) {
					highestCorner = Math.min(highestCorner, top(item));
					cornerTongue = Math.max(cornerTongue, emberTop - top(item));
				}
				if (middle) {
					middleTongue = Math.max(middleTongue, emberTop - top(item));
				}
			}
			assertTrue(highestCorner <= third + cell + 1e-6, viewport + ": the corners reach a third, within one cell: " + highestCorner + " for " + third);
			assertTrue(middleTongue <= 0.22 * cornerTongue + cell, viewport + ": the middle tenth at most 22 % of the corners: " + middleTongue + " / " + cornerTongue);
		}
	}

	@Test
	public void theOpacityStaysWithinItsMost() {
		Viewport viewport = new Viewport(1280, 800);
		for (SceneItem item : items(viewport, QualityTier.FULL, flames(FlameSetting.FULL, 1.0), NO_GUARD)) {
			assertTrue(item.alpha() <= FireColours.MOST + 1e-9);
		}
		for (SceneItem item : items(viewport, QualityTier.FULL, flames(FlameSetting.DISCREET, 1.0), NO_GUARD)) {
			assertTrue(item.alpha() <= FireColours.MOST * FlameDrawing.DISCREET + 1e-9);
		}
	}

	@Test
	public void aSmallScoreAreaKeepsTheFireLow() {
		Viewport low = new Viewport(600, 120);
		for (SceneItem item : items(low, QualityTier.FULL, flames(FlameSetting.FULL, 1.0), NO_GUARD)) {
			assertTrue(top(item) >= low.height() - 40.0 - 1e-6, "within 40 px");
		}
		Viewport tiny = new Viewport(600, 24);
		List<SceneItem> items = items(tiny, QualityTier.FULL, flames(FlameSetting.FULL, 1.0), NO_GUARD);
		assertFalse(items.isEmpty());
		for (SceneItem item : items) {
			assertEquals(SceneItem.Role.FLAME_EMBER, item.role(), "fewer than 8 cells high: ember rows only, whatever the level");
		}
	}

	@Test
	public void theGuardBoxesAreNeverCovered() {
		for (Viewport viewport : new Viewport[] { new Viewport(1280, 800), new Viewport(800, 600), new Viewport(1500, 500), new Viewport(600, 300) }) {
			double w = viewport.width();
			double h = viewport.height();
			List<double[]> guard = new ArrayList<double[]>(Arrays.asList(new double[] { 20, h - 60, 70, h - 10 }, new double[] { w / 2 - 20, h - 50, w / 2 + 20, h - 5 },
					new double[] { w - 220, h - 90, w - 10, h - 20 }));
			for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.DISCREET }) {
				List<FlameState.Burst> bursts = Arrays.asList(new FlameState.Burst(2_000_000_000L, 5), new FlameState.Burst(2_300_000_000L, 12));
				FlameState flames = new FlameState(setting, 1.0, 2_500_000_000L, true, bursts, FireTuning.SCORE_DEFAULTS);
				for (SceneItem item : items(viewport, QualityTier.FULL, flames, guard)) {
					if (item.alpha() <= 0.12) {
						continue;
					}
					double[] box = { item.value(0), item.value(1), item.value(4), item.value(5) };
					for (double[] g : guard) {
						boolean meets = box[0] < g[2] + 4 && box[2] > g[0] - 4 && box[1] < g[3] + 4 && box[3] > g[1] - 4;
						assertFalse(meets, viewport + " " + setting + ": an item at " + item.alpha() + " meets a guard box");
					}
				}
			}
		}
	}

	@Test
	public void theSameInputsGiveTheSameItems() {
		Viewport viewport = new Viewport(1280, 800);
		List<double[]> guard = Collections.singletonList(new double[] { 600, 700, 680, 780 });
		assertEquals(SceneCapture.of(ScoreFireScene.scene(viewport, QualityTier.FULL, flames(FlameSetting.FULL, 0.7), guard)),
				SceneCapture.of(ScoreFireScene.scene(viewport, QualityTier.FULL, flames(FlameSetting.FULL, 0.7), guard)));
	}
}
