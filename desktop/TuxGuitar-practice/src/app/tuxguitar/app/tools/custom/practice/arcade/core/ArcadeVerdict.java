package app.tuxguitar.app.tools.custom.practice.arcade.core;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.TimingVerdict;

/**
 * What became of an event in Arcade (spec 003 FR-020, research R6): one verdict per event, derived from the result of the tempo-following session
 * and nothing else. The pitch and the timing of the following mode are kept as they are; the techniques never enter it. Every status of the session
 * maps to exactly one verdict.
 */
public enum ArcadeVerdict {
	/** The event is still to come, or its window is open. */
	WAITING,
	ON_TIME,
	EARLY,
	LATE,
	/** A wrong note, or a chord that was wrong without a pitch the session could name as missing. */
	WRONG,
	/** A chord with pitches not heard: they are named, at chord level, never string by string. */
	INCOMPLETE,
	MISSED,
	/** Could not be judged (a pause, the clocks not ready, a window not seen whole): neutral, never an error. */
	NOT_JUDGED,
	/** An event passed without judging (a dead note, a harmonic). */
	AUTO_PASSED;

	/**
	 * @param status the status of the event in the session
	 * @param timing its timing verdict, or null
	 * @param chord true for a chord
	 * @param missingPitches true when the session named pitches of the event that were not heard
	 */
	public static ArcadeVerdict of(FollowSession.Status status, TimingVerdict timing, boolean chord, boolean missingPitches) {
		switch (status) {
			case MATCHED:
				if (timing == TimingVerdict.EARLY) {
					return EARLY;
				}
				return timing == TimingVerdict.LATE ? LATE : ON_TIME;
			case WRONG_PITCH:
				return chord && missingPitches ? INCOMPLETE : WRONG;
			case MISSED:
				return MISSED;
			case NOT_JUDGED:
				return NOT_JUDGED;
			case AUTO_PASSED:
				return AUTO_PASSED;
			default:
				return WAITING;
		}
	}

	/** The verdict of a result of the session for an event of the sequence. */
	public static ArcadeVerdict of(FollowSession.EventResult result, PracticeEvent event) {
		boolean chord = event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD;
		return of(result.status(), result.timing(), chord, result.missingPitches().length > 0);
	}
}
