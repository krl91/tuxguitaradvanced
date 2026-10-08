package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Draws a scene to a PNG for the owner's review of the reference scenes (spec 003 T044), with the renderer of the application ({@code UiPainterRenderer}) on
 * {@link Java2dPainter}, which has the contract of the toolkit's painters: the composition, the colours, the texts and the geometry are what the renderer sends
 * to a painter, while the anti-aliasing and the font rendering are those of Java2D.
 */
public final class SceneImageWriter {


	private SceneImageWriter() {
	}

	public static BufferedImage draw(Scene scene) {
		return draw(scene, QualityTier.FULL);
	}

	public static BufferedImage draw(Scene scene, QualityTier tier) {
		int width = (int) Math.round(scene.viewport().width());
		int height = (int) Math.round(scene.viewport().height());
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int base = scene.theme().background();
		Color top = new Color(Math.max(0, ((base >> 16) & 0xFF) - 8), Math.max(0, ((base >> 8) & 0xFF) - 8), Math.max(0, (base & 0xFF) - 8));
		g.setPaint(new java.awt.GradientPaint(0, 0, top, 0, height, new Color(base)));
		g.fillRect(0, 0, width, height);
		// the renderer of the application, on a painter with the contract of the toolkit's: what the renderer sends is what is drawn
		app.tuxguitar.app.tools.custom.practice.arcade.ui.UiPainterRenderer renderer = new app.tuxguitar.app.tools.custom.practice.arcade.ui.UiPainterRenderer(Java2dPainter.factory(),
				"Sans");
		renderer.target(new Java2dPainter(g));
		renderer.render(scene, tier);
		renderer.dispose();
		g.dispose();
		return image;
	}

	public static void write(Scene scene, File file) throws IOException {
		write(scene, file, QualityTier.FULL);
	}

	public static void write(Scene scene, File file, QualityTier tier) throws IOException {
		file.getParentFile().mkdirs();
		ImageIO.write(draw(scene, tier), "png", file);
	}
}
