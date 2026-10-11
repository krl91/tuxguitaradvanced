package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.input.CaptureClock;

/**
 * What the clocks say about their own uncertainty (spec 002 T094): the spread of the arrival lag of the capture over the last seconds. It
 * <b>never widens a window and never moves an onset</b>; it only decides whether the route is called unstable, which gives a warning and a reduced
 * precision of the rhythm, never a block. Provisional threshold until real routes (USB, Bluetooth) have been measured.
 */
public final class ClockUncertainty {

	/** A route whose arrival lag spreads over this much (ms) or more is called unstable: measured 26 to 130 ms on the built-in microphone, research R6c. */
	public static final double UNSTABLE_SPREAD_MILLIS = 80.0;

	private final double captureLagSpreadMillis;

	public ClockUncertainty(double captureLagSpreadMillis) {
		if (Double.isNaN(captureLagSpreadMillis) || captureLagSpreadMillis < 0.0) {
			throw new IllegalArgumentException("a spread cannot be negative: " + captureLagSpreadMillis);
		}
		this.captureLagSpreadMillis = captureLagSpreadMillis;
	}

	/** A measured spread, or none yet (NaN, as when the input has just been opened): then nothing is known, so the route is not called unstable. */
	public static ClockUncertainty ofMeasurement(double captureLagSpreadMillis) {
		return new ClockUncertainty(Double.isNaN(captureLagSpreadMillis) ? 0.0 : captureLagSpreadMillis);
	}

	public static ClockUncertainty of(CaptureClock clock) {
		return ofMeasurement(clock.lagSpreadMillis());
	}

	public double captureLagSpreadMillis() {
		return this.captureLagSpreadMillis;
	}

	public boolean isUnstable() {
		return this.captureLagSpreadMillis >= UNSTABLE_SPREAD_MILLIS;
	}
}
