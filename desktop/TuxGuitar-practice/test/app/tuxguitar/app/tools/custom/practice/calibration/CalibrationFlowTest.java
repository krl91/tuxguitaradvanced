package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.Event;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;

/**
 * Spec 013 (T031): the flow of contracts/calibration-flow.md is a total table: every pair of state and event is either a row or changes nothing.
 * The table is written out again here, independently of the code, and every one of the pairs is checked against it.
 */
public class CalibrationFlowTest {

	private static final Map<State, Map<Event, State>> TABLE = new EnumMap<State, Map<Event, State>>(State.class);

	private static void row(State from, Event event, State to) {
		Map<Event, State> row = TABLE.get(from);
		if (row == null) {
			row = new EnumMap<Event, State>(Event.class);
			TABLE.put(from, row);
		}
		row.put(event, to);
	}

	static {
		row(State.OFFER, Event.CALIBRATE, State.TUNING);
		row(State.OFFER, Event.USE_DEFAULT, State.DONE);
		row(State.TUNING, Event.YES, State.ADVICE);
		row(State.TUNING, Event.NO, State.TUNER_OPEN);
		row(State.TUNER_OPEN, Event.TUNER_CLOSED, State.TUNING);
		row(State.ADVICE, Event.OK, State.PIECE);
		row(State.PIECE, Event.OK, State.LISTENING);
		row(State.LISTENING, Event.LISTENING_ENDED, State.QUALITY);
		row(State.LISTENING, Event.LISTENING_REFUSED, State.REFUSED);
		row(State.QUALITY, Event.YES, State.ANALYSING);
		row(State.QUALITY, Event.NO, State.REFUSED);
		row(State.REFUSED, Event.RECORD_AGAIN, State.PIECE);
		row(State.REFUSED, Event.USE_DEFAULT, State.DONE);
		row(State.REFUSED, Event.STOP, State.CANCELLED);
		row(State.ANALYSING, Event.SEARCH_SAVED, State.RESULT);
		row(State.ANALYSING, Event.SEARCH_NEEDS_CHOICE, State.CHOICE);
		row(State.RESULT, Event.OK, State.DONE);
		row(State.CHOICE, Event.USE_FOUND, State.DONE);
		row(State.CHOICE, Event.RECORD_AGAIN, State.PIECE);
		row(State.CHOICE, Event.USE_DEFAULT, State.DONE);
		// cancelling (the Cancel button, or the cross answered Yes) stops every step, but not the finished ones: on the result screen the profile is already saved
		for (State state : State.values()) {
			if (state != State.DONE && state != State.CANCELLED) {
				row(state, Event.CANCEL, state == State.RESULT ? State.DONE : State.CANCELLED);
			}
		}
		// a device lost stops what needs the device: everything up to the start of the analysis
		for (State state : new State[] { State.OFFER, State.TUNING, State.TUNER_OPEN, State.ADVICE, State.PIECE, State.LISTENING, State.QUALITY, State.REFUSED }) {
			row(state, Event.DEVICE_LOST, State.CANCELLED);
		}
	}

	@Test
	public void everyPairOfStateAndEventIsARowOfTheTableOrChangesNothing() {
		int rows = 0;
		for (State state : State.values()) {
			for (Event event : Event.values()) {
				Map<Event, State> row = TABLE.get(state);
				State expected = (row != null && row.containsKey(event)) ? row.get(event) : state;
				assertEquals(expected, CalibrationFlow.next(state, event), state + " on " + event);
				if (expected != state) {
					rows++;
				}
			}
		}
		assertTrue(rows > 30, "the table has its rows: " + rows);
	}

	@Test
	public void theFlowStartsAtTheOfferAndFollowsTheHappyPath() {
		CalibrationFlow flow = new CalibrationFlow();
		assertEquals(State.OFFER, flow.getState());
		assertEquals(State.TUNING, flow.on(Event.CALIBRATE));
		assertEquals(State.ADVICE, flow.on(Event.YES));
		assertEquals(State.PIECE, flow.on(Event.OK));
		assertEquals(State.LISTENING, flow.on(Event.OK));
		assertEquals(State.QUALITY, flow.on(Event.LISTENING_ENDED));
		assertEquals(State.ANALYSING, flow.on(Event.YES));
		assertEquals(State.RESULT, flow.on(Event.SEARCH_SAVED));
		assertEquals(State.DONE, flow.on(Event.OK));
		assertTrue(flow.isFinished());
	}

	@Test
	public void aPairNotInTheTableChangesNothing() {
		CalibrationFlow flow = new CalibrationFlow();
		assertEquals(State.OFFER, flow.on(Event.OK));
		assertEquals(State.OFFER, flow.on(Event.SEARCH_SAVED));
		assertFalse(flow.isFinished());
	}

	@Test
	public void theTunerNeverLeadsOnByItself() {
		CalibrationFlow flow = new CalibrationFlow();
		flow.on(Event.CALIBRATE);
		assertEquals(State.TUNER_OPEN, flow.on(Event.NO));
		for (Event event : EnumSet.complementOf(EnumSet.of(Event.TUNER_CLOSED, Event.CANCEL, Event.DEVICE_LOST))) {
			assertEquals(State.TUNER_OPEN, CalibrationFlow.next(State.TUNER_OPEN, event), "while the tuner is open: " + event);
		}
		assertEquals(State.TUNING, flow.on(Event.TUNER_CLOSED), "closing the tuner brings the question back, it does not answer it");
	}

	@Test
	public void theAnalysisIsReachedOnlyFromTheQualityQuestionAnswerYes() {
		Set<String> into = new java.util.TreeSet<String>();
		for (State state : State.values()) {
			for (Event event : Event.values()) {
				if (CalibrationFlow.next(state, event) == State.ANALYSING && state != State.ANALYSING) {
					into.add(state + " on " + event);
				}
			}
		}
		assertEquals(Set.of("QUALITY on YES"), into, "a refused recording (a wrong number of sounds, a silent one, a clipped one) never reaches the analysis");
	}

	@Test
	public void everyRefusalOffersTheThreeChoicesAndNothingElseGoesOn() {
		assertEquals(State.PIECE, CalibrationFlow.next(State.REFUSED, Event.RECORD_AGAIN));
		assertEquals(State.DONE, CalibrationFlow.next(State.REFUSED, Event.USE_DEFAULT));
		assertEquals(State.CANCELLED, CalibrationFlow.next(State.REFUSED, Event.STOP));
		for (Event event : EnumSet.complementOf(EnumSet.of(Event.RECORD_AGAIN, Event.USE_DEFAULT, Event.STOP, Event.CANCEL, Event.DEVICE_LOST))) {
			assertEquals(State.REFUSED, CalibrationFlow.next(State.REFUSED, event), event.toString());
		}
	}

	@Test
	public void theFinishedStatesAreFinal() {
		for (State state : new State[] { State.DONE, State.CANCELLED }) {
			for (Event event : Event.values()) {
				assertEquals(state, CalibrationFlow.next(state, event));
			}
		}
	}

	@Test
	public void aDeviceLostAfterTheRecordingDoesNotStopTheAnalysis() {
		for (State state : new State[] { State.ANALYSING, State.RESULT, State.CHOICE }) {
			assertEquals(state, CalibrationFlow.next(state, Event.DEVICE_LOST));
		}
	}
}
