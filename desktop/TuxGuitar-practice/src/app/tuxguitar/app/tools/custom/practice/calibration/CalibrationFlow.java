package app.tuxguitar.app.tools.custom.practice.calibration;

/**
 * The steps of the calibration (spec 013, contracts/calibration-flow.md) as a pure state machine, like the check of the start window: a total table of
 * states and events, and a pair that is not a row of the table changes nothing. It holds no data and does nothing but give the next state; the controller
 * performs what each step needs.
 */
public final class CalibrationFlow {

	public enum State {
		/** Calibrate, or use the default configuration */
		OFFER,
		/** is the guitar in tune? */
		TUNING,
		/** the tuner is open; closing it brings the question back */
		TUNER_OPEN,
		ADVICE,
		/** the piece to play */
		PIECE,
		LISTENING,
		/** is the recording good enough? */
		QUALITY,
		/** the recording is refused, or the player said it is not good enough: record again, use the default, or stop */
		REFUSED,
		/** the offline search, in a window that blocks the application */
		ANALYSING,
		/** the search found settings better than the default and at least 60 %: saved, the rates are shown */
		RESULT,
		/** the settings found are not better than the default, or under 60 %: use them anyway, record again, or use the default */
		CHOICE,
		DONE,
		CANCELLED
	}

	public enum Event {
		CALIBRATE,
		USE_DEFAULT,
		YES,
		NO,
		OK,
		TUNER_CLOSED,
		/** the right number of sounds was heard and the recording is not refused */
		LISTENING_ENDED,
		/** a wrong number of sounds, a silent or clipped recording, or a timeout */
		LISTENING_REFUSED,
		RECORD_AGAIN,
		STOP,
		/** the search found settings that are better than the default and at least 60 %: they are saved */
		SEARCH_SAVED,
		SEARCH_NEEDS_CHOICE,
		USE_FOUND,
		/** the Cancel button, or the cross answered Yes to "Stop the calibration?" */
		CANCEL,
		DEVICE_LOST
	}

	private State state = State.OFFER;

	public State getState() {
		return this.state;
	}

	public boolean isFinished() {
		return this.state == State.DONE || this.state == State.CANCELLED;
	}

	/** Applies the event and returns the state: unchanged when the pair is not in the table. */
	public State on(Event event) {
		this.state = next(this.state, event);
		return this.state;
	}

	public static State next(State state, Event event) {
		switch (state) {
		case OFFER:
			if (event == Event.CALIBRATE) {
				return State.TUNING;
			}
			if (event == Event.USE_DEFAULT) {
				return State.DONE;
			}
			break;
		case TUNING:
			if (event == Event.YES) {
				return State.ADVICE;
			}
			if (event == Event.NO) {
				return State.TUNER_OPEN;
			}
			break;
		case TUNER_OPEN:
			if (event == Event.TUNER_CLOSED) {
				return State.TUNING;
			}
			break;
		case ADVICE:
			if (event == Event.OK) {
				return State.PIECE;
			}
			break;
		case PIECE:
			if (event == Event.OK) {
				return State.LISTENING;
			}
			break;
		case LISTENING:
			if (event == Event.LISTENING_ENDED) {
				return State.QUALITY;
			}
			if (event == Event.LISTENING_REFUSED) {
				return State.REFUSED;
			}
			break;
		case QUALITY:
			if (event == Event.YES) {
				return State.ANALYSING;
			}
			if (event == Event.NO) {
				return State.REFUSED;
			}
			break;
		case REFUSED:
			if (event == Event.RECORD_AGAIN) {
				return State.PIECE;
			}
			if (event == Event.USE_DEFAULT) {
				return State.DONE;
			}
			if (event == Event.STOP) {
				return State.CANCELLED;
			}
			break;
		case ANALYSING:
			if (event == Event.SEARCH_SAVED) {
				return State.RESULT;
			}
			if (event == Event.SEARCH_NEEDS_CHOICE) {
				return State.CHOICE;
			}
			break;
		case RESULT:
			if (event == Event.OK) {
				return State.DONE;
			}
			break;
		case CHOICE:
			if (event == Event.USE_FOUND || event == Event.USE_DEFAULT) {
				return State.DONE;
			}
			if (event == Event.RECORD_AGAIN) {
				return State.PIECE;
			}
			break;
		default:
			return state;
		}
		if (event == Event.CANCEL) {
			// on the result screen the profile is already saved: closing it ends the flow, it cancels nothing
			return state == State.RESULT ? State.DONE : State.CANCELLED;
		}
		if (event == Event.DEVICE_LOST && state != State.ANALYSING && state != State.RESULT && state != State.CHOICE) {
			return State.CANCELLED;
		}
		return state;
	}
}
