package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;

/**
 * The state machine of the start window (spec 010 T011, T018, T024; contract invariants 2, 3, 4, 9, 17): total (every pair of state and event is defined,
 * the others are ignored), nothing remembered from one start to the next, ready only after the tuning was answered yes.
 */
public class PreRunCheckTest {

	private enum Event {
		TUNED_YES, TUNED_NO, TUNER_CLOSED, VERDICT_GOOD, VERDICT_CLIPPING, VERDICT_TOO_LOUD, VERDICT_TOO_WEAK, VERDICT_NO_STRUM, CONTINUE_ANYWAY, CHECK_AGAIN, CANCEL
	}

	private static PreRunCheck inState(State state, AtomicInteger resets) {
		PreRunCheck check = new PreRunCheck(FollowSettings.Mode.WAIT);
		check.setMeterReset(resets::incrementAndGet);
		switch (state) {
			case TUNING:
				break;
			case TUNER_OPEN:
				check.answerTuned(false);
				break;
			case STRUM:
				check.answerTuned(true);
				break;
			case LEVEL_BAD:
				check.answerTuned(true);
				check.verdict(StrumVerdict.CLIPPING);
				break;
			case READY:
				check.answerTuned(true);
				check.verdict(StrumVerdict.GOOD);
				break;
			case CANCELLED:
				check.cancel();
				break;
		}
		assertEquals(state, check.state(), "the setup of the test");
		resets.set(0);
		return check;
	}

	private static void apply(PreRunCheck check, Event event) {
		switch (event) {
			case TUNED_YES:
				check.answerTuned(true);
				break;
			case TUNED_NO:
				check.answerTuned(false);
				break;
			case TUNER_CLOSED:
				check.tunerClosed();
				break;
			case VERDICT_GOOD:
				check.verdict(StrumVerdict.GOOD);
				break;
			case VERDICT_CLIPPING:
				check.verdict(StrumVerdict.CLIPPING);
				break;
			case VERDICT_TOO_LOUD:
				check.verdict(StrumVerdict.TOO_LOUD);
				break;
			case VERDICT_TOO_WEAK:
				check.verdict(StrumVerdict.TOO_WEAK);
				break;
			case VERDICT_NO_STRUM:
				check.verdict(StrumVerdict.NO_STRUM);
				break;
			case CONTINUE_ANYWAY:
				check.continueAnyway();
				break;
			case CHECK_AGAIN:
				check.checkAgain();
				break;
			case CANCEL:
				check.cancel();
				break;
		}
	}

	/** The table of the contract: the state after each event, in the order of {@link Event}; null means the state does not change (ignored). */
	private static State expected(State from, Event event) {
		switch (from) {
			case TUNING:
				return event == Event.TUNED_YES ? State.STRUM : event == Event.TUNED_NO ? State.TUNER_OPEN : event == Event.CANCEL ? State.CANCELLED : null;
			case TUNER_OPEN:
				return event == Event.TUNER_CLOSED ? State.TUNING : event == Event.CANCEL ? State.CANCELLED : null;
			case STRUM:
				switch (event) {
					case VERDICT_GOOD:
					case CONTINUE_ANYWAY:
						return State.READY;
					case VERDICT_CLIPPING:
					case VERDICT_TOO_LOUD:
					case VERDICT_TOO_WEAK:
						return State.LEVEL_BAD;
					case CANCEL:
						return State.CANCELLED;
					default:
						return null; // NO_STRUM keeps STRUM, CHECK_AGAIN stays in STRUM, the rest is ignored
				}
			case LEVEL_BAD:
				return event == Event.CONTINUE_ANYWAY ? State.READY : event == Event.CHECK_AGAIN ? State.STRUM : event == Event.CANCEL ? State.CANCELLED : null;
			default:
				return null; // READY and CANCELLED are final
		}
	}

	/** How many times the meter must have been asked to start again by an event from a state (the cells marked "meter reset" of the table). */
	private static int expectedResets(State from, Event event) {
		if (from == State.TUNING && event == Event.TUNED_YES) {
			return 1;
		}
		if ((from == State.STRUM || from == State.LEVEL_BAD) && event == Event.CHECK_AGAIN) {
			return 1;
		}
		return 0;
	}

	@Test
	public void everyPairOfStateAndEventDoesWhatTheTableOfTheContractSaysAndTheIgnoredOnesChangeNothingAndThrowNothing() {
		int cells = 0;
		for (State from : State.values()) {
			for (Event event : Event.values()) {
				AtomicInteger resets = new AtomicInteger();
				PreRunCheck check = inState(from, resets);
				apply(check, event);
				State target = expected(from, event);
				assertEquals(target == null ? from : target, check.state(), from + " + " + event);
				assertEquals(expectedResets(from, event), resets.get(), "the meter reset asked by " + from + " + " + event);
				cells++;
			}
		}
		assertEquals(6 * 11, cells, "6 states and 11 events (the three bad verdicts are three events)");
	}

	@Test
	public void aNewCheckStartsAtTheTuningQuestionWhateverTheOnesBeforeDidAndNothingIsStatic() {
		PreRunCheck first = new PreRunCheck(FollowSettings.Mode.ARCADE);
		first.answerTuned(true);
		first.continueAnyway();
		assertEquals(State.READY, first.state());
		assertTrue(first.continuedAnyway());
		PreRunCheck second = new PreRunCheck(FollowSettings.Mode.ARCADE);
		assertNotSame(first, second);
		assertEquals(State.TUNING, second.state(), "a second start asks again");
		assertFalse(second.continuedAnyway(), "the choice of the first start is not remembered");
		assertEquals(StrumVerdict.NO_STRUM, second.lastVerdict());
		assertEquals(FollowSettings.Mode.ARCADE, second.mode());
	}

	@Test
	public void aGoodVerdictAfterTheTuningAnsweredYesIsReadyAndABadOneBlocksWithTheAdviceToFollow() {
		for (StrumVerdict bad : new StrumVerdict[] { StrumVerdict.CLIPPING, StrumVerdict.TOO_LOUD, StrumVerdict.TOO_WEAK }) {
			PreRunCheck check = new PreRunCheck(FollowSettings.Mode.WAIT);
			check.answerTuned(true);
			check.verdict(bad);
			assertEquals(State.LEVEL_BAD, check.state(), bad.name());
			assertEquals(bad, check.lastVerdict(), "the window needs the verdict to say what to do");
			check.checkAgain();
			assertEquals(State.STRUM, check.state());
			check.verdict(StrumVerdict.GOOD);
			assertEquals(State.READY, check.state(), "a good strum after checking again goes on without continuing anyway");
			assertFalse(check.continuedAnyway());
		}
		PreRunCheck silent = new PreRunCheck(FollowSettings.Mode.FOLLOW);
		silent.answerTuned(true);
		silent.verdict(StrumVerdict.NO_STRUM);
		assertEquals(State.STRUM, silent.state(), "silence is never a good level and never a bad one: the window goes on asking for a strum");
	}

	@Test
	public void readyIsNeverReachedWithoutTheTuningAnsweredYesNotEvenByContinuingAnyway() {
		PreRunCheck check = new PreRunCheck(FollowSettings.Mode.WAIT);
		check.continueAnyway();
		check.verdict(StrumVerdict.GOOD);
		assertEquals(State.TUNING, check.state());
		check.answerTuned(false);
		check.continueAnyway();
		check.verdict(StrumVerdict.GOOD);
		assertEquals(State.TUNER_OPEN, check.state(), "not in tune: the tuner is open and nothing begins");
		check.tunerClosed();
		assertEquals(State.TUNING, check.state(), "back to the question, never ready by itself");
	}

	@Test
	public void aCancelAfterReadyDoesNotUndoItAndCancelIsIdempotent() {
		PreRunCheck ready = new PreRunCheck(FollowSettings.Mode.WAIT);
		ready.answerTuned(true);
		ready.verdict(StrumVerdict.GOOD);
		ready.cancel();
		assertEquals(State.READY, ready.state());
		PreRunCheck cancelled = new PreRunCheck(FollowSettings.Mode.WAIT);
		cancelled.cancel();
		cancelled.cancel();
		assertEquals(State.CANCELLED, cancelled.state());
	}

	@Test
	public void aStrumHeardWhileTheQuestionIsReadIsIgnoredAndEnteringTheStrumStateAsksTheMeterToStartAgain() {
		AtomicInteger resets = new AtomicInteger();
		PreRunCheck check = new PreRunCheck(FollowSettings.Mode.WAIT);
		check.setMeterReset(resets::incrementAndGet);
		check.verdict(StrumVerdict.CLIPPING); // while the question is shown
		assertEquals(State.TUNING, check.state());
		check.answerTuned(true);
		assertEquals(1, resets.get(), "the strum that counts is the one after the answer");
		check.verdict(StrumVerdict.CLIPPING);
		check.checkAgain();
		assertEquals(2, resets.get());
	}

	@Test
	public void notInTuneNeverReachesReadyAndTheTunerWayBackGoesToTheQuestionAgainKeepingTheMode() {
		for (FollowSettings.Mode mode : FollowSettings.Mode.values()) {
			PreRunCheck check = new PreRunCheck(mode);
			check.answerTuned(false);
			assertEquals(State.TUNER_OPEN, check.state(), mode.name());
			check.continueAnyway();
			check.verdict(StrumVerdict.GOOD);
			assertEquals(State.TUNER_OPEN, check.state(), "no way to ready while the tuner is open");
			check.tunerClosed();
			assertEquals(State.TUNING, check.state(), "the tuner closed: the question again, never ready by itself");
			assertEquals(mode, check.mode(), "the mode is the one asked for");
			check.tunerClosed();
			assertEquals(State.TUNING, check.state(), "a tuner closed event outside TUNER_OPEN is ignored");
			check.answerTuned(true);
			assertEquals(State.STRUM, check.state());
			check.cancel();
			assertEquals(State.CANCELLED, check.state(), "cancel from the strum");
		}
		for (State from : State.values()) {
			PreRunCheck check = inState(from, new AtomicInteger());
			check.cancel();
			assertEquals(from == State.READY ? State.READY : State.CANCELLED, check.state(), "cancel from " + from);
		}
	}
}
