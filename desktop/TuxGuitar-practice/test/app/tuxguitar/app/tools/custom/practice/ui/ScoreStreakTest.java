package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession;

/**
 * Spec 019 (T016, research R3, data model "ScoreStreak"): the streak of right notes of the score fire, fed with what the window already reads from each
 * snapshot. The snapshots of the sessions cannot be built outside their package, so the streak is fed here with the values the window takes from them: the
 * accepted count, the last passed event and its outcome, the verdict (Wait), and the index and status of each result (Follow).
 */
public class ScoreStreakTest {

	private static final PracticeSession.Verdict NONE = PracticeSession.Verdict.NONE;

	@Test
	public void waitAcceptancesCountOnceEach() {
		ScoreStreak streak = new ScoreStreak();
		streak.waitSnapshot(1, null, null, PracticeSession.Verdict.CORRECT);
		streak.waitSnapshot(2, null, null, PracticeSession.Verdict.CORRECT);
		streak.waitSnapshot(3, null, null, PracticeSession.Verdict.CORRECT);
		assertEquals(3, streak.streak());
		assertEquals(Arrays.asList(1, 2, 3), streak.takeBursts());
		// the same snapshot rendered again counts nothing
		streak.waitSnapshot(3, null, null, PracticeSession.Verdict.CORRECT);
		assertEquals(3, streak.streak());
		assertTrue(streak.takeBursts().isEmpty());
	}

	@Test
	public void aWrongVerdictOrASkipIsAMistake() {
		ScoreStreak streak = new ScoreStreak();
		streak.waitSnapshot(1, null, null, NONE);
		streak.waitSnapshot(2, null, null, NONE);
		streak.takeBursts();
		streak.waitSnapshot(2, null, null, PracticeSession.Verdict.WRONG);
		assertEquals(0, streak.streak());
		assertTrue(streak.takeBursts().isEmpty());

		streak.waitSnapshot(3, null, null, NONE);
		assertEquals(1, streak.streak());
		Object skipped = new Object();
		streak.waitSnapshot(3, skipped, PracticeSession.Outcome.SKIPPED, NONE);
		assertEquals(0, streak.streak());
	}

	@Test
	public void anAutomaticPassChangesNothing() {
		ScoreStreak streak = new ScoreStreak();
		streak.waitSnapshot(1, null, null, NONE);
		streak.waitSnapshot(2, null, null, NONE);
		streak.takeBursts();
		streak.waitSnapshot(2, new Object(), PracticeSession.Outcome.AUTO_PASSED, NONE);
		assertEquals(2, streak.streak());
		assertTrue(streak.takeBursts().isEmpty());
	}

	@Test
	public void aWrongVerdictHeldOnScreenCountsOnce() {
		ScoreStreak streak = new ScoreStreak();
		streak.waitSnapshot(0, null, null, PracticeSession.Verdict.WRONG);
		streak.waitSnapshot(0, null, null, PracticeSession.Verdict.WRONG);
		assertEquals(1, streak.judged(), "one mistake, shown for a second");
		streak.waitSnapshot(1, null, null, NONE);
		assertEquals(2, streak.judged());
		assertEquals(1, streak.streak());
	}

	@Test
	public void followResultsCountOnceWhenTheyLeavePending() {
		ScoreStreak streak = new ScoreStreak();
		streak.followResult(0, FollowSession.Status.PENDING);
		assertEquals(0, streak.streak());
		streak.followResult(0, FollowSession.Status.MATCHED);
		streak.followResult(1, FollowSession.Status.MATCHED);
		streak.followResult(2, FollowSession.Status.MATCHED);
		assertEquals(3, streak.streak());
		assertEquals(Arrays.asList(1, 2, 3), streak.takeBursts());
		// seen again at the next snapshot: nothing
		streak.followResult(0, FollowSession.Status.MATCHED);
		streak.followResult(1, FollowSession.Status.MATCHED);
		assertEquals(3, streak.streak());
		assertTrue(streak.takeBursts().isEmpty());

		streak.followResult(3, FollowSession.Status.WRONG_PITCH);
		assertEquals(0, streak.streak());
		streak.followResult(4, FollowSession.Status.MATCHED);
		streak.followResult(5, FollowSession.Status.MISSED);
		assertEquals(0, streak.streak());
		streak.followResult(3, FollowSession.Status.WRONG_PITCH);
		streak.followResult(6, FollowSession.Status.MATCHED);
		assertEquals(1, streak.streak(), "a mistake seen again is not counted again");
	}

	@Test
	public void resetGivesZeroAndNoBursts() {
		ScoreStreak streak = new ScoreStreak();
		streak.waitSnapshot(1, null, null, NONE);
		streak.followResult(0, FollowSession.Status.MATCHED);
		streak.reset();
		assertEquals(0, streak.streak());
		assertEquals(0, streak.judged());
		assertEquals(Collections.emptyList(), streak.takeBursts());
		// after a reset, the accepted count of the new run starts again
		streak.waitSnapshot(1, null, null, NONE);
		assertEquals(1, streak.streak());
		streak.followResult(0, FollowSession.Status.MATCHED);
		assertEquals(2, streak.streak(), "the results of the new run are new");
	}
}
