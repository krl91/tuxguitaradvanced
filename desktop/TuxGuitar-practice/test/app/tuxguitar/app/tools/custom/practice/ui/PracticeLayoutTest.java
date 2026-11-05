package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The practice window's tabs fit a 768-pixel screen (spec 025, FR-005, FR-006, clarification 4, data-model "Practice tab height"): the folder is as high as
 * the tallest tab when it fits, otherwise what the screen leaves after the rest of the window, and the tab scrolls; never below one row of controls.
 */
public class PracticeLayoutTest {

	private static final float DELTA = 0.01f;

	@Test
	public void aTabThatFitsKeepsItsHeight() {
		assertEquals(300f, PracticeLayout.tabHeight(300f, 768f, 200f), DELTA);
	}

	@Test
	public void aTallTabIsCappedToWhatTheScreenLeaves() {
		assertEquals(568f, PracticeLayout.tabHeight(900f, 768f, 200f), DELTA);
	}

	@Test
	public void aSmallerScreenCapsMore() {
		assertEquals(400f, PracticeLayout.tabHeight(900f, 600f, 200f), DELTA);
	}

	@Test
	public void anUnknownScreenIsTakenAs768() {
		assertEquals(568f, PracticeLayout.tabHeight(900f, 0f, 200f), DELTA);
		assertEquals(568f, PracticeLayout.tabHeight(900f, -1f, 200f), DELTA);
	}

	@Test
	public void aLargerScreenIsTakenAs768() {
		assertEquals(568f, PracticeLayout.tabHeight(900f, 1440f, 200f), DELTA);
	}

	@Test
	public void neverBelowOneRowOfControls() {
		assertEquals(PracticeLayout.MIN_TAB_HEIGHT, PracticeLayout.tabHeight(900f, 300f, 280f), DELTA);
		assertEquals(PracticeLayout.MIN_TAB_HEIGHT, PracticeLayout.tabHeight(900f, 300f, 400f), DELTA);
	}

	@Test
	public void aShortTabIsNotRaisedToTheMinimum() {
		// the folder keeps the height of its content when that is small: the minimum is only a floor for the cap
		assertEquals(40f, PracticeLayout.tabHeight(40f, 768f, 200f), DELTA);
	}
}
