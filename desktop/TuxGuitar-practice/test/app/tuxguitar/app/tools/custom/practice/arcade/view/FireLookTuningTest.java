package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Spec 018 (T026; owner's look gate of 2026-10-30, research R9): the pixels of the fire follow the demo's grid, 1/192 of the picture's width. */
public class FireLookTuningTest {

	@Test
	public void aCellIsOneHundredAndNinetySecondOfTheWidthAtTheFullTierAndTwiceThatAtTheBalancedOne() {
		assertEquals(7.0, FireField.cell(new Viewport(1280, 720), QualityTier.FULL), 1e-9);
		assertEquals(10.0, FireField.cell(new Viewport(1920, 1080), QualityTier.FULL), 1e-9);
		assertEquals(14.0, FireField.cell(new Viewport(1280, 720), QualityTier.BALANCED), 1e-9);
	}

	@Test
	public void aCellNeverGetsSmallerThanFourPixels() {
		assertEquals(4.0, FireField.cell(new Viewport(640, 480), QualityTier.FULL), 1e-9);
	}
}
