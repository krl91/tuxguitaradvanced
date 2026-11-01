package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The native device without hardware (spec 022 T018, research R1 addendum and R8, FR-013): its estimate, and a missing library. */
public class NativeMonitorDeviceTest {

	@Test
	public void theEstimateAddsBothDevicesBothBuffersAndTheOutputRingsTwoBlocksAtTheInputRate() {
		// the built-in microphone and speakers of the owner's Mac (research R1 "Measured"), both at 44.1 kHz
		Assertions.assertEquals(1378 + 778 + 4 * 128, NativeMonitorDevice.estimate(1378, 778, 44100f, 44100f, 128));
		// a USB microphone at 48 kHz and the speakers at 44.1 kHz: the output's frames are counted at the input's rate
		Assertions.assertEquals(148 + Math.round(100 * 48000f / 44100f) + 4 * 128, NativeMonitorDevice.estimate(148, 100, 48000f, 44100f, 128));
		// a system that declares nothing: the buffers only
		Assertions.assertEquals(4 * 128, NativeMonitorDevice.estimate(0, 0, 48000f, 48000f, 128));
	}

	@Test
	public void aMissingLibraryGivesAClearReasonAndNeverACrash() {
		NativeMonitorDevice device = new NativeMonitorDevice(() -> "no tuxguitar_monitor_native in java.library.path");
		MonitorDeviceException exception = Assertions.assertThrows(MonitorDeviceException.class, () -> device.open("", -1, "", MonitorEngine.BLOCK_FRAMES));
		Assertions.assertTrue(exception.getMessage().contains("tuxguitar_monitor_native"), exception.getMessage());
	}

	@Test
	public void theEngineFailsWithThatReason() throws InterruptedException {
		List<String> reasons = new ArrayList<String>();
		MonitorEngine engine = new MonitorEngine(new NativeMonitorDevice(() -> "no tuxguitar_monitor_native in java.library.path"), MonitorEngine.BLOCK_FRAMES,
				System::nanoTime, runnable -> {
					Thread thread = new Thread(runnable, "native-missing");
					thread.setDaemon(true);
					thread.start();
				}, Runnable::run);
		engine.addListener((state, reason) -> {
			synchronized (reasons) {
				if( state == MonitorState.FAILED ) {
					reasons.add(reason);
					reasons.notifyAll();
				}
			}
		});
		engine.start("", -1, "");
		synchronized (reasons) {
			long deadline = System.currentTimeMillis() + 5000;
			while( reasons.isEmpty() && System.currentTimeMillis() < deadline ) {
				reasons.wait(100);
			}
		}
		Assertions.assertEquals(MonitorState.FAILED, engine.state());
		Assertions.assertTrue(reasons.get(0).contains("tuxguitar_monitor_native"), reasons.toString());
	}
}
