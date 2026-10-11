package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Bug (owner, 2026-10-11, the coloured Arcade button): a run started at once, as soon as the input is opened, found no measure of the arrival lag yet
 * (the spread is NaN) and failed with "a spread cannot be negative: NaN"; the preparation window showed itself instead of the game.
 */
public class ClockUncertaintyTest {

	@Test
	public void aSpreadNotMeasuredYetIsNotUnstableAndDoesNotFailTheStart() {
		ClockUncertainty unknown = ClockUncertainty.ofMeasurement(Double.NaN);
		assertEquals(0.0, unknown.captureLagSpreadMillis(), 0.0);
		assertFalse(unknown.isUnstable(), "no measure is no warning");
	}

	@Test
	public void aMeasuredSpreadIsKeptAndTheConstructorStillRefusesNonsense() {
		assertTrue(ClockUncertainty.ofMeasurement(ClockUncertainty.UNSTABLE_SPREAD_MILLIS).isUnstable());
		assertEquals(12.5, ClockUncertainty.ofMeasurement(12.5).captureLagSpreadMillis(), 0.0);
		assertThrows(IllegalArgumentException.class, () -> new ClockUncertainty(-1.0));
		assertThrows(IllegalArgumentException.class, () -> new ClockUncertainty(Double.NaN));
	}
}
