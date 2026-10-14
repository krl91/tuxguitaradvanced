package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The hop listener (spec 010 T008, contract invariants 16 and 18): every hop of the signal the analysis sees, in order, after a {@code configure(sampleRate)}
 * that comes before the first hop, at every reconfiguration and for a listener added while the engine runs; offline and live; a listener changes nothing of
 * the frames.
 */
public class HopListenerTest {

	private static final int HOP = 256;
	private static final long LIMIT_SECONDS = 10;

	/** Records what it receives, as strings, in order. */
	private static final class Recorder implements HopListener {
		final List<String> events = new CopyOnWriteArrayList<String>();
		final List<float[]> hops = new CopyOnWriteArrayList<float[]>();
		final AtomicInteger hopCount = new AtomicInteger();
		volatile CountDownLatch latch;

		@Override
		public void configure(int sampleRate) {
			this.events.add("configure:" + sampleRate);
		}

		@Override
		public void hop(float[] samples, int offset, int length) {
			float[] copy = new float[length];
			System.arraycopy(samples, offset, copy, 0, length);
			this.hops.add(copy);
			this.events.add("hop");
			this.hopCount.incrementAndGet();
			CountDownLatch current = this.latch;
			if (current != null) {
				current.countDown();
			}
		}
	}

	@Test
	public void offlineAnalysisConfiguresOnceBeforeTheFirstHopAndGivesEveryHopInOrder() {
		float[] signal = SyntheticSignals.sine(440.0, 48000, 48000, 0.4);
		Recorder recorder = new Recorder();
		OfflineAnalysis.analyze(signal, 48000, RecognitionSettings.defaults(), false, null, recorder);
		assertEquals("configure:48000", recorder.events.get(0));
		assertEquals(1, recorder.events.stream().filter(e -> e.startsWith("configure")).count(), "configured once");
		assertEquals(signal.length / HOP, recorder.hops.size(), "one call per hop");
		for (int i = 0; i < recorder.hops.size(); i++) {
			float[] hop = recorder.hops.get(i);
			assertEquals(HOP, hop.length);
			for (int k = 0; k < HOP; k++) {
				assertEquals(signal[i * HOP + k], hop[k], 0.0f, "hop " + i + " sample " + k);
			}
		}
	}

	@Test
	public void aHopListenerChangesNothingOfTheFrames() {
		float[] signal = SyntheticSignals.sine(220.0, 44100, 30000, 0.7);
		List<AnalysisFrame> without = OfflineAnalysis.analyze(signal, 44100, RecognitionSettings.defaults(), false, null);
		List<AnalysisFrame> with = OfflineAnalysis.analyze(signal, 44100, RecognitionSettings.defaults(), false, null, new Recorder());
		assertEquals(without.size(), with.size());
		for (int i = 0; i < without.size(); i++) {
			AnalysisFrame a = without.get(i);
			AnalysisFrame b = with.get(i);
			assertEquals(a.getTimestampNanos(), b.getTimestampNanos());
			assertEquals(a.getRmsDbfs(), b.getRmsDbfs(), 0.0);
			assertEquals(a.getPeakDbfs(), b.getPeakDbfs(), 0.0);
			assertEquals(a.getClippedRatio(), b.getClippedRatio(), 0.0);
			assertEquals(a.getLevelState(), b.getLevelState());
			assertEquals(a.getF0Hz(), b.getF0Hz(), 0.0);
			assertEquals(a.getConfidence(), b.getConfidence(), 0.0);
			assertEquals(a.isOnset(), b.isOnset());
		}
	}

	private static AnalysisService service(TGContext context, SampleRingBuffer buffer, int sampleRate) {
		return new AnalysisService(context, buffer, new LevelAnalyzer(-80.0, -1.0, 0.99, 0.1), window -> new PitchEstimate(440.0, 1.0, true), sampleRate, 2048, HOP);
	}

	@Test
	public void theServiceCallsAListenerOncePerHopAfterItsConfigureAndStopsAfterRemoval() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			int hops = 12;
			float[] samples = SyntheticSignals.sine(440.0, 48000, hops * HOP, 0.5);
			SampleRingBuffer buffer = new SampleRingBuffer(samples.length * 2);
			buffer.write(samples, 0, samples.length);
			AnalysisService service = service(context, buffer, 48000);
			Recorder recorder = new Recorder();
			recorder.latch = new CountDownLatch(hops);
			service.addHopListener(recorder);
			AnalysisService.Listener frames = frame -> {
			};
			service.subscribe(frames); // the worker runs while a frame listener is subscribed
			assertTrue(recorder.latch.await(LIMIT_SECONDS, TimeUnit.SECONDS));
			assertEquals("configure:48000", recorder.events.get(0));
			assertEquals(hops, recorder.hopCount.get());
			service.removeHopListener(recorder);
			float[] more = SyntheticSignals.sine(440.0, 48000, 4 * HOP, 0.5);
			buffer.write(more, 0, more.length);
			Thread.sleep(150);
			assertEquals(hops, recorder.hopCount.get(), "no hop after the removal");
			service.unsubscribe(frames);
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void aListenerAddedWhileTheEngineRunsIsConfiguredOnTheAnalysisThreadBeforeItsFirstHop() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			SampleRingBuffer buffer = new SampleRingBuffer(48000);
			AnalysisService service = service(context, buffer, 44100);
			CountDownLatch framesSeen = new CountDownLatch(2);
			AnalysisService.Listener frames = frame -> framesSeen.countDown();
			service.subscribe(frames);
			float[] first = SyntheticSignals.sine(440.0, 44100, 2048 + 3 * HOP, 0.5);
			buffer.write(first, 0, first.length);
			assertTrue(framesSeen.await(LIMIT_SECONDS, TimeUnit.SECONDS), "the engine is running");
			Recorder late = new Recorder();
			late.latch = new CountDownLatch(5);
			Thread adder = new Thread(() -> service.addHopListener(late)); // from another thread, as the window does
			adder.start();
			adder.join();
			float[] second = SyntheticSignals.sine(440.0, 44100, 6 * HOP, 0.5);
			buffer.write(second, 0, second.length);
			assertTrue(late.latch.await(LIMIT_SECONDS, TimeUnit.SECONDS));
			assertEquals("configure:44100", late.events.get(0), "a configure before the first hop, with the rate in use");
			assertEquals(1, late.events.stream().filter(e -> e.startsWith("configure")).count());
			service.unsubscribe(frames);
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void aReconfigurationIsFollowedByAConfigureWithTheNewRateBeforeTheNextHop() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			SampleRingBuffer buffer = new SampleRingBuffer(96000);
			AnalysisService service = service(context, buffer, 48000);
			Recorder recorder = new Recorder();
			recorder.latch = new CountDownLatch(4);
			service.addHopListener(recorder);
			AnalysisService.Listener frames = frame -> {
			};
			service.subscribe(frames);
			float[] first = SyntheticSignals.sine(440.0, 48000, 4 * HOP, 0.5);
			buffer.write(first, 0, first.length);
			assertTrue(recorder.latch.await(LIMIT_SECONDS, TimeUnit.SECONDS));
			recorder.latch = new CountDownLatch(3);
			service.reconfigure(44100, window -> new PitchEstimate(440.0, 1.0, true), buffer.getWrittenSampleCount());
			float[] second = SyntheticSignals.sine(440.0, 44100, 3 * HOP, 0.5);
			buffer.write(second, 0, second.length);
			assertTrue(recorder.latch.await(LIMIT_SECONDS, TimeUnit.SECONDS));
			List<String> events = new ArrayList<String>(recorder.events);
			int configureAt = events.indexOf("configure:44100");
			assertTrue(configureAt > 0, "a configure at the new rate: " + events);
			assertEquals("hop", events.get(configureAt + 1), "and a hop right after it");
			assertEquals("configure:48000", events.get(0));
			service.unsubscribe(frames);
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void aListenerThatThrowsDoesNotStopTheAnalysisNorTheOthers() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			float[] samples = SyntheticSignals.sine(440.0, 48000, 2048 + 4 * HOP, 0.5);
			SampleRingBuffer buffer = new SampleRingBuffer(samples.length * 2);
			buffer.write(samples, 0, samples.length);
			AnalysisService service = service(context, buffer, 48000);
			service.addHopListener(new HopListener() {
				@Override
				public void configure(int sampleRate) {
				}

				@Override
				public void hop(float[] s, int offset, int length) {
					throw new IllegalStateException("a bad listener");
				}
			});
			Recorder good = new Recorder();
			good.latch = new CountDownLatch(10);
			service.addHopListener(good);
			CountDownLatch frames = new CountDownLatch(5);
			AnalysisService.Listener listener = frame -> frames.countDown();
			service.subscribe(listener);
			assertTrue(good.latch.await(LIMIT_SECONDS, TimeUnit.SECONDS), "the other listener still gets its hops");
			assertTrue(frames.await(LIMIT_SECONDS, TimeUnit.SECONDS), "and the frames are still published");
			service.unsubscribe(listener);
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}
}
