package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;

/**
 * What the learner has to do before a mode begins (spec 010): say whether the guitar is in tune, then strum a chord so that the level can be judged. No widget
 * and nothing remembered: a new object is made for each start. The transitions are the total table of {@code contracts/pre-run-start.md}: every pair of state
 * and event is defined; the ones not listed there are <em>ignored</em> (no exception, no change of state, nothing started or undone). {@link State#READY} is
 * reached only after the tuning was answered yes; {@link State#READY} and {@link State#CANCELLED} are final.
 */
public final class PreRunCheck {

	public enum State {
		/** The question "is your guitar in tune?" is shown. */
		TUNING,
		/** The learner said no: the tuner is open (or about to be) and the mode does not begin. */
		TUNER_OPEN,
		/** In tune: the window waits for a strum and judges its level. */
		STRUM,
		/** The strum clips or is too weak: the mode does not begin until the learner checks again or continues anyway. */
		LEVEL_BAD,
		/** The mode may begin. Final. */
		READY,
		/** The mode does not begin. Final. */
		CANCELLED
	}

	private final FollowSettings.Mode mode;
	private State state = State.TUNING;
	private StrumVerdict lastVerdict = StrumVerdict.NO_STRUM;
	private boolean continuedAnyway;
	private Runnable meterReset;

	public PreRunCheck(FollowSettings.Mode mode) {
		this.mode = mode;
	}

	/** The mode this check was made for (captured when the start was asked, FR-013). */
	public FollowSettings.Mode mode() {
		return this.mode;
	}

	public synchronized State state() {
		return this.state;
	}

	/** The last verdict that was acted on (a bad one while the state is {@link State#LEVEL_BAD}). */
	public synchronized StrumVerdict lastVerdict() {
		return this.lastVerdict;
	}

	/** True when {@link State#READY} was reached by "continue anyway" and not by a good strum; for this start only. */
	public synchronized boolean continuedAnyway() {
		return this.continuedAnyway;
	}

	/** Called when the strum meter has to start again: on entering {@link State#STRUM} and on "check again". */
	public synchronized void setMeterReset(Runnable meterReset) {
		this.meterReset = meterReset;
	}

	/** The answer to the question: yes goes to the strum, no to the tuner; ignored outside {@link State#TUNING}. */
	public void answerTuned(boolean tuned) {
		Runnable reset = null;
		synchronized (this) {
			if (this.state != State.TUNING) {
				return;
			}
			this.state = tuned ? State.STRUM : State.TUNER_OPEN;
			if (tuned) {
				reset = this.meterReset;
			}
		}
		run(reset);
	}

	/** The tuner was closed, by any means: back to the question; ignored outside {@link State#TUNER_OPEN}. */
	public synchronized void tunerClosed() {
		if (this.state == State.TUNER_OPEN) {
			this.state = State.TUNING;
		}
	}

	/** The verdict of the strum meter; it acts only in {@link State#STRUM}: good is ready, a bad one blocks, no strum keeps waiting. */
	public synchronized void verdict(StrumVerdict verdict) {
		if (verdict == null) {
			throw new IllegalArgumentException("verdict");
		}
		if (this.state != State.STRUM) {
			return;
		}
		switch (verdict) {
			case GOOD:
				this.lastVerdict = verdict;
				this.state = State.READY;
				break;
			case CLIPPING:
			case TOO_LOUD:
			case TOO_WEAK:
				this.lastVerdict = verdict;
				this.state = State.LEVEL_BAD;
				break;
			default:
				break;
		}
	}

	/** Go on although the level is bad, or although no strum came: for this start only; ignored unless a strum is awaited or the level is bad. */
	public synchronized void continueAnyway() {
		if (this.state == State.STRUM || this.state == State.LEVEL_BAD) {
			this.continuedAnyway = true;
			this.state = State.READY;
		}
	}

	/** Judge the level again: back to the strum with a fresh meter; ignored outside {@link State#STRUM} and {@link State#LEVEL_BAD}. */
	public void checkAgain() {
		Runnable reset;
		synchronized (this) {
			if (this.state != State.STRUM && this.state != State.LEVEL_BAD) {
				return;
			}
			this.state = State.STRUM;
			this.lastVerdict = StrumVerdict.NO_STRUM;
			reset = this.meterReset;
		}
		run(reset);
	}

	/** The mode does not begin; idempotent, and it never undoes {@link State#READY}. */
	public synchronized void cancel() {
		if (this.state != State.READY) {
			this.state = State.CANCELLED;
		}
	}

	private static void run(Runnable reset) {
		if (reset != null) {
			reset.run();
		}
	}
}
