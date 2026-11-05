package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.ui.resource.UIRectangle;

/**
 * The usable height of the screen holding a window (spec 025, FR-005, research R3): without an SWT display (another toolkit, or no screen at all, as in
 * these tests) it is unknown, said as 0, and the practice layout then takes the 768-pixel reference.
 */
public class ScreensUsableHeightTest {

	@Test
	public void withoutADisplayTheHeightIsUnknown() {
		assertEquals(0f, Screens.usableHeight(new UIRectangle(100f, 100f, 600f, 400f)), 0.01f);
	}

	@Test
	public void withoutBoundsTheHeightIsUnknown() {
		assertEquals(0f, Screens.usableHeight(null), 0.01f);
	}
}
