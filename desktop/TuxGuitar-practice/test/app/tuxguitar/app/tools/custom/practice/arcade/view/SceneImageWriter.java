package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Draws a scene to a PNG with Java2D, for the owner's review of the reference scenes (spec 003 T044). It is a tool of the tests: the application paints the
 * same scene with the toolkit's painter ({@code UiPainterRenderer}), so the composition, the colours, the texts and the geometry are those of the
 * application, while the anti-aliasing and the font rendering are those of Java2D.
 */
public final class SceneImageWriter {


	private SceneImageWriter() {
	}

	public static BufferedImage draw(Scene scene) {
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
		for (SceneItem item : scene.items()) {
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0.0, Math.min(1.0, item.alpha()))));
			g.setColor(new Color(item.colour()));
			switch (item.shape()) {
				case LINE:
					g.setStroke(new BasicStroke((float) Math.max(1.0, item.lineWidth()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(new java.awt.geom.Line2D.Double(item.value(0), item.value(1), item.value(2), item.value(3)));
					break;
				case QUAD:
					Path2D.Double path = new Path2D.Double();
					path.moveTo(item.value(0), item.value(1));
					for (int i = 2; i < 8; i += 2) {
						path.lineTo(item.value(i), item.value(i + 1));
					}
					path.closePath();
					g.fill(path);
					break;
				case DISC:
					g.fill(new java.awt.geom.Ellipse2D.Double(item.value(0) - item.value(2), item.value(1) - item.value(3), 2 * item.value(2), 2 * item.value(3)));
					break;
				case RING:
					g.setStroke(new BasicStroke((float) Math.max(1.0, item.lineWidth())));
					g.draw(new java.awt.geom.Ellipse2D.Double(item.value(0) - item.value(2), item.value(1) - item.value(3), 2 * item.value(2), 2 * item.value(3)));
					break;
				case TEXT:
					g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(6, (int) Math.round(item.value(2)))));
					FontMetrics metrics = g.getFontMetrics();
					double w = metrics.stringWidth(item.text());
					double x = item.value(0) - (item.align() == SceneItem.Align.CENTRE ? w / 2 : item.align() == SceneItem.Align.RIGHT ? w : 0);
					g.drawString(item.text(), (float) x, (float) (item.value(1) - metrics.getHeight() / 2.0 + metrics.getAscent()));
					break;
				default:
					break;
			}
		}
		g.dispose();
		return image;
	}

	public static void write(Scene scene, File file) throws IOException {
		file.getParentFile().mkdirs();
		ImageIO.write(draw(scene), "png", file);
	}
}
