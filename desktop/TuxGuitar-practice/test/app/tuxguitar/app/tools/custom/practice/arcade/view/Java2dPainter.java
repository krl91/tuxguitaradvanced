package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Proxy;

import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIImage;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * A {@link UIPainter} that paints with Java2D, with the contract that the SWT, JavaFX and Qt painters of the toolkit all have (read in {@code SWTPainter},
 * {@code JFXPainter}, {@code QTPainter}): {@code addCircle(x, y, width)} takes the CENTRE and the diameter, {@code drawString} takes the top left of the text,
 * a path is drawn or filled when it is closed, the alpha is 0 to 255. The images for the owner's review are made with the renderer of the application
 * ({@code UiPainterRenderer}) on this painter, so that they show what the renderer sends to a painter and not what the scene says (the first ones, drawn
 * from the scene, hid a circle drawn at the wrong place by the renderer, spec 003 FR-066).
 */
public final class Java2dPainter implements UIPainter {

	private final Graphics2D g;
	private final Path2D.Double path = new Path2D.Double();
	private int style = PATH_DRAW;
	private int alpha = 255;
	private Color foreground = Color.BLACK;
	private Color background = Color.BLACK;
	private double lineWidth = 1.0;
	private UIFont font;

	public Java2dPainter(Graphics2D g) {
		this.g = g;
	}

	/** The factory the renderer asks for colours and fonts: they are plain values here. */
	public static UIFactory factory() {
		return (UIFactory) Proxy.newProxyInstance(UIFactory.class.getClassLoader(), new Class<?>[] { UIFactory.class }, (p, method, args) -> {
			if (method.getName().equals("createColor")) {
				final int r = (Integer) args[0];
				final int gr = (Integer) args[1];
				final int b = (Integer) args[2];
				return Proxy.newProxyInstance(UIColor.class.getClassLoader(), new Class<?>[] { UIColor.class }, (q, m, a) -> {
					switch (m.getName()) {
						case "getRed":
							return r;
						case "getGreen":
							return gr;
						case "getBlue":
							return b;
						default:
							return m.getReturnType() == boolean.class ? (Object) false : null;
					}
				});
			}
			if (method.getName().equals("createFont")) {
				final String name = (String) args[0];
				final float height = ((Number) args[1]).floatValue();
				final boolean bold = (Boolean) args[2];
				return Proxy.newProxyInstance(UIFont.class.getClassLoader(), new Class<?>[] { UIFont.class }, (q, m, a) -> {
					switch (m.getName()) {
						case "getName":
							return name;
						case "getHeight":
							return height;
						case "isBold":
							return bold;
						default:
							return m.getReturnType() == boolean.class ? (Object) false : null;
					}
				});
			}
			return null;
		});
	}

	private FontMetrics metrics() {
		return this.g.getFontMetrics(awtFont());
	}

	private Font awtFont() {
		if (this.font == null) {
			return new Font(Font.SANS_SERIF, Font.BOLD, 12);
		}
		return new Font(Font.SANS_SERIF, this.font.isBold() ? Font.BOLD : Font.PLAIN, Math.max(6, Math.round(this.font.getHeight())));
	}

	private void composite() {
		this.g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, this.alpha / 255f))));
	}

	@Override
	public void initPath(int style) {
		this.style = style;
		this.path.reset();
	}

	@Override
	public void initPath() {
		initPath(PATH_DRAW);
	}

	@Override
	public void closePath() {
		composite();
		if ((this.style & PATH_FILL) != 0) {
			this.g.setColor(this.background);
			this.g.fill(this.path);
		} else {
			this.g.setColor(this.foreground);
			this.g.setStroke(new BasicStroke((float) Math.max(1.0, this.lineWidth), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			this.g.draw(this.path);
		}
		this.path.reset();
	}

	@Override
	public void drawString(String string, float x, float y) {
		composite();
		this.g.setFont(awtFont());
		this.g.setColor(this.foreground);
		this.g.drawString(string, x, y + metrics().getAscent());
	}

	@Override
	public void drawImage(UIImage image, float srcX, float srcY, float srcWidth, float srcHeight, float destX, float destY, float destWidth, float destHeight) {
	}

	@Override
	public void drawImage(UIImage image, float x, float y) {
	}

	@Override
	public void cubicTo(float xc1, float yc1, float xc2, float yc2, float x1, float y1) {
		this.path.curveTo(xc1, yc1, xc2, yc2, x1, y1);
	}

	@Override
	public void lineTo(float x, float y) {
		if (this.path.getCurrentPoint() == null) {
			this.path.moveTo(x, y);
		} else {
			this.path.lineTo(x, y);
		}
	}

	@Override
	public void moveTo(float x, float y) {
		this.path.moveTo(x, y);
	}

	@Override
	public void addCircle(float x, float y, float width) {
		this.path.append(new Ellipse2D.Double(x - width / 2.0, y - width / 2.0, width, width), false);
	}

	@Override
	public void addRectangle(float x, float y, float width, float height) {
		this.path.append(new Rectangle2D.Double(x, y, width, height), false);
	}

	@Override
	public void setFont(UIFont font) {
		this.font = font;
	}

	@Override
	public void setForeground(UIColor color) {
		this.foreground = new Color(color.getRed(), color.getGreen(), color.getBlue());
	}

	@Override
	public void setBackground(UIColor color) {
		this.background = new Color(color.getRed(), color.getGreen(), color.getBlue());
	}

	@Override
	public void setLineWidth(float lineWidth) {
		this.lineWidth = lineWidth;
	}

	@Override
	public void setLineStyleSolid() {
	}

	@Override
	public void setLineStyleDot() {
	}

	@Override
	public void setLineStyleDash() {
	}

	@Override
	public void setLineStyleDashDot() {
	}

	@Override
	public void setAlpha(int alpha) {
		this.alpha = alpha;
	}

	@Override
	public void setAntialias(boolean enabled) {
		this.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, enabled ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	@Override
	public float getFontSize() {
		return this.font == null ? 12f : this.font.getHeight();
	}

	@Override
	public float getFMBaseLine() {
		return metrics().getAscent();
	}

	@Override
	public float getFMTopLine() {
		return 0f;
	}

	@Override
	public float getFMMiddleLine() {
		return metrics().getHeight() / 2f;
	}

	@Override
	public float getFMHeight() {
		return metrics().getHeight();
	}

	@Override
	public float getFMWidth(String text) {
		return metrics().stringWidth(text);
	}

	@Override
	public boolean isDisposed() {
		return false;
	}

	@Override
	public void dispose() {
	}
}
