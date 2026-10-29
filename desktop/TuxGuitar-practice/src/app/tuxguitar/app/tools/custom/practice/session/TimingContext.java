package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Window;
import app.tuxguitar.audio.engine.input.CaptureClock;

/**
 * The three separate things of the timing side by side (spec 002 T094, owner's decision of 2026-10-07): the tolerance the learner chose
 * ({@link TimingTolerance}), the latency compensation of the route ({@link LatencyCompensation}) and the uncertainty of the clocks
 * ({@link ClockUncertainty}). They are kept apart and reported apart: the tolerance gives the windows, the compensation moves the observed
 * onset, the uncertainty only says how much to trust the route. Nothing is added to anything else.
 * <p>
 * <b>The gate for T095:</b> no frame received before both clocks (the capture clock and the player clock) are ready may produce a verdict EARLY, LATE,
 * MISSED, EXTRA, or WRONG. {@link #mayJudge()} is that gate and every method here that can return a verdict goes through it; the follow session must
 * ask it before any verdict, the pitch one (WRONG) included.
 * <p>
 * Nothing here takes the name or the transport of a device: a Bluetooth route is neither refused nor guessed at (FR-057).
 */
public final class TimingContext {

	/** Why there is no timing verdict. */
	public enum Reason {
		/** A clock is not ready yet: nothing can be dated. */
		CLOCKS_NOT_READY,
		/** The onset is outside the outer window of the event. */
		NO_MATCH,
		/** The outer window of the event is still open: it is not missed yet. */
		WINDOW_OPEN
	}

	/** How much the timing of the rhythm can be trusted. */
	public enum RhythmPrecision {
		/** A clock is not ready: nothing is judged yet. */
		NOT_READY,
		/** Judged, with "reduced rhythmic precision" shown: see {@link #precisionReasons()}. */
		REDUCED,
		NORMAL
	}

	/** Why the precision is reduced; reported separately, never merged. */
	public enum PrecisionReason {
		CLOCKS_NOT_READY,
		/** The route has no calibration or manual adjustment. */
		NOT_CALIBRATED,
		/** The arrival lag of the capture spreads too much. */
		UNSTABLE_ROUTE
	}

	private final TimingTolerance tolerance;
	/** How the windows are made: the tolerance itself, or the time to play of Follow (spec 016). */
	private final WindowPolicy windowPolicy;
	private final LatencyCompensation compensation;
	private final ClockUncertainty uncertainty;
	private final boolean captureClockReady;
	private final boolean playerClockReady;

	public TimingContext(TimingTolerance tolerance, LatencyCompensation compensation, ClockUncertainty uncertainty,
			boolean captureClockReady, boolean playerClockReady) {
		this.tolerance = tolerance;
		this.windowPolicy = tolerance;
		this.compensation = compensation;
		this.uncertainty = uncertainty;
		this.captureClockReady = captureClockReady;
		this.playerClockReady = playerClockReady;
	}

	/**
	 * A context whose windows come from another policy (spec 016: the time to play of Follow). {@link #tolerance()} is then the policy when it is a
	 * {@link TimingTolerance}, otherwise null: the windows are asked of {@link #windowPolicy()}.
	 */
	public TimingContext(WindowPolicy windowPolicy, LatencyCompensation compensation, ClockUncertainty uncertainty, boolean captureClockReady,
			boolean playerClockReady) {
		this.tolerance = (windowPolicy instanceof TimingTolerance ? (TimingTolerance) windowPolicy : null);
		this.windowPolicy = windowPolicy;
		this.compensation = compensation;
		this.uncertainty = uncertainty;
		this.captureClockReady = captureClockReady;
		this.playerClockReady = playerClockReady;
	}

	/** The context of the live clocks: ready when both say so, the uncertainty read from the capture clock. */
	public static TimingContext of(TimingTolerance tolerance, LatencyCompensation compensation, CaptureClock capture, TempoClock player) {
		return new TimingContext(tolerance, compensation, ClockUncertainty.of(capture), capture.isReady(), player.isReady());
	}

	public TimingTolerance tolerance() {
		return this.tolerance;
	}

	/** How the windows of the events are made. */
	public WindowPolicy windowPolicy() {
		return this.windowPolicy;
	}

	public LatencyCompensation compensation() {
		return this.compensation;
	}

	public ClockUncertainty uncertainty() {
		return this.uncertainty;
	}

	/** The gate: true only when both clocks are ready. No verdict of any kind may be given before. */
	public boolean mayJudge() {
		return this.captureClockReady && this.playerClockReady;
	}

	public RhythmPrecision precision() {
		if (!mayJudge()) {
			return RhythmPrecision.NOT_READY;
		}
		return precisionReasons().isEmpty() ? RhythmPrecision.NORMAL : RhythmPrecision.REDUCED;
	}

	/** The reasons of a reduced or unavailable precision, each on its own; empty when the precision is normal. */
	public Set<PrecisionReason> precisionReasons() {
		Set<PrecisionReason> reasons = EnumSet.noneOf(PrecisionReason.class);
		if (!mayJudge()) {
			reasons.add(PrecisionReason.CLOCKS_NOT_READY);
		}
		if (!this.compensation.isCalibrated()) {
			reasons.add(PrecisionReason.NOT_CALIBRATED);
		}
		if (this.uncertainty.isUnstable()) {
			reasons.add(PrecisionReason.UNSTABLE_ROUTE);
		}
		return Collections.unmodifiableSet(reasons);
	}

	/** An onset observed at the given wall time, for the event of the window: ON_TIME, EARLY or LATE after the compensation of the latency; no verdict outside the outer window. */
	public TimingOutcome judgeOnset(Window window, long observedNanos) {
		if (!mayJudge()) {
			return TimingOutcome.none(Reason.CLOCKS_NOT_READY, 0L);
		}
		long adjusted = this.compensation.adjust(observedNanos);
		TimingVerdict verdict = window.classify(adjusted);
		long offset = window.offsetNanos(adjusted);
		return verdict != null ? TimingOutcome.verdict(verdict, offset) : TimingOutcome.none(Reason.NO_MATCH, offset);
	}

	/** An onset that matched no event: EXTRA. */
	public TimingOutcome judgeUnmatchedOnset(long observedNanos) {
		if (!mayJudge()) {
			return TimingOutcome.none(Reason.CLOCKS_NOT_READY, 0L);
		}
		return TimingOutcome.verdict(TimingVerdict.EXTRA, 0L);
	}

	/** The window of an event at the wall time {@code nowNanos}: MISSED once its last nanosecond has passed, after the compensation of the latency. */
	public TimingOutcome judgeClosure(Window window, long nowNanos) {
		if (!mayJudge()) {
			return TimingOutcome.none(Reason.CLOCKS_NOT_READY, 0L);
		}
		if (this.compensation.adjust(nowNanos) > window.outerTo()) {
			return TimingOutcome.verdict(TimingVerdict.MISSED, 0L);
		}
		return TimingOutcome.none(Reason.WINDOW_OPEN, 0L);
	}
}
