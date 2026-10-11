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

	/** The Arcade button of the transport bar (owner, 2026-10-11) starts at once with the input as it is: only an unusable input or no track stops it. */
	@Test
	public void anImmediateStartDoesNotWaitForTheLevelCheck() {
		for( InputAssessment.Status status : InputAssessment.Status.values() ) {
			assertEquals(status != InputAssessment.Status.UNUSABLE, QuickStart.canStart(true, status, true), status.name());
			assertFalse(QuickStart.canStart(false, status, true), status.name());
			assertEquals(QuickStart.canStart(true, status), QuickStart.canStart(true, status, false), status.name());
		}
	}
}
