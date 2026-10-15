package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Spec 013 (T027): the listening mode counts "a sound was played" without saying which, with its own detectors on the hops, stops by itself and gives up on
 * timeouts (FR-004). Time is the time of the samples it was given.
 */
public class SoundCounterTest {

	private static final int RATE = 48000;
	private static final int HOP = 256;

	private static void feed(SoundCounter counter, float[] signal) {
		float[] hop = new float[HOP];
		for (int offset = 0; offset + HOP <= signal.length && counter.getState() == SoundCounter.State.LISTENING; offset += HOP) {
			System.arraycopy(signal, offset, hop, 0, HOP);
			counter.hop(hop, 0, HOP);
		}
	}

	private static SoundCounter counter(int expected) {
		SoundCounter counter = new SoundCounter(expected, RecognitionSettings.defaults());
		counter.configure(RATE);
		return counter;
	}

	private static float[] plucks(double[] seconds, double length) {
		int[] onsets = new int[seconds.length];
		for (int i = 0; i < seconds.length; i++) {
			onsets[i] = (int) (seconds[i] * RATE);
		}
		return SyntheticSignals.repeatedPlucks(110.0, RATE, (int) (length * RATE), onsets, 7L);
	}

	@Test
	public void itStopsOneSecondAfterTheLastExpectedSound() {
		SoundCounter counter = counter(4);
		feed(counter, plucks(new double[] { 1.0, 3.0, 5.0, 7.0 }, 14.0));
		assertEquals(SoundCounter.State.COMPLETE, counter.getState());
		assertEquals(4, counter.getHeard());
		assertTrue(counter.getElapsedMillis() >= 7000 + SoundDetection.STOP_AFTER_LAST_MILLIS, "not before one second after the fourth sound: " + counter.getElapsedMillis());
		assertTrue(counter.getElapsedMillis() <= 7000 + 400 + SoundDetection.STOP_AFTER_LAST_MILLIS + 300, "and not much after: " + counter.getElapsedMillis());
	}

	@Test
	public void itTellsTheListenerEachSoundAsItIsHeard() {
		SoundCounter counter = counter(3);
		List<Integer> told = new ArrayList<Integer>();
		counter.setListener(told::add);
		feed(counter, plucks(new double[] { 1.0, 3.0, 5.0 }, 10.0));
		assertEquals(List.of(1, 2, 3), told);
	}

	@Test
	public void twoAttacksLessThanTheRefractoryTimeApartCountAsOne() {
		SoundCounter counter = counter(2);
		feed(counter, plucks(new double[] { 1.0, 1.10, 3.0 }, 8.0));
		assertEquals(SoundCounter.State.COMPLETE, counter.getState());
		assertEquals(2, counter.getHeard());
	}

	@Test
	public void aSoundPlayedDuringTheLastSecondIsCountedToo() {
		SoundCounter counter = counter(4);
		feed(counter, plucks(new double[] { 1.0, 3.0, 5.0, 7.0, 7.6 }, 12.0));
		assertEquals(SoundCounter.State.COMPLETE, counter.getState());
		assertEquals(5, counter.getHeard(), "one too many: the recording will be refused with the numbers");
	}

	@Test
	public void noSoundForTwentySecondsEndsOnTheSilenceTimeout() {
		SoundCounter counter = counter(4);
		feed(counter, SyntheticSignals.silence(25 * RATE));
		assertEquals(SoundCounter.State.SILENCE_TIMEOUT, counter.getState());
		assertEquals(0, counter.getHeard());
		assertTrue(Math.abs(counter.getElapsedMillis() - SoundDetection.SILENCE_TIMEOUT_MILLIS) < 100, String.valueOf(counter.getElapsedMillis()));
	}

	@Test
	public void theSilenceIsMeasuredFromTheLastSoundNotFromTheStart() {
		SoundCounter counter = counter(4);
		feed(counter, plucks(new double[] { 1.0, 3.0 }, 30.0));
		assertEquals(SoundCounter.State.SILENCE_TIMEOUT, counter.getState());
		assertEquals(2, counter.getHeard());
		assertTrue(counter.getElapsedMillis() > 3000 + SoundDetection.SILENCE_TIMEOUT_MILLIS - 1000, String.valueOf(counter.getElapsedMillis()));
	}

	@Test
	public void threeMinutesInAllEndsOnTheTotalTimeout() {
		SoundCounter counter = counter(100);
		double[] seconds = new double[19];
		for (int i = 0; i < seconds.length; i++) {
			seconds[i] = 5.0 + 10.0 * i;
		}
		feed(counter, plucks(seconds, 200.0));
		assertEquals(SoundCounter.State.TOTAL_TIMEOUT, counter.getState());
		assertTrue(Math.abs(counter.getElapsedMillis() - SoundDetection.TOTAL_TIMEOUT_MILLIS) < 100, String.valueOf(counter.getElapsedMillis()));
	}

	@Test
	public void aCancelEndsAtOnceAndLaterHopsAreIgnored() {
		SoundCounter counter = counter(4);
		float[] signal = plucks(new double[] { 1.0, 3.0, 5.0, 7.0 }, 12.0);
		float[] first = java.util.Arrays.copyOf(signal, 2 * RATE);
		feed(counter, first);
		counter.cancel();
		assertEquals(SoundCounter.State.CANCELLED, counter.getState());
		int heard = counter.getHeard();
		feed(counter, signal);
		assertEquals(heard, counter.getHeard());
	}

	@Test
	public void theHissOfARoomIsNotASound() {
		SoundCounter counter = counter(4);
		feed(counter, SyntheticSignals.noise(25 * RATE, 0.0005, 3L));
		assertEquals(0, counter.getHeard(), "below the input level minimum");
	}

	@Test
	public void aHopDoesNotAllocateInTheCounterItself() {
		SoundCounter counter = counter(1000);
		float[] pluck = plucks(new double[] { 0.1 }, 6.0);
		float[] hop = new float[HOP];
		for (int offset = 0; offset + HOP <= pluck.length; offset += HOP) {
			System.arraycopy(pluck, offset, hop, 0, HOP);
			counter.hop(hop, 0, HOP);
		}
		com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
		int hops = 0;
		for (int round = 0; round < 3; round++) {
			for (int offset = 0; offset + HOP <= pluck.length; offset += HOP) {
				System.arraycopy(pluck, offset, hop, 0, HOP);
				counter.hop(hop, 0, HOP);
				hops++;
			}
		}
		long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
		assertTrue(allocated / hops < 64, hops + " hops allocated " + allocated + " bytes: " + allocated / hops + " per hop");
	}
}
