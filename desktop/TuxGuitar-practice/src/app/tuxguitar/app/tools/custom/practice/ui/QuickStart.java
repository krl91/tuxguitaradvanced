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
}
