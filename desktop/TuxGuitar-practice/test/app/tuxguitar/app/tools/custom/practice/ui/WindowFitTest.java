package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.resource.UISize;

/**
 * Regression for the practice window whose buttons left the visible window after an input was
 * selected (new hint and level lines, window size unchanged): the window must take at least its
 * packed size and stay inside the reference area.
 */
public class WindowFitTest {

	private static final UIRectangle AREA = new UIRectangle(0, 0, 1440, 900);

	@Test
	public void growsToThePackedSizeWhenContentGetsTaller() {
		UIRectangle fitted = WindowFit.fit(new UIRectangle(400, 200, 520, 360), new UISize(520, 470), AREA);

		assertBounds(400, 200, 520, 470, fitted);
	}

	@Test
	public void neverShrinksBelowTheLearnersSize() {
		UIRectangle fitted = WindowFit.fit(new UIRectangle(400, 200, 700, 600), new UISize(520, 470), AREA);

		assertBounds(400, 200, 700, 600, fitted);
	}

	@Test
	public void movesBackInsideTheAreaWhenGrowingPastItsEdge() {
		UIRectangle fitted = WindowFit.fit(new UIRectangle(1100, 600, 300, 200), new UISize(360, 420), AREA);

		assertBounds(1080, 480, 360, 420, fitted);
	}

	@Test
	public void keepsTheTopLeftVisibleAndNeverCapsTheSizeWhenLargerThanTheArea() {
		UIRectangle fitted = WindowFit.fit(new UIRectangle(300, 300, 400, 400), new UISize(1600, 1000), AREA);

		assertBounds(0, 0, 1600, 1000, fitted);
	}

	@Test
	public void keepsThePositionWithoutAReferenceArea() {
		UIRectangle fitted = WindowFit.fit(new UIRectangle(-50, 2000, 300, 200), new UISize(320, 240), null);

		assertBounds(-50, 2000, 320, 240, fitted);
	}

	private static void assertBounds(float x, float y, float width, float height, UIRectangle actual) {
		assertEquals(x, actual.getX());
		assertEquals(y, actual.getY());
		assertEquals(width, actual.getWidth());
		assertEquals(height, actual.getHeight());
	}
}
