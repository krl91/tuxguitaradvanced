package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.ui.FlameLevel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;

/**
 * Spec 019 (T018, FR-003, FR-009, SC-002): the level of the score fire follows the streak of right notes, {@code min(1, streak / 16)}, and falls to nothing
 * within a second after a mistake. A scripted clock at 30 updates per second.
 */
public class ScoreFireLevelTest {

	private static final long STEP = 1_000_000_000L / 30;

	private long wall = 5_000_000_000L;
	private final FlameLevel level = new FlameLevel(FireTuning.SCORE_DEFAULTS);

	private void run(int streak, int judged, double seconds) {
		int steps = (int) Math.round(seconds * 30);
		for (int i = 0; i < steps; i++) {
			this.wall += STEP;
			this.level.update(Math.min(1.0, streak / 16.0), judged, this.wall);
		}
	}

	@Test
	public void noStreakNoFire() {
		run(0, 0, 2.0);
		assertEquals(0.0, this.level.level(), 0.0);
		run(0, 5, 2.0);
		assertEquals(0.0, this.level.level(), 0.0, "mistakes only");
	}

	@Test
	public void eachRightNoteRaisesTheFireToFullAtSixteen() {
		run(0, 0, 0.1);
		double previous = 0.0;
		for (int streak = 1; streak <= 16; streak++) {
			run(streak, streak, 0.6);
			assertEquals(streak / 16.0, this.level.level(), 1e-9, "streak " + streak);
			assertTrue(this.level.level() > previous);
			previous = this.level.level();
		}
		run(20, 20, 2.0);
		assertEquals(1.0, this.level.level(), 0.0, "full, and it holds there");
	}

	@Test
	public void aMistakeAtFullHeightPutsTheFireOutWithinASecond() {
		run(0, 0, 0.1);
		run(16, 16, 1.0);
		assertEquals(1.0, this.level.level(), 0.0);
		run(0, 17, 1.0 + 1.0 / 30);
		assertEquals(0.0, this.level.level(), 0.0, "out within 1.0 s (+ one step)");
	}

	@Test
	public void aPauseKeepsTheLevel() {
		run(0, 0, 0.1);
		run(8, 8, 1.0);
		double before = this.level.level();
		this.level.hold(this.wall);
		run(0, 9, 3.0);
		assertEquals(before, this.level.level(), 0.0, "held");
		this.level.resume(this.wall);
		run(8, 9, 0.1);
		assertEquals(before, this.level.level(), 1e-9, "goes on from the same level");
	}
}
