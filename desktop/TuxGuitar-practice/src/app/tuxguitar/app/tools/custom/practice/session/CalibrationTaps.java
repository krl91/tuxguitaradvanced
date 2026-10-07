package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the notes the learner played along the clicks say about the delay of the route (spec 002 T097, FR-054): the median of the offsets, once the
 * ones too far from the others are set aside. A note played a little before or after its click is human; a note played on the wrong click, or a noise,
 * is not, and a median alone would follow half of them.
 * <p>
 * The offset of a note is its observed time minus the time its click was handed to the output: positive when the sound is seen late, the way
 * {@link LatencyCompensation} subtracts it. Nothing here reads a device.
 */
public final class CalibrationTaps {

	/** How many clicks the calibration plays. */
	public static final int CLICKS = 8;
	/** At least this many notes must be usable, or the calibration is not kept. */
	public static final int MIN_USABLE = 5;
	/** A note farther than this many scaled median deviations from the median is set aside (3 is the usual limit of a robust estimate). */
	public static final double OUTLIER_DEVIATIONS = 3.0;
	/** ... and never closer than this to the median: the notes of a steady player scatter by a few milliseconds and must not all be outliers. */
	public static final double OUTLIER_FLOOR_MILLIS = 40.0;
	/** The usable notes spread more than this (standard deviation): the delay is not stable enough to be kept. */
	public static final double UNSTABLE_SPREAD_MILLIS = 60.0;

	public enum Status {
		/** The delay was measured: see {@link Result#medianMillis()}. */
		CALIBRATED,
		/** Fewer than {@link #MIN_USABLE} notes were usable. */
		TOO_FEW,
		/** The usable notes are too spread out around the clicks. */
		UNSTABLE,
		/** The median is outside what {@link LatencyCompensation} accepts. */
		OUT_OF_RANGE
	}

	public static final class Result {
		private final Status status;
		private final double medianMillis;
		private final double spreadMillis;
		private final int used;
		private final int total;

		Result(Status status, double medianMillis, double spreadMillis, int used, int total) {
			this.status = status;
			this.medianMillis = medianMillis;
			this.spreadMillis = spreadMillis;
			this.used = used;
			this.total = total;
		}

		public Status status() {
			return this.status;
		}

		/** The delay of the route, meaningful for {@link Status#CALIBRATED}. */
		public double medianMillis() {
			return this.medianMillis;
		}

		/** The standard deviation of the usable notes. */
		public double spreadMillis() {
			return this.spreadMillis;
		}

		public int used() {
			return this.used;
		}

		public int total() {
			return this.total;
		}

		/** The compensation to keep, or none when the calibration failed. */
		public LatencyCompensation compensation() {
			return this.status == Status.CALIBRATED ? LatencyCompensation.calibrated(this.medianMillis) : LatencyCompensation.none();
		}
	}

	private final List<Double> offsetsMillis = new ArrayList<Double>();

	/** A note played along a click, as its offset to the click in milliseconds. */
	public void add(double offsetMillis) {
		if (!Double.isNaN(offsetMillis) && !Double.isInfinite(offsetMillis)) {
			this.offsetsMillis.add(offsetMillis);
		}
	}

	public int count() {
		return this.offsetsMillis.size();
	}

	public Result evaluate() {
		return evaluate(this.offsetsMillis, CLICKS);
	}

	/** @param total how many clicks were played, to say how many notes were missing */
	public static Result evaluate(List<Double> offsets, int total) {
		if (offsets.size() < MIN_USABLE) {
			return new Result(Status.TOO_FEW, 0.0, 0.0, offsets.size(), total);
		}
		double median = median(offsets);
		List<Double> deviations = new ArrayList<Double>();
		for (double offset : offsets) {
			deviations.add(Math.abs(offset - median));
		}
		// 1.4826 turns the median absolute deviation into the standard deviation of a normal spread
		double limit = Math.max(OUTLIER_FLOOR_MILLIS, OUTLIER_DEVIATIONS * 1.4826 * median(deviations));
		List<Double> kept = new ArrayList<Double>();
		for (double offset : offsets) {
			if (Math.abs(offset - median) <= limit) {
				kept.add(offset);
			}
		}
		if (kept.size() < MIN_USABLE) {
			return new Result(Status.TOO_FEW, 0.0, 0.0, kept.size(), total);
		}
		double keptMedian = median(kept);
		double mean = 0.0;
		for (double offset : kept) {
			mean += offset;
		}
		mean /= kept.size();
		double variance = 0.0;
		for (double offset : kept) {
			variance += (offset - mean) * (offset - mean);
		}
		double spread = Math.sqrt(variance / kept.size());
		if (spread > UNSTABLE_SPREAD_MILLIS) {
			return new Result(Status.UNSTABLE, keptMedian, spread, kept.size(), total);
		}
		if (Math.abs(keptMedian) > LatencyCompensation.LIMIT_MILLIS) {
			return new Result(Status.OUT_OF_RANGE, keptMedian, spread, kept.size(), total);
		}
		return new Result(Status.CALIBRATED, keptMedian, spread, kept.size(), total);
	}

	static double median(List<Double> values) {
		double[] sorted = new double[values.size()];
		for (int i = 0; i < sorted.length; i++) {
			sorted[i] = values.get(i);
		}
		Arrays.sort(sorted);
		int middle = sorted.length / 2;
		return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
	}
}
