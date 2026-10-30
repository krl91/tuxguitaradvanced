package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Spec 018 (T003, FR-002, research R4): the colours of the demo fire, from dark red to white, translucent. */
public class FireColoursTest {

	@Test
	public void heatZeroDrawsNothing() {
		assertEquals(0.0, FireColours.alpha(0, false), 0.0, "a cold cell has no opacity: nothing is drawn there");
		assertEquals(0.0, FireColours.alpha(0, true), 0.0);
	}

	@Test
	public void theStepsGoFromDarkRedToWhite() {
		int previous = FireColours.colour(1);
		assertTrue(red(previous) > green(previous) && green(previous) >= blue(previous), "step 1 is a dark red");
		assertTrue(red(previous) < 0x80, "dark");
		for (int step = 2; step < FireColours.STEPS; step++) {
			int colour = FireColours.colour(step);
			assertTrue(red(colour) >= red(previous) && green(colour) >= green(previous) && blue(colour) >= blue(previous), "step " + step + " is not darker than the one before");
			previous = colour;
		}
		int white = FireColours.colour(FireColours.STEPS - 1);
		assertTrue(red(white) > 0xF0 && green(white) > 0xF0 && blue(white) > 0xC0, "the hottest step is almost white");
	}

	@Test
	public void theOpacityGrowsWithTheHeatAndNeverPassesSixtyPercent() {
		double previous = 0.0;
		for (int step = 1; step < FireColours.STEPS; step++) {
			double alpha = FireColours.alpha(step, false);
			assertTrue(alpha > previous, "step " + step + " is more opaque than the one before");
			assertTrue(alpha <= 0.60 + 1e-9, "at most 60 % (owner's answer, FR-002)");
			assertTrue(FireColours.alpha(step, true) <= 0.36 + 1e-9, "discreet at most 36 %");
			assertEquals(0.6 * alpha, FireColours.alpha(step, true), 1e-9, "discreet is 60 % of full");
			previous = alpha;
		}
		assertEquals(0.60, FireColours.alpha(FireColours.STEPS - 1, false), 1e-9);
	}

	private static int red(int rgb) {
		return (rgb >> 16) & 0xFF;
	}

	private static int green(int rgb) {
		return (rgb >> 8) & 0xFF;
	}

	private static int blue(int rgb) {
		return rgb & 0xFF;
	}
}
