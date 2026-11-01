package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;

/**
 * Spec 019 (T035, FR-020): the preview of a fire in the Advanced tab, without its canvas. It follows a changed tuning at its next picture, throws a burst
 * at the streak of its growth slider, maps that slider to the share of the score fire or of Arcade, holds still with Reduce motion, and ticks only while it
 * is shown.
 */
public class FirePreviewTest {

	private static final long MS = 1_000_000L;

	private static final class Ticks implements ScoreFire.Ticker {
		int started;
		int stopped;
		boolean running;

		public void start(Runnable tick) {
			this.started++;
			this.running = true;
		}

		public void stop() {
			this.stopped++;
			this.running = false;
		}
	}

	private final Ticks ticks = new Ticks();
	private long wall = 1_000_000_000L;

	private FirePreview preview(FireTuning tuning, boolean motion) {
		FirePreview preview = new FirePreview(null, tuning, this.ticks, () -> this.wall, System::nanoTime);
		preview.setMotion(motion);
		return preview;
	}

	private void run(FirePreview preview, int frames) {
		for (int i = 0; i < frames; i++) {
			this.wall += 33 * MS;
			preview.tick();
		}
	}

	@Test
	public void aChangedTuningIsUsedAtTheNextPicture() {
		FirePreview preview = preview(FireTuning.SCORE_DEFAULTS, true);
		preview.setGrowth(1.0);
		run(preview, 30);
		FireTuning faint = FireTuning.SCORE_DEFAULTS.with(FireParameter.OPACITY_MOST, 0.3);
		preview.setTuning(faint);
		assertEquals(faint, preview.flames().tuning());
		boolean any = false;
		for (SceneItem item : preview.scene(new Viewport(360, 120)).items()) {
			any = true;
			assertTrue(item.alpha() <= 0.3 + 1e-9, "the new most opacity is drawn at once: " + item.alpha());
		}
		assertTrue(any);
	}

	@Test
	public void theSparksButtonThrowsOneBurstAtTheStreakOfTheSlider() {
		FirePreview score = preview(FireTuning.SCORE_DEFAULTS, true);
		score.setGrowth(0.5);
		run(score, 3);
		assertEquals(12, score.streak(), "half the slider is 1.5 x 16 / 2 right notes");
		score.sparks();
		FlameState flames = score.flames();
		assertEquals(1, flames.bursts().size());
		assertEquals(12, flames.bursts().get(0).streak());
		assertEquals(flames.flameClockNanos(), flames.bursts().get(0).clockNanos(), "thrown now");

		FirePreview arcade = preview(FireTuning.ARCADE_DEFAULTS, true);
		arcade.setGrowth(1.0);
		arcade.sparks();
		assertEquals(FireTuning.ARCADE_DEFAULTS.integer(FireParameter.SPARKS_STREAK_CAP), arcade.flames().bursts().get(0).streak(), "a full slider throws the most");
	}

	@Test
	public void theGrowthSliderGivesTheStreakForTheScoreAndTheShareForArcade() {
		FirePreview score = preview(FireTuning.SCORE_DEFAULTS, true);
		for (double position : new double[] { 0.0, 0.25, 0.5, 2.0 / 3.0, 1.0 }) {
			score.setGrowth(position);
			assertEquals(Math.round(position * 24), score.streak());
			assertEquals(Math.min(1.0, score.streak() / 16.0), score.share(), 1e-12);
		}
		score.setGrowth(0.5);
		run(score, 60);
		assertEquals(0.75, score.flames().level(), 1e-9, "the level grows to the share of the streak");

		FirePreview arcade = preview(FireTuning.ARCADE_DEFAULTS, true);
		arcade.setGrowth(0.8);
		assertEquals(0.8, arcade.share(), 1e-12);
		run(arcade, 60);
		assertEquals((0.8 - 0.5) / 0.5, arcade.flames().level(), 1e-9, "Arcade's flames start at half of the maximum");
	}

	@Test
	public void withReduceMotionTheClockHoldsAndNoBurstIsDrawn() {
		FirePreview preview = preview(FireTuning.SCORE_DEFAULTS, false);
		preview.setGrowth(1.0);
		long clock = preview.flames().flameClockNanos();
		run(preview, 20);
		preview.sparks();
		assertEquals(clock, preview.flames().flameClockNanos(), "the fire clock does not move");
		assertTrue(preview.flames().bursts().isEmpty(), "no burst");
		assertFalse(preview.flames().motion());
		assertEquals(1.0, preview.flames().level(), 1e-9, "only the height follows the slider, at once");
	}

	@Test
	public void itTicksOnlyWhileShownAndWithMotion() {
		FirePreview preview = preview(FireTuning.SCORE_DEFAULTS, true);
		assertFalse(this.ticks.running, "not shown: no tick");
		preview.setShown(true);
		assertTrue(this.ticks.running);
		preview.setShown(false);
		assertFalse(this.ticks.running, "hidden: the ticks stop");
		assertTrue(this.ticks.stopped > 0);

		Ticks still = new Ticks();
		FirePreview reduced = new FirePreview(null, FireTuning.SCORE_DEFAULTS, still, () -> this.wall, System::nanoTime);
		reduced.setMotion(false);
		reduced.setShown(true);
		assertEquals(0, still.started, "Reduce motion: drawn still, no tick");
		preview.dispose();
		reduced.dispose();
	}
}
