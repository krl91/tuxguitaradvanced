package app.tuxguitar.app.tools.custom.practice.session;

/**
 * The latency of the audio route (output and input), as the learner calibrated or adjusted it (spec 002 T094, FR-054, FR-058). It is
 * <b>subtracted from the observed time of an onset</b>; it is never added to a window: the window is the learner's tolerance, this is a property of
 * the route, and the two must not be merged silently. No calibration is an offset of 0 <b>and</b> a flag saying so.
 * <p>
 * It belongs to one route (an input, an output and an audio format or profile, kept by the calibration of T097) and knows nothing of a device:
 * no name, no transport, so a Bluetooth route is neither refused nor guessed at (FR-057). The offset may be positive (the usual case: the
 * sound is seen late) or negative (adjusted by hand).
 */
public final class LatencyCompensation {

	public enum Source {
		/** Nothing was calibrated or adjusted: the offset is 0 and the precision of the rhythm is reduced. */
		NONE,
		/** The learner set the offset by hand. */
		MANUAL,
		/** The offset was measured by the calibration (taps along the metronome). */
		CALIBRATED
	}

	/**
	 * A bound for sanity, not a model of any device: a calibrated Bluetooth route is a few hundred milliseconds. The default of
	 * {@code recognition.clock.latency-limit} (spec 026); an offset is checked against the limit given where it is made (the setting).
	 */
	public static final double LIMIT_MILLIS = 1000.0;
	/** The highest limit the setting allows: an offset stored under it stays readable whatever the limit is now. */
	public static final double MAX_LIMIT_MILLIS = 3000.0;

	private static final LatencyCompensation NO_CALIBRATION = new LatencyCompensation(0L, Source.NONE);

	private final long offsetNanos;
	private final Source source;

	private LatencyCompensation(long offsetNanos, Source source) {
		this.offsetNanos = offsetNanos;
		this.source = source;
	}

	public static LatencyCompensation none() {
		return NO_CALIBRATION;
	}

	public static LatencyCompensation manual(double offsetMillis) {
		return manual(offsetMillis, LIMIT_MILLIS);
	}

	public static LatencyCompensation manual(double offsetMillis, double limitMillis) {
		return new LatencyCompensation(nanosOf(offsetMillis, limitMillis), Source.MANUAL);
	}

	public static LatencyCompensation calibrated(double offsetMillis) {
		return calibrated(offsetMillis, LIMIT_MILLIS);
	}

	public static LatencyCompensation calibrated(double offsetMillis, double limitMillis) {
		return new LatencyCompensation(nanosOf(offsetMillis, limitMillis), Source.CALIBRATED);
	}

	private static long nanosOf(double offsetMillis, double limitMillis) {
		if (Double.isNaN(offsetMillis) || Math.abs(offsetMillis) > limitMillis) {
			throw new IllegalArgumentException("a latency offset must be within +-" + limitMillis + " ms: " + offsetMillis);
		}
		return Math.round(offsetMillis * 1e6);
	}

	public long offsetNanos() {
		return this.offsetNanos;
	}

	public Source source() {
		return this.source;
	}

	/** True when the learner calibrated or adjusted the offset of this route. */
	public boolean isCalibrated() {
		return this.source != Source.NONE;
	}

	/** The time of an observed onset once the latency of the route is taken out. */
	public long adjust(long observedNanos) {
		return observedNanos - this.offsetNanos;
	}
}
