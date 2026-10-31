package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;

/** Spec 019 (T004, FR-017): the Arcade defaults of the growth are the constants of {@link FlameLevel}, which only this package can read. */
public class FireParameterLevelDefaultsTest {

	@Test
	public void theGrowthDefaultsAreTheConstantsOfFlameLevel() {
		assertEquals(FlameLevel.MIN_JUDGED, FireParameter.GROWTH_MIN_JUDGED.arcadeDefault(), 0.0);
		assertEquals(FlameLevel.RISE_PER_SECOND, FireParameter.GROWTH_RISE.arcadeDefault(), 0.0);
		assertEquals(FlameLevel.FALL_PER_SECOND, FireParameter.GROWTH_FALL.arcadeDefault(), 0.0);
		assertEquals(FlameLevel.MAX_STEP_NANOS / 1e6, FireParameter.GROWTH_MAX_STEP.arcadeDefault(), 0.0);
	}
}
