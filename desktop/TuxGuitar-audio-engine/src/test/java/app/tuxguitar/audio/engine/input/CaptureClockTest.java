package app.tuxguitar.audio.engine.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The capture clock (spec 002 T093, research R6c): a read of the capture does not arrive when its samples were taken (measured on the
 * built-in microphone: reads come in bursts, the arrival jitters by up to 65 ms and its lag by 130 ms), so the samples are dated by their
 * count and related to the wall clock by the lower envelope of the arrival lag. Scripted reads, deterministic, no device.
 */
public class CaptureClockTest {

	private static final int RATE = 48000;
	private static final int CHUNK = 1024;
	private static final double CHUNK_MS = 1000.0 * CHUNK / RATE;
	private static final long T0 = 5_000_000_000L;

	/** A run of reads: the n-th read ends at sample (n+1)*CHUNK and arrives at its capture time plus a lag in [minLagMs, minLagMs + jitterMs]. */
	private static void feed(CaptureClock clock, int fromRead, int toRead, double minLagMs, double jitterMs, long seed, double ppm) {
		Random random = new Random(seed);
		for( int n = fromRead ; n < toRead ; n ++ ) {
			long end = (long) (n + 1) * CHUNK;
			double captureNanos = end * 1e9 / RATE * (1.0 + ppm * 1e-6);
			long lag = (long) ((minLagMs + (random.nextDouble() < 0.1 ? 0.0 : random.nextDouble() * jitterMs)) * 1e6);
			clock.onSamples(T0 + (long) captureNanos + lag, end);
		}
	}

	/** The wall time at which sample {@code index} really is taken, plus the smallest lag a read can have. */
	private static double truthMs(long index, double minLagMs) {
		return T0 / 1e6 + index * 1000.0 / RATE + minLagMs;
	}

	@Test
	public void isNotReadyBeforeEnoughReadsAndSaysSo() {
		CaptureClock clock = new CaptureClock(RATE);
		assertFalse(clock.isReady());
		assertEquals(CaptureClock.NOT_READY, clock.wallNanosOf(1000));
		feed(clock, 0, 5, 20.0, 0.0, 1L, 0.0);
		assertFalse(clock.isReady());
		feed(clock, 5, 60, 20.0, 0.0, 1L, 0.0);
		assertTrue(clock.isReady());
	}

	@Test
	public void burstsOfArrivalDoNotMoveTheDateOfASampleByMoreThanOneRead() {
		CaptureClock clock = new CaptureClock(RATE);
		// the measured case: the lag of the arrival spreads over 130 ms
		feed(clock, 0, 1500, 12.0, 130.0, 42L, 0.0);

		for( long index : new long[] { 1500L * CHUNK - 5000, 1500L * CHUNK - 30000, 1500L * CHUNK + 20000 } ) {
			double errorMs = clock.wallNanosOf(index) / 1e6 - truthMs(index, 12.0);
			assertTrue(Math.abs(errorMs) <= CHUNK_MS + 1.0, "the date of sample " + index + " is " + errorMs + " ms off");
		}
		// the arrival time of a single read, used as is, would have been off by up to the spread
		assertTrue(clock.lagSpreadMillis() > 100.0, "the spread of the lag must be reported for the unstable route warning: " + clock.lagSpreadMillis());
	}

	@Test
	public void aSteadyRouteReportsASmallSpread() {
		CaptureClock clock = new CaptureClock(RATE);
		feed(clock, 0, 600, 8.0, 2.0, 7L, 0.0);
		assertTrue(clock.lagSpreadMillis() < 5.0, "" + clock.lagSpreadMillis());
	}

	@Test
	public void theDateIncreasesWithTheSampleIndex() {
		CaptureClock clock = new CaptureClock(RATE);
		feed(clock, 0, 300, 10.0, 60.0, 3L, 0.0);
		long previous = Long.MIN_VALUE;
		for( long index = 1000 ; index < 200000 ; index += 777 ) {
			long wall = clock.wallNanosOf(index);
			assertTrue(wall > previous);
			previous = wall;
		}
	}

	@Test
	public void aSampleClockThatDriftsFromTheWallClockIsFollowed() {
		CaptureClock clock = new CaptureClock(RATE);
		// 50 ppm of drift is 0.5 ms in 10 s: the window of the envelope follows it
		feed(clock, 0, 3000, 10.0, 40.0, 11L, 50.0);
		long index = 3000L * CHUNK - 8000;
		double errorMs = clock.wallNanosOf(index) / 1e6 - (T0 / 1e6 + index * 1000.0 / RATE * (1.0 + 50e-6) + 10.0);
		assertTrue(Math.abs(errorMs) <= CHUNK_MS + 2.0, "drift not followed: " + errorMs + " ms");
	}

	@Test
	public void aRouteWhoseLagGrowsIsFollowedOnceTheOldEnvelopeHasExpired() {
		CaptureClock clock = new CaptureClock(RATE);
		feed(clock, 0, 600, 10.0, 5.0, 5L, 0.0);
		double before = clock.wallNanosOf(500L * CHUNK) / 1e6 - truthMs(500L * CHUNK, 10.0);
		assertTrue(Math.abs(before) <= CHUNK_MS + 1.0);
		// a new route (a buffer that grew, another device): every read now arrives 60 ms later; after the window it is the new envelope
		feed(clock, 600, 2400, 70.0, 5.0, 6L, 0.0);
		long index = 2399L * CHUNK;
		double errorMs = clock.wallNanosOf(index) / 1e6 - truthMs(index, 70.0);
		assertTrue(Math.abs(errorMs) <= CHUNK_MS + 1.0, "the old envelope was kept: " + errorMs + " ms");
	}

	@Test
	public void resettingAtADropOfSamplesGivesTheNewDateAtOnce() {
		CaptureClock clock = new CaptureClock(RATE);
		feed(clock, 0, 800, 10.0, 8.0, 9L, 0.0);
		// the ring buffer dropped half a second of samples: the index no longer follows time, the old envelope is wrong
		clock.reset();
		assertFalse(clock.isReady());
		long skipped = RATE / 2;
		Random random = new Random(13L);
		for( int n = 800 ; n < 900 ; n ++ ) {
			long end = (long) (n + 1) * CHUNK;
			long lag = (long) ((10.0 + random.nextDouble() * 8.0) * 1e6);
			// the samples taken during the gap never reached the index: the index is `skipped` behind real time
			clock.onSamples(T0 + (long) ((end + skipped) * 1e9 / RATE) + lag, end);
		}
		assertTrue(clock.isReady());
		long index = 899L * CHUNK;
		double errorMs = clock.wallNanosOf(index) / 1e6 - (truthMs(index, 10.0) + 1000.0 * skipped / RATE);
		assertTrue(Math.abs(errorMs) <= CHUNK_MS + 8.5, "after the reset: " + errorMs + " ms");
	}

	@Test
	public void aNewStreamStartsAgain() {
		CaptureClock clock = new CaptureClock(RATE);
		feed(clock, 0, 300, 10.0, 5.0, 2L, 0.0);
		assertTrue(clock.isReady());
		clock.configure(44100);
		assertFalse(clock.isReady());
		assertEquals(CaptureClock.NOT_READY, clock.wallNanosOf(1));
	}
}
