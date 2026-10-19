package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationFlow.State;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 013 (T052, FR-020, FR-017): the diagnostic copy. With a sink and a ticked box the recording is given to the sink once, at the quality answer; in every
 * other case (no tick, no sink, a refusal, a stop, a cancel) the sink is never called; a sink that fails is reported and the calibration goes on; the samples
 * are not kept after the answer.
 */
public class CalibrationControllerDiagnosticTest {

	private static final int RATE = 48000;
	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);

	private static final class Port implements CalibrationController.ListeningPort {
		float[] signal;
		boolean stopRequested;

		public void start(HopListener... listeners) {
			this.stopRequested = false;
			for (HopListener listener : listeners) {
				listener.configure(RATE);
			}
			float[] hop = new float[256];
			for (int offset = 0; offset + 256 <= this.signal.length && !this.stopRequested; offset += 256) {
				System.arraycopy(this.signal, offset, hop, 0, 256);
				for (HopListener listener : listeners) {
					listener.hop(hop, 0, 256);
				}
			}
		}

		public void stop() {
			this.stopRequested = true;
		}

		public FrontEndState frontEnd() {
			return new FrontEndState(1.0, false);
		}

		public int sampleRate() {
			return RATE;
		}
	}

	private static final class Tuner implements CalibrationController.TunerPort {
		public boolean available() {
			return true;
		}

		public void open() {
		}

		public void setClosedListener(Runnable listener) {
		}
	}

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static final class Sink implements CalibrationController.DiagnosticSink {
		final List<float[]> given = new ArrayList<float[]>();
		final List<Integer> rates = new ArrayList<Integer>();
		String path = "/tmp/take.wav";

		public String write(float[] samples, int sampleRate) {
			this.given.add(samples);
			this.rates.add(sampleRate);
			return this.path;
		}
	}

	private static float[] fourSounds() {
		int[] onsets = { RATE, 3 * RATE, 5 * RATE, 7 * RATE };
		float[] signal = SyntheticSignals.repeatedPlucks(110.0, RATE, 10 * RATE, onsets, 7L);
		float max = 0f;
		for (float value : signal) {
			max = Math.max(max, Math.abs(value));
		}
		for (int i = 0; i < signal.length; i++) {
			signal[i] *= 0.5f / max;
		}
		return signal;
	}

	private static CalibrationController controller(float[] signal, CalibrationController.DiagnosticSink sink) {
		Port port = new Port();
		port.signal = signal;
		CalibrationController controller = new CalibrationController(4, KEY, new ProfileLibrary(new Memory()), RecognitionSettings::defaults, port, new Tuner(),
				(segments, rate) -> settings -> new CalibrationScore(4, 0, new RecognitionRate(4, 10, 0, 0)), Runnable::run, () -> 1760000000000L, snapshot -> {
				});
		if (sink != null) {
			controller.setDiagnosticSink(sink);
		}
		return controller;
	}

	private static void toQuality(CalibrationController controller) {
		controller.calibrate();
		controller.answerTuning(true);
		controller.ok();
		controller.ok();
		assertEquals(State.QUALITY, controller.getSnapshot().getState());
	}

	@Test
	public void aTickedBoxGivesTheRecordingToTheSinkOnceAtTheAnswerAndTheSnapshotCarriesThePath() {
		Sink sink = new Sink();
		CalibrationController controller = controller(fourSounds(), sink);
		toQuality(controller);
		assertTrue(sink.given.isEmpty(), "nothing is written before the answer");
		controller.answerQuality(true, true);
		assertEquals(1, sink.given.size());
		assertEquals(RATE, (int) sink.rates.get(0));
		assertTrue(sink.given.get(0).length > 7 * RATE, "the whole recording, not only the sounds");
		assertEquals("/tmp/take.wav", controller.getSnapshot().getDiagnosticPath());
		assertFalse(controller.getSnapshot().isDiagnosticFailed());
		assertFalse(controller.isRecordingHeld(), "the recording is dropped once the analysis is done");
	}

	@Test
	public void theCopyIsAlsoWrittenWhenThePlayerAnswersNo() {
		Sink sink = new Sink();
		CalibrationController controller = controller(fourSounds(), sink);
		toQuality(controller);
		controller.answerQuality(false, true);
		assertEquals(1, sink.given.size());
		assertEquals(State.REFUSED, controller.getSnapshot().getState());
		assertEquals("/tmp/take.wav", controller.getSnapshot().getDiagnosticPath());
		assertFalse(controller.isRecordingHeld());
	}

	@Test
	public void anUntickedBoxTheOldAnswerOrNoSinkNeverWritesAnything() {
		Sink sink = new Sink();
		CalibrationController unticked = controller(fourSounds(), sink);
		toQuality(unticked);
		unticked.answerQuality(true, false);
		CalibrationController old = controller(fourSounds(), sink);
		toQuality(old);
		old.answerQuality(true);
		CalibrationController noSink = controller(fourSounds(), null);
		toQuality(noSink);
		noSink.answerQuality(true, true);
		assertTrue(sink.given.isEmpty());
		assertNull(unticked.getSnapshot().getDiagnosticPath());
		assertNull(noSink.getSnapshot().getDiagnosticPath());
	}

	@Test
	public void aRefusalAStopAndACancelNeverWriteAnything() {
		Sink sink = new Sink();
		CalibrationController silent = controller(new float[10 * RATE], sink);
		silent.calibrate();
		silent.answerTuning(true);
		silent.ok();
		silent.ok();
		silent.stop();
		CalibrationController stopped = controller(fourSounds(), sink);
		toQuality(stopped);
		stopped.cancel();
		assertTrue(sink.given.isEmpty());
		assertFalse(stopped.isRecordingHeld(), "a cancelled calibration keeps nothing");
	}

	@Test
	public void aSinkThatFailsIsReportedAndTheCalibrationGoesOn() {
		Sink sink = new Sink();
		sink.path = null;
		CalibrationController controller = controller(fourSounds(), sink);
		toQuality(controller);
		controller.answerQuality(true, true);
		assertEquals(1, sink.given.size());
		assertNull(controller.getSnapshot().getDiagnosticPath());
		assertTrue(controller.getSnapshot().isDiagnosticFailed());
		// the fake scorer gives 40 %, under the mark: the analysis went on to the three-choice message
		assertEquals(State.CHOICE, controller.getSnapshot().getState(), "the analysis went on");
	}
}
