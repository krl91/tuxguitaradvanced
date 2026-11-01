package app.tuxguitar.audio.engine.monitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A late input once the sound plays (spec 022, found by the native probe T063): a read that times out writes nothing, so the output keeps its delay. Before,
 * each timed-out read wrote a block of silence on top of the blocks still to come, and the delay grew by a block each time (79 ms against 58 ms estimated at
 * 128 frames on the built-in microphone, which hands its input over in bursts). The first block is still written before any input, so that the output starts.
 */
public class MonitorEngineLateInputTest {

	private static final int BLOCK = 64;

	@Test
	public void aTimedOutReadWritesNothingOnceTheInputHasStarted() throws InterruptedException {
		FakeMonitorDevice device = new FakeMonitorDevice();
		MonitorEngine engine = new MonitorEngine(device, BLOCK, System::nanoTime, runnable -> {
			Thread thread = new Thread(runnable, "monitor-late-input");
			thread.setDaemon(true);
			thread.start();
		}, Runnable::run);
		engine.setChain(null);
		engine.start("", -1, "");
		try {
			Assertions.assertTrue(device.awaitWrites(1, 5000), "the first block is written before any input");
			device.feed(new float[BLOCK]);
			Assertions.assertTrue(device.awaitWrites(2, 5000), "the input block is written");
			int written = device.writes();
			long silent = engine.stats().silentBlocks();
			// the fake device's read times out after 20 ms: ten of them, and no input
			Thread.sleep(250);
			Assertions.assertTrue(engine.stats().silentBlocks() > silent, "the late reads are counted");
			Assertions.assertEquals(written, device.writes(), "a late read adds no block of silence to the output");
			device.feed(new float[BLOCK]);
			Assertions.assertTrue(device.awaitWrites(written + 1, 5000), "the next input block is written");
		} finally {
			engine.stop();
		}
	}
}
