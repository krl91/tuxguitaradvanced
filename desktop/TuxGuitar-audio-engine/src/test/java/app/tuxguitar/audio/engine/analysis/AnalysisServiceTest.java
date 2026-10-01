package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

	@Test
	public void reconfigureAnalysesAtTheNegotiatedRateAndDiscardsEarlierSamples() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			int windowSize = TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE;
			int hopSize = TarsosFastYinEstimator.DEFAULT_HOP_SIZE;
			SampleRingBuffer buffer = new SampleRingBuffer(48000);
			float[] previousOpening = SyntheticSignals.sine(220.0, 48000, windowSize, 0.5);
			buffer.write(previousOpening, 0, previousOpening.length);

			AnalysisService service = new AnalysisService(context, buffer,
					new LevelAnalyzer(-80.0, -1.0, 0.99, 0.1), new TarsosFastYinEstimator(),
					48000, windowSize, hopSize);
			service.reconfigure(44100, new TarsosFastYinEstimator(44100f, windowSize, hopSize),
					buffer.getWrittenSampleCount());
			float[] negotiated = SyntheticSignals.sine(440.0, 44100, windowSize + hopSize, 0.5);
			buffer.write(negotiated, 0, negotiated.length);

			List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
			CountDownLatch latch = new CountDownLatch(2);
			AnalysisService.Listener listener = frame -> {
				frames.add(frame);
				latch.countDown();
			};
			service.subscribe(listener);
			assertTrue(latch.await(2, TimeUnit.SECONDS));
			service.unsubscribe(listener);

			assertEquals(windowSize * 1_000_000_000L / 44100, frames.get(0).getTimestampNanos());
			for (AnalysisFrame frame : frames) {
				assertEquals(440.0, frame.getF0Hz(), 2.0);
			}
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void framesCarryEvidenceOnlyWhileExpectedPitchesAreSet() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			int sampleRate = 44100;
			float[] samples = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(52), sampleRate, 2048 + 6 * 256, 5L);
			SampleRingBuffer buffer = new SampleRingBuffer(2 * samples.length);
			buffer.write(samples, 0, samples.length);
			AnalysisService service = new AnalysisService(context, buffer, new LevelAnalyzer(-80.0, -1.0, 0.99, 0.1),
					window -> new PitchEstimate(164.8, 1.0, true), sampleRate, 2048, 256);
			service.setExpectedPitches(new int[] { 52 });
			List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
			CountDownLatch latch = new CountDownLatch(3);
			AnalysisService.Listener listener = frame -> {
				frames.add(frame);
				latch.countDown();
			};
			service.subscribe(listener);
			assertTrue(latch.await(2, TimeUnit.SECONDS));
			service.unsubscribe(listener);
			assertTrue(frames.get(0).getTargetEvidence() != null);
			assertTrue(frames.get(0).getTargetEvidence().marginOver(52) > 0.2);

			service.setExpectedPitches(null);
			buffer.write(samples, 0, samples.length);
			CountDownLatch more = new CountDownLatch(2);
			List<AnalysisFrame> later = new CopyOnWriteArrayList<AnalysisFrame>();
			AnalysisService.Listener second = frame -> {
				later.add(frame);
				more.countDown();
			};
			service.subscribe(second);
			assertTrue(more.await(2, TimeUnit.SECONDS));
			service.unsubscribe(second);
			assertTrue(later.get(0).getTargetEvidence() == null);
		} finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void rapidResubscriptionNeverRunsTwoConsumersAtOnce() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			SampleRingBuffer buffer = new SampleRingBuffer(48000);
			AtomicInteger inFlight = new AtomicInteger();
			AtomicInteger maxInFlight = new AtomicInteger();
			PitchEstimator estimator = window -> {
				maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
				try {
					Thread.sleep(1L);
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				inFlight.decrementAndGet();
				return new PitchEstimate(440.0, 1.0, true);
			};
			AnalysisService service = new AnalysisService(context, buffer,
					new LevelAnalyzer(-80.0, -1.0, 0.99, 0.1), estimator, 48000, 2048, 256);
			float[] block = SyntheticSignals.sine(440.0, 48000, 256, 0.5);
			AnalysisService.Listener listener = frame -> { };

			for (int i = 0; i < 200; i++) {
				buffer.write(block, 0, block.length);
				service.subscribe(listener);
				service.unsubscribe(listener);
			}
			service.subscribe(listener);
			Thread.sleep(50L);
			service.unsubscribe(listener);

			assertTrue(maxInFlight.get() <= 1, "concurrent analysis workers: " + maxInFlight.get());
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void theOptionalSpectrumLeavesSingleNoteFieldsUnchanged() throws InterruptedException {
		float[] samples = SyntheticSignals.karplusStrong(110.0, 48000, 48000 / 2, 3L);
		List<AnalysisFrame> plain = frames(samples, false, 20);
		List<AnalysisFrame> withSpectrum = frames(samples, true, 20);

		for (int i = 0; i < 20; i++) {
			assertEquals(plain.get(i).getF0Hz(), withSpectrum.get(i).getF0Hz());
			assertEquals(plain.get(i).getConfidence(), withSpectrum.get(i).getConfidence());
			assertEquals(plain.get(i).isOnset(), withSpectrum.get(i).isOnset());
			assertEquals(null, plain.get(i).getSpectrum());
			assertTrue(withSpectrum.get(i).getSpectrum() != null);
		}
		assertEquals(45, withSpectrum.get(19).getSpectrum().getCandidates().get(0).getMidi());
	}

	private static List<AnalysisFrame> frames(float[] samples, boolean spectrum, int count) throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			SampleRingBuffer buffer = new SampleRingBuffer(samples.length);
			buffer.write(samples, 0, samples.length);
			AnalysisService service = new AnalysisService(context, buffer, new LevelAnalyzer(-80.0, -1.0, 0.99, 0.1),
					new TarsosFastYinEstimator(), 48000, TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
			service.setSpectrumEnabled(spectrum);
			List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
			CountDownLatch latch = new CountDownLatch(count);
			AnalysisService.Listener listener = frame -> {
				if (frames.size() < count) {
					frames.add(frame);
					latch.countDown();
				}
			};
			service.subscribe(listener);
			assertTrue(latch.await(5, TimeUnit.SECONDS));
			service.unsubscribe(listener);
			return frames;
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}
}
