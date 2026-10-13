package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** Everything written on the HUD is inside the picture, whatever the shape and the size of the window (owner, 2026-10-13: the texts of the top were cut). */
public class HudVisibilityTest {

	@Test
	public void theTextsOfTheHudAreInsideThePictureForEveryShapeOfWindow() {
		SongFixture song = new SongFixture(2);
		song.quarter(0, 0, 2, 5);
		song.quarter(0, 1, 3, 7);
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		ArcadeRun.View run = ArcadeSceneTest.completedBefore(1, fixture.events.size());
		double[][] sizes = { { 1280, 720 }, { 800, 600 }, { 640, 480 }, { 1500, 500 }, { 2000, 1000 }, { 1100, 950 }, { 900, 400 } };
		for (double[] size : sizes) {
			Viewport viewport = new Viewport(size[0], size[1]);
			Scene scene = fixture.scene(false).build(fixture.onset(0) - 500 * SceneFixture.MS, viewport, run, 100, QualityTier.FULL);
			for (SceneItem item : scene.items()) {
				if (item.role() == Role.HUD_STREAK || item.role() == Role.HUD_SPEED || item.role() == Role.HUD_NEXT) {
					SceneBoxes.Box box = SceneBoxes.ofText(item);
					assertTrue(box.x1 >= 0 && box.y1 >= 0 && box.x2 <= size[0] && box.y2 <= size[1], size[0] + "x" + size[1] + ": " + box + " is inside the picture");
				}
			}
		}
	}
}
