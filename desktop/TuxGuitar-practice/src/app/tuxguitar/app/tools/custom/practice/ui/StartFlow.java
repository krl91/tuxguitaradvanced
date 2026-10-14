package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.tools.custom.practice.ui.PreRunCheck.State;

/**
 * The start of a mode as the window sees it (spec 010 contract invariants 1, 12, 13, 14): a start is a check held until it ends, and the mode begins only
 * through the gate's {@code onReady}, once. One instance per window; no widget. Whether a start is <em>pending</em> is derived from the state of the held
 * check, never stored, so a gate that cancels without calling back cannot leave the flow stuck.
 */
final class StartFlow {

	private final StartGate gate;
	private PreRunCheck held;

	StartFlow(StartGate gate) {
		this.gate = gate;
	}

	/** True while the held check waits for the learner: the question, the tuner, the strum or a bad level. */
	synchronized boolean pending() {
		if (this.held == null) {
			return false;
		}
		State state = this.held.state();
		return state == State.TUNING || state == State.TUNER_OPEN || state == State.STRUM || state == State.LEVEL_BAD;
	}

	/**
	 * Asks for a start. False, with nothing opened and nothing run, when a start is already pending (its window comes forward). Otherwise the gate runs the
	 * check and {@code startNow} runs once if and when it is ready.
	 */
	boolean begin(PreRunCheck check, Runnable startNow) {
		synchronized (this) {
			if (pending()) {
				this.gate.toFront();
				return false;
			}
			this.held = check;
		}
		try {
			this.gate.request(check, () -> ready(check, startNow));
		} catch (RuntimeException exception) {
			synchronized (this) {
				if (this.held == check) {
					this.held = null;
				}
			}
			throw exception;
		}
		return true;
	}

	private void ready(PreRunCheck check, Runnable startNow) {
		synchronized (this) {
			if (this.held != check || check.state() != State.READY) {
				return;
			}
			// cleared before the start runs: whatever it does, nothing stays pending and a second answer finds nothing
			this.held = null;
		}
		startNow.run();
	}

	void toFront() {
		this.gate.toFront();
	}

	/** The start is no longer wanted (the mode, the track or the window changed): the check is cancelled, its window closed, and a later ready runs nothing. */
	void cancelPending() {
		PreRunCheck check;
		synchronized (this) {
			check = this.held;
			this.held = null;
		}
		if (check != null) {
			boolean open = check.state() != State.READY && check.state() != State.CANCELLED;
			check.cancel();
			if (open) {
				this.gate.closePending();
			}
		}
	}
}
