package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;

/**
 * Pause and resume of the player and of the judging together (spec 003 T020, T021, Arcade US5.2) on the fake player and its clock: the position stops,
 * nothing is judged meanwhile, the count-in runs again, and the rest of the score is judged at the times of the new playing.
 */
public class FollowPauseFunctionalTest {

	private static SongFixture notes(int count) {
		SongFixture fixture = new SongFixture((count + 3) / 4);
		for (int i = 0; i < count; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 5);
		}
		return fixture;
	}

	/** The wall time at which the player reaches an event, on the clock as it is now. */
	private static long wallOf(FakePlayerRun run, int event) {
		return run.clock.wallNanosAt(FakePlayerRun.BASE_TICK + run.timeline.targetTicks(event));
	}

	private static void pause(FakePlayerRun run) {
		run.playback.pause();
		run.session.pause(PracticeSession.PauseReason.USER);
	}

	private static void resume(FakePlayerRun run) {
		assertTrue(run.playback.resume());
		run.session.resolve(PracticeSession.PauseReason.USER);
		run.session.resume(null);
	}

	@Test
	public void theScoreIsJudgedAtTheTimesOfTheNewPlayingAfterAPauseAndTheTimeOfThePauseCountsForNothing() {
		FakePlayerRun run = new FakePlayerRun(notes(8), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, 0);
		run.runUntil(run.target(1) + 300 * MS);
		assertEquals(Status.MATCHED, run.last.results().get(1).status());

		pause(run);
		long frozen = (long) run.clock.positionNow();
		run.playEvent(2, 0);                                     // a note at the old time of event 2: played while paused, it is nobody's
		run.runUntil(run.now + 3_000 * MS);
		assertEquals(State.PAUSED, run.last.state());
		assertEquals(frozen, (long) run.clock.positionNow(), "the position did not move while paused");
		assertEquals(0, run.last.counts().extra(), "nothing is judged while paused");
		assertEquals(0, run.last.counts().missed());

		resume(run);
		run.runUntil(run.now + 100 * MS);
		assertEquals(State.FOLLOWING, run.last.state(), "the session goes on");
		// the count-in of the resume is running: the clock is stalled and the session judges nothing
		assertFalse(run.clock.isStalled(run.clock.nowNanos()) && run.last.counts().extra() > 0);
		run.runUntil(run.now + 2_100 * MS);
		assertFalse(run.clock.isStalled(run.clock.nowNanos()), "the clock runs again after the count-in");
		for (int i = 2; i < 8; i++) {
			run.play(wallOf(run, i), run.sequence.getEvents().get(i).getExpectedMidi(), 600 * MS);
		}
		run.runUntil(wallOf(run, 7) + 2_000 * MS);

		for (int i = 0; i < 8; i++) {
			assertEquals(Status.MATCHED, run.last.results().get(i).status(), "event " + i);
			assertEquals(TimingVerdict.ON_TIME, run.last.results().get(i).timing(), "event " + i);
		}
		assertEquals(0, run.last.counts().extra());
		assertEquals(State.COMPLETE, run.last.state());
	}

	@Test
	public void aNoteDuringTheCountInOfTheResumeIsNotJudged() {
		FakePlayerRun run = new FakePlayerRun(notes(6), 100);
		run.start();
		run.playEvent(0, 0);
		run.runUntil(run.target(0) + 300 * MS);
		pause(run);
		run.runUntil(run.now + 500 * MS);
		resume(run);
		run.play(run.now + 500 * MS, 71, 200 * MS);              // inside the count-in
		run.runUntil(run.now + 1_500 * MS);

		assertEquals(0, run.last.counts().extra(), "the count-in is not judged");
		assertEquals(0, run.last.counts().wrongPitch());
	}

	@Test
	public void theEndOfTheRunWhilePausedGivesTheStateBack() {
		FakePlayerRun run = new FakePlayerRun(notes(4), 100);
		run.start();
		run.runUntil(run.target(0) + 300 * MS);
		pause(run);

		run.playback.stop();

		assertFalse(run.player.isPlayerOnlyMuted(FakePlayerRun.TRACK));
		assertEquals(100, run.player.tempoPercent());
		assertEquals(0, run.player.listenerCount());
	}
}
