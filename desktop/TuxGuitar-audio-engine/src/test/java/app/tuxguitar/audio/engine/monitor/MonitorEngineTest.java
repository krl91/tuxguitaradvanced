package app.tuxguitar.audio.engine.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The monitor engine on a fake duplex device (spec 022, contracts/monitor-engine.md, research R1 addendum; task T007). */
public class MonitorEngineTest {

	private static final int RATE = 8000;
	private static final int BLOCK = 64;

	static final class Recorder implements MonitorEngine.Listener {
		final List<String> events = new ArrayList<String>();
		final List<MonitorNotice> notices = new ArrayList<MonitorNotice>();

		@Override
		public synchronized void stateChanged(MonitorState state, String reason) {
			this.events.add(state + (reason == null ? "" : ":" + reason));
			notifyAll();
		}

		@Override
		public synchronized void notice(MonitorNotice notice) {
			this.notices.add(notice);
			notifyAll();
		}

		synchronized List<MonitorNotice> notices() {
			return new ArrayList<MonitorNotice>(this.notices);
		}

		synchronized List<String> events() {
			return new ArrayList<String>(this.events);
		}

		synchronized boolean await(String prefix, long timeoutMillis) throws InterruptedException {
			long deadline = System.currentTimeMillis() + timeoutMillis;
			while( true ) {
				for( String event : this.events ) {
					if( event.startsWith(prefix) ) {
						return true;
					}
				}
				long left = deadline - System.currentTimeMillis();
				if( left <= 0 ) {
					return false;
				}
				wait(left);
			}
		}
	}

	private static MonitorEngine engine(MonitorDevice device, Recorder recorder) {
		return engine(device, recorder, System::nanoTime);
	}

	private static MonitorEngine engine(MonitorDevice device, Recorder recorder, java.util.function.LongSupplier clock) {
		MonitorEngine engine = new MonitorEngine(device, BLOCK, clock, runnable -> {
			Thread thread = new Thread(runnable, "monitor-test");
			thread.setDaemon(true);
			thread.start();
		}, Runnable::run);
		engine.addListener(recorder);
		return engine;
	}

	private static float[] sine(int offset, int frames, double amplitude) {
		float[] block = new float[frames];
		for( int i = 0 ; i < frames ; i++ ) {
			block[i] = (float) (amplitude * Math.sin(2 * Math.PI * 440 * (offset + i) / RATE));
		}
		return block;
	}

	/** Feeds one input block and lets the engine's thread write exactly once more. */
	private static void step(MonitorEngine engine, FakeMonitorDevice device, float[] block) throws InterruptedException {
		int before = device.writes();
		device.feed(block);
		device.grant(1);
		Assertions.assertTrue(device.awaitWrites(before + 1, 5000), "the output thread wrote the block");
	}

	@Test
	public void theStatesGoFromOffToOnAndBackToOff() throws InterruptedException {
		FakeMonitorDevice device = FakeMonitorDevice.gated();
		Recorder recorder = new Recorder();
		MonitorEngine engine = engine(device, recorder);
		engine.setChain(null);
		Assertions.assertEquals(MonitorState.OFF, engine.state());
		engine.start("Guitar interface", -1, "Headphones");
		Assertions.assertTrue(device.awaitWrites(1, 5000), "a first (silent) block was written");
		Assertions.assertEquals("Guitar interface", device.lastInput());
		Assertions.assertEquals("Headphones", device.lastOutput());
		Assertions.assertEquals(MonitorState.STARTING, engine.state());
		for( int b = 0 ; b < RATE / BLOCK ; b++ ) {
			step(engine, device, sine(b * BLOCK, BLOCK, 0.1));
		}
		Assertions.assertEquals(MonitorState.ON, engine.state(), "on once the half-second fade in is over");
		engine.stop();
		for( int b = 0 ; b < 10 && engine.state() != MonitorState.OFF ; b++ ) {
			device.feed(sine(0, BLOCK, 0.1));
			device.grant(1);
			recorder.await(MonitorState.OFF.name(), 200);
		}
		Assertions.assertTrue(recorder.await(MonitorState.OFF.name(), 5000));
		Assertions.assertEquals(MonitorState.OFF, engine.state());
		List<String> events = recorder.events();
		Assertions.assertEquals(MonitorState.STARTING.name(), events.get(0));
		Assertions.assertEquals(MonitorState.ON.name(), events.get(1));
		Assertions.assertEquals(MonitorState.STOPPING.name(), events.get(2));
		Assertions.assertEquals(MonitorState.OFF.name(), events.get(3));
	}

	@Test
	public void aDryChainPassesTheInputThroughTheOutputStageOnBothChannels() throws InterruptedException {
		FakeMonitorDevice device = FakeMonitorDevice.gated();
		MonitorEngine engine = engine(device, new Recorder());
		engine.setChain(null);
		engine.setGuitarVolume(1.0);
		engine.start("", -1, "");
		Assertions.assertTrue(device.awaitWrites(1, 5000));
		int first = device.writes();
		int blocks = RATE / BLOCK + 20;
		for( int b = 0 ; b < blocks ; b++ ) {
			step(engine, device, sine(b * BLOCK, BLOCK, 0.1));
		}
		for( int b = blocks - 10 ; b < blocks ; b++ ) {
			float[] expected = sine(b * BLOCK, BLOCK, 0.1);
			float[] left = device.left(first + b);
			float[] right = device.right(first + b);
			for( int i = 0 ; i < BLOCK ; i++ ) {
				Assertions.assertEquals(expected[i], left[i], 1e-6f, "block " + b + " sample " + i);
				Assertions.assertEquals(left[i], right[i], 0f, "both channels equal");
			}
		}
		engine.stop();
		device.grant(1000);
	}

	@Test
	public void aChainSetFromAnotherThreadIsPickedUpAtTheNextBlock() throws Exception {
		FakeMonitorDevice device = FakeMonitorDevice.gated();
		MonitorEngine engine = engine(device, new Recorder());
		engine.setChain(null);
		engine.start("", -1, "");
		Assertions.assertTrue(device.awaitWrites(1, 5000));
		step(engine, device, sine(0, BLOCK, 0.1));
		Assertions.assertNull(engine.appliedChain());
		Thread other = new Thread(() -> engine.setChain(ReadyMadeChains.LEAD));
		other.start();
		other.join();
		step(engine, device, sine(BLOCK, BLOCK, 0.1));
		step(engine, device, sine(2 * BLOCK, BLOCK, 0.1));
		Assertions.assertEquals(ReadyMadeChains.LEAD, engine.appliedChain());
		engine.stop();
		device.grant(1000);
	}

	@Test
	public void aDeviceThatRefusesToOpenGivesFailedAndStartNeverThrows() throws InterruptedException {
		FakeMonitorDevice device = new FakeMonitorDevice();
		device.refuse = true;
		Recorder recorder = new Recorder();
		MonitorEngine engine = engine(device, recorder);
		Assertions.assertDoesNotThrow(() -> engine.start("", -1, "Nowhere"));
		Assertions.assertTrue(recorder.await(MonitorState.FAILED.name(), 5000));
		Assertions.assertEquals(MonitorState.FAILED, engine.state());
		Assertions.assertTrue(recorder.events().contains("FAILED:format refused by the fake device"), recorder.events().toString());
	}

	@Test
	public void aDeviceLostWhileOnGivesFailedWithAMessageAndStartingAgainWorks() throws InterruptedException {
		FakeMonitorDevice device = FakeMonitorDevice.gated();
		Recorder recorder = new Recorder();
		MonitorEngine engine = engine(device, recorder);
		engine.start("", -1, "");
		Assertions.assertTrue(device.awaitWrites(1, 5000));
		for( int b = 0 ; b < RATE / BLOCK + 2 ; b++ ) {
			step(engine, device, sine(b * BLOCK, BLOCK, 0.1));
		}
		Assertions.assertEquals(MonitorState.ON, engine.state());
		device.loseAfterWrites = device.writes();
		device.feed(sine(0, BLOCK, 0.1));
		device.grant(1);
		Assertions.assertTrue(recorder.await("FAILED", 5000));
		Assertions.assertTrue(recorder.events().contains("FAILED:the fake device was unplugged"), recorder.events().toString());
		Assertions.assertEquals(MonitorState.FAILED, engine.state());
		// the learner chooses an output again
		device.loseAfterWrites = -1;
		int writes = device.writes();
		int startings = count(recorder.events(), "STARTING");
		engine.start("", -1, "Speakers");
		Assertions.assertTrue(device.awaitWrites(writes + 1, 5000));
		Assertions.assertEquals(startings + 1, count(recorder.events(), "STARTING"));
		for( int b = 0 ; b < RATE / BLOCK + 2 ; b++ ) {
			step(engine, device, sine(b * BLOCK, BLOCK, 0.1));
		}
		Assertions.assertEquals(MonitorState.ON, engine.state());
		Assertions.assertEquals("Speakers", device.lastOutput());
		engine.stop();
		device.grant(1000);
	}

	private static int count(List<String> events, String name) {
		int count = 0;
		for( String event : events ) {
			if( event.equals(name) ) {
				count++;
			}
		}
		return count;
	}

	@Test
	public void statsCountDroppedAndSilentBlocks() throws InterruptedException {
		FakeMonitorDevice device = FakeMonitorDevice.gated();
		MonitorEngine engine = engine(device, new Recorder());
		engine.start("", -1, "");
		Assertions.assertTrue(device.awaitWrites(1, 5000));
		Assertions.assertTrue(engine.stats().silentBlocks() >= 1, "the first block, with no input yet, was silent");
		// the engine's thread is held: fed blocks pile up in the device and the oldest are dropped
		for( int i = 0 ; i < 20 ; i++ ) {
			device.feed(sine(i * BLOCK, BLOCK, 0.1));
		}
		device.grant(1);
		Assertions.assertTrue(device.awaitWrites(2, 5000));
		device.grant(1);
		Assertions.assertTrue(device.awaitWrites(3, 5000));
		Assertions.assertEquals(18, engine.stats().droppedBlocks(), "two blocks wait at most, as in the native rings");
		Assertions.assertEquals(256, device.latencyFrames);
		Assertions.assertEquals((BLOCK + 256) * 1000.0 / RATE, engine.stats().estimatedDelayMillis(), 1e-6, "the device's estimate (both devices and buffers) plus one processing block");
		engine.stop();
		device.grant(1000);
	}

	@Test
	public void aClippedInputBlockIsCountedAndToldAtMostOncePerSecond() throws InterruptedException {
		FakeMonitorDevice device = FakeMonitorDevice.gated();
		Recorder recorder = new Recorder();
		AtomicLong now = new AtomicLong(1_000_000_000L);
		MonitorEngine engine = engine(device, recorder, now::get);
		engine.start("", -1, "");
		Assertions.assertTrue(device.awaitWrites(1, 5000));
		float[] loud = sine(0, BLOCK, 1.0);
		loud[10] = 0.999f;
		for( int i = 0 ; i < 5 ; i++ ) {
			step(engine, device, loud);
		}
		step(engine, device, sine(0, BLOCK, 0.5));
		Assertions.assertEquals(5, engine.stats().clippedInputBlocks());
		Assertions.assertEquals(1, recorder.notices().size());
		now.addAndGet(1_100_000_000L);
		step(engine, device, loud);
		step(engine, device, sine(0, BLOCK, 0.5));
		Assertions.assertEquals(2, recorder.notices().size());
		Assertions.assertEquals(MonitorNotice.INPUT_CLIPPING, recorder.notices().get(1));
		engine.stop();
		device.grant(1000);
	}
}
