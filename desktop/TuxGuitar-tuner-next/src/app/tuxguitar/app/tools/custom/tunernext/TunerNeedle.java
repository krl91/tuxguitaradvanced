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

/**
 * Fine tuning needle (display logic adapted from the legacy {@code TGTunerFineWidget}): a
 * half-dial where the needle points left for flat, straight up when in tune and right for sharp.
 * The needle moves smoothly toward each new position; without a confident reading it rests at the
 * centre and is not drawn. Flat/sharp are also marked with ♭/♯, not by colour alone.
 */
public final class TunerNeedle {

	private static final float MARGIN = 14f;
	/** Time constant of the needle (seconds): it settles in about a quarter of a second instead of shaking with every reading. */
	private static final double TIME_CONSTANT_SECONDS = 0.12;

	private final TGContext context;
	private final UICanvas canvas;
	private double target;
	private double shown;
	private boolean active;
	private boolean held;
	private long lastNanos;

	public TunerNeedle(TGContext context, UIFactory uiFactory, UIContainer parent) {
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

	/**
	 * @param position from −1 (flat) through 0 (in tune) to +1 (sharp)
	 * @param active false when there is no confident reading
	 */
	public void setPosition(double position, boolean active) {
		setPosition(position, active, false);
	}

	/** @param held true when the needle shows the last reliable reading, not a live one: drawn grey and still */
	public void setPosition(double position, boolean active, boolean held) {
		this.held = held;
		if( held ) {
			this.shown = position;
		}
		this.active = active;
		this.target = (active ? position : 0.0);
		long now = System.nanoTime();
		double elapsed = (this.lastNanos == 0L ? 0.1 : Math.min(0.5, (now - this.lastNanos) / 1e9));
		this.lastNanos = now;
		// independent of how often the readings arrive
		this.shown += (1.0 - Math.exp(-elapsed / TIME_CONSTANT_SECONDS)) * (this.target - this.shown);
		if( !this.canvas.isDisposed() ) {
			this.canvas.redraw();
		}
	}

	private void paint(UIPainter painter) {
		TGColorManager colors = TGColorManager.getInstance(this.context);
		UIRectangle bounds = this.canvas.getBounds();
		float centreX = bounds.getWidth() / 2f;
		float pivotY = bounds.getHeight() - MARGIN;
		float radius = Math.max(10f, Math.min(centreX - MARGIN, pivotY - 2 * MARGIN));

		painter.setForeground(colors.getColor(TGColorManager.COLOR_BLACK));
		painter.setLineWidth(1);
		for( int tick = -4 ; tick <= 4 ; tick ++ ) {
			double angle = angle(tick / 4.0);
			float inner = radius * (tick == 0 ? 0.80f : 0.88f);
			painter.initPath();
			painter.moveTo(centreX + (float) (inner * Math.cos(angle)), pivotY - (float) (inner * Math.sin(angle)));
			painter.lineTo(centreX + (float) (radius * Math.cos(angle)), pivotY - (float) (radius * Math.sin(angle)));
			painter.closePath();
		}
		painter.drawString("♭", centreX - radius - 4f, pivotY - 4f);
		painter.drawString("♯", centreX + radius - 4f, pivotY - 4f);

		if( this.active ) {
			double angle = angle(this.shown);
			painter.setForeground(colors.getColor(this.held ? TGColorManager.COLOR_GRAY : TGColorManager.COLOR_RED));
			painter.setLineWidth(this.held ? 2 : 3);
			painter.initPath();
			painter.moveTo(centreX, pivotY);
			painter.lineTo(centreX + (float) (radius * 0.95 * Math.cos(angle)), pivotY - (float) (radius * 0.95 * Math.sin(angle)));
			painter.closePath();
		}
	}

	/** −1 → 170°, 0 → 90° (up), +1 → 10°. */
	private static double angle(double position) {
		return Math.toRadians(90.0 - 80.0 * Math.max(-1.0, Math.min(1.0, position)));
	}
}
