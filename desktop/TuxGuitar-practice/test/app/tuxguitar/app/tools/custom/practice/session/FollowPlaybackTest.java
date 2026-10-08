package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback.EndReason;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback.Result;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback.State;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;

/**
 * The life of a tempo-following run on the player (spec 002 T096): what the run changes in the player, in which order, and that everything is given
 * back exactly once however the run ends. The player is the fake of the tests: no thread, no sleep, no device.
 */
public class FollowPlaybackTest {

	private static final long MS = 1_000_000L;
	private static final int TRACK = 2;
	private static final int OTHER_TRACK = 1;
	private static final long START_TICK = 960L + 3840L;

	private final List<EndReason> ends = new ArrayList<EndReason>();

	private FakeMidiPlayer player() {
		FakeTempoClock clock = new FakeTempoClock(ticks -> ticks * 500_000_000.0 / 960.0, 960L, 38_400L);
		return new FakeMidiPlayer(clock).withInitialState(100, false, true).countDownNanos(2_000 * MS);
	}

	private FollowPlayback run(FakeMidiPlayer player, int percent) {
		return new FollowPlayback(player, TRACK, percent, START_TICK, this.ends::add);
	}

	private static void assertGivenBack(FakeMidiPlayer player, int percent, boolean countDown, boolean loop) {
		assertFalse(player.isPlayerOnlyMuted(TRACK), "the track is audible again");
		assertEquals(percent, player.tempoPercent());
		assertEquals(countDown, player.countDownEnabled());
		assertEquals(loop, player.loopEnabled());
		assertEquals(0, player.listenerCount(), "no listener left");
		assertFalse(player.isRunning());
	}

	// ------------------------------------------------------------------ what a run changes, and in which order

	@Test
	public void aRunMutesOnlyThePractisedTrackInThePlayerBeforeItPlaysAndSetsWhatItNeeds() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);

		assertEquals(Result.STARTED, run.start());

		assertEquals(State.PLAYING, run.state());
		assertTrue(player.isPlayerOnlyMuted(TRACK));
		assertTrue(player.isSilent(TRACK));
		assertFalse(player.isSilent(OTHER_TRACK), "the other tracks stay audible");
		assertEquals(70, player.tempoPercent());
		assertTrue(player.countDownEnabled(), "a count-in is asked for even when the learner switched it off");
		assertFalse(player.loopEnabled(), "a loop would restart the song under the run");
		assertEquals(START_TICK, player.tickPosition());
		List<String> calls = player.calls();
		assertTrue(calls.indexOf("mute-" + TRACK + "=true") < calls.indexOf("play"), "mute before play: " + calls);
		assertTrue(calls.indexOf("tick=" + START_TICK) < calls.indexOf("play"), "position before play: " + calls);
		assertTrue(calls.indexOf("percent=70") < calls.indexOf("play"), "tempo before play: " + calls);
		assertEquals(1, player.listenerAdds());
	}

	@Test
	public void aTrackTheSongItselfMutesStaysMutedByTheSongWhenTheRunEnds() {
		FakeMidiPlayer player = player().songMutes(TRACK);
		FollowPlayback run = run(player, 100);
		run.start();
		run.stop();
		assertFalse(player.isPlayerOnlyMuted(TRACK));
		assertTrue(player.isSilent(TRACK), "the mute of the song is not the run's to remove");
	}

	@Test
	public void stopGivesEverythingBackExactlyOnceAndStopsThePlayer() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);
		run.start();

		run.stop();
		List<String> afterFirst = player.calls();
		run.stop();
		run.stop();

		assertEquals(State.ENDED, run.state());
		assertGivenBack(player, 100, false, true);
		assertEquals(afterFirst, player.calls(), "a second stop changes nothing");
		assertEquals(1, player.listenerRemoves());
		assertEquals(1, player.calls().stream().filter("mute-2=false"::equals).count());
		assertEquals(0, this.ends.size(), "the owner's own stop is not an end to tell");
	}

	// ------------------------------------------------------------------ the lifecycle

	@Test
	public void twoSuccessiveRunsEachStartCleanAndLeaveThePlayerAsTheyFoundIt() {
		FakeMidiPlayer player = player();
		FollowPlayback first = run(player, 70);
		assertEquals(Result.STARTED, first.start());
		first.stop();
		assertGivenBack(player, 100, false, true);

		FollowPlayback second = run(player, 100);
		assertEquals(Result.STARTED, second.start());
		assertEquals(100, player.tempoPercent());
		assertTrue(player.isPlayerOnlyMuted(TRACK));
		second.stop();

		assertGivenBack(player, 100, false, true);
		assertEquals(2, player.listenerAdds());
		assertEquals(2, player.listenerRemoves());
		assertEquals(Result.NOT_IDLE, first.start(), "a run is used once");
	}

	@Test
	public void stoppingDuringTheCountInStopsTheCountInAndGivesEverythingBack() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);
		run.start();
		player.advance(800 * MS);
		assertTrue(player.isCounting());

		run.stop();

		assertFalse(player.isCounting());
		assertFalse(player.fakeClock().isStarted(), "the clock never started");
		assertGivenBack(player, 100, false, true);
	}

	@Test
	public void stoppingWhileFollowingGivesEverythingBackAndTheClockStops() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);
		run.start();
		player.advance(2_000 * MS);
		player.advance(1_000 * MS);
		assertTrue(player.fakeClock().isReady());

		run.stop();

		assertFalse(player.fakeClock().isStarted());
		assertGivenBack(player, 100, false, true);
	}

	@Test
	public void aStartThatFailsGivesBackWhatItAlreadyChangedAndTellsNothingMore() {
		FakeMidiPlayer player = player().failOnPlay(true);
		FollowPlayback run = run(player, 70);

		assertEquals(Result.FAILED, run.start());

		assertEquals(State.ENDED, run.state());
		assertGivenBack(player, 100, false, true);
		assertEquals(0, this.ends.size());
		assertEquals(1, player.listenerAdds());
		assertEquals(1, player.listenerRemoves());
	}

	@Test
	public void aRuntimeExceptionInTheMiddleOfTheStartIsGivenBackToo() {
		FakeMidiPlayer player = player().failOnTickPosition(true);
		FollowPlayback run = run(player, 70);

		assertEquals(Result.FAILED, run.start());

		assertGivenBack(player, 100, false, true);
		assertFalse(player.calls().contains("play"), "the player was never started");
	}

	@Test
	public void aPlayerThatIsAlreadyPlayingIsNeverTakenOver() {
		FakeMidiPlayer player = player();
		player.learnerPlays();
		List<String> before = player.calls();
		FollowPlayback run = run(player, 70);

		assertEquals(Result.ALREADY_PLAYING, run.start());

		assertEquals(before, player.calls(), "nothing was changed");
		assertTrue(player.isRunning(), "the learner's playback goes on");
		assertEquals(0, player.listenerAdds());
	}

	// ------------------------------------------------------------------ what ends a run from outside

	@Test
	public void theStartNotificationOfTheRunsOwnPlayIsNotAPlayOfTheLearner() {
		FakeMidiPlayer player = player().autoStartNotification(false);
		FollowPlayback run = run(player, 70);
		run.start();

		player.deliverStartNotification();

		assertEquals(State.PLAYING, run.state());
		assertEquals(0, this.ends.size());
		assertTrue(player.isPlayerOnlyMuted(TRACK));
	}

	@Test
	public void aPlayOfTheLearnerEndsTheRunOnceGivesBackItsStateAndLetsThePlaybackGoOn() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);
		run.start();

		player.learnerPlays();
		player.learnerPlays();

		assertEquals(State.ENDED, run.state());
		assertEquals(List.of(EndReason.MANUAL_PLAY), this.ends, "told once");
		assertFalse(player.isPlayerOnlyMuted(TRACK));
		assertEquals(100, player.tempoPercent());
		assertEquals(0, player.listenerCount());
		assertTrue(player.isRunning(), "the normal playback goes on and is not restarted by the run");
		assertFalse(player.calls().contains("stop"), "the run did not stop the learner's playback");
		run.stop();
		assertEquals(1, this.ends.size());
	}

	@Test
	public void thePlayerStoppingByItselfEndsTheRunOnceAndGivesEverythingBack() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);
		run.start();
		player.advance(3_000 * MS);

		player.stopsByItself();
		player.stopsByItself();

		assertEquals(List.of(EndReason.PLAYER_STOPPED), this.ends);
		assertGivenBack(player, 100, false, true);
		run.stop();
		assertEquals(1, this.ends.size());
		assertNotEquals(0, player.listenerRemoves());
	}

	@Test
	public void theLearnerStoppingThePlayerDuringTheCountInEndsTheRun() {
		FakeMidiPlayer player = player();
		FollowPlayback run = run(player, 70);
		run.start();
		player.advance(500 * MS);

		player.stopsByItself();

		assertEquals(List.of(EndReason.PLAYER_STOPPED), this.ends);
		assertGivenBack(player, 100, false, true);
	}

	@Test
	public void aWindowClosedWhileTheRunStartsIsAStopLikeAnyOther() {
		FakeMidiPlayer player = player().autoStartNotification(false);
		FollowPlayback run = run(player, 70);
		run.start();

		run.stop();
		player.deliverStartNotification();

		assertEquals(0, this.ends.size(), "a late notification after the stop is nothing");
		assertGivenBack(player, 100, false, true);
	}

	// ------------------------------------------------------------------ pause and resume (spec 003 T020, T021, Arcade US5.2)

	private FollowPlayback playing(FakeMidiPlayer player, int percent) throws InterruptedException {
		FollowPlayback run = run(player, percent);
		assertEquals(Result.STARTED, run.start());
		player.advance(2_000 * MS);
		player.advance(1_000 * MS);
		assertTrue(player.fakeClock().isReady());
		return run;
	}

	@Test
	public void aPauseKeepsEverythingAsTheRunSetItAndResumeContinuesFromTheSamePlace() throws InterruptedException {
		FakeMidiPlayer player = player();
		FollowPlayback run = playing(player, 70);
		double before = player.fakeClock().positionNow();

		run.pause();

		assertEquals(State.PAUSED, run.state());
		assertFalse(player.isRunning());
		assertTrue(player.isPlayerOnlyMuted(TRACK), "the practised track stays silent while paused");
		assertEquals(70, player.tempoPercent());
		assertEquals(1, player.listenerCount(), "the run is still listening");
		assertEquals(0, this.ends.size(), "the stop caused by the pause is not an end");
		assertTrue(player.fakeClock().isStalled(player.fakeClock().nowNanos()));
		player.advance(10_000 * MS);
		assertEquals(Math.floor(before), player.fakeClock().positionNow(), 1.0, "the position does not move while paused");

		assertTrue(run.resume());

		assertEquals(State.PLAYING, run.state());
		assertTrue(player.isRunning());
		assertEquals(0, this.ends.size(), "its own start after the pause is not a play of the learner");
		assertEquals(Math.floor(before), (double) player.tickPosition(), 1.0, "it plays again from where it stopped");
		player.advance(2_000 * MS);
		assertFalse(player.fakeClock().isReady() && player.isCounting(), "the count-in of the resume runs first");
		player.advance(1_000 * MS);
		assertEquals(Math.floor(before) + 1920.0, player.fakeClock().positionNow(), 40.0, "then the clock goes on at the same speed");
	}

	@Test
	public void pausingTwiceOrResumingWithoutAPauseChangesNothing() throws InterruptedException {
		FakeMidiPlayer player = player();
		FollowPlayback run = playing(player, 70);

		assertFalse(run.resume(), "nothing to resume");
		run.pause();
		List<String> afterFirst = player.calls();
		run.pause();
		assertEquals(afterFirst, player.calls(), "a second pause does nothing");
		assertTrue(run.resume());
		List<String> afterResume = player.calls();
		assertFalse(run.resume(), "a second resume does nothing");
		assertEquals(afterResume, player.calls());
	}

	@Test
	public void stoppingWhilePausedGivesBackEverythingExactlyOnce() throws InterruptedException {
		FakeMidiPlayer player = player();
		FollowPlayback run = playing(player, 70);
		run.pause();

		run.stop();
		run.stop();

		assertGivenBack(player, 100, false, true);
		assertEquals(1, player.calls().stream().filter("mute-2=false"::equals).count());
		assertEquals(0, this.ends.size());
		assertFalse(run.resume(), "an ended run does not resume");
	}

	@Test
	public void aPlayOfTheLearnerWhilePausedEndsTheRunAndTheirPlaybackGoesOn() throws InterruptedException {
		FakeMidiPlayer player = player();
		FollowPlayback run = playing(player, 70);
		run.pause();

		player.learnerPlays();

		assertEquals(List.of(EndReason.MANUAL_PLAY), this.ends);
		assertFalse(player.isPlayerOnlyMuted(TRACK));
		assertEquals(100, player.tempoPercent());
		assertTrue(player.isRunning(), "the learner's playback is not stopped");
		assertEquals(0, player.listenerCount());
	}

	@Test
	public void aPauseDuringTheCountInAndALateStopNotificationAreBothHandled() throws InterruptedException {
		FakeMidiPlayer player = player().autoStopNotification(false).autoStartNotification(false);
		FollowPlayback run = run(player, 70);
		run.start();
		player.deliverStartNotification();
		player.advance(800 * MS);
		assertTrue(player.isCounting());

		run.pause();
		assertFalse(player.isCounting(), "the count-in stops with the pause");
		assertTrue(run.resume());
		player.deliverStopNotification();           // the stop caused by the pause arrives after the new play was asked
		player.deliverStartNotification();          // and then the start of the new play

		assertEquals(State.PLAYING, run.state());
		assertEquals(0, this.ends.size(), "a late stop of the pause is not the end of the run");
		player.advance(2_500 * MS);
		assertTrue(player.fakeClock().isReady(), "the count-in ran again, then the clock started");
		run.stop();
		assertGivenBack(player, 100, false, true);
	}

	@Test
	public void aPlayerThatCannotStartAgainAfterAPauseGivesBackEverything() throws InterruptedException {
		FakeMidiPlayer player = player().failOnResume(true);
		FollowPlayback run = playing(player, 70);
		run.pause();

		assertFalse(run.resume());

		assertEquals(State.ENDED, run.state());
		assertGivenBack(player, 100, false, true);
		assertEquals(0, this.ends.size(), "the caller got the failure from resume()");
	}

	@Test
	public void theEndOfTheSongWhilePausedIsNotMistakenForAnEndOfTheRunButStopStillGivesBack() throws InterruptedException {
		FakeMidiPlayer player = player();
		FollowPlayback run = playing(player, 70);
		run.pause();

		player.deliverStopNotification();

		assertEquals(State.PAUSED, run.state());
		run.stop();
		assertGivenBack(player, 100, false, true);
	}
}
