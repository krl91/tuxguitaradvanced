package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.app.tools.custom.practice.session.TimingContext.Reason;

/** The answer of the timing context about one onset or one window: a verdict, or the reason there is none. Immutable. */
public final class TimingOutcome {

	private final TimingVerdict verdict;
	private final Reason reason;
	private final long offsetNanos;

	private TimingOutcome(TimingVerdict verdict, Reason reason, long offsetNanos) {
		this.verdict = verdict;
		this.reason = reason;
		this.offsetNanos = offsetNanos;
	}

	static TimingOutcome verdict(TimingVerdict verdict, long offsetNanos) {
		return new TimingOutcome(verdict, null, offsetNanos);
	}

	static TimingOutcome none(Reason reason, long offsetNanos) {
		return new TimingOutcome(null, reason, offsetNanos);
	}

	/** The verdict, or null when there is none: see {@link #reason()}. */
	public TimingVerdict verdict() {
		return this.verdict;
	}

	/** Why there is no verdict; null when there is one. */
	public Reason reason() {
		return this.reason;
	}

	/** Signed offset of the compensated onset from the target time (negative: early); 0 when not meaningful. */
	public long offsetNanos() {
		return this.offsetNanos;
	}
}
