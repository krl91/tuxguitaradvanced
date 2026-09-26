package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

public class AnalysisServiceTest {

	@Test
	public void consumesSlidingWindowsAndProducesDeterministicTimestamps() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			int sampleRate = 48000;
			int windowSize = 2048;
			int hopSize = 256;
			float[] samples = SyntheticSignals.sine(440.0, sampleRate, windowSize + 2 * hopSize, 0.5);
			SampleRingBuffer buffer = new SampleRingBuffer(samples.length);
			assertEquals(samples.length, buffer.write(samples, 0, samples.length));

			PitchEstimator estimator = window -> new PitchEstimate(440.0, 1.0, true);
			AnalysisService service = new AnalysisService(context, buffer,
					new LevelAnalyzer(-80.0, -1.0, 0.99, 0.1), estimator,
					sampleRate, windowSize, hopSize);
			List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
			CountDownLatch latch = new CountDownLatch(3);
			AnalysisService.Listener listener = frame -> {
				frames.add(frame);
				latch.countDown();
			};

			service.subscribe(listener);
			assertTrue(latch.await(2, TimeUnit.SECONDS));
			service.unsubscribe(listener);

			assertEquals(3, frames.size());
			assertEquals(windowSize * 1_000_000_000L / sampleRate, frames.get(0).getTimestampNanos());
			assertEquals((windowSize + hopSize) * 1_000_000_000L / sampleRate, frames.get(1).getTimestampNanos());
			assertEquals(440.0, frames.get(2).getF0Hz());
			assertEquals(1.0, frames.get(2).getConfidence());
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}
}
