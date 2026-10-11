package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The open string as a bar (spec 003 FR-069, SC-026, the owner's reference of 2026-10-11): a single note at fret 0 is a thin horizontal bar in the colour of its
 * string, as wide as the lanes, with a bright cap at its left end and the word "open"; it has no disk, no fret number and no connector; its lower edge is on the
 * top edge of the neck at the onset; inside a chord an open string is only an {@code O} of the diagram.
 */
public class OpenBarTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static SceneFixture opens(int strings) {
		SongFixture song = new SongFixture(3);
		for (int string = 1; string <= strings; string++) {
			song.quarter(1 + (string - 1) / 4, (string - 1) % 4, string, 0);
		}
		return new SceneFixture(song, strings == 6 ? SceneFixture.sixStrings() : TrackTuningFactory.any(43, 38, 33, 28), 100);
	}

	@Test
	public void aNoteAtFretZeroIsOneBarAcrossTheLanesInTheColourOfItsStringForEveryStringAndProgress() {
		for (int strings : new int[] { 4, 6 }) {
			SceneFixture fixture = opens(strings);
			for (int event = 0; event < Math.min(4, fixture.events.size()); event++) {
				int string = fixture.events.get(event).positions().get(0).tgString();
				ArcadeRun.View run = event == 0 ? null : ArcadeSceneTest.completedBefore(event, fixture.events.size());
				double previousThickness = 0.0;
				for (int step = 1; step <= 101; step++) {
					double progress = step / 101.0;
					long now = fixture.onset(event) - (long) ((1.0 - progress) * CameraTrack.Settings.standard().lookAheadNanos);
					Scene scene = fixture.scene(false).build(now, SCREEN, run, 100, QualityTier.FULL);
					List<SceneItem> bars = scene.forEvent(event, Role.OPEN_BAR);
					assertEquals(1, bars.size(), strings + " strings, string " + string + ", progress " + progress + ": one bar");
					SceneItem bar = bars.get(0);
					assertEquals(SceneItem.Shape.QUAD, bar.shape());
					assertEquals(StringColours.forStrings(strings).of(string), bar.colour(), "the colour of its string");
					double left = Math.min(Math.min(bar.value(0), bar.value(2)), Math.min(bar.value(4), bar.value(6)));
					double right = Math.max(Math.max(bar.value(0), bar.value(2)), Math.max(bar.value(4), bar.value(6)));
					IsometricProjection projection = new IsometricProjection(SCREEN);
					Camera cam = fixture.camera.at(now);
					ScreenPoint expectedLeft = projection.approach(cam.left(), progress, cam);
					ScreenPoint expectedRight = projection.approach(cam.right(), progress, cam);
					assertEquals(expectedLeft.x(), left, 0.5, "the left end is the left end of the lanes at this depth");
					assertEquals(expectedRight.x(), right, 0.5, "the right end is the right end of the lanes");
					double bottom = Math.max(Math.max(bar.value(1), bar.value(3)), Math.max(bar.value(5), bar.value(7)));
					double top = Math.min(Math.min(bar.value(1), bar.value(3)), Math.min(bar.value(5), bar.value(7)));
					assertEquals(expectedLeft.y(), bottom, 0.5, "its lower edge follows the lanes");
					assertTrue(bottom <= projection.neckTopY() + 0.5, "nothing below the top edge of the neck");
					assertTrue(bottom - top >= previousThickness - 1e-9, "its thickness only grows");
					previousThickness = bottom - top;
					// nothing of a fretted note: no marker, no fret number, no disk on the lane, no connector
					assertTrue(scene.forEvent(event, Role.MARKER).isEmpty() && scene.forEvent(event, Role.MARKER_FRET).isEmpty() && scene.forEvent(event, Role.TRAJECTORY).isEmpty(),
							"no marker, fret number or trajectory for an open string");
				}
			}
		}
	}

	@Test
	public void theBarLandsWithItsLowerEdgeOnTheTopEdgeOfTheNeckAtTheOnsetAndTheWordOpenAndTheCapAreThere() {
		SceneFixture fixture = opens(6);
		for (QualityTier tier : QualityTier.values()) {
			Scene scene = fixture.scene(false).build(fixture.onset(0), SCREEN, null, 100, tier);
			SceneItem bar = scene.forEvent(0, Role.OPEN_BAR).get(0);
			double bottom = Math.max(Math.max(bar.value(1), bar.value(3)), Math.max(bar.value(5), bar.value(7)));
			assertEquals(new IsometricProjection(SCREEN).neckTopY(), bottom, 0.5, tier + ": on the top edge of the neck at the onset");
			List<SceneItem> words = scene.forEvent(0, Role.OPEN_LABEL);
			assertEquals(1, words.size(), "the word of the open string");
			assertEquals("Open string", words.get(0).text());
			assertEquals(1, scene.forEvent(0, Role.OPEN_CAP).size(), "the bright cap at the left end");
			assertTrue(scene.forEvent(0, Role.TARGET_RECT).isEmpty(), "an open string has no target disk");
			assertTrue(scene.withRole(Role.TARGET_RECT).isEmpty(), "and none from any other event");
		}
	}

	@Test
	public void anOpenStringInsideAChordStaysAnOInTheDiagramWithNoBar() {
		SongFixture song = new SongFixture(3);
		song.chord(1, 0, new int[] { 4, 3, 2 }, new int[] { 2, 0, 1 });
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 300 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertTrue(scene.withRole(Role.OPEN_BAR).isEmpty(), "no flying bar for an open string of a chord");
		boolean open = false;
		for (SceneItem mark : scene.withRole(Role.CARTOUCHE_MARK)) {
			open |= "O".equals(mark.text());
		}
		assertTrue(open, "the O of the diagram");
		assertTrue(scene.withRole(Role.TARGET_RECT).stream().noneMatch(d -> d.tgString() == 3), "no disk on an open string of the chord");
		assertEquals(3, scene.withRole(Role.TARGET_RECT).size() + 1, "the two fretted positions have their disks (the open one has none)");
	}
}
