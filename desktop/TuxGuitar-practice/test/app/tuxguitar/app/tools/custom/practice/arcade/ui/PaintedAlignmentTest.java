package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Java2dPainter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The destination of an incoming note measured on the pixels that the renderer of the application paints (spec 003 FR-066, SC-024), in every build: the scene is
 * replayed by {@code UiPainterRenderer} on a Java2D painter that has the contract of the toolkit's painters ({@code addCircle} takes the centre), and the
 * painted disk is looked for among the painted wires and strings. A renderer that gives a painter the corner of the circle instead of its centre fails here, as
 * it failed on the real SWT canvas ({@code ArcadePixelAlignmentSwtTest} is the same measure on the native canvas).
 */
public class PaintedAlignmentTest {

	private static PaintedAlignment.Picture paint(Scene scene, QualityTier tier) {
		int width = (int) Math.round(scene.viewport().width());
		int height = (int) Math.round(scene.viewport().height());
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new java.awt.Color(scene.theme().background()));
		g.fillRect(0, 0, width, height);
		UiPainterRenderer renderer = new UiPainterRenderer(Java2dPainter.factory(), "Sans");
		renderer.target(new Java2dPainter(g));
		renderer.render(scene, tier);
		renderer.dispose();
		g.dispose();
		int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
		for (int i = 0; i < rgb.length; i++) {
			rgb[i] &= 0xFFFFFF;
		}
		return new PaintedAlignment.Picture(width, height, rgb);
	}

	private static SceneFixture note(int string, int fret, int later) {
		SongFixture song = new SongFixture(4);
		song.quarter(1, 0, string, fret);
		song.quarter(1, 2, 1 + (string % 6), later);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void theDiskIsOnItsPaintedCellAndItsPaintedStringForEveryFretStringSizeProgressAndTier() throws Exception {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		Viewport[] sizes = { new Viewport(1280, 720), new Viewport(900, 560), new Viewport(1600, 900), new Viewport(2560, 1440) };
		int checked = 0;
		for (Viewport viewport : sizes) {
			for (int fret : new int[] { 1, 5, 10, 12, 20 }) {
				for (int string = 1; string <= 6; string++) {
					SceneFixture fixture = note(string, fret, fret > 3 ? fret - 2 : fret + 2);
					for (double progress : new double[] { 0.3, 0.7, 1.0 }) {
						for (QualityTier tier : QualityTier.values()) {
							long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
							Scene scene = fixture.scene(false).build(now, viewport, null, 100, tier);
							PaintedAlignment.check(scene, paint(app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFilter.withoutTheMarker(scene), tier), fret, progress, "fret " + fret + " string " + string + " " + viewport.width() + " p=" + progress + " " + tier, true);
							checked++;
						}
					}
				}
			}
		}
		assertTrue(checked >= 1000, "pictures measured: " + checked);
	}

	@Test
	public void aMovingCameraKeepsTheDiskOnItsPaintedString() throws Exception {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		int checked = 0;
		for (int fret : new int[] { 1, 3, 14, 22 }) {
			for (int string = 1; string <= 6; string++) {
				// the next note is far away, so the camera pans while this one comes in: the wires of its cell may be out of the picture, the disk and its string are not
				SceneFixture fixture = note(string, fret, fret < 10 ? 23 : 1);
				for (double progress : new double[] { 0.2, 0.5, 0.9, 1.0 }) {
					long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
					Scene scene = fixture.scene(false).build(now, new Viewport(1280, 720), null, 100, QualityTier.FULL);
					PaintedAlignment.check(scene, paint(app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFilter.withoutTheMarker(scene), QualityTier.FULL), fret, progress, "moving camera fret " + fret + " string " + string + " p=" + progress, false);
					checked++;
				}
			}
		}
		assertTrue(checked == 96, "pictures measured: " + checked);
	}
}
