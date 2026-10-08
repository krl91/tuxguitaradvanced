package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import java.util.HashMap;
import java.util.Map;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.resource.UIColor;
import app.tuxguitar.ui.resource.UIFont;
import app.tuxguitar.ui.resource.UIPainter;

/**
 * The baseline renderer (spec 003 T041): replays a scene on the toolkit's {@link UIPainter}, the 2D path painter that the SWT, JavaFX and Qt toolkits all
 * have. The colours and fonts it needs are created once and reused, and released by {@link #dispose()}. The painter belongs to one paint event: the canvas
 * hands it over with {@link #target(UIPainter)} before each render. The lower field resolution of the cheapest tier is not applied here (that needs an
 * off-screen surface and is the business of a later backend, T046): the baseline paints at native resolution and only drops the antialiasing.
 */
public final class UiPainterRenderer implements ArcadeRenderer {

	private final UIFactory factory;
	private final String fontName;
	private final Map<Integer, UIColor> colours = new HashMap<Integer, UIColor>();
	private final Map<Integer, UIFont> fonts = new HashMap<Integer, UIFont>();
	private UIPainter painter;

	public UiPainterRenderer(UIFactory factory, String fontName) {
		this.factory = factory;
		this.fontName = fontName == null ? "Sans" : fontName;
	}

	/** The painter of the paint event being handled. */
	public void target(UIPainter painter) {
		this.painter = painter;
	}

	private UIColor colour(int rgb) {
		UIColor colour = this.colours.get(rgb);
		if (colour == null) {
			colour = this.factory.createColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
			this.colours.put(rgb, colour);
		}
		return colour;
	}

	private UIFont font(double size) {
		int key = Math.max(6, (int) Math.round(size));
		UIFont font = this.fonts.get(key);
		if (font == null) {
			font = this.factory.createFont(this.fontName, key, true, false);
			this.fonts.put(key, font);
		}
		return font;
	}

	@Override
	public void render(Scene scene, QualityTier tier) {
		UIPainter p = this.painter;
		for (SceneItem item : scene.items()) {
			p.setAlpha((int) Math.round(Math.max(0.0, Math.min(1.0, item.alpha())) * 255.0));
			p.setAntialias(item.shape() == SceneItem.Shape.TEXT || tier.antialias());
			switch (item.shape()) {
				case LINE:
					p.setForeground(colour(item.colour()));
					p.setLineWidth((float) item.lineWidth());
					p.initPath(UIPainter.PATH_DRAW);
					p.moveTo((float) item.value(0), (float) item.value(1));
					p.lineTo((float) item.value(2), (float) item.value(3));
					p.closePath();
					break;
				case QUAD:
					p.setBackground(colour(item.colour()));
					p.initPath(UIPainter.PATH_FILL);
					p.moveTo((float) item.value(0), (float) item.value(1));
					p.lineTo((float) item.value(2), (float) item.value(3));
					p.lineTo((float) item.value(4), (float) item.value(5));
					p.lineTo((float) item.value(6), (float) item.value(7));
					p.closePath();
					break;
				case DISC:
					p.setBackground(colour(item.colour()));
					p.initPath(UIPainter.PATH_FILL);
					p.addCircle((float) (item.value(0) - item.value(2)), (float) (item.value(1) - item.value(2)), (float) (2.0 * item.value(2)));
					p.closePath();
					break;
				case RING:
					p.setForeground(colour(item.colour()));
					p.setLineWidth((float) item.lineWidth());
					p.initPath(UIPainter.PATH_DRAW);
					p.addCircle((float) (item.value(0) - item.value(2)), (float) (item.value(1) - item.value(2)), (float) (2.0 * item.value(2)));
					p.closePath();
					break;
				case TEXT:
					p.setFont(font(item.value(2)));
					p.setForeground(colour(item.colour()));
					float width = p.getFMWidth(item.text());
					float height = p.getFMHeight();
					float x = (float) item.value(0) - (item.align() == SceneItem.Align.CENTRE ? width / 2f : (item.align() == SceneItem.Align.RIGHT ? width : 0f));
					p.drawString(item.text(), x, (float) item.value(1) - height / 2f);
					break;
				default:
					break;
			}
		}
		p.setAlpha(255);
	}

	@Override
	public void dispose() {
		for (UIColor colour : this.colours.values()) {
			colour.dispose();
		}
		for (UIFont font : this.fonts.values()) {
			font.dispose();
		}
		this.colours.clear();
		this.fonts.clear();
		this.painter = null;
	}
}
