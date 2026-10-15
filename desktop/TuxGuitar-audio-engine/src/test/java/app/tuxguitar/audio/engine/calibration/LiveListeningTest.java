package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.FakeAudioInput;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.thread.TGThreadManager;

/**
 * Spec 013 (T029): a played signal reaches the recorder and the counter through the REAL analysis service of an engine without a device (the fake input
 * writes into the ring buffer): the listening mode works on what the live chain gives, and what it recorded is cut into the same number of sounds.
 */
public class LiveListeningTest {

	private static final int RATE = 48000;

	@Test
	public void aPlayedSignalIsCountedRecordedAndCutThroughTheRealEngine() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
			SoundCounter counter = new SoundCounter(4, RecognitionSettings.defaults());
			HopRecorder recorder = new HopRecorder();
			engine.addHopListener(counter);
			engine.addHopListener(recorder);
			AnalysisService.Listener running = frame -> {
			};
			engine.subscribe(running); // the analysis worker runs while a frame listener is subscribed
			recorder.begin(new FrontEndState(engine.getInputGain(), engine.isNoiseReductionEnabled()));

			int[] onsets = { RATE, 3 * RATE, 5 * RATE, 7 * RATE };
			float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, 10 * RATE, onsets, 7L);
			float max = 0f;
			for (float value : signal) {
				max = Math.max(max, Math.abs(value));
			}
			for (int i = 0; i < signal.length; i++) {
				signal[i] *= 0.5f / max;
			}
			FakeAudioInput.of(engine).feedAll(signal);

			long deadline = System.currentTimeMillis() + 30000;
			while (counter.getState() == SoundCounter.State.LISTENING && System.currentTimeMillis() < deadline) {
				Thread.sleep(20);
			}
			recorder.end();
			engine.unsubscribe(running);
			engine.removeHopListener(counter);
			engine.removeHopListener(recorder);

			assertEquals(SoundCounter.State.COMPLETE, counter.getState(), "the listening stopped by itself");
			assertEquals(4, counter.getHeard());
			float[] recorded = recorder.getSamples();
			assertTrue(recorded.length > 8 * RATE, "what the live chain gave was recorded: " + recorded.length);
			assertEquals(1.0, recorder.getFrontEnd().getGainMultiplier(), 1e-9, "the front-end state is that of the recording");
			for (int i = 0; i < 20000; i += 997) {
				assertEquals(signal[i], recorded[i], 1e-6f, "a neutral front end changes nothing, sample " + i);
			}
			SoundSegmenter.Result cut = SoundSegmenter.segment(recorded, recorder.getSampleRate(), 4);
			assertTrue(cut.isAccepted(), "the recording is cut into the same four sounds: " + cut.getRefusal() + ", heard " + cut.getHeard());
			RecordingQuality quality = RecordingQuality.measure(recorded, recorder.getSampleRate(), counter.getHeard(), 4);
			assertEquals(null, quality.getRefusal());
			recorder.discard();
			assertEquals(0, recorder.getLength(), "the recording is dropped");
		} finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}
}
