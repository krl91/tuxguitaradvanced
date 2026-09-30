package app.tuxguitar.app.tools.custom.practice.model;

/**
 * A playing technique written in the score (spec 002, data-model.md "Technique"). The display-only
 * ones ({@link #isJudged()} false) are shown but never judged. A technique without a detector is
 * reported as {@link TechniqueVerdict#NOT_VERIFIABLE}.
 */
public enum PlayingTechnique {
	HAMMER_ON(true),
	PULL_OFF(true),
	SLIDE(true),
	BEND(true),
	PRE_BEND(true),
	RELEASE(true),
	VIBRATO(true),
	PALM_MUTE(true),
	TREMOLO_PICKING(true),
	NATURAL_HARMONIC(true),
	ARTIFICIAL_HARMONIC(true),
	PINCH_HARMONIC(true),
	TAPPING(true),
	ACCENT(false),
	LET_RING(false),
	STACCATO(false);

	private final boolean judged;

	PlayingTechnique(boolean judged) {
		this.judged = judged;
	}

	/** False for the techniques that are only displayed (accent, let ring, staccato). */
	public boolean isJudged() {
		return this.judged;
	}
}
