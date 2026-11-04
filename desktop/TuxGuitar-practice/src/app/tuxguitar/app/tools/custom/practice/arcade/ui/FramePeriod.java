package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;

/**
 * The periods of Arcade from the display's rate (spec 023, FR-015, research R7): a frame lasts one display cycle, the timer ticks twice a frame (see
 * {@link ArcadeCanvas#TICK_NANOS} for why), and the quality controller's budget is the frame. With no rate known, today's 60 Hz values.
 */
public final class FramePeriod {

	/** No rate known: sixty frames a second, as before. */
	public static final FramePeriod UNKNOWN = new FramePeriod(DisplayRate.UNKNOWN, ArcadeCanvas.FRAME_NANOS);

	private final double rateHz;
	private final long frameNanos;

	private FramePeriod(double rateHz, long frameNanos) {
		this.rateHz = rateHz;
		this.frameNanos = frameNanos;
	}

	/** @param rateHz the display's rate in frames a second; 0, a negative or a non-finite value when it is not known */
	public static FramePeriod of(double rateHz) {
		if( !(rateHz > 0.0) || Double.isInfinite(rateHz) ) {
			return UNKNOWN;
		}
		return new FramePeriod(rateHz, Math.round(1e9 / rateHz));
	}

	/** The period of the main display, read through the toolkit (UI thread). */
	public static FramePeriod ofDisplay() {
		return of(DisplayRate.current());
	}

	public boolean isKnown() {
		return this.rateHz > 0.0;
	}

	/** The rate in frames a second, or {@link DisplayRate#UNKNOWN}. */
	public double rateHz() {
		return this.rateHz;
	}

	public long frameNanos() {
		return this.frameNanos;
	}

	public long tickNanos() {
		return this.frameNanos / 2L;
	}

	/** A quality controller whose budget is one frame. */
	public QualityController quality() {
		return new QualityController(this.frameNanos);
	}

	@Override
	public String toString() {
		return isKnown() ? Math.round(this.rateHz) + " Hz" : "unknown (60 Hz)";
	}
}
