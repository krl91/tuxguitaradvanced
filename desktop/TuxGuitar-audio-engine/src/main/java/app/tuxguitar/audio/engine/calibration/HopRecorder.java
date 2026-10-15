package app.tuxguitar.audio.engine.calibration;

import java.util.Arrays;

import app.tuxguitar.audio.engine.analysis.HopListener;

/**
 * Keeps the signal the analysis sees (after the input front end) while the player plays the calibration piece (spec 013, FR-004, FR-017). The recording
 * lives in memory only: nothing here uses a file, and {@link #discard()} drops it. A hop is copied into an array made once, when the recording begins,
 * so a hop allocates nothing (constitution VI: the hop arrives on the analysis thread). It records between {@link #begin} and {@link #end}, up to a
 * capacity (the total time of the listening mode), then says it is full.
 */
public final class HopRecorder implements HopListener {

	private final int capacityMillis;
	private volatile int sampleRate;
	private volatile boolean recording;
	/** a recording was asked for (it may wait for the rate) */
	private boolean wanted;
	private volatile int length;
	private volatile FrontEndState frontEnd;
	private float[] samples;

	public HopRecorder() {
		this(SoundDetection.TOTAL_TIMEOUT_MILLIS);
	}

	public HopRecorder(int capacityMillis) {
		this.capacityMillis = capacityMillis;
	}

	/**
	 * The rate of the hops that follow, announced by the engine on the analysis thread before the first hop. The recording asked for with
	 * {@link #begin} may have been asked before the rate was known: the array is made now. A recording made at another rate is not continued.
	 */
	@Override
	public synchronized void configure(int sampleRate) {
		boolean changed = sampleRate != this.sampleRate;
		this.sampleRate = sampleRate;
		if (this.wanted && (changed || this.samples == null)) {
			allocate();
		}
	}

	private void allocate() {
		this.length = 0;
		this.samples = this.sampleRate > 0 ? new float[(int) ((long) this.capacityMillis * this.sampleRate / 1000L)] : null;
	}

	/**
	 * Starts recording from the next hop, remembering the front-end state in force. The array is made here once the rate is known (or at the next
	 * {@link #configure}), never per hop.
	 */
	public synchronized void begin(FrontEndState frontEndState) {
		this.wanted = true;
		this.frontEnd = frontEndState;
		allocate();
		this.recording = true;
	}

	public synchronized void end() {
		this.recording = false;
		this.wanted = false;
	}

	@Override
	public void hop(float[] hop, int offset, int count) {
		if (!this.recording) {
			return;
		}
		float[] target = this.samples;
		int at = this.length;
		if (target == null || at >= target.length) {
			return;
		}
		int n = Math.min(count, target.length - at);
		System.arraycopy(hop, offset, target, at, n);
		this.length = at + n;
	}

	public boolean isRecording() {
		return this.recording;
	}

	/** True when the capacity was reached: the rest of the playing is not recorded. */
	public boolean isFull() {
		float[] current = this.samples;
		return current != null && this.length >= current.length;
	}

	public int getLength() {
		return this.length;
	}

	public int getSampleRate() {
		return this.sampleRate;
	}

	public FrontEndState getFrontEnd() {
		return this.frontEnd;
	}

	/** A copy of what was recorded so far (empty when nothing). */
	public synchronized float[] getSamples() {
		return this.samples == null ? new float[0] : Arrays.copyOf(this.samples, this.length);
	}

	/** Drops the recording from memory: it is not kept after the analysis, nor when the calibration is cancelled, refused or fails (FR-017). */
	public synchronized void discard() {
		this.recording = false;
		this.wanted = false;
		if (this.samples != null) {
			Arrays.fill(this.samples, 0f);
		}
		this.samples = null;
		this.length = 0;
		this.frontEnd = null;
	}
}
