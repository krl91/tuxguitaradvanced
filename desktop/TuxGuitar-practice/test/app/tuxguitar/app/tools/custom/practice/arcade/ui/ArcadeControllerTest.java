package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;

/**
 * The life of an Arcade run without a window (spec 003 T052, US1, US5): start, pause, resume, stop, and the end for every cause, reported exactly once, with a
 * result only when the whole passage was judged and the song came to its end; two successive runs; a start that fails.
 */
public class ArcadeControllerTest {

	private static final class FakeRun implements ArcadeRunPort {
		FollowPlayback.Result startResult = FollowPlayback.Result.STARTED;
		boolean resumable = true;
		final List<String> calls = new ArrayList<String>();
		ArcadeController controller;
		FollowRun.Ended endOnStop = FollowRun.Ended.STOPPED;

		public FollowPlayback.Result start() {
			this.calls.add("start");
			return this.startResult;
		}

		public void pause() {
			this.calls.add("pause");
		}

		public boolean resume() {
			this.calls.add("resume");
			return this.resumable;
		}

		public void stop() {
			this.calls.add("stop");
			// the real run reports the end through its own listener, once
			this.controller.ended(this.endOnStop);
		}
	}

	private static final class FakeOutcome implements ArcadeController.Outcome {
		boolean complete;
		final ArcadeResult result = null;

		public boolean isComplete() {
			return this.complete;
		}

		public ArcadeResult result() {
			return this.result;
		}
	}

	private static final class Recorder implements ArcadeController.Listener {
		final List<String> events = new ArrayList<String>();
		FollowRun.Ended endedReason;
		int ended;

		public void stateChanged(ArcadeController.State state) {
			this.events.add(state.name());
		}

		public void ended(FollowRun.Ended reason, ArcadeResult result) {
			this.ended++;
			this.endedReason = reason;
			this.events.add("ended " + reason + (result == null ? " no result" : " result"));
		}

		public void startFailed(FollowPlayback.Result result) {
			this.events.add("failed " + result);
		}
	}

	private static ArcadeController make(FakeRun run, FakeOutcome outcome, Recorder recorder) {
		ArcadeController controller = new ArcadeController(run, outcome, recorder);
		run.controller = controller;
		return controller;
	}

	@Test
	public void aRunStartsPausesResumesAndStopsAndTheEndIsReportedOnce() {
		FakeRun run = new FakeRun();
		Recorder recorder = new Recorder();
		ArcadeController controller = make(run, new FakeOutcome(), recorder);
		assertEquals(ArcadeController.State.READY, controller.state());

		controller.pause();
		controller.resume();
		assertEquals(List.of(), run.calls, "nothing happens before the start");

		controller.start();
		controller.start();
		assertEquals(ArcadeController.State.RUNNING, controller.state());
		controller.pause();
		controller.pause();
		assertEquals(ArcadeController.State.PAUSED, controller.state());
		controller.resume();
		assertEquals(ArcadeController.State.RUNNING, controller.state());
		controller.stop();
		controller.stop();
		controller.ended(FollowRun.Ended.FAILED);

		assertEquals(List.of("start", "pause", "resume", "stop"), run.calls, "each call reaches the run once, in order");
		assertEquals(1, recorder.ended, "the end is reported once, whatever comes after");
		assertEquals(FollowRun.Ended.STOPPED, recorder.endedReason);
		assertEquals(ArcadeController.State.ENDED, controller.state());
	}

	@Test
	public void aPlayerThatCannotStartEndsTheRunAtOnceAndSaysWhy() {
		FakeRun run = new FakeRun();
		run.startResult = FollowPlayback.Result.ALREADY_PLAYING;
		Recorder recorder = new Recorder();
		ArcadeController controller = make(run, new FakeOutcome(), recorder);

		controller.start();

		assertEquals(ArcadeController.State.ENDED, controller.state());
		assertEquals(List.of("failed ALREADY_PLAYING"), recorder.events);
		controller.stop();
		assertEquals(List.of("start"), run.calls, "a run that never started is not stopped");
	}

	@Test
	public void aResumeThatCannotRestartThePlayerLeavesTheRunPausedUntilTheRunReportsItsOwnEnd() {
		FakeRun run = new FakeRun();
		run.resumable = false;
		Recorder recorder = new Recorder();
		ArcadeController controller = make(run, new FakeOutcome(), recorder);
		controller.start();
		controller.pause();

		controller.resume();
		assertEquals(ArcadeController.State.PAUSED, controller.state());
		controller.ended(FollowRun.Ended.FAILED);

		assertEquals(1, recorder.ended);
		assertEquals(FollowRun.Ended.FAILED, recorder.endedReason);
	}

	@Test
	public void aResultExistsOnlyWhenThePassageWasJudgedToItsEndAndTheSongCameToIt() {
		for (FollowRun.Ended reason : FollowRun.Ended.values()) {
			for (boolean complete : new boolean[] { false, true }) {
				FakeRun run = new FakeRun();
				FakeOutcome outcome = new FakeOutcome();
				outcome.complete = complete;
				final boolean[] asked = new boolean[1];
				Recorder recorder = new Recorder();
				ArcadeController controller = new ArcadeController(run, new ArcadeController.Outcome() {
					public boolean isComplete() {
						return outcome.isComplete();
					}

					public ArcadeResult result() {
						asked[0] = true;
						return outcome.result();
					}
				}, recorder);
				run.controller = controller;
				controller.start();
				controller.ended(reason);
				boolean wanted = reason == FollowRun.Ended.PLAYER_STOPPED && complete;
				assertEquals(wanted, asked[0], reason + " complete=" + complete + ": the result is built only for the natural end");
				assertEquals(1, recorder.ended);
			}
		}
	}

	@Test
	public void twoSuccessiveRunsAreIndependent() {
		FakeRun first = new FakeRun();
		Recorder one = new Recorder();
		ArcadeController a = make(first, new FakeOutcome(), one);
		a.start();
		a.stop();
		FakeRun second = new FakeRun();
		Recorder two = new Recorder();
		ArcadeController b = make(second, new FakeOutcome(), two);
		b.start();
		assertEquals(ArcadeController.State.RUNNING, b.state());
		assertEquals(0, two.ended);
		assertEquals(ArcadeController.State.ENDED, a.state());
		assertNull(two.endedReason);
		assertSame(second.controller, b);
		assertTrue(one.events.contains("ENDED"));
	}
}
