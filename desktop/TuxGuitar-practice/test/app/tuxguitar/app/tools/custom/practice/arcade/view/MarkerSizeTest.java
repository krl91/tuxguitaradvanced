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

	private static double radius(double factor, long beforeOnset) {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene scene = fixture.scene(false).withMarkerSize(factor).build(fixture.onset(0) - beforeOnset * MS, SCREEN, null, 100, QualityTier.FULL);
		// the marker is a rounded rectangle twice as wide as high: the radius of the setting is its half height over 0.6
		return scene.forEvent(0, Role.MARKER).get(0).value(3) / 0.6;
	}

	@Test
	public void theMarkerShrinksAndGrowsWithTheSettingAtEveryDepth() {
		for (long before : new long[] { 4500, 2500, 1000, 100 }) {
			double normal = radius(1.0, before);
			assertTrue(radius(0.5, before) < normal * 0.75, "half the size is clearly smaller at " + before + " ms");
			assertTrue(radius(1.5, before) > normal * 1.2, "one and a half times is clearly bigger at " + before + " ms");
		}
	}

	/** FR-073 (owner, 2026-10-11): 100 % is three quarters of the former size, 0 % is a single pixel. At the onset the former radius was 3.6 % of the height. */
	@Test
	public void theScaleOfTheSettingIsThreeQuartersOfTheFormerSizeAtOneHundredPercentAndOnePixelAtZero() {
		double former = 720 * 0.036;
		assertEquals(0.75 * former, radius(1.0, 0), 0.01, "100 % is 75 % of the former size");
		assertEquals(0.5, radius(0.0, 0), 1e-9, "0 % is one pixel across");
		assertEquals(0.5, radius(0.0, 3000), 1e-9, "at every depth");
		assertEquals(0.4 * 0.75 * former, radius(0.4, 0), 0.01, "40 % is 40 % of that: no minimum hides the setting");
	}
}
