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

/**
 * Spec 019 (T030, FR-006, FR-009): the sparks of the right notes on the score, with the score's defaults. They rise from the bottom of the fire
 * ({@code sparks.start} 0), more with the streak, gone within {@code sparks.life}, and never over a guard box. The sparks of a picture are the items it has
 * beyond the same picture with no burst: they come after the cells of the fire.
 */
public class ScoreSparksTest {

	private static final Viewport VIEWPORT = new Viewport(1280, 800);
	private static final long NOW = 5_000_000_000L;
	private static final List<double[]> NO_GUARD = Collections.emptyList();

	private static FlameState flames(FlameSetting setting, double level, boolean motion, List<FlameState.Burst> bursts) {
		return new FlameState(setting, level, NOW, motion, bursts, FireTuning.SCORE_DEFAULTS);
	}

	private static List<SceneItem> items(QualityTier tier, FlameState flames, List<double[]> guard) {
		return ScoreFireScene.scene(VIEWPORT, tier, flames, guard).items();
	}

	/** The spark items of a picture: what it has beyond the same picture without bursts, which it starts with. */
	private static List<SceneItem> sparks(QualityTier tier, FlameSetting setting, double level, boolean motion, List<FlameState.Burst> bursts, List<double[]> guard) {
		List<SceneItem> without = items(tier, flames(setting, level, motion, null), guard);
		List<SceneItem> with = items(tier, flames(setting, level, motion, bursts), guard);
		assertTrue(with.size() >= without.size());
		for (int i = 0; i < without.size(); i++) {
			assertEquals(without.get(i).toString(), with.get(i).toString(), "the cells of the fire are the same with sparks");
		}
		return new ArrayList<SceneItem>(with.subList(without.size(), with.size()));
	}

	private static List<FlameState.Burst> one(long age, int streak) {
		return Collections.singletonList(new FlameState.Burst(NOW - age, streak));
	}

	private static double cell() {
		return FireField.cell(VIEWPORT, QualityTier.FULL, FireTuning.SCORE_DEFAULTS);
	}

	/** The top edge of a row of the score's fire, row 0 the bottom one. */
	private static double rowTop(int row) {
		return VIEWPORT.height() - (row + 1) * cell();
	}

	@Test
	public void aBurstStartsAtTheBottomOfTheFireAndRises() {
		int embers = FireTuning.SCORE_DEFAULTS.integer(FireParameter.EMBER_ROWS);
		// discreet, so that a spark over the hottest cells still shows (full, the fire hides it there)
		List<SceneItem> born = sparks(QualityTier.FULL, FlameSetting.DISCREET, 1.0, true, one(0L, 5), NO_GUARD);
		assertFalse(born.isEmpty(), "a burst throws sparks");
		for (SceneItem spark : born) {
			assertEquals(rowTop(embers), spark.value(1), 1e-6, "at age 0 every spark is on the first row above the embers: the bottom of the fire");
		}
		List<SceneItem> older = sparks(QualityTier.FULL, FlameSetting.DISCREET, 1.0, true, one(500_000_000L, 5), NO_GUARD);
		assertFalse(older.isEmpty());
		for (SceneItem spark : older) {
			assertTrue(spark.value(1) < rowTop(embers) - 4 * cell(), "half a second later the sparks have risen: " + spark.value(1));
		}
	}

	@Test
	public void noSparkOnceItsLifeIsOver() {
		assertTrue(sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, one(1_000_000_000L, 5), NO_GUARD).isEmpty());
		assertTrue(sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, one(1_500_000_000L, 5), NO_GUARD).isEmpty());
		assertFalse(sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, one(990_000_000L, 5), NO_GUARD).isEmpty(), "still there just before");
	}

	@Test
	public void moreSparksWithTheStreakUpToTwelve() {
		assertEquals(20, FlameDrawing.sparkCount(FireTuning.SCORE_DEFAULTS, 0));
		assertEquals(50, FlameDrawing.sparkCount(FireTuning.SCORE_DEFAULTS, 12));
		assertEquals(50, FlameDrawing.sparkCount(FireTuning.SCORE_DEFAULTS, 40));
		// at level 0 nothing of the fire is above the embers, so every spark shows
		assertEquals(22, sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, one(0L, 1), NO_GUARD).size());
		assertEquals(50, sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, one(0L, 12), NO_GUARD).size());
		assertEquals(50, sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, one(0L, 30), NO_GUARD).size());
		assertEquals(30, sparks(QualityTier.FULL, FlameSetting.DISCREET, 0.0, true, one(0L, 12), NO_GUARD).size(), "60 % of them discreet");
	}

	@Test
	public void atMostThreeHundredInTheAir() {
		List<FlameState.Burst> bursts = new ArrayList<FlameState.Burst>();
		for (int k = 19; k >= 0; k--) {
			bursts.add(new FlameState.Burst(NOW - k * 40_000_000L, 12));
		}
		assertEquals(300, sparks(QualityTier.FULL, FlameSetting.FULL, 0.0, true, bursts, NO_GUARD).size());
	}

	@Test
	public void noSparkAtTheBalancedTierWithoutMotionOrWhenOff() {
		assertTrue(sparks(QualityTier.BALANCED, FlameSetting.FULL, 0.5, true, one(0L, 12), NO_GUARD).isEmpty());
		assertTrue(sparks(QualityTier.FULL, FlameSetting.FULL, 0.5, false, one(0L, 12), NO_GUARD).isEmpty());
		assertTrue(items(QualityTier.FULL, flames(FlameSetting.OFF, 0.5, true, one(0L, 12)), NO_GUARD).isEmpty());
	}

	@Test
	public void aHeldFireClockKeepsTheSparksStill() {
		// in a pause the fire clock does not move: the picture depends on that clock only, never on the wall time
		List<FlameState.Burst> bursts = Arrays.asList(new FlameState.Burst(NOW - 300_000_000L, 4), new FlameState.Burst(NOW - 100_000_000L, 5));
		String first = SceneCapture.of(ScoreFireScene.scene(VIEWPORT, QualityTier.FULL, flames(FlameSetting.FULL, 0.4, true, bursts), NO_GUARD));
		String later = SceneCapture.of(ScoreFireScene.scene(VIEWPORT, QualityTier.FULL, flames(FlameSetting.FULL, 0.4, true, bursts), NO_GUARD));
		assertEquals(first, later);
		assertFalse(sparks(QualityTier.FULL, FlameSetting.FULL, 0.4, true, bursts, NO_GUARD).isEmpty());
	}

	@Test
	public void noSparkOverAGuardBox() {
		double w = VIEWPORT.width();
		double h = VIEWPORT.height();
		// boxes where the sparks of the corners rise
		List<double[]> guard = Arrays.asList(new double[] { 0, h - 300, 200, h - 120 }, new double[] { w - 200, h - 260, w, h - 100 },
				new double[] { w / 2 - 40, h - 80, w / 2 + 40, h - 30 });
		for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.DISCREET }) {
			for (long age = 0L; age < 1_000_000_000L; age += 100_000_000L) {
				for (SceneItem spark : sparks(QualityTier.FULL, setting, 0.6, true, one(age, 12), guard)) {
					if (spark.alpha() <= 0.12) {
						continue;
					}
					double[] box = { spark.value(0), spark.value(1), spark.value(4), spark.value(5) };
					for (double[] g : guard) {
						boolean meets = box[0] < g[2] + 4 && box[2] > g[0] - 4 && box[1] < g[3] + 4 && box[3] > g[1] - 4;
						assertFalse(meets, setting + " age " + age + ": a spark at " + spark.alpha() + " meets a guard box");
					}
				}
			}
		}
	}
}
