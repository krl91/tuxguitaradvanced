package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * Whole Arcade runs on the fake player (spec 003 T013, T014): the score, the streak and the result from the real tempo-following session, an error
 * that moves nothing, a pause that scores nothing, two runs of the same play that give the same result (SC-004).
 */
public class ArcadeRunFunctionalTest {

	private static SongFixture notes(int count) {
		SongFixture fixture = new SongFixture((count + 3) / 4);
		for (int i = 0; i < count; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 5);
		}
		return fixture;
	}

	private static ArcadeRun arcade(FakePlayerRun run) {
		return new ArcadeRun(run.sequence, ArcadeScoring.standard());
	}

	@Test
	public void aPerfectRunScoresTheMaximumAndTheStreakIsTheNumberOfEvents() {
		FakePlayerRun run = new FakePlayerRun(notes(12), 100);
		run.start();
		for (int i = 0; i < 12; i++) {
			run.playEvent(i, 0);
		}
		run.runAll();

		ArcadeRun arcade = arcade(run);
		ArcadeRun.View view = arcade.update(run.last);
		ArcadeResult result = arcade.result(run.last);

		assertEquals(12, view.streak());
		assertEquals(12, view.bestStreak());
		assertEquals(10 * 100 + 2 * 150, view.score(), "the multiplier steps up after ten");
		assertEquals(view.score(), result.maxScore());
		assertEquals(100, result.percent());
		assertEquals(12, result.summary().onTime());
		assertEquals(12, result.bestStreak());
	}

	@Test
	public void goodBadAndMissedNotesScoreAndResetTheStreakAsDocumented() {
		FakePlayerRun run = new FakePlayerRun(notes(8), 100);
		run.start();
		run.playEvent(0, 0);                                            // on time: 100
		run.playEvent(1, -150);                                         // early: 60
		run.play(run.target(2), 70, 600 * MS);                          // wrong: 0, streak reset
		// event 3 is not played: missed
		run.playEvent(4, 150);                                          // late: 60, a new streak of 1
		run.playEvent(5, 0);                                            // on time: 100, streak 2
		run.play(run.target(5) + 250 * MS, 71, 120 * MS);               // an extra note: nothing
		run.playEvent(6, 0);                                            // on time: 100, streak 3
		run.playEvent(7, 0);                                            // on time: 100, streak 4
		run.runAll();

		ArcadeRun arcade = arcade(run);
		ArcadeRun.View view = arcade.update(run.last);

		assertEquals(ArcadeVerdict.ON_TIME, view.states().get(0).verdict());
		assertEquals(ArcadeVerdict.EARLY, view.states().get(1).verdict());
		assertEquals(ArcadeVerdict.WRONG, view.states().get(2).verdict());
		assertEquals(ArcadeVerdict.MISSED, view.states().get(3).verdict());
		assertEquals(ArcadeVerdict.LATE, view.states().get(4).verdict());
		assertEquals(100 + 60 + 0 + 0 + 60 + 100 + 100 + 100, view.score());
		assertEquals(4, view.streak());
		assertEquals(4, view.bestStreak(), "the first streak was 2, the second reached 4");
		assertEquals(1, view.extras(), "the extra note cost nothing and broke nothing");
		assertEquals(4, view.states().get(7).streakAfter());
	}

	@Test
	public void theBestStreakIsKeptWhenItIsFollowedByAShorterOne() {
		FakePlayerRun run = new FakePlayerRun(notes(8), 100);
		run.start();
		for (int i = 0; i < 5; i++) {
			run.playEvent(i, 0);                        // five good notes
		}
		// event 5 is missed
		run.playEvent(6, 0);
		run.playEvent(7, 0);                            // then two
		run.runAll();

		ArcadeRun.View view = arcade(run).update(run.last);

		assertEquals(2, view.streak());
		assertEquals(5, view.bestStreak());
		assertEquals(5, arcade(run).result(run.last).bestStreak());
	}

	@Test
	public void aMistakeNeverDelaysTheEventsAfterIt() {
		FakePlayerRun run = new FakePlayerRun(notes(6), 100);
		run.start();
		run.playEvent(0, 0);
		run.play(run.target(1), 70, 600 * MS);          // wrong
		// event 2 not played
		run.playEvent(3, 0);
		run.playEvent(4, 0);
		run.playEvent(5, 0);
		run.runAll();

		ArcadeRun.View view = arcade(run).update(run.last);

		for (int i = 3; i < 6; i++) {
			assertEquals(ArcadeVerdict.ON_TIME, view.states().get(i).verdict(), "event " + i);
			assertEquals(0L, view.states().get(i).offsetNanos(), "judged at its own time, not moved by the mistakes before it");
		}
		assertEquals(100 + 100 + 100 + 100, view.score());
		assertEquals(3, view.streak());
	}

	@Test
	public void theEventsOfAPauseAreNotJudgedAndNeitherScoreNorBreakTheStreak() {
		FakePlayerRun run = new FakePlayerRun(notes(6), 100);
		run.start();
		for (int i = 0; i < 6; i++) {
			run.playEvent(i, 0);
		}
		run.runUntil(run.target(1) + 250 * MS);
		run.session.pause(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runUntil(run.target(3) + 250 * MS);
		run.session.resume(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runAll();

		ArcadeRun.View view = arcade(run).update(run.last);

		assertEquals(ArcadeVerdict.NOT_JUDGED, view.states().get(2).verdict());
		assertEquals(ArcadeVerdict.NOT_JUDGED, view.states().get(3).verdict());
		assertEquals(0, view.states().get(2).points());
		assertEquals(4, view.judged());
		assertEquals(4, view.streak(), "the pause neither extended nor reset it");
		assertEquals(400, view.score());
		assertEquals(400, arcade(run).result(run.last).maxScore(), "and the maximum counts only what was judged");
	}

	@Test
	public void theViewIsAPureFunctionOfTheSnapshotAndTwoRunsOfTheSamePlayGiveTheSameResult() {
		int[] scores = new int[2];
		String[] verdicts = new String[2];
		for (int pass = 0; pass < 2; pass++) {
			FakePlayerRun run = new FakePlayerRun(notes(10), 100);
			run.start();
			run.playEvent(0, 0);
			run.playEvent(1, 90);
			run.play(run.target(2), 70, 600 * MS);
			run.playEvent(4, -120);
			for (int i = 5; i < 10; i++) {
				run.playEvent(i, 0);
			}
			run.runAll();
			ArcadeRun arcade = arcade(run);
			ArcadeRun.View first = arcade.update(run.last);
			ArcadeRun.View again = arcade.update(run.last);
			assertEquals(first.score(), again.score(), "asking twice changes nothing");
			assertEquals(first.streak(), again.streak());
			scores[pass] = arcade.result(run.last).score();
			StringBuilder sb = new StringBuilder();
			for (ArcadeRun.EventState state : first.states()) {
				sb.append(state.verdict()).append(state.points()).append(' ');
			}
			verdicts[pass] = sb.toString();
		}
		assertEquals(scores[0], scores[1]);
		assertEquals(verdicts[0], verdicts[1]);
		assertTrue(scores[0] > 0);
	}

	@Test
	public void nothingIsKeptAnywhereAfterARun() {
		java.nio.file.Path home = java.nio.file.Paths.get(System.getProperty("user.dir"));
		long before = filesIn(home);
		FakePlayerRun run = new FakePlayerRun(notes(4), 100);
		run.start();
		for (int i = 0; i < 4; i++) {
			run.playEvent(i, 0);
		}
		run.runAll();
		ArcadeResult result = arcade(run).result(run.last);
		assertEquals(400, result.score());
		assertEquals(before, filesIn(home), "no file appeared in the working directory (the result lives in memory only)");
	}

	private static long filesIn(java.nio.file.Path directory) {
		try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(directory, 2)) {
			return stream.filter(java.nio.file.Files::isRegularFile).filter(p -> !p.toString().contains("/target/")).count();
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}
}
