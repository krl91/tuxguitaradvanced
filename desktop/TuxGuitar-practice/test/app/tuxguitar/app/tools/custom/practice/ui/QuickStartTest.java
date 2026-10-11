package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.InputAssessment;

public class QuickStartTest {

	@Test
	public void practiceCanStartOnlyWithATrackAndAUsableInput() {
		for( InputAssessment.Status status : InputAssessment.Status.values() ) {
			boolean usable = (status == InputAssessment.Status.SUITABLE || status == InputAssessment.Status.SUBOPTIMAL);
			assertEquals(usable, QuickStart.canStart(true, status), status.name());
			assertFalse(QuickStart.canStart(false, status), status.name());
		}
	}

	@Test
	public void aOneClickStartNeedsTheButtonAndAPractisableTrackUnderTheCursor() {
		assertTrue(QuickStart.requested(true, true));
		assertFalse(QuickStart.requested(true, false));
		assertFalse(QuickStart.requested(false, true));
		assertFalse(QuickStart.requested(false, false));
	}

	@Test
	public void itStartsAsSoonAsEverythingIsReadyAndNeverWithoutBeingRequested() {
		assertTrue(QuickStart.startNow(true, true));
		assertFalse(QuickStart.startNow(true, false));
		assertFalse(QuickStart.startNow(false, true));
	}

	@Test
	public void anUncheckedOrUnusableInputKeepsTheRequestPendingWithoutStarting() {
		boolean requested = QuickStart.requested(true, true);
		assertFalse(QuickStart.startNow(requested, QuickStart.canStart(true, InputAssessment.Status.UNCHECKED)));
		// once the level check says the input is suitable, the same request starts practice
		assertTrue(QuickStart.startNow(requested, QuickStart.canStart(true, InputAssessment.Status.SUITABLE)));
	}

	/** The Arcade button of the transport bar (owner, 2026-10-11) starts at once with the input as it is: only the lack of a track or of an input (checked by the window) stops it. */
	@Test
	public void anImmediateStartDoesNotWaitForTheLevelCheck() {
		for( InputAssessment.Status status : InputAssessment.Status.values() ) {
			// even an input that hears nothing yet (a guitar that is not played is judged unusable, NO_SIGNAL): the game starts, it is the player of the game who plays
			assertTrue(QuickStart.canStart(true, status, true), status.name());
			assertFalse(QuickStart.canStart(false, status, true), status.name());
			assertEquals(QuickStart.canStart(true, status), QuickStart.canStart(true, status, false), status.name());
		}
	}

	/**
	 * Bug (owner, 2026-10-11, the button Restart and the coloured button): the preparation window must never come over a run. It was shown by itself
	 * when the level check judged the input unusable (a guitar that is not played), because it had never been shown.
	 */
	@Test
	public void anUnusableInputNeverShowsThePreparationWindowOverARunOrAnImmediateStart() {
		for( boolean windowShown : new boolean[] { false, true } ) {
			for( boolean runOngoing : new boolean[] { false, true } ) {
				for( boolean immediate : new boolean[] { false, true } ) {
					boolean expected = !windowShown && !runOngoing && !immediate;
					assertEquals(expected, QuickStart.showForUnusableInput(windowShown, runOngoing, immediate, InputAssessment.Status.UNUSABLE),
							windowShown + " " + runOngoing + " " + immediate);
					assertFalse(QuickStart.showForUnusableInput(windowShown, runOngoing, immediate, InputAssessment.Status.SUITABLE));
				}
			}
		}
	}
}
