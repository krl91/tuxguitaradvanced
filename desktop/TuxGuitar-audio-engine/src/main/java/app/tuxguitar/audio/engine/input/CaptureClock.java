package app.tuxguitar.audio.engine.input;

/**
 * Relates the samples of the capture to the wall clock ({@code System.nanoTime()}), for the tempo-following mode (spec 002 T093, research R6c).
 * <p>
 * Measured on the built-in microphone: the sample clock is exact (47999.87 frames a second for 48000) but a read of the capture does not
 * arrive when its samples were taken: reads come in bursts, the arrival jitters by up to 65 ms and its lag by 130 ms. So a sample is dated by
 * its <b>count</b>, and the wall clock is related to it by the <b>lower envelope</b> of the arrival lag over the last seconds: the read
 * that arrived with the least delay is the one closest to the moment its samples were taken. The date of a sample is then
 * {@code index / rate + minimum lag}; its error is that of the best read of the window (about one read, 21 ms, at worst), plus the latency of the
 * input device, which no software can know: the calibration of the offset takes it in (the offset is the learner's, per route).
 * <p>
 * The estimator follows a slow drift of the sample clock (the window slides), is reset when samples were dropped (the index then no longer
 * follows time) and when a stream starts, and reports the spread of the lag (maximum minus minimum over the window): a large or growing
 * spread is the sign of an unstable route, which the application tells the learner about without ever blocking.
 * <p>
 * Threads: {@link #onSamples}, {@link #reset} and {@link #configure} are called by the capture thread only (one writer, no allocation, no lock);
 * the results are read from any thread from volatile fields.
 */
public final class CaptureClock {

	/** Returned by {@link #wallNanosOf(long)} while the estimate is not usable. */
	public static final long NOT_READY = Long.MIN_VALUE;

	private static final long WINDOW_NANOS = 10_000_000_000L;
	private static final int MIN_READS = 20;
	private static final int CAPACITY = 8192;

	private final long[] minArrival = new long[CAPACITY];
	private final long[] minLag = new long[CAPACITY];
	private int minHead;
	private int minSize;
	private final long[] maxArrival = new long[CAPACITY];
	private final long[] maxLag = new long[CAPACITY];
	private int maxHead;
	private int maxSize;
	private int reads;

	private volatile int sampleRate;
	private volatile boolean ready;
	private volatile long lowestLagNanos;
	private volatile long highestLagNanos;

	public CaptureClock(int sampleRate) {
		this.sampleRate = sampleRate;
	}

	/** A new stream (device, format or rate): forgets everything. */
	public void configure(int sampleRate) {
		this.sampleRate = sampleRate;
		this.reset();
	}

	/** The index no longer follows time (samples were dropped): the estimate starts again. */
	public void reset() {
		this.minHead = 0;
		this.minSize = 0;
		this.maxHead = 0;
		this.maxSize = 0;
		this.reads = 0;
		this.ready = false;
	}

	/**
	 * A read of the capture arrived.
	 *
	 * @param arrivalNanos {@code System.nanoTime()} taken as soon as the read returned
	 * @param sampleCount the number of samples of the stream received up to and including this read
	 */
	public void onSamples(long arrivalNanos, long sampleCount) {
		int rate = this.sampleRate;
		if (rate <= 0) {
			return;
		}
		long lag = arrivalNanos - sampleCount * 1_000_000_000L / rate;
		long limit = arrivalNanos - WINDOW_NANOS;
		// the sliding minimum: the candidates are kept in increasing lag, the front is the minimum
		while (this.minSize > 0 && this.minArrival[this.minHead] < limit) {
			this.minHead = (this.minHead + 1) % CAPACITY;
			this.minSize--;
		}
		while (this.minSize > 0 && this.minLag[(this.minHead + this.minSize - 1) % CAPACITY] >= lag) {
			this.minSize--;
		}
		if (this.minSize == CAPACITY) {
			this.minHead = (this.minHead + 1) % CAPACITY;
			this.minSize--;
		}
		int at = (this.minHead + this.minSize) % CAPACITY;
		this.minArrival[at] = arrivalNanos;
		this.minLag[at] = lag;
		this.minSize++;
		// the sliding maximum, for the spread
		while (this.maxSize > 0 && this.maxArrival[this.maxHead] < limit) {
			this.maxHead = (this.maxHead + 1) % CAPACITY;
			this.maxSize--;
		}
		while (this.maxSize > 0 && this.maxLag[(this.maxHead + this.maxSize - 1) % CAPACITY] <= lag) {
			this.maxSize--;
		}
		if (this.maxSize == CAPACITY) {
			this.maxHead = (this.maxHead + 1) % CAPACITY;
			this.maxSize--;
		}
		at = (this.maxHead + this.maxSize) % CAPACITY;
		this.maxArrival[at] = arrivalNanos;
		this.maxLag[at] = lag;
		this.maxSize++;

		this.reads++;
		this.lowestLagNanos = this.minLag[this.minHead];
		this.highestLagNanos = this.maxLag[this.maxHead];
		this.ready = this.reads >= MIN_READS;
	}

	public boolean isReady() {
		return this.ready;
	}

	/**
	 * The wall clock time ({@code System.nanoTime()} scale) at which the given sample of the stream is taken to be available to the
	 * application, or {@link #NOT_READY}.
	 */
	public long wallNanosOf(long sampleIndex) {
		int rate = this.sampleRate;
		if (!this.ready || rate <= 0) {
			return NOT_READY;
		}
		return sampleIndex * 1_000_000_000L / rate + this.lowestLagNanos;
	}

	/** Largest minus smallest arrival lag over the last seconds, in milliseconds: the instability of the route. 0 until ready. */
	public double lagSpreadMillis() {
		return this.ready ? (this.highestLagNanos - this.lowestLagNanos) / 1e6 : 0.0;
	}
}
