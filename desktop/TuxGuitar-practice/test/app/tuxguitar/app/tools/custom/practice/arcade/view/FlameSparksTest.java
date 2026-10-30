package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;

/**
 * Spec 018 (T014; FR-002, FR-005, FR-006, FR-007, FR-008, FR-010, FR-011, SC-002, SC-005): the sparks thrown by each right note, read from the flame items
 * of a state with bursts against those of the same state without them.
 */
public class FlameSparksTest {

	private static final Viewport SCREEN = new Viewport(1280, 720);
	private static final long CLOCK = 5_000_000_000L;
	private static final long MS = 1_000_000L;
	private static final List<double[]> NO_TEXT = Collections.emptyList();

	private static FlameState state(FlameSetting setting, boolean motion, List<FlameState.Burst> bursts) {
		return new FlameState(setting, 0.8, CLOCK, motion, bursts);
	}

	private static List<FlameState.Burst> burst(long ageMs, int streak) {
		List<FlameState.Burst> bursts = new ArrayList<FlameState.Burst>();
		bursts.add(new FlameState.Burst(CLOCK - ageMs * MS, streak));
		return bursts;
	}

	/** The spark items: what the bursts add after the cells of the fire. */
	private static List<SceneItem> sparks(QualityTier tier, FlameState state, List<double[]> texts) {
		FlameState without = new FlameState(state.setting(), state.level(), state.flameClockNanos(), state.motion());
		List<SceneItem> all = FlameDrawing.items(SCREEN, tier, state, texts);
		int cells = FlameDrawing.items(SCREEN, tier, without, texts).size();
		return all.subList(cells, all.size());
	}

	@Test
	public void aRightNoteThrowsTwentyToFiftySparksThatAreGoneAfterOneSecond() {
		List<SceneItem> young = sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(200, 3)), NO_TEXT);
		assertTrue(young.size() >= 20 && young.size() <= 50, "a burst 0.2 s old: " + young.size() + " sparks (SC-005)");
		double cell = FireField.cell(SCREEN, QualityTier.FULL);
		for (SceneItem spark : young) {
			assertEquals(Role.FLAME_EMBER, spark.role());
			assertEquals(cell, spark.value(2) - spark.value(0), 1e-9, "one cell wide");
			assertEquals(cell, spark.value(5) - spark.value(1), 1e-9, "one cell high");
		}
		assertTrue(sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(1000, 3)), NO_TEXT).isEmpty(), "gone after 1 s");
		assertTrue(sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(1500, 3)), NO_TEXT).isEmpty());
	}

	@Test
	public void aLongerStreakThrowsMoreSparksAtMostFiftyPerNoteAndThreeHundredInTheAir() {
		int one = sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(100, 1)), NO_TEXT).size();
		int twelve = sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(100, 12)), NO_TEXT).size();
		int fifty = sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(100, 50)), NO_TEXT).size();
		assertTrue(twelve > one, "streak 12: " + twelve + " against " + one);
		assertTrue(fifty <= 50, "at most 50 per note: " + fifty);
		List<FlameState.Burst> many = new ArrayList<FlameState.Burst>();
		for (int i = 0; i < 20; i++) {
			many.add(new FlameState.Burst(CLOCK - (900 - i * 40) * MS, 12));
		}
		assertTrue(sparks(QualityTier.FULL, state(FlameSetting.FULL, true, many), NO_TEXT).size() <= 300, "at most 300 in the air");
	}

	@Test
	public void noSparkAtTheLowerTiersWithTheSettingOffOrWithReducedMotion() {
		assertTrue(sparks(QualityTier.BALANCED, state(FlameSetting.FULL, true, burst(200, 6)), NO_TEXT).isEmpty(), "balanced");
		assertTrue(FlameDrawing.items(SCREEN, QualityTier.PERFORMANCE, state(FlameSetting.FULL, true, burst(200, 6)), NO_TEXT).isEmpty(), "performance");
		assertTrue(FlameDrawing.items(SCREEN, QualityTier.FULL, state(FlameSetting.OFF, true, burst(200, 6)), NO_TEXT).isEmpty(), "off");
		assertTrue(sparks(QualityTier.FULL, state(FlameSetting.FULL, false, burst(200, 6)), NO_TEXT).isEmpty(), "reduced motion");
	}

	@Test
	public void discreetThrowsFewerSparks() {
		int full = sparks(QualityTier.FULL, state(FlameSetting.FULL, true, burst(150, 12)), NO_TEXT).size();
		int discreet = sparks(QualityTier.FULL, state(FlameSetting.DISCREET, true, burst(150, 12)), NO_TEXT).size();
		assertTrue(discreet > 0 && discreet < full, "discreet " + discreet + " against " + full);
	}

	@Test
	public void noSparkAboveTwelvePercentComesWithinFourPixelsOfAText() {
		ReferenceScenes.Reference reference = ReferenceScenes.flameReading();
		Scene plain = reference.scene(ReferenceScenes.SCREEN, QualityTier.FULL);
		List<double[]> texts = FlameDrawing.textBoxes(plain.items());
		assertFalse(texts.isEmpty());
		List<FlameState.Burst> many = new ArrayList<FlameState.Burst>();
		for (int i = 0; i < 12; i++) {
			many.add(new FlameState.Burst(CLOCK - (950 - i * 80) * MS, 1 + i));
		}
		int seen = 0;
		for (long later = 0; later < 1000; later += 50) {
			FlameState state = new FlameState(FlameSetting.FULL, 1.0, CLOCK + later * MS, true, many);
			for (SceneItem spark : sparks(QualityTier.FULL, state, texts)) {
				seen++;
				if (spark.alpha() <= 0.12) {
					continue;
				}
				for (double[] t : texts) {
					boolean meets = spark.value(0) < t[2] + 4.0 && spark.value(2) > t[0] - 4.0 && spark.value(1) < t[3] + 4.0 && spark.value(5) > t[1] - 4.0;
					assertFalse(meets, "a spark at " + spark.alpha() + " within 4 px of a text: " + spark);
				}
			}
		}
		assertTrue(seen > 0);
	}

	@Test
	public void theSameStateGivesTheSameSparks() {
		FlameState a = state(FlameSetting.FULL, true, burst(300, 7));
		FlameState b = state(FlameSetting.FULL, true, burst(300, 7));
		assertEquals(sparks(QualityTier.FULL, a, NO_TEXT).toString(), sparks(QualityTier.FULL, b, NO_TEXT).toString());
	}

	@Test
	public void aSparkOverACellOfTheFireNeverMakesItMoreThanEightyFivePercentOpaque() {
		List<FlameState.Burst> many = new ArrayList<FlameState.Burst>();
		for (int i = 0; i < 12; i++) {
			many.add(new FlameState.Burst(CLOCK - (950 - i * 80) * MS, 12));
		}
		for (long later = 0; later < 1000; later += 25) {
			FlameState state = new FlameState(FlameSetting.FULL, 1.0, CLOCK + later * MS, true, many);
			FlameState without = new FlameState(FlameSetting.FULL, 1.0, CLOCK + later * MS, true);
			Map<String, Double> cells = new HashMap<String, Double>();
			for (SceneItem cell : FlameDrawing.items(SCREEN, QualityTier.FULL, without, NO_TEXT)) {
				cells.put(Math.rint(cell.value(0)) + ":" + Math.rint(cell.value(5)), cell.alpha());
			}
			for (SceneItem spark : sparks(QualityTier.FULL, state, NO_TEXT)) {
				Double under = cells.get(Math.rint(spark.value(0)) + ":" + Math.rint(spark.value(5)));
				double a = under == null ? 0.0 : under;
				double combined = 1.0 - (1.0 - a) * (1.0 - spark.alpha());
				assertTrue(combined <= 0.85 + 1e-9, "a spark over a cell: " + combined);
			}
		}
	}
}
