package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** Everything lands at the foot of the neck at the same instant, a chord as a single note (spec 003 Q37, the owner of 2026-10-13). */
public class FootLandingTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static double bottom(SceneItem quad) {
		return Math.max(Math.max(quad.value(1), quad.value(3)), Math.max(quad.value(5), quad.value(7)));
	}

	@Test
	public void aChordPanelIsOnTheGroundPathOfTheFootprintOfANoteAtEveryProgressAndLandsAtTheFootAtTheOnset() {
		SongFixture song = new SongFixture(2);
		song.chord(0, 0, new int[] { 5, 4, 3 }, new int[] { 3, 2, 0 });
		song.quarter(1, 0, 2, 3);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		IsometricProjection projection = new IsometricProjection(SCREEN);
		for (int step = 1; step <= 100; step++) {
			double progress = step / 100.0;
			long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
			Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
			double expected = Landing.ground(projection, SCREEN, fixture.camera.at(now), fixture.camera.at(now).centre(), progress).y();
			assertEquals(expected, bottom(scene.forEvent(0, Role.CHORD_PANEL).get(0)), 0.5, "the panel stands on the ground path at p=" + progress);
		}
		Scene landed = fixture.scene(false).build(fixture.onset(0), SCREEN, null, 100, QualityTier.FULL);
		assertEquals(Landing.footY(SCREEN), bottom(landed.forEvent(0, Role.CHORD_PANEL).get(0)), 0.5, "at the foot at the onset");
	}

	@Test
	public void theApproachBandGoesTheWayOfTheLanesAndReachesTheFootOfTheNeckWhereEverythingLands() {
		SongFixture song = new SongFixture(2);
		song.quarter(0, 0, 1, 12);
		song.quarter(0, 1, 2, 5);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 500 * MS, SCREEN, null, 100, QualityTier.FULL);
		IsometricProjection projection = new IsometricProjection(SCREEN);
		double foot = Landing.footY(SCREEN);
		boolean reaches = false;
		for (SceneItem band : scene.withRole(Role.APPROACH_BAND)) {
			assertTrue(bottom(band) <= foot + 0.5, "the band does not go under the foot");
			reaches |= Math.abs(bottom(band) - foot) < 0.5;
			// the sides follow the lanes: straight from the horizon to the foot, so the lower corners are not under the upper ones
			double topLeft = band.value(0);
			double bottomLeft = band.value(6);
			double centre = SCREEN.width() / 2.0;
			assertTrue(Math.abs(bottomLeft - centre) >= Math.abs(topLeft - centre) - 0.5, "the sides open toward the foot, as the lanes do");
		}
		assertTrue(reaches, "the band reaches the foot of the neck");
	}
}
