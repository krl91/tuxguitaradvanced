package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Window;

/**
 * The time to play a note in Follow (spec 016 FR-011, contracts/play-time-window.md): the window opens when the note turns red, the note may be played up
 * to the time to play after it and up to 50 ms before it; the window of a note ends 20 ms before the next one turns red, so that two windows never overlap.
 * All times in nanoseconds, as the session gives them.
 */
public class PlayTimeWindowTest {

	private static final long MS = 1_000_000L;
	private static final long NONE = Long.MAX_VALUE;
	private static final long T = 10_000 * MS;
	private static final long BEAT = 500 * MS;

	private static Window window(int playTimeMillis, long gapBefore, long gapAfter) {
		return new PlayTimeWindow(playTimeMillis).windowFor(T, BEAT, gapBefore, gapAfter);
	}

	@Test
	public void withoutNeighboursTheWindowGoesFromFiftyMillisecondsBeforeTheRedToTheTimeToPlayAfterIt() {
		Window window = window(300, NONE, NONE);
		assertEquals(T - 50 * MS, window.outerFrom());
		assertEquals(T + 300 * MS, window.outerTo());
		assertEquals(T, window.onTimeFrom());
		assertEquals(T + 150 * MS, window.onTimeTo(), "on time for the first half of the time to play");
		assertEquals(T, window.target());
	}

	@Test
	public void earlyBeforeTheRedOnTimeThenLateAndNothingOutside() {
		Window window = window(300, NONE, NONE);
		assertEquals(TimingVerdict.EARLY, window.classify(T - 50 * MS));
		assertEquals(TimingVerdict.EARLY, window.classify(T - 1));
		assertEquals(TimingVerdict.ON_TIME, window.classify(T));
		assertEquals(TimingVerdict.ON_TIME, window.classify(T + 150 * MS));
		assertEquals(TimingVerdict.LATE, window.classify(T + 150 * MS + 1));
		assertEquals(TimingVerdict.LATE, window.classify(T + 300 * MS));
		assertNull(window.classify(T + 300 * MS + 1));
		assertNull(window.classify(T - 50 * MS - 1));
	}

	@Test
	public void notesOneSecondApartKeepTheWholeWindow() {
		Window window = window(300, 1_000 * MS, 1_000 * MS);
		assertEquals(T - 50 * MS, window.outerFrom());
		assertEquals(T + 300 * MS, window.outerTo());
	}

	@Test
	public void notesTwoHundredMillisecondsApartEndTwentyBeforeTheNextRedAndTheNextAdvanceShrinksToTwenty() {
		Window first = window(300, NONE, 200 * MS);
		assertEquals(T + 180 * MS, first.outerTo(), "late 180 ms");
		assertEquals(T + 150 * MS, first.onTimeTo(), "on time up to half the time to play: min(180, 150)");
		Window second = new PlayTimeWindow(300).windowFor(T + 200 * MS, BEAT, 200 * MS, NONE);
		assertEquals(T + 200 * MS - 20 * MS + 1, second.outerFrom(), "the advance is what the 20 ms leave, just after the previous window");
		assertTrue(second.outerFrom() > first.outerTo(), "no overlap");
	}

	@Test
	public void twoConsecutiveWindowsNeverOverlapAndTwentyMillisecondsLieBeforeTheNextRed() {
		for (int p = 100; p <= 600; p += 50) {
			for (long gap = 1 * MS; gap <= 1_200 * MS; gap += 7 * MS) {
				Window first = new PlayTimeWindow(p).windowFor(T, BEAT, NONE, gap);
				Window second = new PlayTimeWindow(p).windowFor(T + gap, BEAT, gap, NONE);
				assertTrue(second.outerFrom() > first.outerTo(), "P " + p + ", gap " + gap / MS + " ms");
				assertTrue(T + gap - first.outerTo() >= 20 * MS || first.outerTo() == T, "20 ms before the next red: P " + p + ", gap " + gap / MS);
				assertTrue(first.outerTo() >= T && second.outerFrom() <= T + gap, "never a window that ends before its red or begins after it");
			}
		}
	}

	@Test
	public void theBeatChangesNothing() {
		PlayTimeWindow policy = new PlayTimeWindow(300);
		Window slow = policy.windowFor(T, 2_000 * MS, 400 * MS, 400 * MS);
		Window fast = policy.windowFor(T, 100 * MS, 400 * MS, 400 * MS);
		Window broken = policy.windowFor(T, 0L, 400 * MS, 400 * MS);
		for (Window window : new Window[] { fast, broken }) {
			assertEquals(slow.outerFrom(), window.outerFrom());
			assertEquals(slow.outerTo(), window.outerTo());
			assertEquals(slow.onTimeTo(), window.onTimeTo());
		}
	}

	@Test
	public void aTimeToPlayOutsideOneHundredToSixHundredIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new PlayTimeWindow(99));
		assertThrows(IllegalArgumentException.class, () -> new PlayTimeWindow(601));
		assertEquals(100, new PlayTimeWindow(100).playTimeMillis());
		assertEquals(600, new PlayTimeWindow(600).playTimeMillis());
	}

	@Test
	public void theTimingToleranceIsAWindowPolicyThatGivesTheSameWindowsAsBefore() {
		TimingTolerance tolerance = TimingTolerance.ofMillis(100);
		WindowPolicy policy = tolerance;
		Window direct = tolerance.windowFor(T, BEAT, 500 * MS, 500 * MS);
		Window through = policy.windowFor(T, BEAT, 500 * MS, 500 * MS);
		assertEquals(direct.outerFrom(), through.outerFrom());
		assertEquals(direct.outerTo(), through.outerTo());
		TimingContext context = new TimingContext(tolerance, LatencyCompensation.none(), new ClockUncertainty(5.0), true, true);
		assertEquals(tolerance, context.windowPolicy(), "the existing constructor: the policy is the tolerance");
		TimingContext timed = new TimingContext(new PlayTimeWindow(300), LatencyCompensation.none(), new ClockUncertainty(5.0), true, true);
		assertTrue(timed.windowPolicy() instanceof PlayTimeWindow);
	}
}
