package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.FakeAudioInput;
import app.tuxguitar.audio.engine.RingBufferDrain;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AnalysisService;
import app.tuxguitar.audio.engine.benchmark.MonitorFixtureSet;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The sound heard never feeds the recognition (spec 022 FR-003, SC-002; task T009). Each recorded guitar input goes through the real
 * analysis twice: once as today, and once while a monitor engine plays the same recording through a ready-made chain on a fake duplex
 * device, in the same process (the sound has its own input, research R1 addendum). The analysis frames (level, pitch, onset) must be the
 * same, frame by frame.
 */
public class RecognitionUntouchedTest {

	private static List<String> frames(float[] samples, ReadyMadeChains.Named chain) throws Exception {
		TGContext context = new TGContext();
		TGThreadManager.getInstance(context).setThreadHandler(new TGMultiThreadHandler());
		AudioEngine engine = FakeAudioInput.newEngine(context, RecognitionSettings.defaults());
		List<String> frames = Collections.synchronizedList(new ArrayList<String>());
		AnalysisService.Listener listener = frame -> frames.add(signature(frame));
		engine.subscribe(listener);
		MonitorEngine monitor = null;
		try {
			FakeMonitorDevice device = null;
			if( chain != null ) {
				device = new FakeMonitorDevice();
				device.rate = 48000f;
				device.play(samples);
				monitor = new MonitorEngine(device, MonitorEngine.BLOCK_FRAMES, System::nanoTime, runnable -> {
					Thread thread = new Thread(runnable, "monitor-identity");
					thread.setDaemon(true);
					thread.start();
				}, Runnable::run);
				monitor.setChain(chain.chain());
				monitor.start("", -1, "");
			}
			FakeAudioInput.of(engine).feedAll(samples);
			long deadline = System.currentTimeMillis() + 60000;
			int seen = -1;
			while( System.currentTimeMillis() < deadline ) {
				Thread.sleep(100);
				int now = frames.size();
				if( now == seen && RingBufferDrain.drained(engine) && (device == null || device.recordingDone()) ) {
					break;
				}
				seen = now;
			}
		} finally {
			engine.unsubscribe(listener);
			if( monitor != null ) {
				monitor.stop();
			}
		}
		synchronized (frames) {
			return new ArrayList<String>(frames);
		}
	}

	private static String signature(AnalysisFrame frame) {
		return frame.getCaptureSampleEnd() + " " + frame.getRmsDbfs() + " " + frame.getPeakDbfs() + " " + frame.getClippedRatio() + " "
				+ frame.getLevelState() + " " + frame.getF0Hz() + " " + frame.getConfidence() + " " + frame.isOnset();
	}

	@Test
	public void theAnalysisIsTheSameWithTheSoundOffAndOn() throws Exception {
		List<MonitorFixtureSet.Input> inputs = MonitorFixtureSet.recognitionInputs();
		Assertions.assertEquals(16, inputs.size(), "the 15 references of the manifest and the silence");
		for( MonitorFixtureSet.Input input : inputs ) {
			float[] samples = input.samples();
			List<String> today = frames(samples, null);
			Assertions.assertTrue(today.size() > 100, input.name() + ": " + today.size() + " frames");
			for( ReadyMadeChains.Named chain : ReadyMadeChains.all() ) {
				List<String> monitored = frames(samples, chain);
				Assertions.assertEquals(today.size(), monitored.size(), input.name() + " with " + chain.name() + ": frame count");
				for( int i = 0 ; i < today.size() ; i++ ) {
					Assertions.assertEquals(today.get(i), monitored.get(i), input.name() + " with " + chain.name() + ": frame " + i);
				}
			}
		}
	}
}
