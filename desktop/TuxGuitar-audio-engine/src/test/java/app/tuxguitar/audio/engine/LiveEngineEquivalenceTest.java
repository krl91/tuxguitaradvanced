package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The live engine (ring buffer, analysis thread, the same stages) and the offline analysis that every benchmark and replay test uses must
 * produce the same frames from the same samples (spec 002 T127, T132): otherwise the measured figures would not be the ones the learner gets.
 * The live chain is fed through {@link FakeAudioInput}; nothing is compared with a tolerance beyond floating-point noise.
 */
public class LiveEngineEquivalenceTest {

	private static final int SAMPLE_RATE = 48000;
	private static final int[] CANDIDATES = { 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66 };

	private static TGContext newContext() {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		return context;
	}

	private static List<AnalysisFrame> live(float[] samples, boolean realTime) throws InterruptedException {
		TGContext context = newContext();
		try {
			AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
			engine.setSpectrumEnabled(true);
			engine.setAttackCandidates(CANDIDATES);
			FakeAudioInput.FrameCounter counter = new FakeAudioInput.FrameCounter();
			engine.subscribe(counter);
			FakeAudioInput input = FakeAudioInput.of(engine);
			if( realTime ) {
				input.feedRealTime(samples, SAMPLE_RATE);
			} else {
				input.feedAll(samples);
			}
			int expected = FakeAudioInput.framesFor(samples.length, OfflineAnalysis.WINDOW, OfflineAnalysis.HOP);
			long deadline = System.nanoTime() + 30_000_000_000L;
			while( counter.count() < expected && System.nanoTime() < deadline ) {
				Thread.sleep(2L);
			}
			engine.unsubscribe(counter);
			assertEquals(0L, engine.getDroppedSampleCount(), "the live analysis must see every sample");
			return counter.frames();
		} finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	private static float[] passage() {
		return TechniqueSignals.fastPassage(new int[] { 52, 57, 62, 55, 59, 64 }, SAMPLE_RATE, 3.0, SAMPLE_RATE / 2, SAMPLE_RATE / 2, 0.5, 11L).getSignal();
	}

	private static void assertSameFrames(List<AnalysisFrame> offline, List<AnalysisFrame> live) {
		assertEquals(offline.size(), live.size(), "number of frames");
		for( int i = 0 ; i < offline.size() ; i ++ ) {
			AnalysisFrame a = offline.get(i);
			AnalysisFrame b = live.get(i);
			String at = "frame " + i;
			assertEquals(a.getTimestampNanos(), b.getTimestampNanos(), at + " timestamp");
			assertEquals(a.getRmsDbfs(), b.getRmsDbfs(), 1e-9, at + " rms");
			assertEquals(a.getLevelState(), b.getLevelState(), at + " level state");
			assertEquals(a.isOnset(), b.isOnset(), at + " onset");
			assertEquals(Double.isNaN(a.getF0Hz()), Double.isNaN(b.getF0Hz()), at + " pitched");
			if( !Double.isNaN(a.getF0Hz()) ) {
				assertEquals(a.getF0Hz(), b.getF0Hz(), 1e-9, at + " f0");
				assertEquals(a.getConfidence(), b.getConfidence(), 1e-9, at + " confidence");
			}
			assertEquals(a.getSpectrum() == null, b.getSpectrum() == null, at + " spectrum present");
			if( a.getAttackEvidence() == null ) {
				assertNull(b.getAttackEvidence(), at + " attack evidence");
			} else {
				assertNotNull(b.getAttackEvidence(), at + " attack evidence");
				for( int pitch : CANDIDATES ) {
					assertEquals(a.getAttackEvidence().amplitudeOf(pitch), b.getAttackEvidence().amplitudeOf(pitch), 1e-9, at + " amplitude of " + pitch);
				}
				assertEquals(a.getAttackEvidence().getAttackStrength(), b.getAttackEvidence().getAttackStrength(), 1e-9, at + " attack strength");
			}
		}
	}

	@Test
	public void theLiveEngineFedAsFastAsPossibleGivesTheFramesOfTheOfflineAnalysis() throws InterruptedException {
		float[] samples = passage();
		List<AnalysisFrame> offline = OfflineAnalysis.analyze(samples, SAMPLE_RATE, RecognitionSettings.defaults(), true, CANDIDATES);
		assertTrue(offline.size() > 200);
		assertTrue(offline.stream().anyMatch(f -> f.getAttackEvidence() != null), "the passage must produce attack evidence");
		assertSameFrames(offline, live(samples, false));
	}

	@Test
	public void theLiveEngineFedInRealTimeGivesTheSameFrames() throws InterruptedException {
		// a shorter signal: real time is slow
		float[] full = passage();
		float[] samples = java.util.Arrays.copyOf(full, SAMPLE_RATE * 2);
		List<AnalysisFrame> offline = OfflineAnalysis.analyze(samples, SAMPLE_RATE, RecognitionSettings.defaults(), true, CANDIDATES);
		assertSameFrames(offline, live(samples, true));
	}
}
