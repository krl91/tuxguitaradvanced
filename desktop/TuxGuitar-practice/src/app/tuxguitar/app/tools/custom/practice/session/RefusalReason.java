package app.tuxguitar.app.tools.custom.practice.session;

/**
 * Why an event of a Follow run was not accepted on time (spec 016 FR-001, contracts/refusal-reasons.md). The verdict of the event is decided first;
 * the reason only says why, it never changes the verdict. The same list is used by the feedback of the run, its summary and the record of a run.
 */
public enum RefusalReason {
	/** Accepted (on time, early or late inside its window), passed without judging, or not resolved yet. */
	NONE,
	/** No attack was heard in the window, nor the right pitch near it. */
	NO_ATTACK,
	/** A sound was heard in the window, but its pitch never became clear enough to tell the note: uncertain, not wrong (constitution XIV). */
	UNCLEAR,
	/** Another pitch was heard in the window (the detail is the pitch heard most often). */
	OTHER_PITCH,
	/** The right pitch was heard before the window (the detail is the offset in milliseconds, negative). */
	EARLY_OUTSIDE,
	/** The right pitch was heard after the window (the detail is the offset in milliseconds). */
	LATE_OUTSIDE,
	/** Nothing could be judged when its window was open (a pause, the clocks, the input). */
	NOT_JUDGED
}
