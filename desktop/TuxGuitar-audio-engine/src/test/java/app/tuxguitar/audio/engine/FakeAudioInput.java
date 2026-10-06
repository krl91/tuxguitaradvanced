package app.tuxguitar.audio.engine;

import java.util.concurrent.atomic.AtomicInteger;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * A stand-in for the audio input (spec 002 T127): samples go straight into the engine's ring buffer, replacing {@code CaptureService} and
 * Java Sound, so that the whole live chain (ring buffer, analysis thread, front end, frames) runs on a recording or a synthetic signal.
 * It lives in the engine's own package because the engine's test constructor and ring buffer are package-private: the public API is not widened.
 */
public final class FakeAudioInput {

	private final AudioEngine engine;

	private FakeAudioInput(AudioEngine engine) {
		this.engine = engine;
	}

	/** An engine built like the production one for these settings (level thresholds, attack detection, pitch engine), without a capture device. */
	public static AudioEngine newEngine(TGContext context, RecognitionSettings settings) {
		return new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context), AudioEngine.DEFAULT_RING_BUFFER_CAPACITY,
				AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings),
				sampleRate -> PitchEngineCatalog.resolve(settings.get(RecognitionParameters.ENGINE_PITCH)).create(sampleRate), null);
	}

	public static FakeAudioInput of(AudioEngine engine) {
		return new FakeAudioInput(engine);
	}

	/**
	 * Writes all the samples, as fast as the analysis consumes them: when the ring buffer is full it waits for room instead of dropping,
	 * so that the live analysis sees every sample (the same ones the offline analysis sees).
	 */
	public void feedAll(float[] samples) throws InterruptedException {
		SampleRingBuffer buffer = this.engine.getRingBuffer();
		int offset = 0;
		while( offset < samples.length ) {
			// the buffer counts what it cannot hold as dropped: only write what fits
			int room = Math.min(buffer.remainingCapacity(), Math.min(4096, samples.length - offset));
			if( room <= 0 ) {
				Thread.sleep(1L);
				continue;
			}
			offset += buffer.write(samples, offset, room);
		}
	}

	/** Writes the samples at the pace of real time, in blocks of 1024 (what capture does). */
	public void feedRealTime(float[] samples, int sampleRate) throws InterruptedException {
		SampleRingBuffer buffer = this.engine.getRingBuffer();
		long blockNanos = 1024L * 1_000_000_000L / sampleRate;
		long start = System.nanoTime();
		for( int offset = 0, block = 0 ; offset < samples.length ; offset += 1024, block ++ ) {
			buffer.write(samples, offset, Math.min(1024, samples.length - offset));
			long next = start + (block + 1) * blockNanos;
			while( System.nanoTime() < next ) {
				Thread.sleep(1L);
			}
		}
	}

	/** The number of frames the analysis produces for this many samples. */
	public static int framesFor(int sampleCount, int windowSize, int hopSize) {
		return sampleCount < windowSize ? 0 : (sampleCount - windowSize) / hopSize + 1;
	}

	/** Counts the frames the engine delivers (a listener to subscribe). */
	public static final class FrameCounter implements AnalysisService.Listener {
		private final AtomicInteger count = new AtomicInteger();
		private final java.util.List<AnalysisFrame> frames = new java.util.concurrent.CopyOnWriteArrayList<AnalysisFrame>();

		@Override
		public void analysisFrame(AnalysisFrame frame) {
			this.frames.add(frame);
			this.count.incrementAndGet();
		}

		public int count() {
			return this.count.get();
		}

		public java.util.List<AnalysisFrame> frames() {
			return new java.util.ArrayList<AnalysisFrame>(this.frames);
		}
	}
}
