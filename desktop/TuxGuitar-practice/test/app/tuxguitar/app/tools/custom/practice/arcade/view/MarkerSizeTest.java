package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** The size of the flying markers follows the setting (owner, 2026-10-11): the setting must change what is painted. */
public class MarkerSizeTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static double halfHeight(double factor, long beforeOnset) {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene scene = fixture.scene(false).withMarkerSize(factor).build(fixture.onset(0) - beforeOnset * MS, SCREEN, null, 100, QualityTier.FULL);
		return scene.forEvent(0, Role.MARKER).get(0).value(3);
	}

	@Test
	public void theMarkerShrinksAndGrowsWithTheSettingAtEveryDepth() {
		for (long before : new long[] { 4500, 2500, 1000, 100 }) {
			double normal = halfHeight(1.0, before);
			assertTrue(halfHeight(0.5, before) < normal * 0.75, "half the size is clearly smaller at " + before + " ms");
			assertTrue(halfHeight(1.5, before) > normal * 1.2, "one and a half times is clearly bigger at " + before + " ms");
		}
	}

	/** FR-073: 100 % lands on the target rectangle (24 px high at 720 px), 0 % is one pixel across. */
	@Test
	public void theScaleOfTheSettingIsTheTargetSizeAtOneHundredPercentAndOnePixelAtZero() {
		assertEquals(12.0, halfHeight(1.0, 0), 0.01, "100 % is the half height of the target rectangle");
		assertEquals(0.5, halfHeight(0.0, 0), 1e-9, "0 % is one pixel across");
		assertEquals(0.5, halfHeight(0.0, 3000), 1e-9, "at every depth");
		assertEquals(0.4 * 12.0, halfHeight(0.4, 0), 0.01, "40 % is 40 % of that: no minimum hides the setting");
	}
}
