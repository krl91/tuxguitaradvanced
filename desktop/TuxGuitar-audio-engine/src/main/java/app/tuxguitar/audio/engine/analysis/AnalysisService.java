package app.tuxguitar.audio.engine.analysis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.thread.TGThreadPriority;
import app.tuxguitar.util.TGContext;

/** Dedicated sliding-window analysis worker. */
public final class AnalysisService {

	public interface Listener {
		void analysisFrame(AnalysisFrame frame);
	}

	private final TGContext context;
	private final SampleRingBuffer ringBuffer;
	private final LevelAnalyzer levelAnalyzer;
	private final PitchEstimator pitchEstimator;
	private final int sampleRate;
	private final int windowSize;
	private final int hopSize;
	private final List<Listener> listeners;
	private volatile boolean running;
	private long generation;

	public AnalysisService(TGContext context, SampleRingBuffer ringBuffer, LevelAnalyzer levelAnalyzer,
			PitchEstimator pitchEstimator, int sampleRate, int windowSize, int hopSize) {
		if (sampleRate <= 0 || windowSize <= 0 || hopSize <= 0 || hopSize > windowSize || windowSize % hopSize != 0) {
			throw new IllegalArgumentException("invalid analysis dimensions");
		}
		this.context = context;
		this.ringBuffer = ringBuffer;
		this.levelAnalyzer = levelAnalyzer;
		this.pitchEstimator = pitchEstimator;
		this.sampleRate = sampleRate;
		this.windowSize = windowSize;
		this.hopSize = hopSize;
		this.listeners = new CopyOnWriteArrayList<Listener>();
	}

	public synchronized void subscribe(Listener listener) {
		this.listeners.add(listener);
		if (!this.running) {
			this.running = true;
			long workerGeneration = ++this.generation;
			TGThreadManager.getInstance(this.context).start(TGThreadPriority.HIGH, () -> run(workerGeneration));
		}
	}

	public synchronized void unsubscribe(Listener listener) {
		this.listeners.remove(listener);
		if (this.listeners.isEmpty()) {
			this.running = false;
			this.generation++;
		}
	}

	private void run(long workerGeneration) {
		float[] window = new float[this.windowSize];
		float[] hop = new float[this.hopSize];
		int windowFill = 0;
		int hopFill = 0;
		long processedSamples = 0;
		long observedDroppedSamples = this.ringBuffer.getDroppedSampleCount();

		while (isCurrent(workerGeneration)) {
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
				publish(analyze(window, processedSamples));
			}
		}
	}

	private AnalysisFrame analyze(float[] window, long processedSamples) {
		LevelMeasurement level = this.levelAnalyzer.analyze(window);
		PitchEstimate pitch = this.pitchEstimator.estimate(window);
		long timestampNanos = processedSamples * 1_000_000_000L / this.sampleRate;
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

	private synchronized boolean isCurrent(long workerGeneration) {
		return this.running && workerGeneration == this.generation;
	}

	private static void pauseBriefly() {
		try {
			Thread.sleep(1L);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}
}
