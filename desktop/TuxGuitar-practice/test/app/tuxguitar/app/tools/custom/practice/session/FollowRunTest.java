package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun.Ended;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The lifecycle of a whole run of the tempo-following mode, window excepted (spec 002 T096): two runs one after the other, a stop during the count-in
 * and while following, a player that cannot start, the window that closes, an exception, a play of the learner, the player that stops, and the events
 * of the input, which pause the run but never because the player started. The player is the fake of the tests; the session thread is the real one and
 * the tests wait for what it does, never a fixed time.
 */
public class FollowRunTest {

	private static final long WAIT_SECONDS = 20;

	private TGContext context;
	private FakePlayerRun fake;
	private final BlockingQueue<FollowSession.Snapshot> snapshots = new LinkedBlockingQueue<FollowSession.Snapshot>();
	private final List<Ended> ends = new CopyOnWriteArrayList<Ended>();
	private FollowEvents events;
	private final CountDownLatch endSignal = new CountDownLatch(1);

	@BeforeEach
	public void setUp() {
		this.context = new TGContext();
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		this.fake = new FakePlayerRun(fixture, 100);
	}

	@AfterEach
	public void tearDown() {
		TGThreadManager.getInstance(this.context).dispose();
	}

	private FollowRun newRun(FakePlayerRun with) {
		return new FollowRun(this.context, with.session, with.player, FakePlayerRun.TRACK, 100, FakePlayerRun.BASE_TICK, this.snapshots::add, Runnable::run,
				this::ended, (loop, handler) -> {
					this.events = new FollowEvents(this.context, loop, handler, CurrentInputSelection.getInstance(this.context), PracticeTunerContext.getInstance(this.context));
					return this.events;
				}, null);
	}

	private void ended(Ended reason) {
		this.ends.add(reason);
		this.endSignal.countDown();
	}

	private void awaitEnd() throws InterruptedException {
		assertTrue(this.endSignal.await(WAIT_SECONDS, TimeUnit.SECONDS), "the run did not end");
	}

	private FollowSession.Snapshot await(Predicate<FollowSession.Snapshot> condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (System.nanoTime() < deadline) {
			FollowSession.Snapshot snapshot = this.snapshots.poll(250, TimeUnit.MILLISECONDS);
			if (snapshot != null && condition.test(snapshot)) {
				return snapshot;
			}
		}
		throw new AssertionError("the session did not reach the expected state");
	}

	private static void assertGivenBack(FakeMidiPlayerView view) {
		view.check();
	}

	/** What the player must look like once a run is over. */
	private final class FakeMidiPlayerView {
		private final FakePlayerRun run;

		FakeMidiPlayerView(FakePlayerRun run) {
			this.run = run;
		}

		void check() {
			assertFalse(this.run.player.isPlayerOnlyMuted(FakePlayerRun.TRACK), "the track is audible again");
			assertEquals(100, this.run.player.tempoPercent());
			assertFalse(this.run.player.countDownEnabled());
			assertTrue(this.run.player.loopEnabled());
			assertEquals(0, this.run.player.listenerCount(), "no listener of the run is left");
		}
	}

	// ------------------------------------------------------------------ start, stop

	@Test
	public void aRunStartsTheSessionAndThePlayerAndEndsOnceWhateverTheNumberOfStops() throws InterruptedException {
		FollowRun run = newRun(this.fake);

		assertEquals(FollowPlayback.Result.STARTED, run.start());
		assertTrue(this.fake.player.isRunning());
		assertTrue(this.fake.player.isPlayerOnlyMuted(FakePlayerRun.TRACK));
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		run.stop();
		run.stop();
		run.stop();

		assertEquals(List.of(Ended.STOPPED), this.ends);
		assertTrue(run.isFinished());
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertFalse(this.fake.player.isRunning());
		await(snapshot -> snapshot.state() == State.STOPPED);
	}

	@Test
	public void twoRunsOneAfterTheOtherEachStartCleanAndLeaveNothingBehind() throws InterruptedException {
		FollowRun first = newRun(this.fake);
		assertEquals(FollowPlayback.Result.STARTED, first.start());
		first.stop();
		assertGivenBack(new FakeMidiPlayerView(this.fake));

		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		FakePlayerRun other = new FakePlayerRun(fixture, 100);
		FollowRun second = newRun(other);
		assertEquals(FollowPlayback.Result.STARTED, second.start());
		await(snapshot -> snapshot.state() == State.COUNT_IN);
		second.stop();

		assertGivenBack(new FakeMidiPlayerView(other));
		assertEquals(List.of(Ended.STOPPED, Ended.STOPPED), this.ends);
		assertEquals(1, this.fake.player.listenerAdds());
		assertEquals(1, this.fake.player.listenerRemoves());
	}

	@Test
	public void stoppingDuringTheCountInGivesBackAndTheClockNeverStarted() {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.player.advance(800 * MS);
		assertTrue(this.fake.player.isCounting());

		run.stop();

		assertFalse(this.fake.player.isCounting());
		assertFalse(this.fake.clock.isStarted());
		assertGivenBack(new FakeMidiPlayerView(this.fake));
	}

	@Test
	public void stoppingWhileFollowingGivesBackWhateverTheSessionWasDoing() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.playEvent(0, 0);
		this.fake.player.advance(2_000 * MS);
		this.fake.now = FollowHarness.T0 + 2_000 * MS;
		for (int i = 0; i < 80; i++) {
			this.fake.advanceTo(this.fake.now);
			run.postFrame(FollowHarness.frameOf(this.fake.notes, this.fake.flagged, this.fake.now));
			this.fake.now += FollowHarness.HOP;
		}
		await(snapshot -> snapshot.state() == State.FOLLOWING);

		run.stop();

		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertEquals(List.of(Ended.STOPPED), this.ends);
		assertFalse(this.fake.clock.isStarted());
	}

	@Test
	public void aPlayerThatCannotStartGivesBackEverythingAndLeavesNoThreadOrListener() {
		this.fake.player.failOnPlay(true);
		FollowRun run = newRun(this.fake);

		assertEquals(FollowPlayback.Result.FAILED, run.start());

		assertTrue(run.isFinished());
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertEquals(0, this.ends.size(), "the caller got the failure from start()");
		assertEquals(FollowPlayback.Result.NOT_IDLE, run.start(), "a run is used once");
	}

	@Test
	public void aPlayerAlreadyPlayingIsNeverTakenOver() {
		this.fake.player.learnerPlays();
		FollowRun run = newRun(this.fake);

		assertEquals(FollowPlayback.Result.ALREADY_PLAYING, run.start());

		assertTrue(this.fake.player.isRunning(), "the learner's playback goes on");
		assertFalse(this.fake.player.isPlayerOnlyMuted(FakePlayerRun.TRACK));
	}

	// ------------------------------------------------------------------ what ends a run from outside

	@Test
	public void aPlayOfTheLearnerEndsTheRunCleanlyAndTheNormalPlaybackGoesOn() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		this.fake.player.learnerPlays();

		assertEquals(List.of(Ended.MANUAL_PLAY), this.ends);
		assertTrue(this.fake.player.isRunning(), "the playback of the learner is not stopped, and Follow does not restart");
		assertFalse(this.fake.player.isPlayerOnlyMuted(FakePlayerRun.TRACK));
		assertEquals(100, this.fake.player.tempoPercent());
		assertEquals(0, this.fake.player.listenerCount());
		await(snapshot -> snapshot.state() == State.STOPPED);
		run.stop();
		assertEquals(1, this.ends.size());
	}

	@Test
	public void thePlayerStoppingEndsTheRun() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.player.advance(3_000 * MS);

		this.fake.player.stopsByItself();

		assertEquals(List.of(Ended.PLAYER_STOPPED), this.ends);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		await(snapshot -> snapshot.state() == State.STOPPED);
	}

	@Test
	public void anExceptionOfTheSessionEndsTheRunAndGivesBackInsteadOfLeavingThePlayerPlaying() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.failDating = true;

		run.postFrame(new AnalysisFrame(1_000_000L, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, false));
		awaitEnd();

		assertEquals(List.of(Ended.FAILED), this.ends);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertFalse(this.fake.player.isRunning());
	}

	@Test
	public void anExceptionOfTheDisplayEndsTheRunToo() throws InterruptedException {
		FollowRun run = new FollowRun(this.context, this.fake.session, this.fake.player, FakePlayerRun.TRACK, 100, FakePlayerRun.BASE_TICK,
				snapshot -> {
					throw new IllegalStateException("the window is gone");
				}, Runnable::run, this::ended, null, null);
		run.start();
		run.postCommand(PracticeCommand.RESUME);
		awaitEnd();

		assertEquals(List.of(Ended.FAILED), this.ends);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
	}

	// ------------------------------------------------------------------ the events of the input

	@Test
	public void theStartOfThePlayerNeverPausesARunButTheTunerAndTheInputDo() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		this.events.processEvent(new MidiPlayerEvent(MidiPlayerEvent.NOTIFY_STARTED));
		this.events.processEvent(new MidiPlayerEvent(MidiPlayerEvent.NOTIFY_STOPPED));
		run.postCommand(PracticeCommand.RESUME);
		FollowSession.Snapshot after = await(snapshot -> true);
		assertEquals(State.COUNT_IN, after.state(), "the player's own start is not a reason to pause");

		this.events.tunerStateChanged(true);
		FollowSession.Snapshot paused = await(snapshot -> snapshot.state() == State.PAUSED);
		assertTrue(paused.pauseReasons().contains(PracticeSession.PauseReason.TUNER_OPEN));

		this.events.tunerStateChanged(false);
		await(snapshot -> snapshot.state() == State.READY_TO_RESUME);
		run.postCommand(PracticeCommand.RESUME);
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		CurrentInputSelection.getInstance(this.context).updateAssessment(new InputAssessment(InputAssessment.Status.UNUSABLE, InputAssessment.Reason.DEVICE_LOST));
		FollowSession.Snapshot lost = await(snapshot -> snapshot.state() == State.PAUSED);
		assertTrue(lost.pauseReasons().contains(PracticeSession.PauseReason.INPUT_UNUSABLE));
		run.stop();
		assertNotNull(this.events);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
	}

	// ------------------------------------------------------------------ the commands and the events that stop a run

	@Test
	public void theStopCommandEndsTheWholeRunNotOnlyTheSession() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		run.postCommand(PracticeCommand.STOP);
		awaitEnd();

		assertEquals(List.of(Ended.COMMAND), this.ends);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertFalse(this.fake.player.isRunning(), "the player does not go on playing without a session");
		assertTrue(run.isFinished());
	}

	@Test
	public void loadingAnotherSongEndsTheWholeRunAndGivesTheStateBack() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.player.advance(3_000 * MS);
		await(snapshot -> snapshot.state() == State.COUNT_IN || snapshot.state() == State.FOLLOWING);

		this.events.processEvent(new app.tuxguitar.editor.event.TGUpdateEvent(app.tuxguitar.editor.event.TGUpdateEvent.SONG_LOADED, null));
		awaitEnd();

		assertEquals(List.of(Ended.COMMAND), this.ends);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertFalse(this.fake.player.isRunning());
	}

	@Test
	public void theOtherEventsOfTheScoreNeverEndARun() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		for (int i = 0; i < 5; i++) {
			this.events.processEvent(new app.tuxguitar.editor.event.TGUpdateEvent(app.tuxguitar.editor.event.TGUpdateEvent.MEASURE_UPDATED, null));
			this.events.processEvent(new app.tuxguitar.editor.event.TGUpdateEvent(app.tuxguitar.editor.event.TGUpdateEvent.SELECTION, null));
		}
		run.postCommand(PracticeCommand.RESUME);
		FollowSession.Snapshot after = await(snapshot -> true);

		assertEquals(State.COUNT_IN, after.state());
		assertEquals(0, this.ends.size());
		run.stop();
	}

	@Test
	public void aRestartAsksTheWindowForANewRunAndNeverStartsOneByItself() throws InterruptedException {
		int[] restarts = new int[1];
		FollowRun run = new FollowRun(this.context, this.fake.session, this.fake.player, FakePlayerRun.TRACK, 100, FakePlayerRun.BASE_TICK, this.snapshots::add,
				Runnable::run, this::ended, null, () -> restarts[0]++);
		run.start();
		run.postCommand(PracticeCommand.RESTART);
		await(snapshot -> snapshot.state() == State.COUNT_IN);
		run.postCommand(PracticeCommand.RESUME);
		await(snapshot -> true);

		assertEquals(1, restarts[0]);
		assertEquals(0, this.ends.size(), "the run goes on until the window ends it and starts another");
		assertTrue(this.fake.player.isRunning());
		run.stop();
	}

	// ------------------------------------------------------------------ pause and resume of the whole run (spec 003 T021)

	@Test
	public void thePlayerAndTheSessionPauseAndResumeTogether() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.player.advance(3_000 * MS);
		await(snapshot -> snapshot.state() == State.COUNT_IN);

		run.pause();
		FollowSession.Snapshot paused = await(snapshot -> snapshot.state() == State.PAUSED);

		assertTrue(paused.pauseReasons().contains(PracticeSession.PauseReason.USER));
		assertFalse(this.fake.player.isRunning(), "the backing stops with the judging");
		assertTrue(this.fake.player.isPlayerOnlyMuted(FakePlayerRun.TRACK), "the practised track stays silent");
		assertEquals(0, this.ends.size(), "a pause is not an end");

		assertTrue(run.resume());
		await(snapshot -> snapshot.state() == State.COUNT_IN || snapshot.state() == State.FOLLOWING);

		assertTrue(this.fake.player.isRunning(), "the backing plays again");
		assertEquals(0, this.ends.size());
		run.stop();
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertEquals(List.of(Ended.STOPPED), this.ends);
	}

	@Test
	public void aPauseOrAResumeThatMeansNothingChangesNothing() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		assertFalse(run.resume(), "not started");
		run.pause();
		run.start();
		assertFalse(run.resume(), "not paused");
		run.stop();
		List<String> calls = this.fake.player.calls();
		run.pause();
		assertFalse(run.resume(), "ended");
		assertEquals(calls, this.fake.player.calls(), "an ended run does not touch the player again");
	}

	@Test
	public void aPlayerThatCannotStartAgainEndsTheRunWithAFailureAndGivesBack() throws InterruptedException {
		FollowRun run = newRun(this.fake);
		run.start();
		this.fake.player.advance(3_000 * MS);
		run.pause();
		await(snapshot -> snapshot.state() == State.PAUSED);
		this.fake.player.failOnResume(true);

		assertFalse(run.resume());

		assertEquals(List.of(Ended.FAILED), this.ends);
		assertGivenBack(new FakeMidiPlayerView(this.fake));
		assertTrue(run.isFinished());
	}
}
