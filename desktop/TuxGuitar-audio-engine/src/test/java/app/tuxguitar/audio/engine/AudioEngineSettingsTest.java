package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Propagation of the engine's recognition settings (task T102): level thresholds and attack
 * detection follow the settings, and a change during capture restarts analysis cleanly — frames
 * are computed with the old settings, then only with the new ones, never a mix.
 */
public class AudioEngineSettingsTest {

	private static final int SAMPLE_RATE = 48000;

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		private Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	@Test
	public void defaultAnalysersBehaveLikeTheFormerHardCodedOnes() {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		float[] quiet = SyntheticSignals.sine(440.0, SAMPLE_RATE, 2048, 0.005);
		float[] normal = SyntheticSignals.sine(440.0, SAMPLE_RATE, 2048, 0.1);
		assertEquals(AnalysisFrame.LevelState.TOO_LOW, AudioEngine.levelAnalyzer(defaults).analyze(quiet).getLevelState());
		assertEquals(AnalysisFrame.LevelState.SUITABLE, AudioEngine.levelAnalyzer(defaults).analyze(normal).getLevelState());
	}

	@Test
	public void levelThresholdsFollowTheSettings() {
		float[] signal = SyntheticSignals.sine(440.0, SAMPLE_RATE, 2048, 0.05);
		LevelMeasurement before = AudioEngine.levelAnalyzer(RecognitionSettings.defaults()).analyze(signal);
		LevelMeasurement after = AudioEngine.levelAnalyzer(RecognitionSettings.defaults().with(RecognitionParameters.INPUT_LEVEL_MIN, -25)).analyze(signal);
		assertEquals(AnalysisFrame.LevelState.SUITABLE, before.getLevelState());
		assertEquals(AnalysisFrame.LevelState.TOO_LOW, after.getLevelState());
	}

	@Test
	public void onlyEngineValuesRestartTheAnalysis() {
		RecognitionSettings base = RecognitionSettings.defaults();
		RecognitionParameter[] engine = { RecognitionParameters.INPUT_LEVEL_MIN, RecognitionParameters.INPUT_LEVEL_MAX,
				RecognitionParameters.LEVEL_CLIP_SAMPLE, RecognitionParameters.LEVEL_CLIP_SHARE,
				RecognitionParameters.NOTE_ATTACK, RecognitionParameters.ATTACK_MIN_LEVEL };
		for( RecognitionParameter parameter : engine ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			assertTrue(AudioEngine.engineValuesDiffer(base, base.with(number, number.getMaximum())), parameter.getKey());
		}
		assertFalse(AudioEngine.engineValuesDiffer(base, base.with(RecognitionParameters.NOTE_ACCEPT, 20)));
		assertFalse(AudioEngine.engineValuesDiffer(base, base.with(RecognitionParameters.PASSAGE_MEASURES, "0")));
		assertFalse(AudioEngine.engineValuesDiffer(base, base.with(RecognitionParameters.TUNER_IN_TUNE, 9)));
		assertFalse(AudioEngine.engineValuesDiffer(base, base));
	}

	@Test
	public void aChangeDuringCaptureSwitchesCleanlyToTheNewValues() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
			RecognitionSettings settings = store.get();
			AudioEngine engine = new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context),
					AudioEngine.DEFAULT_RING_BUFFER_CAPACITY, AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings),
					sampleRate -> window -> new PitchEstimate(440.0, 1.0, true), store);
			List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
			AnalysisService.Listener listener = frames::add;
			engine.subscribe(listener);

			// −29 dBFS: suitable for the default −40 dBFS floor, too low for a −25 dBFS floor
			float[] block = SyntheticSignals.sine(440.0, SAMPLE_RATE, 256, 0.05);
			SampleRingBuffer buffer = engine.getRingBuffer();
			feed(buffer, block, 40, frames, 20);
			int beforeChange = frames.size();
			store.update(settings.with(RecognitionParameters.INPUT_LEVEL_MIN, -25));
			feed(buffer, block, 40, frames, beforeChange + 20);
			engine.unsubscribe(listener);

			boolean switched = false;
			long previous = Long.MIN_VALUE;
			for( AnalysisFrame frame : frames ) {
				if( frame.getLevelState() == AnalysisFrame.LevelState.TOO_LOW ) {
					switched = true;
				} else {
					// never back to the old values once the new ones are in use
					assertFalse(switched, "old settings used after the switch");
					assertEquals(AnalysisFrame.LevelState.SUITABLE, frame.getLevelState());
				}
				assertTrue(frame.getTimestampNanos() > previous, "timestamps keep increasing");
				previous = frame.getTimestampNanos();
			}
			assertTrue(switched, "new settings never applied");
			assertEquals(0L, engine.getDroppedSampleCount());
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	private static void feed(SampleRingBuffer buffer, float[] block, int blocks, List<AnalysisFrame> frames, int wantedFrames) throws InterruptedException {
		for( int i = 0 ; i < blocks ; i ++ ) {
			while( buffer.remainingCapacity() < block.length ) {
				Thread.sleep(1L);
			}
			buffer.write(block, 0, block.length);
		}
		long deadline = System.nanoTime() + 2_000_000_000L;
		while( frames.size() < wantedFrames && System.nanoTime() < deadline ) {
			Thread.sleep(2L);
		}
		assertTrue(frames.size() >= wantedFrames, "frames: " + frames.size());
	}
}
