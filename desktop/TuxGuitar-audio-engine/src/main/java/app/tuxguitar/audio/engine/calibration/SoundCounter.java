package app.tuxguitar.audio.engine.calibration;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The listening mode of the calibration (spec 013, FR-004): counts that "a sound was played", without saying which note or chord. It runs its OWN attack
 * detector and level measure on the hops (a {@link HopListener} gives samples only, not analysis frames), without allocating per hop. A sound is an attack
 * whose window level is above the input minimum, and two sounds less than {@link SoundDetection#REFRACTORY_MILLIS} apart are one (the strings of a chord).
 * It ends by itself {@link SoundDetection#STOP_AFTER_LAST_MILLIS} after the expected number of sounds was reached, or on a timeout, or on a cancel. Time
 * is the time of the samples it was given.
 */
public final class SoundCounter implements HopListener {

	public enum State {
		LISTENING,
		/** the expected number of sounds was heard and the last second has passed (more sounds than expected may have been heard in it) */
		COMPLETE,
		/** no sound for {@link SoundDetection#SILENCE_TIMEOUT_MILLIS} */
		SILENCE_TIMEOUT,
		/** {@link SoundDetection#TOTAL_TIMEOUT_MILLIS} in all */
		TOTAL_TIMEOUT,
		CANCELLED
	}

	/** Told each time a sound is counted, and once when the listening ends (on the thread of the hops, or of the cancel). */
	public interface Listener {
		void soundHeard(int count);

		/** The listening left {@link State#LISTENING}: complete, timed out or cancelled. */
		default void ended(State finalState) {
		}
	}

	private final int expected;
	private final double levelMinimum;
	private final ArticulationDetector detector;
	private final float[] window = new float[OfflineAnalysis.WINDOW];
	private volatile State state = State.LISTENING;
	private volatile int heard;
	private volatile long samplesSeen;
	private volatile Listener listener;
	private int sampleRate;
	private int filled;
	private long lastOnsetSamples = -1;
	private long lastSoundSamples = 0;
	private long reachedAtSamples = -1;

	public SoundCounter(int expected, RecognitionSettings settings) {
		this.expected = expected;
		this.levelMinimum = settings.get(RecognitionParameters.INPUT_LEVEL_MIN);
		this.detector = AudioEngine.articulationDetector(settings);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	@Override
	public void configure(int sampleRate) {
		this.sampleRate = sampleRate;
	}

	public State getState() {
		return this.state;
	}

	public int getHeard() {
		return this.heard;
	}

	public int getExpected() {
		return this.expected;
	}

	/** The time of the samples seen so far, in milliseconds. */
	public long getElapsedMillis() {
		return this.sampleRate <= 0 ? 0 : this.samplesSeen * 1000L / this.sampleRate;
	}

	public void cancel() {
		if (this.state == State.LISTENING) {
			finish(State.CANCELLED);
		}
	}

	private void finish(State finalState) {
		this.state = finalState;
		Listener told = this.listener;
		if (told != null) {
			told.ended(finalState);
		}
	}

	@Override
	public void hop(float[] hop, int offset, int length) {
		if (this.state != State.LISTENING || this.sampleRate <= 0 || length <= 0) {
			return;
		}
		int n = Math.min(length, this.window.length);
		System.arraycopy(this.window, n, this.window, 0, this.window.length - n);
		System.arraycopy(hop, offset + length - n, this.window, this.window.length - n, n);
		this.filled = Math.min(this.window.length, this.filled + n);
		this.samplesSeen += length;
		if (this.filled == this.window.length) {
			boolean onset = this.detector.process(this.window);
			if (onset && windowDbfs() >= this.levelMinimum) {
				long gap = this.lastOnsetSamples < 0 ? Long.MAX_VALUE : (this.samplesSeen - this.lastOnsetSamples) * 1000L / this.sampleRate;
				this.lastOnsetSamples = this.samplesSeen;
				if (gap >= SoundDetection.REFRACTORY_MILLIS) {
					count();
				}
			}
		}
		long elapsed = getElapsedMillis();
		if (this.reachedAtSamples >= 0 && (this.samplesSeen - this.reachedAtSamples) * 1000L / this.sampleRate >= SoundDetection.STOP_AFTER_LAST_MILLIS) {
			finish(State.COMPLETE);
		} else if (elapsed >= SoundDetection.TOTAL_TIMEOUT_MILLIS) {
			finish(State.TOTAL_TIMEOUT);
		} else if (this.reachedAtSamples < 0 && (this.samplesSeen - this.lastSoundSamples) * 1000L / this.sampleRate >= SoundDetection.SILENCE_TIMEOUT_MILLIS) {
			finish(State.SILENCE_TIMEOUT);
		}
	}

	private void count() {
		int now = this.heard + 1;
		this.heard = now;
		this.lastSoundSamples = this.samplesSeen;
		if (now == this.expected) {
			this.reachedAtSamples = this.samplesSeen;
		}
		Listener told = this.listener;
		if (told != null) {
			told.soundHeard(now);
		}
	}

	/** The level of the window, in dBFS (the root mean square, as the analysis measures it). */
	private double windowDbfs() {
		double sum = 0.0;
		for (float value : this.window) {
			sum += (double) value * value;
		}
		double rms = Math.sqrt(sum / this.window.length);
		return rms <= 1e-6 ? -120.0 : 20.0 * Math.log10(rms);
	}
}
