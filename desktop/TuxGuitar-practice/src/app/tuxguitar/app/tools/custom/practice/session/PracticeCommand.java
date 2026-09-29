package app.tuxguitar.app.tools.custom.practice.session;

/**
 * Learner commands posted to the session: by the key-bindable practice actions (START, SKIP,
 * RESUME, STOP) or by the practice window (RESTART after completion).
 */
public enum PracticeCommand {
	START,
	SKIP,
	RESUME,
	STOP,
	RESTART
}
