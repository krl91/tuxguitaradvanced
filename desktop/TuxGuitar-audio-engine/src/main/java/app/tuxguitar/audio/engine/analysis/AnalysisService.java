package app.tuxguitar.audio.engine.analysis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.thread.TGThreadPriority;
import app.tuxguitar.util.TGContext;

/**
 * Dedicated sliding-window analysis worker.
 *
 * <p>At most one worker thread exists at a time, so the ring buffer always has a single
 * consumer: when the last subscriber leaves, the worker exits at its next loop check; if a
 * subscriber arrives before that, the same worker simply continues. A new sample rate and
 * estimator ({@link #reconfigure}) are applied by the worker itself at the top of its loop.</p>
 */
public final class AnalysisService {

	public interface Listener {
		void analysisFrame(AnalysisFrame frame);
	}

	private static final class Configuration {
		private final int sampleRate;
		private final PitchEstimator pitchEstimator;
		private final long discardUntil;

		private Configuration(int sampleRate, PitchEstimator pitchEstimator, long discardUntil) {
			this.sampleRate = sampleRate;
			this.pitchEstimator = pitchEstimator;
			this.discardUntil = discardUntil;
		}
	}

	private final TGContext context;
	private final SampleRingBuffer ringBuffer;
	private final LevelAnalyzer levelAnalyzer;
	private final int windowSize;
	private final int hopSize;
	private final List<Listener> listeners;
	private final AtomicReference<Configuration> pendingConfiguration;
	private Configuration configuration;
	private boolean running;
	private boolean workerAlive;

	public AnalysisService(TGContext context, SampleRingBuffer ringBuffer, LevelAnalyzer levelAnalyzer,
			PitchEstimator pitchEstimator, int sampleRate, int windowSize, int hopSize) {
		if (sampleRate <= 0 || windowSize <= 0 || hopSize <= 0 || hopSize > windowSize || windowSize % hopSize != 0) {
			throw new IllegalArgumentException("invalid analysis dimensions");
		}
		this.context = context;
		this.ringBuffer = ringBuffer;
		this.levelAnalyzer = levelAnalyzer;
		this.windowSize = windowSize;
		this.hopSize = hopSize;
		this.configuration = new Configuration(sampleRate, pitchEstimator, 0L);
		this.pendingConfiguration = new AtomicReference<Configuration>();
		this.listeners = new CopyOnWriteArrayList<Listener>();
	}

	public synchronized void subscribe(Listener listener) {
		this.listeners.add(listener);
		this.running = true;
		if (!this.workerAlive) {
			this.workerAlive = true;
			TGThreadManager.getInstance(this.context).start(TGThreadPriority.HIGH, this::run);
		}
	}

	public synchronized void unsubscribe(Listener listener) {
		this.listeners.remove(listener);
		if (this.listeners.isEmpty()) {
			this.running = false;
		}
	}

	/**
	 * Switches analysis to a new sample rate and estimator, for example after capture opened
	 * the input at its negotiated rate. Samples written to the ring buffer before
	 * {@code discardUntil} (a {@link SampleRingBuffer#getWrittenSampleCount()} value) belong
	 * to the previous configuration and are discarded instead of analysed.
	 */
	public void reconfigure(int sampleRate, PitchEstimator pitchEstimator, long discardUntil) {
		if (sampleRate <= 0) {
			throw new IllegalArgumentException("invalid sample rate");
		}
		this.pendingConfiguration.set(new Configuration(sampleRate, pitchEstimator, discardUntil));
	}

	private void run() {
		float[] window = new float[this.windowSize];
		float[] hop = new float[this.hopSize];
		int windowFill = 0;
		int hopFill = 0;
		long baseNanos = 0;
		long processedSamples = 0;
		long observedDroppedSamples = this.ringBuffer.getDroppedSampleCount();

		while (keepRunning()) {
			Configuration pending = this.pendingConfiguration.getAndSet(null);
			if (pending != null) {
				baseNanos += processedSamples * 1_000_000_000L / this.configuration.sampleRate;
				processedSamples = 0;
				this.configuration = pending;
				this.ringBuffer.skipTo(pending.discardUntil);
				observedDroppedSamples = this.ringBuffer.getDroppedSampleCount();
				windowFill = 0;
				hopFill = 0;
			}
			long droppedSamples = this.ringBuffer.getDroppedSampleCount();
			if (droppedSamples != observedDroppedSamples) {
				processedSamples += droppedSamples - observedDroppedSamples;
				observedDroppedSamples = droppedSamples;
				windowFill = 0;
				hopFill = 0;
			}
			int read = this.ringBuffer.read(hop, hopFill, this.hopSize - hopFill);
			if (read == 0) {
				pauseBriefly();
				continue;
			}
			hopFill += read;
			if (hopFill < this.hopSize) {
				continue;
			}

			if (windowFill < this.windowSize) {
				int copy = Math.min(this.hopSize, this.windowSize - windowFill);
				System.arraycopy(hop, 0, window, windowFill, copy);
				windowFill += copy;
			} else {
				System.arraycopy(window, this.hopSize, window, 0, this.windowSize - this.hopSize);
				System.arraycopy(hop, 0, window, this.windowSize - this.hopSize, this.hopSize);
			}
			processedSamples += this.hopSize;
			hopFill = 0;
			if (windowFill == this.windowSize) {
				long timestampNanos = baseNanos + processedSamples * 1_000_000_000L / this.configuration.sampleRate;
				publish(analyze(window, timestampNanos));
			}
		}
	}

	private AnalysisFrame analyze(float[] window, long timestampNanos) {
		LevelMeasurement level = this.levelAnalyzer.analyze(window);
		PitchEstimate pitch = this.configuration.pitchEstimator.estimate(window);
		return new AnalysisFrame(timestampNanos, level.getRmsDbfs(), level.getPeakDbfs(),
				level.getClippedRatio(), level.getLevelState(),
				pitch.isPitched() ? pitch.getF0Hz() : Double.NaN,
				pitch.getProbability(), false);
	}

	private void publish(AnalysisFrame frame) {
		for (Listener listener : this.listeners) {
			try {
				listener.analysisFrame(frame);
			} catch (RuntimeException exception) {
				// Analysis continues even if one consumer rejects a frame.
			}
		}
	}

	private synchronized boolean keepRunning() {
		if (!this.running) {
			this.workerAlive = false;
		}
		return this.running;
	}

	private static void pauseBriefly() {
		try {
			Thread.sleep(1L);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
