package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;

/** Spec 019 (T006, FR-017): the level of the flames with Arcade's defaults is the level of before, at every step of a scripted run. */
public class FireParameterFlameLevelIdentityTest {

	@Test
	public void theLevelWithTheDefaultsIsTheLevelOfBefore() {
		FlameLevel old = new FlameLevel();
		FlameLevel tuned = new FlameLevel(FireTuning.ARCADE_DEFAULTS);
		long wall = 1_000_000_000L;
		double[] shares = { 0.0, 0.2, 0.6, 0.9, 1.0, 1.0, 0.7, 0.3, 0.0, 0.8 };
		for (int frame = 0; frame < 600; frame++) {
			wall += (frame % 97 == 0 ? 400_000_000L : 16_666_667L);
			double share = shares[(frame / 60) % shares.length];
			int judged = frame / 25;
			if (frame == 200) {
				old.hold(wall);
				tuned.hold(wall);
			}
			if (frame == 260) {
				old.resume(wall);
				tuned.resume(wall);
			}
			old.update(share, judged, wall);
			tuned.update(share, judged, wall);
			assertEquals(old.level(), tuned.level(), 0.0, "frame " + frame);
		}
	}
}
