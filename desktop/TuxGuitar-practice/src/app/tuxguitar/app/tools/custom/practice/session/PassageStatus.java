package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Collections;
import java.util.List;

/**
 * The passage part of a practice snapshot (FR-088–FR-090): the current passage and attempt, the
 * last end-of-attempt decision, and the session summary. Kept in memory only; never saved. Immutable.
 */
public final class PassageStatus {

	/** The decision taken when an attempt ended. */
	public enum Decision {
		/** No attempt has ended yet in this run. */
		NONE,
		/** Error rate at or below the limit: practice moved on. */
		PASSED,
		/** Error rate above the limit: the passage is being repeated. */
		RETRY,
		/** Error rate above the limit after the last allowed retry: practice moved on. */
		MOVED_ON
	}

	/** Measure range of a passage that was left above the error limit. */
	public static final class MeasureRange {
		private final int first;
		private final int last;

		MeasureRange(int first, int last) {
			this.first = first;
			this.last = last;
		}

		public int getFirst() {
			return this.first;
		}

		public int getLast() {
			return this.last;
		}
	}

	/** End-of-session result counts over passages with judged events. */
	public static final class Summary {
		private final int passages;
		private final int passedFirstAttempt;
		private final int passedAfterRetry;
		private final List<MeasureRange> notPassed;
		private final int retries;

		Summary(int passages, int passedFirstAttempt, int passedAfterRetry, List<MeasureRange> notPassed, int retries) {
			this.passages = passages;
			this.passedFirstAttempt = passedFirstAttempt;
			this.passedAfterRetry = passedAfterRetry;
			this.notPassed = Collections.unmodifiableList(notPassed);
			this.retries = retries;
		}

		/** Passages that ended with a decision (those with at least one judged event). */
		public int getPassages() {
			return this.passages;
		}

		public int getPassedFirstAttempt() {
			return this.passedFirstAttempt;
		}

		public int getPassedAfterRetry() {
			return this.passedAfterRetry;
		}

		public List<MeasureRange> getNotPassed() {
			return this.notPassed;
		}

		/** Retries made in the whole run. */
		public int getRetries() {
			return this.retries;
		}
	}

	private final int passageNumber;
	private final int passageCount;
	private final int firstMeasure;
	private final int lastMeasure;
	private final int attempt;
	private final int maxAttempts;
	private final int errors;
	private final int judged;
	private final Decision decision;
	private final int decisionErrors;
	private final int decisionJudged;
	private final int decisionAttempt;
	private final Summary summary;
	private final int thresholdPercent;

	PassageStatus(int passageNumber, int passageCount, int firstMeasure, int lastMeasure, int attempt, int maxAttempts,
			int errors, int judged, Decision decision, int decisionErrors, int decisionJudged, int decisionAttempt, Summary summary, int thresholdPercent) {
		this.thresholdPercent = thresholdPercent;
		this.passageNumber = passageNumber;
		this.passageCount = passageCount;
		this.firstMeasure = firstMeasure;
		this.lastMeasure = lastMeasure;
		this.attempt = attempt;
		this.maxAttempts = maxAttempts;
		this.errors = errors;
		this.judged = judged;
		this.decision = decision;
		this.decisionErrors = decisionErrors;
		this.decisionJudged = decisionJudged;
		this.decisionAttempt = decisionAttempt;
		this.summary = summary;
	}

	/** 1-based current passage number; 0 when there is no current passage (complete). */
	public int getPassageNumber() {
		return this.passageNumber;
	}

	public int getPassageCount() {
		return this.passageCount;
	}

	public int getFirstMeasure() {
		return this.firstMeasure;
	}

	public int getLastMeasure() {
		return this.lastMeasure;
	}

	/** 1-based attempt of the current passage. */
	public int getAttempt() {
		return this.attempt;
	}

	public int getMaxAttempts() {
		return this.maxAttempts;
	}

	/** Events of the current attempt counted as errors so far. */
	public int getErrors() {
		return this.errors;
	}

	/** Judged events of the current passage. */
	public int getJudged() {
		return this.judged;
	}

	public Decision getDecision() {
		return this.decision;
	}

	public int getDecisionErrors() {
		return this.decisionErrors;
	}

	public int getDecisionJudged() {
		return this.decisionJudged;
	}

	/** The attempt that ended with {@link #getDecision()}. */
	public int getDecisionAttempt() {
		return this.decisionAttempt;
	}

	/** The error threshold in percent: a passage above it is repeated. */
	public int getThresholdPercent() {
		return this.thresholdPercent;
	}

	/** Non-null once the session is complete. */
	public Summary getSummary() {
		return this.summary;
	}
}
