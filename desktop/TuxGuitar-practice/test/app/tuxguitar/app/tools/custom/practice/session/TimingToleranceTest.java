package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Preset;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Window;

/**
 * The windows around the target time of an event (spec 002 T088, T094): a fraction of the local beat with a floor and a ceiling in milliseconds,
 * for the three presets the learner chooses from. The numbers are provisional until the benchmark T101: the test that pins them is a reminder
 * that changing one is a decision.
 */
public class TimingToleranceTest {

	private static final long MS = 1_000_000L;
	private static final long NONE = Long.MAX_VALUE;

	private static Window window(Preset preset, double beatMillis) {
		return new TimingTolerance(preset).windowFor(10_000 * MS, Math.round(beatMillis * MS), NONE, NONE);
	}

	private static double onHalf(Window w) {
		return (w.onTimeTo() - w.target()) / 1e6;
	}

	private static double outerHalf(Window w) {
		return (w.outerTo() - w.target()) / 1e6;
	}

	@Test
	public void theProvisionalValuesAreThoseOfTheDocumentation() {
		// at 120 beats a minute (a beat of 500 ms): half-width of the tight window, then of the outer window
		assertEquals(50.0, onHalf(window(Preset.STRICT, 500)), 1e-6);
		assertEquals(125.0, outerHalf(window(Preset.STRICT, 500)), 1e-6);
		assertEquals(75.0, onHalf(window(Preset.NORMAL, 500)), 1e-6);
		assertEquals(200.0, outerHalf(window(Preset.NORMAL, 500)), 1e-6);
		assertEquals(125.0, onHalf(window(Preset.LENIENT, 500)), 1e-6);
		assertEquals(300.0, outerHalf(window(Preset.LENIENT, 500)), 1e-6);
	}

	@Test
	public void theWindowsScaleWithTheBeatSoThePresetWorksAt60And180BeatsAMinute() {
		// 60 beats a minute: a beat of 1000 ms; the tight window reaches its ceiling
		Window slow = window(Preset.NORMAL, 1000);
		assertEquals(150.0, onHalf(slow), 1e-6);
		assertEquals(400.0, outerHalf(slow), 1e-6);
		// 180 beats a minute: a beat of 333.3 ms; the tight window falls to its floor
		Window fast = window(Preset.NORMAL, 1000.0 / 3.0);
		assertEquals(60.0, onHalf(fast), 1e-6);
		assertEquals(150.0, outerHalf(fast), 1e-6);
		// in between the fraction decides: 120 beats a minute gives 15 % of 500 ms
		assertEquals(75.0, onHalf(window(Preset.NORMAL, 500)), 1e-6);
		// the window grows with the beat between the floor and the ceiling
		assertTrue(onHalf(window(Preset.NORMAL, 700)) > onHalf(window(Preset.NORMAL, 500)));
	}

	@Test
	public void strictIsTighterThanNormalWhichIsTighterThanLenientAtEveryTempo() {
		for( double beat : new double[] { 200, 333, 500, 750, 1000, 2000 } ) {
			Window strict = window(Preset.STRICT, beat);
			Window normal = window(Preset.NORMAL, beat);
			Window lenient = window(Preset.LENIENT, beat);
			assertTrue(onHalf(strict) <= onHalf(normal) && onHalf(normal) <= onHalf(lenient), "tight windows at " + beat);
			assertTrue(outerHalf(strict) <= outerHalf(normal) && outerHalf(normal) <= outerHalf(lenient), "outer windows at " + beat);
			assertTrue(onHalf(normal) <= outerHalf(normal), "the tight window is inside the outer one at " + beat);
		}
	}

	@Test
	public void extremeBeatsStayBetweenTheFloorAndTheCeiling() {
		// a beat of 1 ms and of 0 (a broken tempo): the floors
		for( double beat : new double[] { 0, 1, 10 } ) {
			Window w = window(Preset.NORMAL, beat);
			assertEquals(60.0, onHalf(w), 1e-6, "floor at a beat of " + beat + " ms");
			assertEquals(150.0, outerHalf(w), 1e-6);
		}
		// a beat of 6 s (10 beats a minute) and of an hour: the ceilings, not a window of seconds
		for( double beat : new double[] { 6000, 3_600_000 } ) {
			Window w = window(Preset.NORMAL, beat);
			assertEquals(150.0, onHalf(w), 1e-6, "ceiling at a beat of " + beat + " ms");
			assertEquals(450.0, outerHalf(w), 1e-6);
		}
		assertThrows(IllegalArgumentException.class, () -> new TimingTolerance(Preset.NORMAL).windowFor(0, -1, NONE, NONE));
	}

	@Test
	public void theExactLimitsOfTheWindowsAreInclusive() {
		Window w = window(Preset.NORMAL, 500);
		long target = w.target();
		// tight window: [target - 75 ms, target + 75 ms], the two limits included
		assertEquals(TimingVerdict.ON_TIME, w.classify(target - 75 * MS));
		assertEquals(TimingVerdict.ON_TIME, w.classify(target));
		assertEquals(TimingVerdict.ON_TIME, w.classify(target + 75 * MS));
		// just outside: early or late, up to the limits of the outer window, included
		assertEquals(TimingVerdict.EARLY, w.classify(target - 75 * MS - 1));
		assertEquals(TimingVerdict.LATE, w.classify(target + 75 * MS + 1));
		assertEquals(TimingVerdict.EARLY, w.classify(target - 200 * MS));
		assertEquals(TimingVerdict.LATE, w.classify(target + 200 * MS));
		// beyond the outer window it matches nothing
		assertNull(w.classify(target - 200 * MS - 1));
		assertNull(w.classify(target + 200 * MS + 1));
	}

	@Test
	public void theOutcomeCarriesTheSignedOffsetOfTheOnset() {
		Window w = window(Preset.NORMAL, 500);
		assertEquals(-120 * MS, w.offsetNanos(w.target() - 120 * MS));
		assertEquals(0, w.offsetNanos(w.target()));
		assertEquals(90 * MS, w.offsetNanos(w.target() + 90 * MS));
	}

	@Test
	public void twoNeighbouringWindowsNeverOverlapAndLeaveNoGap() {
		// eight notes a second at 120 beats a minute: the events are 125 ms apart, the outer window of a normal preset (200 ms) is cut
		TimingTolerance normal = new TimingTolerance(Preset.NORMAL);
		long first = 5_000 * MS;
		long gap = 125 * MS;
		Window a = normal.windowFor(first, 500 * MS, NONE, gap);
		Window b = normal.windowFor(first + gap, 500 * MS, gap, gap);
		Window c = normal.windowFor(first + 2 * gap, 500 * MS, gap, NONE);

		assertTrue(a.outerTo() < b.outerFrom(), "the windows of two events overlap");
		assertEquals(a.outerTo() + 1, b.outerFrom(), "a nanosecond between two windows belongs to nobody");
		assertEquals(b.outerTo() + 1, c.outerFrom());
		// the tight window never goes beyond the cut outer one
		assertTrue(b.onTimeFrom() >= b.outerFrom() && b.onTimeTo() <= b.outerTo());
		// an odd gap is cut without losing a nanosecond
		Window d = normal.windowFor(0, 500 * MS, NONE, 1001);
		Window e = normal.windowFor(1001, 500 * MS, 1001, NONE);
		assertEquals(d.outerTo() + 1, e.outerFrom());
	}

	@Test
	public void aWindowWithoutNeighboursIsNotCut() {
		Window alone = new TimingTolerance(Preset.NORMAL).windowFor(0, 500 * MS, NONE, NONE);
		assertEquals(-200 * MS, alone.outerFrom());
		assertEquals(200 * MS, alone.outerTo());
	}

	@Test
	public void theWindowDoesNotDependOnTheLatencyOfTheRoute() {
		// the tolerance is the learner's choice: it has no parameter for a latency, a device or a clock
		for( java.lang.reflect.Method method : TimingTolerance.class.getMethods() ) {
			for( Class<?> parameter : method.getParameterTypes() ) {
				assertTrue(parameter != String.class, method + " takes a name");
			}
		}
	}

	// ---------------------------------------------------------------- the adjustable window (spec 012): one width W in milliseconds at 120 beats a minute

	private static Window wide(int widthMillis, double beatMillis) {
		return TimingTolerance.ofMillis(widthMillis).windowFor(10_000 * MS, Math.round(beatMillis * MS), NONE, NONE);
	}

	@Test
	public void aWidthOfSeventyFiveMillisecondsIsExactlyTheOldNormalAtEveryTempoAndGap() {
		TimingTolerance old = new TimingTolerance(Preset.NORMAL);
		TimingTolerance now = TimingTolerance.ofMillis(75);
		long[] gaps = { NONE, 100 * MS, 150 * MS, 400 * MS };
		for (long beat = 0; beat <= 2000; beat += (beat < 30 ? 30 : 10)) {
			for (long before : gaps) {
				for (long after : gaps) {
					Window a = old.windowFor(10_000 * MS, beat * MS, before, after);
					Window b = now.windowFor(10_000 * MS, beat * MS, before, after);
					String at = "beat " + beat + " ms, gaps " + before + " and " + after;
					assertEquals(a.onTimeFrom(), b.onTimeFrom(), at);
					assertEquals(a.onTimeTo(), b.onTimeTo(), at);
					assertEquals(a.outerFrom(), b.outerFrom(), at);
					assertEquals(a.outerTo(), b.outerTo(), at);
				}
			}
		}
	}

	@Test
	public void theTightHalfWindowAtOneHundredAndTwentyBeatsAMinuteIsTheWidthChosen() {
		assertEquals(40.0, onHalf(wide(40, 500)), 1e-6);
		assertEquals(75.0, onHalf(wide(75, 500)), 1e-6);
		assertEquals(100.0, onHalf(wide(100, 500)), 1e-6);
		assertEquals(200.0, onHalf(wide(200, 500)), 1e-6);
	}

	@Test
	public void aNoteNinetyMillisecondsLateIsOnTimeWithTheNewDefaultAndLateWithTheOldNormal() {
		long late = 90 * MS;
		Window newDefault = wide(100, 500);
		Window oldNormal = wide(75, 500);
		assertEquals(TimingVerdict.ON_TIME, newDefault.classify(newDefault.target() + late));
		assertEquals(TimingVerdict.LATE, oldNormal.classify(oldNormal.target() + late));
	}

	@Test
	public void aWiderSettingNeverNarrowsTheWindowAtAnyTempo() {
		for (double beat : new double[] { 200, 333, 500, 700, 1000, 1500 }) {
			double previousOn = 0;
			double previousOuter = 0;
			for (int width = 40; width <= 200; width += 10) {
				Window w = wide(width, beat);
				assertTrue(onHalf(w) >= previousOn, "tight window shrank at " + width + " ms, beat " + beat);
				assertTrue(outerHalf(w) >= previousOuter, "outer window shrank at " + width + " ms, beat " + beat);
				previousOn = onHalf(w);
				previousOuter = outerHalf(w);
			}
		}
	}

	@Test
	public void theWindowsOfTwoCloseEventsNeverOverlapEvenAtTheWidestSetting() {
		long gap = 150 * MS;
		Window first = TimingTolerance.ofMillis(200).windowFor(10_000 * MS, 500 * MS, NONE, gap);
		Window second = TimingTolerance.ofMillis(200).windowFor(10_000 * MS + gap, 500 * MS, gap, NONE);
		assertTrue(first.outerTo() <= second.outerFrom(), "the outer windows overlap: " + first.outerTo() + " against " + second.outerFrom());
	}

	@Test
	public void aWidthOutsideTheRangeIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> TimingTolerance.ofMillis(39));
		assertThrows(IllegalArgumentException.class, () -> TimingTolerance.ofMillis(201));
	}
}
