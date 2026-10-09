package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.List;

/**
 * The anticipatory camera of Arcade (spec 003 FR-029, SC-010, research R4): the shown part of the neck as a pure function of time, computed once for a
 * run from its events. Every written fret of every visible note and chord (every member of a chord) lies in the shown part, with a margin, from the
 * moment the event first appears until it has been judged; the camera moves early enough and smoothly, never jumps, and never depends on the frame
 * rate, so pause, restart and a change of speed need no state.
 * <p>
 * How: the part that the events near in time demand is a step function of time; it is eroded by the smoothing time (the camera starts moving that much
 * earlier, and stays that much longer) and then averaged over the same time (the motion is continuous, a ramp of that length). Eroding first guarantees
 * that the average still covers what was demanded. The result is sampled on a fixed grid and interpolated linearly.
 */
public final class CameraTrack {

	/** The parameters of the camera. */
	public static final class Settings {
		/** How long before its onset an event first appears (the look-ahead of the view). */
		public final long lookAheadNanos;
		/** How much earlier than its appearance the camera must have the target in view. */
		public final long spawnLeadNanos;
		/** How long after its onset an event stays in view (its verdict is shown). */
		public final long holdAfterNanos;
		/** The width of the shown part at normal zoom, in millimetres. */
		public final double baseSpanMm;
		/** The smallest zoom (the widest part shown is {@code baseSpanMm / minZoom}). */
		public final double minZoom;
		/** The room kept between a target and the edge of the shown part, in millimetres. */
		public final double marginMm;
		/** The time over which the camera eases, before and after a change of what is demanded. */
		public final long smoothingNanos;
		/** The grid on which the track is sampled. */
		public final long stepNanos;

		public Settings(long lookAheadNanos, long spawnLeadNanos, long holdAfterNanos, double baseSpanMm, double minZoom, double marginMm, long smoothingNanos,
				long stepNanos) {
			this.lookAheadNanos = lookAheadNanos;
			this.spawnLeadNanos = spawnLeadNanos;
			this.holdAfterNanos = holdAfterNanos;
			this.baseSpanMm = baseSpanMm;
			this.minZoom = minZoom;
			this.marginMm = marginMm;
			this.smoothingNanos = smoothingNanos;
			this.stepNanos = stepNanos;
		}

		/** The settings of the view: events appear 5 s ahead (the owner, 2026-10-09: every note is seen coming from the horizon for five seconds, whatever the number of notes), are in view 1 s earlier, stay 0.8 s, a part of 260 mm, zoom down to 0.5, 600 ms of easing. */
		public static Settings standard() {
			return new Settings(5_000_000_000L, 1_000_000_000L, 800_000_000L, 260.0, 0.5, 12.0, 600_000_000L, 20_000_000L);
		}
	}

	private final Settings settings;
	private final long start;
	private final double[] left;
	private final double[] right;
	private final boolean unfittable;

	private CameraTrack(Settings settings, long start, double[] left, double[] right, boolean unfittable) {
		this.settings = settings;
		this.start = start;
		this.left = left;
		this.right = right;
		this.unfittable = unfittable;
	}

	public static CameraTrack build(List<ArcadeRenderEvent> events, NeckModel neck, Settings settings) {
		double maxSpan = settings.baseSpanMm / settings.minZoom;
		double[] lo = new double[events.size()];
		double[] hi = new double[events.size()];
		int used = 0;
		long first = Long.MAX_VALUE;
		long last = Long.MIN_VALUE;
		for (ArcadeRenderEvent event : events) {
			if (event.positions().isEmpty()) {
				continue;
			}
			double low = Double.MAX_VALUE;
			double high = -Double.MAX_VALUE;
			for (ArcadeRenderEvent.Position position : event.positions()) {
				// the whole cell of the fret, from its wire on the nut side to its own: the lane of the fret ends at the middle of the upper edge between the two
				// wires that are painted (FR-066), so both are in the shown part
				// (the lane before the nut has no wire of its own on its far side: its middle is what is shown, with the nut that is its other side)
				low = Math.min(low, position.fret() == 0 ? neck.fretCentreX(0) : neck.cellLeftX(position.fret()));
				high = Math.max(high, neck.cellRightX(position.fret()));
			}
			// a hair more than the margin, so that rounding in the averages below can never leave a target on the edge
			lo[used] = low - settings.marginMm - 1e-6;
			hi[used] = high + settings.marginMm + 1e-6;
			first = Math.min(first, event.onsetNanos());
			last = Math.max(last, event.onsetNanos());
			used++;
		}
		if (used == 0) {
			double centre = neck.fretCentreX(3);
			return new CameraTrack(settings, 0L, new double[] { centre - settings.baseSpanMm / 2.0 }, new double[] { centre + settings.baseSpanMm / 2.0 }, false);
		}
		long horizon = settings.lookAheadNanos + settings.spawnLeadNanos;
		int smooth = (int) (settings.smoothingNanos / settings.stepNanos);
		long start = first - horizon - 2L * settings.smoothingNanos;
		long end = last + settings.holdAfterNanos + 2L * settings.smoothingNanos;
		int cells = (int) ((end - start) / settings.stepNanos) + 2;
		double[] demandLow = new double[cells];
		double[] demandHigh = new double[cells];
		boolean[] defined = new boolean[cells];
		for (int k = 0; k < cells; k++) {
			demandLow[k] = Double.MAX_VALUE;
			demandHigh[k] = -Double.MAX_VALUE;
		}
		int e = 0;
		for (ArcadeRenderEvent event : events) {
			if (event.positions().isEmpty()) {
				continue;
			}
			// the demand lasts a little after the hold, so that the smoothing at its end cannot cut the verdict short
			long from = event.onsetNanos() - horizon;
			long to = event.onsetNanos() + settings.holdAfterNanos + 2L * settings.stepNanos;
			int kFrom = (int) Math.max(0, (from - start) / settings.stepNanos);
			int kTo = (int) Math.min(cells - 1, (to - start) / settings.stepNanos + 1);
			for (int k = kFrom; k <= kTo; k++) {
				demandLow[k] = Math.min(demandLow[k], lo[e]);
				demandHigh[k] = Math.max(demandHigh[k], hi[e]);
				defined[k] = true;
			}
			e++;
		}
		// where nothing is demanded the camera keeps what it had (or what comes next, before the first event)
		for (int k = 1; k < cells; k++) {
			if (!defined[k] && defined[k - 1]) {
				demandLow[k] = demandLow[k - 1];
				demandHigh[k] = demandHigh[k - 1];
				defined[k] = true;
			}
		}
		for (int k = cells - 2; k >= 0; k--) {
			if (!defined[k] && defined[k + 1]) {
				demandLow[k] = demandLow[k + 1];
				demandHigh[k] = demandHigh[k + 1];
				defined[k] = true;
			}
		}
		double[] erodedLow = new double[cells];
		double[] dilatedHigh = new double[cells];
		for (int k = 0; k < cells; k++) {
			double low = Double.MAX_VALUE;
			double high = -Double.MAX_VALUE;
			for (int j = Math.max(0, k - smooth); j <= Math.min(cells - 1, k + smooth); j++) {
				low = Math.min(low, demandLow[j]);
				high = Math.max(high, demandHigh[j]);
			}
			erodedLow[k] = low;
			dilatedHigh[k] = high;
		}
		double[] left = new double[cells];
		double[] right = new double[cells];
		boolean unfittable = false;
		for (int k = 0; k < cells; k++) {
			double sumLow = 0.0;
			double sumHigh = 0.0;
			int n = 0;
			for (int j = Math.max(0, k - smooth); j <= Math.min(cells - 1, k + smooth); j++) {
				sumLow += erodedLow[j];
				sumHigh += dilatedHigh[j];
				n++;
			}
			double l = sumLow / n;
			double r = sumHigh / n;
			double span = r - l;
			if (span < settings.baseSpanMm) {
				double centre = (l + r) / 2.0;
				l = centre - settings.baseSpanMm / 2.0;
				r = centre + settings.baseSpanMm / 2.0;
			} else if (span > maxSpan + 1e-9) {
				unfittable = true;
				double centre = (l + r) / 2.0;
				l = centre - maxSpan / 2.0;
				r = centre + maxSpan / 2.0;
			}
			left[k] = l;
			right[k] = r;
		}
		return new CameraTrack(settings, start, left, right, unfittable);
	}

	/** The shown part at a time on the timeline (before the first sample and after the last, the nearest one). */
	public Camera at(long nanos) {
		double position = (nanos - this.start) / (double) this.settings.stepNanos;
		if (position <= 0.0) {
			return new Camera(this.left[0], this.right[0]);
		}
		int last = this.left.length - 1;
		if (position >= last) {
			return new Camera(this.left[last], this.right[last]);
		}
		int k = (int) Math.floor(position);
		double part = position - k;
		return new Camera(this.left[k] * (1.0 - part) + this.left[k + 1] * part, this.right[k] * (1.0 - part) + this.right[k + 1] * part);
	}

	/** True when some demand needed more than the widest part the camera may show: the targets were then not all in view (the zoom is bounded). */
	public boolean isUnfittable() {
		return this.unfittable;
	}

	/** The zoom at a time: 1 at normal width, down to the minimum. */
	public double zoomAt(long nanos) {
		return this.settings.baseSpanMm / at(nanos).span();
	}

	public Settings settings() {
		return this.settings;
	}
}
