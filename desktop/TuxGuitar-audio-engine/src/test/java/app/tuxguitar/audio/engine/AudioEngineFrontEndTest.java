package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.frontend.InputGain;
import app.tuxguitar.audio.engine.frontend.InputWarning;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/** The input gain and noise reduction at engine level (spec 002, US8, tasks T028, T035, T036). */
public class AudioEngineFrontEndTest {

	private static final int RATE = 48000;

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		private Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static AudioEngine engine(TGContext context, RecognitionSettingsStore store) {
		RecognitionSettings settings = store.get();
		return new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context),
				AudioEngine.DEFAULT_RING_BUFFER_CAPACITY, AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings),
				sampleRate -> window -> new PitchEstimate(440.0, 1.0, true), store);
	}

	private static TGContext context() {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		return context;
	}

	private static List<AnalysisFrame> framesOf(AudioEngine engine, float[] block, int blocks, int wanted) throws InterruptedException {
		List<AnalysisFrame> frames = new CopyOnWriteArrayList<AnalysisFrame>();
		AnalysisService.Listener listener = frames::add;
		engine.subscribe(listener);
		SampleRingBuffer buffer = engine.getRingBuffer();
		for( int i = 0 ; i < blocks ; i ++ ) {
			while( buffer.remainingCapacity() < block.length ) {
				Thread.sleep(1L);
			}
			buffer.write(block, 0, block.length);
		}
		long deadline = System.nanoTime() + 3_000_000_000L;
		while( frames.size() < wanted && System.nanoTime() < deadline ) {
			Thread.sleep(2L);
		}
		engine.unsubscribe(listener);
		assertTrue(frames.size() >= wanted, "frames " + frames.size());
		return frames;
	}

	@Test
	public void theGainIsNeutralAtStartLimitedAndResetOnDemand() {
		TGContext context = context();
		try {
			AudioEngine engine = engine(context, new RecognitionSettingsStore(new Memory()));
			assertEquals(1.0, engine.getInputGain(), 0.0);
			assertEquals(InputGain.MAX_MULTIPLIER, engine.setInputGain(50.0), 0.0);
			assertEquals(InputGain.MAX_MULTIPLIER, engine.getInputGain(), 0.0);
			engine.resetInputGain();
			assertEquals(1.0, engine.getInputGain(), 0.0);
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void theLevelMeterAndTheFramesSeeTheBoostedSignal() throws InterruptedException {
		float[] block = SyntheticSignals.sine(440.0, RATE, 256, 0.05);
		TGContext plain = context();
		TGContext boosted = context();
		try {
			AudioEngine without = engine(plain, new RecognitionSettingsStore(new Memory()));
			AudioEngine with = engine(boosted, new RecognitionSettingsStore(new Memory()));
			with.setInputGain(2.0);
			double rmsWithout = framesOf(without, block, 40, 20).get(15).getRmsDbfs();
			double rmsWith = framesOf(with, block, 40, 20).get(15).getRmsDbfs();
			// +6 dB, and it crosses the "too low" threshold: -29 dBFS is suitable, -23 even more so
			assertEquals(6.02, rmsWith - rmsWithout, 0.2);
		}
		finally {
			TGThreadManager.getInstance(plain).dispose();
			TGThreadManager.getInstance(boosted).dispose();
		}
	}

	@Test
	public void aFrameStreamWithTheGainNeutralIsUnchangedFromTheMvp() throws InterruptedException {
		float[] block = SyntheticSignals.sine(330.0, RATE, 256, 0.2);
		TGContext first = context();
		TGContext second = context();
		try {
			double a = framesOf(engine(first, new RecognitionSettingsStore(new Memory())), block, 40, 20).get(15).getRmsDbfs();
			AudioEngine other = engine(second, new RecognitionSettingsStore(new Memory()));
			other.setInputGain(2.0);
			other.resetInputGain();
			double b = framesOf(other, block, 40, 20).get(15).getRmsDbfs();
			assertEquals(a, b, 1e-9);
		}
		finally {
			TGThreadManager.getInstance(first).dispose();
			TGThreadManager.getInstance(second).dispose();
		}
	}

	@Test
	public void aNewInputStartsNeutralAndNothingIsSaved() {
		TGContext context = context();
		try {
			Memory memory = new Memory();
			RecognitionSettingsStore store = new RecognitionSettingsStore(memory);
			AudioEngine engine = engine(context, store);
			AudioInputDescriptor first = new AudioInputDescriptor("Interface#0", "Interface", 0, 2, "Interface (input 1)", AudioInputDescriptor.Kind.INSTRUMENT, false);
			AudioInputDescriptor second = new AudioInputDescriptor("Built-in#0", "Built-in", null, 1, "Built-in microphone", AudioInputDescriptor.Kind.BUILT_IN_MICROPHONE, true);
			engine.getSelection().select(first, null);
			engine.setInputGain(3.0);
			assertEquals(3.0, engine.getInputGain(), 0.0);
			// no write of the gain to the settings
			for( String key : memory.read().keySet() ) {
				assertFalse(key.toLowerCase().contains("gain"), key);
			}
			engine.getSelection().select(second, null);
			assertEquals(1.0, engine.getInputGain(), 0.0, "a new input starts neutral");
			for( String key : memory.read().keySet() ) {
				assertFalse(key.toLowerCase().contains("gain"), key);
			}
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void theSavedNoiseReductionChoiceReachesTheFrontEndAtOnce() {
		TGContext context = context();
		try {
			RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
			AudioEngine engine = engine(context, store);
			assertFalse(engine.isNoiseReductionEnabled());
			store.update(store.get().with(RecognitionParameters.INPUT_NOISE_REDUCTION, true));
			assertTrue(engine.isNoiseReductionEnabled());
			store.update(store.get().with(RecognitionParameters.INPUT_NOISE_REDUCTION, false));
			assertFalse(engine.isNoiseReductionEnabled());
			assertEquals(0.0, engine.getFrontEndLatencyMillis(), 0.0);
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void noSuggestionWithoutAPlayingLevelCheckOrWhenTheSwitchIsOff() {
		TGContext context = context();
		try {
			RecognitionSettingsStore store = new RecognitionSettingsStore(new Memory());
			AudioEngine engine = engine(context, store);
			assertNull(engine.getLevelCheckStats());
			assertFalse(engine.suggestGain().isPresent());
			store.update(store.get().with(RecognitionParameters.INPUT_GAIN_SUGGESTION, false));
			assertFalse(engine.suggestGain().isPresent());
			assertEquals(InputWarning.NONE, engine.getInputWarning(null));
		}
		finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}
}
