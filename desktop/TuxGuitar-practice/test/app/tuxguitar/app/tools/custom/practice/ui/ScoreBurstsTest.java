package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FlameState;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;
import app.tuxguitar.ui.widget.UICanvas;

/**
 * Spec 019 (T031, FR-006, SC-005): the score fire throws exactly one burst of sparks per right note, none when it is off, and lets go of each burst once
 * its sparks are dead. The streak is fed as the window feeds it (see {@link ScoreStreakTest}), and the fire runs on a fake score and a fake wall clock.
 */
public class ScoreBurstsTest {

	private static final long MS = 1_000_000L;
	private static final PracticeSession.Verdict NONE = PracticeSession.Verdict.NONE;

	/** The streak and the fire as the window holds them. */
	private static final class Window {
		final ScoreStreak streak = new ScoreStreak();
		final ScoreFire fire;
		long wall;
		int thrown;

		Window(FlameSetting setting) {
			ScoreFire.Score port = new ScoreFire.Score() {
				public UICanvas canvas() {
					return null;
				}

				public List<double[]> guardBoxes(PracticeEvent current, PracticeEvent next, PaletteDodge.Box palette) {
					return Collections.emptyList();
				}

				public void redraw(boolean overlayOnly) {
				}
			};
			ScoreFire.Ticker ticker = new ScoreFire.Ticker() {
				public void start(Runnable tick) {
				}

				public void stop() {
				}
			};
			this.fire = new ScoreFire(null, port, ticker, () -> this.wall, System::nanoTime);
			this.streak.reset();
			this.fire.start(setting, FireTuning.SCORE_DEFAULTS, true);
		}

		void show(long step, boolean paused) {
			this.wall += step;
			List<Integer> bursts = this.streak.takeBursts();
			this.thrown += bursts.size();
			this.fire.update(this.streak.streak(), this.streak.judged(), bursts, paused, null, null, null);
		}

		int bursts() {
			FlameState flames = this.fire.flames();
			return flames != null ? flames.bursts().size() : 0;
		}
	}

	/** Ten right notes, two wrong and one skip, each picture shown twice as the window does, 20 ms apart. */
	private static Window waitRun(FlameSetting setting) {
		Window window = new Window(setting);
		int accepted = 0;
		String script = "RRWRRRSRRWRRR";
		for (char step : script.toCharArray()) {
			if (step == 'R') {
				accepted++;
				window.streak.waitSnapshot(accepted, null, null, PracticeSession.Verdict.CORRECT);
			} else if (step == 'W') {
				window.streak.waitSnapshot(accepted, null, null, PracticeSession.Verdict.WRONG);
			} else {
				window.streak.waitSnapshot(accepted, new Object(), PracticeSession.Outcome.SKIPPED, NONE);
			}
			window.show(20 * MS, false);
			window.show(20 * MS, false);
			window.streak.waitSnapshot(accepted, null, null, NONE);
			window.show(20 * MS, false);
		}
		return window;
	}

	@Test
	public void aWaitRunThrowsOneBurstPerRightNote() {
		Window full = waitRun(FlameSetting.FULL);
		assertEquals(10, full.thrown);
		assertEquals(10, full.bursts(), "all ten still alive: the run lasts less than a second");
		Window off = waitRun(FlameSetting.OFF);
		assertEquals(0, off.bursts());
		assertNull(off.fire.flames());
	}

	@Test
	public void aFollowRunThrowsOneBurstPerMatchedResult() {
		FollowSession.Status[] statuses = { FollowSession.Status.MATCHED, FollowSession.Status.MATCHED, FollowSession.Status.MISSED, FollowSession.Status.MATCHED,
				FollowSession.Status.WRONG_PITCH, FollowSession.Status.MATCHED, FollowSession.Status.MATCHED, FollowSession.Status.MATCHED, FollowSession.Status.MISSED,
				FollowSession.Status.MATCHED, FollowSession.Status.MATCHED, FollowSession.Status.MATCHED };
		for (FlameSetting setting : new FlameSetting[] { FlameSetting.FULL, FlameSetting.OFF }) {
			Window window = new Window(setting);
			for (int i = 0; i < statuses.length; i++) {
				window.streak.followResult(i, FollowSession.Status.PENDING);
				window.show(20 * MS, false);
				// each snapshot carries every result so far: the old ones come again
				for (int j = 0; j <= i; j++) {
					window.streak.followResult(j, statuses[j]);
				}
				window.show(20 * MS, false);
			}
			assertEquals(setting == FlameSetting.OFF ? 0 : 9, window.bursts(), setting + ": one burst per matched result");
		}
	}

	@Test
	public void theBurstsGoOnceTheirSparksAreDead() {
		Window window = waitRun(FlameSetting.FULL);
		assertEquals(10, window.bursts());
		for (int i = 0; i < 40; i++) {
			window.wall += 33 * MS;
			window.fire.tick();
		}
		assertEquals(0, window.bursts(), "after more than a second, no burst is kept");
	}

	@Test
	public void aPauseHoldsTheSparksWhereTheyAre() {
		Window window = new Window(FlameSetting.FULL);
		window.streak.waitSnapshot(1, null, null, PracticeSession.Verdict.CORRECT);
		window.show(20 * MS, false);
		window.show(200 * MS, false);
		window.show(10 * MS, true);
		FlameState before = window.fire.flames();
		window.show(500 * MS, true);
		window.wall += 500 * MS;
		window.fire.tick();
		FlameState after = window.fire.flames();
		assertEquals(before.flameClockNanos(), after.flameClockNanos(), "the fire clock is held");
		assertEquals(1, after.bursts().size());
		assertEquals(before.bursts().get(0).clockNanos(), after.bursts().get(0).clockNanos());
		// a second of wall time passed in the pause, the spark still lives
		window.show(100 * MS, false);
		assertTrue(window.bursts() == 1, "the burst outlives the pause");
	}
}
