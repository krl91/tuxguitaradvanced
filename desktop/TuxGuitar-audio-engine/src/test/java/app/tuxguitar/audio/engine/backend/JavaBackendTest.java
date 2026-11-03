package app.tuxguitar.audio.engine.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.BackendTestEngines;
import app.tuxguitar.audio.engine.FakeAudioInput;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.input.CaptureService;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/** The Java backend is today's capture and analysis, unchanged (spec 023, T041): the same frames as the engine for the same input. */
public class JavaBackendTest {

	private static final int RATE = 48000;
	private static final IntFunction<PitchEstimator> ESTIMATORS = rate -> window -> new PitchEstimate(220.0, 0.9, true);

	@Test
	public void itGivesTheFramesTheEngineGivesForTheSameInput() throws Exception {
		float[] signal = SyntheticSignals.karplusStrong(110.0, RATE, RATE, 7L);

		TGContext engineContext = newContext();
		TGContext backendContext = newContext();
		try {
			AudioEngine engine = BackendTestEngines.withEstimator(engineContext, ESTIMATORS);
			FakeAudioInput.FrameCounter fromEngine = new FakeAudioInput.FrameCounter();
			engine.subscribe(fromEngine);
			FakeAudioInput.of(engine).feedAll(signal);

			SampleRingBuffer ring = new SampleRingBuffer(AudioEngine.DEFAULT_RING_BUFFER_CAPACITY);
			RecognitionSettings settings = RecognitionSettings.defaults();
			JavaBackend backend = new JavaBackend(new CaptureService(backendContext, new InputDeviceCatalog(), CurrentInputSelection.getInstance(backendContext), ring),
					new AnalysisService(backendContext, ring, AudioEngine.levelAnalyzer(settings), new ArticulationDetector(), ESTIMATORS.apply(RATE), RATE, 2048, 256), ring);
			FakeAudioInput.FrameCounter fromBackend = new FakeAudioInput.FrameCounter();
			backend.subscribe(fromBackend);
			backend.setExpectedChord(new int[] { 40, 47, 52 }, new int[0]);
			feedAll(ring, signal);

			int expected = FakeAudioInput.framesFor(signal.length, 2048, 256);
			waitFor(fromEngine, expected);
			waitFor(fromBackend, expected);
			List<AnalysisFrame> a = fromEngine.frames();
			List<AnalysisFrame> b = fromBackend.frames();
			for( int i = 0; i < expected; i++ ) {
				assertEquals(a.get(i).getTimestampNanos(), b.get(i).getTimestampNanos(), "frame " + i);
				assertEquals(a.get(i).getRmsDbfs(), b.get(i).getRmsDbfs(), 0.0, "frame " + i);
				assertEquals(a.get(i).getF0Hz(), b.get(i).getF0Hz(), 0.0, "frame " + i);
				assertEquals(a.get(i).isOnset(), b.get(i).isOnset(), "frame " + i);
				assertNull(b.get(i).getChordEvidence(), "the expected chord is the matcher's business with Java");
			}
			assertEquals(PitchEngineCatalog.Kind.JAVA, backend.engineKind());
			assertEquals(ring.getDroppedSampleCount(), backend.getDroppedSampleCount());
			assertEquals(0, backend.getConsumerCount());
		} finally {
			TGThreadManager.getInstance(engineContext).dispose();
			TGThreadManager.getInstance(backendContext).dispose();
		}
	}

	private static void feedAll(SampleRingBuffer buffer, float[] samples) throws InterruptedException {
		int offset = 0;
		while( offset < samples.length ) {
			int room = Math.min(buffer.remainingCapacity(), Math.min(4096, samples.length - offset));
			if( room <= 0 ) {
				Thread.sleep(1L);
				continue;
			}
			offset += buffer.write(samples, offset, room);
		}
	}

	private static void waitFor(FakeAudioInput.FrameCounter counter, int count) throws InterruptedException {
		long deadline = System.nanoTime() + 10_000_000_000L;
		while( counter.count() < count && System.nanoTime() < deadline ) {
			Thread.sleep(5L);
		}
		assertEquals(count, counter.count());
	}

	private static TGContext newContext() {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		return context;
	}
}
