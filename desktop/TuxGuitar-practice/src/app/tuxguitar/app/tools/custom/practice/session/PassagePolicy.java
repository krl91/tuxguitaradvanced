package app.tuxguitar.app.tools.custom.practice.session;

/**
 * Passage retry policy (owner policy 2026-09-28, FR-088/FR-089). At the end of an attempt, the
 * passage is repeated when its confirmed error rate — events with a confirmed wrong verdict or
 * skipped, each counted once, over the passage's judged events — is above
 * {@link #getMaxErrorRate()}, at most {@link #getMaxRetries()} times. Immutable.
 */
public final class PassagePolicy {

	public static final int DEFAULT_MEASURES = 2;
	public static final double DEFAULT_MAX_ERROR_RATE = 0.5;
	public static final int DEFAULT_MAX_RETRIES = 2;

	private final int measuresPerPassage;
	private final double maxErrorRate;
	private final int maxRetries;

	public PassagePolicy(int measuresPerPassage, double maxErrorRate, int maxRetries) {
		if( measuresPerPassage < 1 || maxErrorRate < 0.0 || maxErrorRate > 1.0 || maxRetries < 0 ) {
			throw new IllegalArgumentException("invalid passage policy");
		}
		this.measuresPerPassage = measuresPerPassage;
		this.maxErrorRate = maxErrorRate;
		this.maxRetries = maxRetries;
	}

	/** The approved policy: 2 played measures, retry above 50 % errors, at most 2 retries. */
	public static PassagePolicy defaults() {
		return new PassagePolicy(DEFAULT_MEASURES, DEFAULT_MAX_ERROR_RATE, DEFAULT_MAX_RETRIES);
	}

	public int getMeasuresPerPassage() {
		return this.measuresPerPassage;
	}

	/** A passage is repeated only when errors / judged events is strictly above this rate. */
	public double getMaxErrorRate() {
		return this.maxErrorRate;
	}

	public int getMaxRetries() {
		return this.maxRetries;
	}

	/** Whether an attempt with these counts is repeated, ignoring the retry limit. */
	public boolean isFailed(int errors, int judged) {
		return (judged > 0 && errors > this.maxErrorRate * judged);
	}
}
