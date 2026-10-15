package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Test;

/** Spec 013 (T026): the recording lives in memory only, is made without allocating per hop, and carries the front-end state (FR-004, FR-017). */
public class HopRecorderTest {

	private static final int RATE = 48000;
	private static final int HOP = 256;

	private static float[] hop(float value) {
		float[] hop = new float[HOP];
		java.util.Arrays.fill(hop, value);
		return hop;
	}

	@Test
	public void itRecordsTheHopsItIsGivenOnlyBetweenBeginAndEnd() {
		HopRecorder recorder = new HopRecorder();
		recorder.configure(RATE);
		recorder.hop(hop(0.1f), 0, HOP);
		assertEquals(0, recorder.getLength(), "not recording yet");
		recorder.begin(new FrontEndState(1.0, false));
		recorder.hop(hop(0.2f), 0, HOP);
		recorder.hop(hop(0.3f), 0, HOP);
		recorder.end();
		recorder.hop(hop(0.4f), 0, HOP);
		assertEquals(2 * HOP, recorder.getLength());
		float[] samples = recorder.getSamples();
		assertEquals(0.2f, samples[0]);
		assertEquals(0.3f, samples[HOP]);
		assertEquals(RATE, recorder.getSampleRate());
	}

	@Test
	public void aRecordingAskedForBeforeTheRateIsKnownStartsWhenTheEngineAnnouncesIt() {
		HopRecorder recorder = new HopRecorder();
		recorder.begin(new FrontEndState(1.0, false));
		recorder.hop(hop(0.1f), 0, HOP);
		assertEquals(0, recorder.getLength(), "no rate yet, nothing to record into");
		recorder.configure(RATE);
		recorder.hop(hop(0.2f), 0, HOP);
		assertEquals(HOP, recorder.getLength());
		assertEquals(0.2f, recorder.getSamples()[0]);
		assertTrue(recorder.isRecording());
	}

	@Test
	public void itCopiesTheHopBecauseTheCallerReusesItsArray() {
		HopRecorder recorder = new HopRecorder();
		recorder.configure(RATE);
		recorder.begin(new FrontEndState(1.0, false));
		float[] shared = hop(0.5f);
		recorder.hop(shared, 0, HOP);
		java.util.Arrays.fill(shared, 0.9f);
		recorder.hop(shared, 0, HOP);
		float[] samples = recorder.getSamples();
		assertEquals(0.5f, samples[0]);
		assertEquals(0.9f, samples[HOP]);
	}

	@Test
	public void itStopsAtItsCapacityAndSaysSo() {
		HopRecorder recorder = new HopRecorder(100);
		recorder.configure(RATE);
		recorder.begin(new FrontEndState(1.0, false));
		for (int i = 0; i < 100; i++) {
			recorder.hop(hop(0.1f), 0, HOP);
		}
		assertTrue(recorder.isFull());
		assertEquals(RATE / 10, recorder.getLength(), "100 ms at 48 kHz");
	}

	@Test
	public void itReportsTheFrontEndStateAtTheTimeOfTheRecording() {
		HopRecorder recorder = new HopRecorder();
		recorder.configure(RATE);
		FrontEndState state = new FrontEndState(1.5, true);
		recorder.begin(state);
		assertEquals(state, recorder.getFrontEnd());
	}

	@Test
	public void discardingDropsTheRecordingFromMemory() {
		HopRecorder recorder = new HopRecorder();
		recorder.configure(RATE);
		recorder.begin(new FrontEndState(1.0, false));
		recorder.hop(hop(0.3f), 0, HOP);
		recorder.discard();
		assertEquals(0, recorder.getLength());
		assertFalse(recorder.isRecording());
		assertNull(recorder.getFrontEnd());
		assertArrayEquals(new float[0], recorder.getSamples());
	}

	@Test
	public void aHopDoesNotAllocate() {
		HopRecorder recorder = new HopRecorder();
		recorder.configure(RATE);
		recorder.begin(new FrontEndState(1.0, false));
		float[] hop = hop(0.2f);
		for (int i = 0; i < 2000; i++) {
			recorder.hop(hop, 0, HOP);
		}
		com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
		for (int i = 0; i < 5000; i++) {
			recorder.hop(hop, 0, HOP);
		}
		long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
		assertTrue(allocated < 2048, "5000 hops allocated " + allocated + " bytes: nothing per hop");
		assertNotNull(recorder.getSamples());
	}
}
