package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.FakeAudioInput;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.HopRecorder;
import app.tuxguitar.audio.engine.calibration.SoundCounter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T039): the port between the controller and the real engine. Without a device (the fake input writes into the ring buffer), the listeners given to
 * {@code start} receive the hops of the real analysis, and after {@code stop} they receive nothing more and the capture is released.
 */
public class EngineListeningPortTest {

	private static final int RATE = 48000;

	@Test
	public void theListenersReceiveTheHopsOfTheRealAnalysisUntilStop() throws InterruptedException {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		try {
			AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
			int[] held = { 0, 0 };
			EngineListeningPort port = new EngineListeningPort(engine, new EngineListeningPort.CaptureHold() {
				public void acquire(String consumer) {
					held[0]++;
				}

				public void release(String consumer) {
					held[1]++;
				}
			}, Runnable::run, () -> RATE);
			SoundCounter counter = new SoundCounter(4, RecognitionSettings.defaults());
			HopRecorder recorder = new HopRecorder();
			recorder.begin(port.frontEnd());
			port.start(counter, recorder);

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
			assertEquals(SoundCounter.State.COMPLETE, counter.getState());
			assertEquals(4, counter.getHeard());
			port.stop();
			assertEquals(1, held[0], "the capture was held once");
			assertEquals(1, held[1], "and let go of once, after the stop");
			int length = recorder.getLength();
			assertTrue(length > 8 * RATE);
			FakeAudioInput.of(engine).feedRealTime(SyntheticSignals.silence(RATE / 4), RATE); // nobody reads any more: it only has to be written, not consumed
			Thread.sleep(300);
			assertEquals(length, recorder.getLength(), "no hop after stop");
			port.stop(); // a second stop changes nothing
			assertEquals(1, held[1]);
			assertEquals(RATE, port.sampleRate());
		} finally {
			TGThreadManager.getInstance(context).dispose();
		}
	}

	@Test
	public void theFrontEndStateIsTheGainAndTheNoiseReductionInForce() {
		TGContext context = new TGContext();
		AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
		EngineListeningPort port = new EngineListeningPort(engine, Runnable::run, () -> 0);
		FrontEndState state = port.frontEnd();
		assertEquals(engine.getInputGain(), state.getGainMultiplier(), 1e-12);
		assertEquals(engine.isNoiseReductionEnabled(), state.isNoiseReduction());
	}

	@Test
	public void aLostDeviceIsPassedOnToTheListenerGivenToTheController() {
		TGContext context = new TGContext();
		AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
		EngineListeningPort port = new EngineListeningPort(engine, new EngineListeningPort.CaptureHold() {
			public void acquire(String consumer) {
			}

			public void release(String consumer) {
			}
		}, Runnable::run, () -> RATE);
		int[] lost = { 0 };
		port.setDeviceLostListener(() -> lost[0]++);
		port.start();
		port.deviceEvent(app.tuxguitar.audio.engine.input.CaptureService.EventType.DEVICE_LOST);
		port.deviceEvent(app.tuxguitar.audio.engine.input.CaptureService.EventType.CAPTURE_ERROR);
		port.deviceEvent(app.tuxguitar.audio.engine.input.CaptureService.EventType.STARTED);
		assertEquals(2, lost[0], "a device lost or a capture error, nothing else");
		port.stop();
	}
}
