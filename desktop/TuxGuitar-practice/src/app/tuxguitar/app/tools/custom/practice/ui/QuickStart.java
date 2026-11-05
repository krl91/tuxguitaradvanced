package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.audio.engine.input.InputAssessment;

/**
 * The rules of the one-click start from the transport-bar button ("practise this track"): practice
 * starts by itself on the track under the score cursor as soon as the input is usable, without the
 * learner pressing Start in the preparation window.
 */
final class QuickStart {

	private QuickStart() {
	}

	/** Practice can start: a track is chosen and the input is suitable (or usable with a warning). */
	static boolean canStart(boolean trackSelected, InputAssessment.Status status) {
		return trackSelected && (status == InputAssessment.Status.SUITABLE || status == InputAssessment.Status.SUBOPTIMAL);
	}

	/**
	 * The Arcade button of the transport bar starts at once with the input as it is, the last one used or the remembered default, without waiting for the
	 * level check: only an unusable input (or no track) stops it.
	 */
	static boolean canStart(boolean trackSelected, InputAssessment.Status status, boolean immediate) {
		return immediate ? trackSelected : canStart(trackSelected, status);
	}

	/**
	 * Why the start button is greyed, as a text key, or null when it can be pressed or no track is chosen (the track tab says that). A one-click start
	 * keeps its own text; otherwise the reason follows the level check: nothing heard yet, or an input that cannot be used (owner, 2026-10-29).
	 */
	static String greyedReason(boolean trackSelected, InputAssessment.Status status, boolean immediate, boolean oneClickWaiting) {
		if( !trackSelected || canStart(trackSelected, status, immediate) ) {
			return null;
		}
		if( oneClickWaiting ) {
			return "practice.quickstart.waiting";
		}
		return (status == InputAssessment.Status.UNCHECKED ? "practice.start.greyed.UNCHECKED" : "practice.start.greyed.UNUSABLE");
	}

	/** Why the start button is greyed, said under it: the text key of the cause and its action, and whether Check again applies. */
	static final class Blocker {
		private final String key;
		private final boolean checkAgain;

		Blocker(String key, boolean checkAgain) {
			this.key = key;
			this.checkAgain = checkAgain;
		}

		String key() {
			return this.key;
		}

		boolean checkAgain() {
			return this.checkAgain;
		}
	}

	/**
	 * The cause of a greyed start button, or null when it can be pressed (spec 025, FR-002 to FR-004, data-model "Start blocker"): no track, the one-click
	 * start waiting for the guitar, a check listening, or the cause the level check found, each with what to do; Check again for the input level's causes.
	 */
	static Blocker blocker(boolean trackSelected, InputAssessment assessment, boolean immediate, boolean oneClickWaiting, boolean checking) {
		if( !trackSelected ) {
			return new Blocker("practice.start.blocker.no-track", false);
		}
		if( canStart(trackSelected, assessment.getStatus(), immediate) ) {
			return null;
		}
		if( oneClickWaiting ) {
			return new Blocker("practice.quickstart.waiting", false);
		}
		if( checking ) {
			return new Blocker("practice.start.blocker.checking", false);
		}
		if( assessment.getStatus() == InputAssessment.Status.UNCHECKED ) {
			return new Blocker("practice.start.blocker.not-checked", true);
		}
		switch( assessment.getReason() ) {
			case CLIPPING: return new Blocker("practice.start.blocker.saturating", true);
			case DEVICE_LOST: return new Blocker("practice.start.blocker.input-lost", true);
			case ACCESS_DENIED_SUSPECTED: return new Blocker("practice.start.blocker.access-refused", true);
			default: return new Blocker("practice.start.blocker.no-signal", true);
		}
	}

	/** The preparation window shows itself for an unusable input only when nothing else is going on: never over a run (Arcade, tempo following) or a start at once. */
	static boolean showForUnusableInput(boolean windowShown, boolean runOngoing, boolean immediateStart, InputAssessment.Status status) {
		return !windowShown && !runOngoing && !immediateStart && status == InputAssessment.Status.UNUSABLE;
	}

	/**
	 * A one-click start only applies to the track under the cursor: if that track cannot be practised,
	 * the learner must choose a track in the preparation window, so nothing starts by itself.
	 */
	static boolean requested(boolean fromButton, boolean cursorTrackCanBePractised) {
		return fromButton && cursorTrackCanBePractised;
	}

	/** Start now: it was requested and everything needed is there. */
	static boolean startNow(boolean requested, boolean canStart) {
		return requested && canStart;
	}

	/**
	 * After the patience of a one-click start, the preparation window is shown for the learner to act, but never over a start window that waits for an answer
	 * (spec 010: the question, the tuner, the strum), never over a run, and only if it is still open and was not shown yet.
	 */
	static boolean showAfterPatience(boolean open, boolean windowShown, boolean runOngoing, boolean startPending) {
		return open && !windowShown && !runOngoing && !startPending;
	}

	/** The learner cancelled the start window of a one-click start: the preparation window that was opened for it, and never shown, goes away. */
	static boolean closeAfterCancelledStart(boolean open, boolean windowShown, boolean runOngoing, boolean disposing) {
		return !disposing && open && !windowShown && !runOngoing;
	}
}
