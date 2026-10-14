package app.tuxguitar.app.tools.custom.practice.ui;

/**
 * What stands between a start and its mode (spec 010): the gate runs the check with the learner and calls {@code onReady} only when the mode may begin. A
 * check that ends otherwise (cancelled) calls nothing back: {@link StartFlow} reads the state of the check.
 */
interface StartGate {

	/** Runs the check (opens the start window); {@code onReady} runs on the user-interface thread when the check reached {@code READY}. */
	void request(PreRunCheck check, Runnable onReady);

	/** Brings the pending start window forward (the tuner while it is open). */
	void toFront();

	/** Closes the pending start window, if any (the check was cancelled from outside). */
	default void closePending() {
	}
}
