package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;

/**
 * The flow of a start (spec 010 T022, contract invariants 1, 12, 13, 14): the mode begins only when the gate says ready, once; one start window at a time; a
 * pending start is derived from the state of the check and is never stuck. The gate is a fake, so no window is needed.
 */
public class StartFlowTest {

	/** A gate that holds the check and the callback, and counts what it was asked. */
	private static final class FakeGate implements StartGate {
		PreRunCheck check;
		Runnable onReady;
		int requests;
		int toFronts;
		int closes;
		RuntimeException failure;

		@Override
		public void request(PreRunCheck check, Runnable onReady) {
			this.requests++;
			if (this.failure != null) {
				throw this.failure;
			}
			this.check = check;
			this.onReady = onReady;
		}

		@Override
		public void toFront() {
			this.toFronts++;
		}

		@Override
		public void closePending() {
			this.closes++;
		}

		/** The window got the learner to ready, and tells the flow. */
		void ready() {
			this.check.answerTuned(true);
			this.check.continueAnyway();
			this.onReady.run();
		}
	}

	private final FakeGate gate = new FakeGate();
	private final StartFlow flow = new StartFlow(this.gate);
	private final List<String> started = new ArrayList<String>();

	private Runnable start(String what) {
		return () -> this.started.add(what);
	}

	@Test
	public void theModeBeginsOnlyWhenTheGateIsReadyAndOnlyOnceForEachOfTheThreeModes() {
		for (FollowSettings.Mode mode : FollowSettings.Mode.values()) {
			FakeGate g = new FakeGate();
			StartFlow f = new StartFlow(g);
			List<String> runs = new ArrayList<String>();
			assertTrue(f.begin(new PreRunCheck(mode), () -> runs.add(mode.name())));
			assertEquals(0, runs.size(), "nothing starts before the answer: " + mode);
			assertTrue(f.pending());
			g.ready();
			assertEquals(1, runs.size(), mode.name());
			assertFalse(f.pending());
			g.onReady.run();
			assertEquals(1, runs.size(), "a second answer of the gate runs nothing: " + mode);
		}
	}

	@Test
	public void cancelledTunerOpenAndAGateThatNeverAnswersNeverRunTheStart() {
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("a")));
		this.gate.check.answerTuned(false); // TUNER_OPEN
		assertEquals(State.TUNER_OPEN, this.gate.check.state());
		assertTrue(this.flow.pending(), "the tuner open is still a pending start");
		this.gate.onReady.run(); // an answer that is not ready
		assertEquals(0, this.started.size());
		this.gate.check.cancel();
		assertFalse(this.flow.pending());
		this.gate.onReady.run();
		assertEquals(0, this.started.size(), "cancelled: nothing runs");
	}

	@Test
	public void oneStartWindowAtATimeEvenWhileTheTunerIsOpenAndANewStartIsAcceptedAfterTheEnd() {
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.ARCADE), start("first")));
		for (State target : new State[] { State.TUNING, State.TUNER_OPEN, State.STRUM }) {
			if (target == State.TUNER_OPEN) {
				this.gate.check.answerTuned(false);
			} else if (target == State.STRUM) {
				this.gate.check.tunerClosed();
				this.gate.check.answerTuned(true);
			}
			int toFronts = this.gate.toFronts;
			int requests = this.gate.requests;
			assertFalse(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("second")), "pending in " + target);
			assertEquals(toFronts + 1, this.gate.toFronts, "the open window comes forward once");
			assertEquals(requests, this.gate.requests, "nothing new is opened");
		}
		this.gate.ready();
		assertEquals(List.of("first"), this.started);
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("third")), "after READY a new start is accepted");
		this.gate.check.cancel();
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.FOLLOW), start("fourth")), "after CANCELLED too");
		this.flow.cancelPending();
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.FOLLOW), start("fifth")), "and after cancelPending");
	}

	@Test
	public void aGateThatCancelsTheCheckWithoutCallingBackLeavesNothingPending() {
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("a")));
		this.gate.check.cancel(); // the close button, Escape or Cancel
		assertFalse(this.flow.pending(), "the flow reads the state: the gate has only an onReady");
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("b")));
	}

	@Test
	public void neverStuckWhenTheStartThrowsDoesNothingOrTheGateFails() {
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), () -> {
			throw new IllegalStateException("window gone");
		}));
		assertThrows(IllegalStateException.class, this.gate::ready);
		assertFalse(this.flow.pending(), "cleared before the start ran");
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("again")));
		this.gate.failure = new IllegalArgumentException("no window");
		this.gate.check.cancel();
		assertThrows(IllegalArgumentException.class, () -> this.flow.begin(new PreRunCheck(FollowSettings.Mode.WAIT), start("x")));
		assertFalse(this.flow.pending(), "a request that throws leaves nothing pending");
		this.gate.failure = null;
		this.flow.toFront();
		assertEquals(1, this.gate.toFronts, "toFront is forwarded whatever the state");
	}

	@Test
	public void cancelPendingMakesALaterReadyRunNothingAndClosesTheWindow() {
		assertTrue(this.flow.begin(new PreRunCheck(FollowSettings.Mode.ARCADE), start("a")));
		PreRunCheck check = this.gate.check;
		Runnable onReady = this.gate.onReady;
		this.flow.cancelPending(); // the mode or the track changed, or the window closed
		assertEquals(State.CANCELLED, check.state());
		assertEquals(1, this.gate.closes, "the start window is closed");
		assertFalse(this.flow.pending());
		onReady.run();
		assertEquals(0, this.started.size());
		this.flow.cancelPending();
		assertEquals(1, this.gate.closes, "nothing pending: nothing to close");
	}
}
