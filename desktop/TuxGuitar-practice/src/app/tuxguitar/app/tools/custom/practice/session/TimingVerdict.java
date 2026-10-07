package app.tuxguitar.app.tools.custom.practice.session;

/** What the tempo-following mode says about the timing of an expected event (spec 002 data-model "TimingVerdict"). */
public enum TimingVerdict {
	/** Played inside the tight window around the target time. */
	ON_TIME,
	/** Played before the tight window, inside the outer one. */
	EARLY,
	/** Played after the tight window, inside the outer one. */
	LATE,
	/** The outer window closed and nothing matched. */
	MISSED,
	/** An onset that matched no event. */
	EXTRA
}
