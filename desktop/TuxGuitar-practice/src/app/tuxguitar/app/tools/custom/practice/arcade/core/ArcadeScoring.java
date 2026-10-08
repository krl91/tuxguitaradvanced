package app.tuxguitar.app.tools.custom.practice.arcade.core;

/**
 * The scoring rules of Arcade (spec 003 FR-022, research R5): a fixed table, identical from one run to the next for the same play. The numbers are
 * data of the table below and are to be confirmed by the owner (analysis O2); nothing else in the code depends on them.
 * <p>
 * An on-time note scores 100, an early or late note with the right pitch 60, a wrong, incomplete or missed event nothing. A streak of good notes
 * multiplies the points of the next event: x1 below 10, x1.5 from 10, x2 from 25. Events that were not judged or passed without judging are neither
 * scored nor counted in the maximum, and extra notes cost nothing.
 */
public final class ArcadeScoring {

	private static final int[] POINTS = new int[ArcadeVerdict.values().length];
	static {
		POINTS[ArcadeVerdict.ON_TIME.ordinal()] = 100;
		POINTS[ArcadeVerdict.EARLY.ordinal()] = 60;
		POINTS[ArcadeVerdict.LATE.ordinal()] = 60;
	}

	private static final int FIRST_STEP = 10;
	private static final int SECOND_STEP = 25;

	private ArcadeScoring() {
	}

	public static ArcadeScoring standard() {
		return new ArcadeScoring();
	}

	/** The base points of a verdict. */
	public int points(ArcadeVerdict verdict) {
		return POINTS[verdict.ordinal()];
	}

	/** The multiplier for the next event after a streak of the given length. */
	public double multiplier(int streakBefore) {
		if (streakBefore >= SECOND_STEP) {
			return 2.0;
		}
		return streakBefore >= FIRST_STEP ? 1.5 : 1.0;
	}

	/** The points of an event with the multiplier of the streak that came before it, rounded. */
	public int eventScore(ArcadeVerdict verdict, int streakBefore) {
		return (int) Math.round(points(verdict) * multiplier(streakBefore));
	}

	public boolean extendsStreak(ArcadeVerdict verdict) {
		return verdict == ArcadeVerdict.ON_TIME || verdict == ArcadeVerdict.EARLY || verdict == ArcadeVerdict.LATE;
	}

	public boolean resetsStreak(ArcadeVerdict verdict) {
		return verdict == ArcadeVerdict.WRONG || verdict == ArcadeVerdict.INCOMPLETE || verdict == ArcadeVerdict.MISSED;
	}

	/** True for the events that were judged (right, wrong or missed): they count in the maximum. */
	public boolean isJudged(ArcadeVerdict verdict) {
		return extendsStreak(verdict) || resetsStreak(verdict);
	}

	/** The score of a run where every one of the {@code judgedEvents} events is on time. */
	public int maximum(int judgedEvents) {
		int score = 0;
		for (int streak = 0; streak < judgedEvents; streak++) {
			score += eventScore(ArcadeVerdict.ON_TIME, streak);
		}
		return score;
	}
}
