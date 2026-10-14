package app.tuxguitar.audio.engine.analysis;

import java.util.concurrent.atomic.AtomicInteger;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The level of the first strum after a check begins (spec 010, research R1 to R3 and R14, contract invariants 5, 10, 15, 16 and 18). Pure and fed hop by hop
 * on the analysis thread: it arms on a hop whose RMS reaches {@link Parameters#armFloorDbfs} <b>and</b> rises {@link Parameters#attackRiseDb} above the
 * quietest of the {@link Parameters#armQuietHops} hops before it (fewer when fewer exist; the first hop never arms), then counts, over the next 300 ms
 * <b>in samples</b>, the samples at the clip level and the mean level, and gives a verdict: clipping at 0.5 % of the samples, else too loud, else too weak,
 * else good. Counters only: no allocation per hop and no lock. The result is read from another thread as one consistent {@link Reading}; a
 * {@link #reset()} from another thread hides the old verdict at once and asks the analysis thread to start again before its next hop.
 */
public final class StrumClipMeter implements HopListener {

	/** The numbers of the meter: provisional constants, not learner settings; the three levels come from the recognition settings. */
	public static final class Parameters {
		public final long windowNanos;
		public final double armFloorDbfs;
		public final int armQuietHops;
		public final double attackRiseDb;
		public final double clipShare;
		public final double clipSample;
		public final double weakDbfs;
		public final double loudDbfs;

		public Parameters(long windowNanos, double armFloorDbfs, int armQuietHops, double attackRiseDb, double clipShare, double clipSample, double weakDbfs, double loudDbfs) {
			if (windowNanos <= 0L || armQuietHops <= 0 || clipShare <= 0.0 || clipSample <= 0.0 || clipSample > 1.0) {
				throw new IllegalArgumentException("invalid strum meter parameters");
			}
			this.windowNanos = windowNanos;
			this.armFloorDbfs = armFloorDbfs;
			this.armQuietHops = armQuietHops;
			this.attackRiseDb = attackRiseDb;
			this.clipShare = clipShare;
			this.clipSample = clipSample;
			this.weakDbfs = weakDbfs;
			this.loudDbfs = loudDbfs;
		}

		/** 300 ms, -40 dBFS floor, 60 hops, 12 dB, 0.5 %, the clip level 0.99, weak under -40 dBFS, loud over -6 dBFS (the defaults of the recognition settings). */
		public static Parameters defaults() {
			return new Parameters(300_000_000L, -40.0, 60, 12.0, 0.005, 0.99, -40.0, -6.0);
		}

		/** The three levels of these settings (the minimal level, the maximal level, the clip level of a sample); the rest are the constants of the meter. */
		public static Parameters of(RecognitionSettings settings) {
			double min = settings.get(RecognitionParameters.INPUT_LEVEL_MIN);
			return new Parameters(300_000_000L, min, 60, 12.0, 0.005, settings.get(RecognitionParameters.LEVEL_CLIP_SAMPLE), min,
					settings.get(RecognitionParameters.INPUT_LEVEL_MAX));
		}
	}

	/** One consistent reading of the last finished window. */
	public static final class Reading {
		private final StrumVerdict verdict;
		private final double clippedShare;
		private final double rmsDbfs;

		Reading(StrumVerdict verdict, double clippedShare, double rmsDbfs) {
			this.verdict = verdict;
			this.clippedShare = clippedShare;
			this.rmsDbfs = rmsDbfs;
		}

		public StrumVerdict verdict() {
			return this.verdict;
		}

		/** The share of the samples of the window at the clip level (0 before a verdict). */
		public double clippedShare() {
			return this.clippedShare;
		}

		/** The mean level of the window in dBFS (NaN before a verdict). */
		public double rmsDbfs() {
			return this.rmsDbfs;
		}
	}

	private static final Reading NONE = new Reading(StrumVerdict.NO_STRUM, 0.0, Double.NaN);
	private static final double SILENCE_DBFS = -120.0;

	/** A finished window and the generation of the check it belongs to: it is shown only while that generation is the current one. */
	private static final class Finished {
		final int generation;
		final Reading reading;

		Finished(int generation, Reading reading) {
			this.generation = generation;
			this.reading = reading;
		}
	}

	private final Parameters parameters;
	private final AtomicInteger generation = new AtomicInteger();
	private volatile boolean resetRequested;
	private volatile Finished finished;

	// the state of the analysis thread
	private int sampleRate;
	private int windowSamples;
	private final float[] history;
	private int historyCount;
	private int historyNext;
	private boolean armed;
	private boolean done;
	private int armGeneration;
	private int counted;
	private int clipped;
	private double sumSquares;

	public StrumClipMeter(Parameters parameters) {
		this.parameters = parameters;
		this.history = new float[parameters.armQuietHops];
	}

	/** The sample rate of the hops that follow; starts again (a different rate, a new device): any check in progress is dropped and its verdict hidden. */
	@Override
	public void configure(int sampleRate) {
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("invalid sample rate");
		}
		this.sampleRate = sampleRate;
		this.windowSamples = (int) Math.round(this.parameters.windowNanos / 1e9 * sampleRate);
		this.generation.incrementAndGet();
		clear();
	}

	/**
	 * Starts a new check, from any thread: the old verdict is hidden at once and the analysis thread clears its counters before its next hop. A strum
	 * heard before the call does not count.
	 */
	public void reset() {
		this.generation.incrementAndGet();
		this.resetRequested = true;
	}

	/** One hop, on the analysis thread. Without a {@link #configure(int)} before, nothing is counted. */
	@Override
	public void hop(float[] samples, int offset, int length) {
		if (this.resetRequested) {
			this.resetRequested = false;
			clear();
		}
		if (this.sampleRate <= 0 || length <= 0 || this.done) {
			return;
		}
		if (!this.armed) {
			double sum = 0.0;
			for (int i = offset; i < offset + length; i++) {
				sum += (double) samples[i] * samples[i];
			}
			double db = toDbfs(sum / length);
			if (this.historyCount > 0 && db >= this.parameters.armFloorDbfs && db >= quietest() + this.parameters.attackRiseDb) {
				this.armed = true;
				this.armGeneration = this.generation.get();
			} else {
				this.history[this.historyNext] = (float) db;
				this.historyNext = (this.historyNext + 1) % this.history.length;
				this.historyCount = Math.min(this.historyCount + 1, this.history.length);
				return;
			}
		}
		int take = Math.min(length, this.windowSamples - this.counted);
		double clipLevel = this.parameters.clipSample;
		for (int i = offset; i < offset + take; i++) {
			float value = samples[i];
			if (Math.abs(value) >= clipLevel) {
				this.clipped++;
			}
			this.sumSquares += (double) value * value;
		}
		this.counted += take;
		if (this.counted >= this.windowSamples) {
			finish();
		}
	}

	/** The last finished window, or no strum: always consistent, from any thread. */
	public Reading snapshot() {
		Finished current = this.finished;
		return current != null && current.generation == this.generation.get() ? current.reading : NONE;
	}

	public StrumVerdict verdict() {
		return snapshot().verdict();
	}

	public double clippedShare() {
		return snapshot().clippedShare();
	}

	public double rmsDbfs() {
		return snapshot().rmsDbfs();
	}

	private void finish() {
		double share = this.clipped / (double) this.windowSamples;
		double rms = toDbfs(this.sumSquares / this.windowSamples);
		StrumVerdict verdict;
		if (share >= this.parameters.clipShare - 1e-12) {
			verdict = StrumVerdict.CLIPPING;
		} else if (rms > this.parameters.loudDbfs) {
			verdict = StrumVerdict.TOO_LOUD;
		} else if (rms < this.parameters.weakDbfs) {
			verdict = StrumVerdict.TOO_WEAK;
		} else {
			verdict = StrumVerdict.GOOD;
		}
		this.done = true;
		this.finished = new Finished(this.armGeneration, new Reading(verdict, share, rms));
	}

	private void clear() {
		this.historyCount = 0;
		this.historyNext = 0;
		this.armed = false;
		this.done = false;
		this.counted = 0;
		this.clipped = 0;
		this.sumSquares = 0.0;
	}

	private double quietest() {
		double quietest = Double.POSITIVE_INFINITY;
		for (int i = 0; i < this.historyCount; i++) {
			quietest = Math.min(quietest, this.history[i]);
		}
		return quietest;
	}

	private static double toDbfs(double meanSquare) {
		return meanSquare <= 1e-12 ? SILENCE_DBFS : 10.0 * Math.log10(meanSquare);
	}
}
