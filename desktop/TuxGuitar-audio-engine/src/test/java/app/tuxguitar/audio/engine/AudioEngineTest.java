package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

public class AudioEngineTest {

	private static final int SAMPLE_RATE = 48000;
	private static final int BLOCK = 1024;

	@Test
	public void sharedInstanceIsCreatedLazilyOncePerContext() {
		TGContext context = new TGContext();
		assertSame(AudioEngine.getInstance(context), AudioEngine.getInstance(context));
		assertEquals(0L, AudioEngine.getInstance(context).getDroppedSampleCount());
	}

	@Test
	public void nominalRateFeedDropsNoSamples() throws InterruptedException {
		TGContext context = newContext();
		try {
			AudioEngine engine = newEngine(context, AudioEngine.DEFAULT_RING_BUFFER_CAPACITY);
			AtomicInteger frames = new AtomicInteger();
			AnalysisService.Listener listener = frame -> frames.incrementAndGet();
			engine.subscribe(listener);

			float[] block = SyntheticSignals.sine(440.0, SAMPLE_RATE, BLOCK, 0.5);
			SampleRingBuffer buffer = engine.getRingBuffer();
			long blockNanos = BLOCK * 1_000_000_000L / SAMPLE_RATE;
			long start = System.nanoTime();
			for (int i = 0; i < 47; i++) {
				buffer.write(block, 0, block.length);
				long next = start + (i + 1) * blockNanos;
				while (System.nanoTime() < next) {
					Thread.sleep(1L);
				}
			}
			Thread.sleep(100L);
			engine.unsubscribe(listener);

			assertEquals(0L, engine.getDroppedSampleCount());
			assertTrue(frames.get() > 100, "frames analysed: " + frames.get());
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void forcedOverrunIsCountedAndAnalysisRecovers() throws InterruptedException {
		TGContext context = newContext();
		try {
			AudioEngine engine = newEngine(context, 4096);
			SampleRingBuffer buffer = engine.getRingBuffer();
			float[] burst = SyntheticSignals.sine(440.0, SAMPLE_RATE, 4096 + 1000, 0.5);

			assertEquals(4096, buffer.write(burst, 0, burst.length));
			assertEquals(1000L, engine.getDroppedSampleCount());

			CountDownLatch recovered = new CountDownLatch(3);
			AnalysisService.Listener listener = frame -> recovered.countDown();
			engine.subscribe(listener);
			float[] block = SyntheticSignals.sine(440.0, SAMPLE_RATE, 256, 0.5);
			for (int i = 0; i < 40 && recovered.getCount() > 0; i++) {
				while (buffer.remainingCapacity() < block.length) {
					Thread.sleep(1L);
				}
				buffer.write(block, 0, block.length);
			}
			assertTrue(recovered.await(2, TimeUnit.SECONDS));
			engine.unsubscribe(listener);
			assertEquals(1000L, engine.getDroppedSampleCount());
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	private static TGContext newContext() {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		return context;
	}

	private static AudioEngine newEngine(TGContext context, int capacity) {
		return new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context),
				capacity, new LevelAnalyzer(AudioEngine.DEFAULT_TOO_LOW_RMS_DBFS, AudioEngine.DEFAULT_TOO_HIGH_RMS_DBFS,
						AudioEngine.DEFAULT_CLIPPED_SAMPLE_LEVEL, AudioEngine.DEFAULT_CLIPPING_RATIO),
				sampleRate -> window -> new PitchEstimate(440.0, 1.0, true));
	}
}
