package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.app.system.icons.TGColorManager;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UIPaintEvent;
import app.tuxguitar.ui.event.UIPaintListener;
import app.tuxguitar.ui.resource.UIPainter;
import app.tuxguitar.ui.resource.UIRectangle;
import app.tuxguitar.ui.widget.UICanvas;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGMusicKeyUtils;

/**
 * Rough tuning scale (display logic adapted from the legacy {@code TGTunerRoughWidget}): the
 * twelve note names of an octave with a marker at the detected pitch.
 */
public final class TunerScale {

	private static final float START = 20f;
	private static final float END = 20f;
	private static final float BOUNDARY = 16f;
	private static final float MARKER = 12f;

	private final TGContext context;
	private final UICanvas canvas;
	private double position = -1;

	public TunerScale(TGContext context, UIFactory uiFactory, UIContainer parent) {
		this.context = context;
		this.canvas = uiFactory.createCanvas(parent, true);
		this.canvas.setBgColor(TGColorManager.getInstance(context).getColor(TGColorManager.COLOR_WHITE));
		this.canvas.addPaintListener(new UIPaintListener() {
			public void onPaint(UIPaintEvent event) {
				paint(event.getPainter());
			}
		});
	}

	public UICanvas getControl() {
		return this.canvas;
	}

	/** @param midi detected note (0–127) with its cents, or a negative value for none */
	public void setPitch(int midi, double cents) {
		this.position = (midi < 0 ? -1 : ((midi % 12) + cents / 100.0 + 12.0) % 12.0);
		if( !this.canvas.isDisposed() ) {
			this.canvas.redraw();
		}
	}

	private void paint(UIPainter painter) {
		TGColorManager colors = TGColorManager.getInstance(this.context);
		UIRectangle bounds = this.canvas.getBounds();
		float middle = bounds.getHeight() / 2f;
		float step = (bounds.getWidth() - START - END) / 12f;

		painter.setForeground(colors.getColor(TGColorManager.COLOR_BLACK));
		painter.setLineWidth(1);
		painter.initPath();
		painter.moveTo(START, middle);
		painter.lineTo(bounds.getWidth() - END, middle);
		painter.closePath();
		for( int tone = 0 ; tone <= 12 ; tone ++ ) {
			float x = START + tone * step;
			painter.initPath();
			painter.moveTo(x, middle - BOUNDARY / 2f);
			painter.lineTo(x, middle + BOUNDARY / 2f);
			painter.closePath();
			if( tone < 12 ) {
				painter.drawString(TGMusicKeyUtils.sharpKeyNames[tone], x, middle - BOUNDARY / 2f - 20f);
			}
		}
		if( this.position >= 0 ) {
			float x = START + (float) (this.position * step);
			painter.setForeground(colors.getColor(TGColorManager.COLOR_BLUE));
			painter.setLineWidth(4);
			painter.initPath();
			painter.moveTo(x, middle - MARKER / 2f);
			painter.lineTo(x, middle + MARKER / 2f);
			painter.closePath();
		}
	}
}
