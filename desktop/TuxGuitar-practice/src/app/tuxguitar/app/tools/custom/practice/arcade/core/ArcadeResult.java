package app.tuxguitar.app.tools.custom.practice.arcade.core;

import app.tuxguitar.app.tools.custom.practice.session.FollowSummary;

/**
 * The result of a run, shown once at its end and then dropped (spec 003 FR-023, FR-026): the score, the share of the maximum, the counts per judgement
 * (those of {@link FollowSummary}, every event counted once) and the best streak. It is an immutable value and nothing writes it anywhere.
 */
public final class ArcadeResult {

	private final int score;
	private final int maxScore;
	private final int bestStreak;
	private final FollowSummary summary;

	ArcadeResult(int score, int maxScore, int bestStreak, FollowSummary summary) {
		this.score = score;
		this.maxScore = maxScore;
		this.bestStreak = bestStreak;
		this.summary = summary;
	}

	public int score() {
		return this.score;
	}

	/** The score of the same run with every judged event on time. */
	public int maxScore() {
		return this.maxScore;
	}

	/** The share of the maximum, rounded down to a whole percent; 0 when nothing was judged. */
	public int percent() {
		return this.maxScore == 0 ? 0 : (int) (100L * this.score / this.maxScore);
	}

	public int bestStreak() {
		return this.bestStreak;
	}

	/** The counts per judgement and the techniques, apart. */
	public FollowSummary summary() {
		return this.summary;
	}
}
