package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.TimingContext.PrecisionReason;
import app.tuxguitar.app.tools.custom.practice.session.TimingContext.Reason;
import app.tuxguitar.app.tools.custom.practice.session.TimingContext.RhythmPrecision;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Preset;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Window;

/**
 * The three separate things of the timing (spec 002 T094, owner's decision of 2026-10-07): the tolerance the learner chose, the latency
 * compensation of the route and the uncertainty of the clocks are never added or merged silently; and no frame received before the clocks are
 * ready can give an EARLY, LATE, MISSED, EXTRA (or WRONG) verdict.
 */
public class TimingContextTest {

	private static final long MS = 1_000_000L;
	private static final long TARGET = 20_000 * MS;
	private static final TimingTolerance NORMAL = new TimingTolerance(Preset.NORMAL);

	private static Window window() {
		return NORMAL.windowFor(TARGET, 500 * MS, Long.MAX_VALUE, Long.MAX_VALUE);
	}

	private static TimingContext context(LatencyCompensation compensation, double spreadMillis, boolean captureReady, boolean playerReady) {
		return new TimingContext(NORMAL, compensation, new ClockUncertainty(spreadMillis), captureReady, playerReady);
	}

	private static TimingContext ready(LatencyCompensation compensation) {
		return context(compensation, 5.0, true, true);
	}

	// ------------------------------------------------------------------ the latency compensation

	@Test
	public void noCalibrationIsAnOffsetOfZeroAndSaysSo() {
		LatencyCompensation none = LatencyCompensation.none();
		assertEquals(0L, none.offsetNanos());
		assertFalse(none.isCalibrated());
		assertEquals(LatencyCompensation.Source.NONE, none.source());
		assertEquals(1234L, none.adjust(1234L));
	}

	@Test
	public void theOffsetIsSubtractedFromTheObservedTimeWhateverItsSign() {
		LatencyCompensation positive = LatencyCompensation.calibrated(120.0);
		assertTrue(positive.isCalibrated());
		assertEquals(TARGET, positive.adjust(TARGET + 120 * MS));
		// a negative offset (adjusted by hand: the route reports early) is added back
		LatencyCompensation negative = LatencyCompensation.manual(-40.0);
		assertTrue(negative.isCalibrated());
		assertEquals(TARGET, negative.adjust(TARGET - 40 * MS));
		assertEquals(LatencyCompensation.Source.MANUAL, negative.source());
		assertEquals(0L, LatencyCompensation.manual(0.0).offsetNanos());
	}

	@Test
	public void anAbsurdOffsetIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> LatencyCompensation.calibrated(1000.1));
		assertThrows(IllegalArgumentException.class, () -> LatencyCompensation.manual(-1000.1));
		assertThrows(IllegalArgumentException.class, () -> LatencyCompensation.manual(Double.NaN));
		assertEquals(1000 * MS, LatencyCompensation.manual(1000.0).offsetNanos());
	}

	@Test
	public void aNoteExactlyOnTheBeatIsNotLateOnceTheOffsetIsCompensated() {
		// the route delays the sound by 180 ms: a note played exactly on the beat is seen 180 ms after it
		long observed = TARGET + 180 * MS;
		TimingOutcome compensated = ready(LatencyCompensation.calibrated(180.0)).judgeOnset(window(), observed);
		assertEquals(TimingVerdict.ON_TIME, compensated.verdict());
		assertEquals(0L, compensated.offsetNanos());
		// without the compensation the same note is late: this is what the calibration is for (and why the precision is then called reduced)
		TimingOutcome raw = ready(LatencyCompensation.none()).judgeOnset(window(), observed);
		assertEquals(TimingVerdict.LATE, raw.verdict());
		assertEquals(180 * MS, raw.offsetNanos());
	}

	@Test
	public void theCompensationMovesTheOnsetAndNeverTheWindow() {
		Window plain = window();
		for( double offset : new double[] { -300, -1, 0, 1, 250, 900 } ) {
			ready(LatencyCompensation.manual(offset)).judgeOnset(plain, TARGET);
			// the window is the learner's tolerance around the target: asking for it again gives the same one
			Window again = NORMAL.windowFor(TARGET, 500 * MS, Long.MAX_VALUE, Long.MAX_VALUE);
			assertEquals(plain.outerFrom(), again.outerFrom());
			assertEquals(plain.onTimeTo(), again.onTimeTo());
		}
	}

	@Test
	public void earlyAndLateAreJudgedAfterTheCompensation() {
		TimingContext context = ready(LatencyCompensation.calibrated(100.0));
		assertEquals(TimingVerdict.EARLY, context.judgeOnset(window(), TARGET + 100 * MS - 150 * MS).verdict());
		assertEquals(TimingVerdict.LATE, context.judgeOnset(window(), TARGET + 100 * MS + 150 * MS).verdict());
		assertEquals(-150 * MS, context.judgeOnset(window(), TARGET + 100 * MS - 150 * MS).offsetNanos());
		assertNull(context.judgeOnset(window(), TARGET + 100 * MS + 201 * MS).verdict(), "beyond the outer window nothing matches");
		assertEquals(Reason.NO_MATCH, context.judgeOnset(window(), TARGET + 100 * MS + 201 * MS).reason());
	}

	// ------------------------------------------------------------------ the clocks must be ready

	@Test
	public void noFrameBeforeTheClocksAreReadyCanGiveAVerdict() {
		// whatever the times, however far from the target, each verdict is refused while a clock is not ready
		boolean[][] states = { { false, false }, { true, false }, { false, true } };
		for( boolean[] state : states ) {
			TimingContext context = context(LatencyCompensation.calibrated(50.0), 5.0, state[0], state[1]);
			assertFalse(context.mayJudge(), "capture " + state[0] + " player " + state[1]);
			for( long observed : new long[] { TARGET, TARGET - 100 * MS, TARGET + 100 * MS, TARGET + 10_000 * MS, TARGET - 10_000 * MS } ) {
				TimingOutcome onset = context.judgeOnset(window(), observed);
				assertNull(onset.verdict(), "an onset was judged before the clocks were ready (EARLY, LATE or ON_TIME)");
				assertEquals(Reason.CLOCKS_NOT_READY, onset.reason());
				TimingOutcome extra = context.judgeUnmatchedOnset(observed);
				assertNull(extra.verdict(), "an EXTRA was given before the clocks were ready");
				assertEquals(Reason.CLOCKS_NOT_READY, extra.reason());
				TimingOutcome closure = context.judgeClosure(window(), observed);
				assertNull(closure.verdict(), "a MISSED was given before the clocks were ready");
				assertEquals(Reason.CLOCKS_NOT_READY, closure.reason());
			}
			assertEquals(RhythmPrecision.NOT_READY, context.precision());
		}
	}

	@Test
	public void onceBothClocksAreReadyTheVerdictsComeBack() {
		TimingContext context = ready(LatencyCompensation.calibrated(50.0));
		assertTrue(context.mayJudge());
		assertEquals(TimingVerdict.EXTRA, context.judgeUnmatchedOnset(TARGET).verdict());
		assertEquals(TimingVerdict.MISSED, context.judgeClosure(window(), TARGET + 50 * MS + 201 * MS).verdict());
	}

	@Test
	public void aWindowIsClosedOnlyOnceItsLastNanosecondHasPassedAfterTheCompensation() {
		TimingContext context = ready(LatencyCompensation.calibrated(100.0));
		long limit = window().outerTo() + 100 * MS;
		TimingOutcome open = context.judgeClosure(window(), limit);
		assertNull(open.verdict());
		assertEquals(Reason.WINDOW_OPEN, open.reason());
		assertEquals(TimingVerdict.MISSED, context.judgeClosure(window(), limit + 1).verdict());
	}

	// ------------------------------------------------------------------ the precision, and why it is reduced

	@Test
	public void theReasonsOfAReducedPrecisionAreReportedSeparately() {
		TimingContext steady = context(LatencyCompensation.calibrated(60.0), 12.0, true, true);
		assertEquals(RhythmPrecision.NORMAL, steady.precision());
		assertTrue(steady.precisionReasons().isEmpty());

		TimingContext uncalibrated = context(LatencyCompensation.none(), 12.0, true, true);
		assertEquals(RhythmPrecision.REDUCED, uncalibrated.precision());
		assertEquals(EnumSet.of(PrecisionReason.NOT_CALIBRATED), uncalibrated.precisionReasons());
		assertTrue(uncalibrated.mayJudge(), "reduced precision still judges: it says so, it does not block");

		TimingContext unstable = context(LatencyCompensation.calibrated(60.0), 130.0, true, true);
		assertEquals(RhythmPrecision.REDUCED, unstable.precision());
		assertEquals(EnumSet.of(PrecisionReason.UNSTABLE_ROUTE), unstable.precisionReasons());
		assertTrue(unstable.mayJudge(), "an unstable route warns, it never blocks");

		TimingContext both = context(LatencyCompensation.none(), 130.0, true, true);
		assertEquals(EnumSet.of(PrecisionReason.NOT_CALIBRATED, PrecisionReason.UNSTABLE_ROUTE), both.precisionReasons());

		TimingContext notReady = context(LatencyCompensation.none(), 0.0, false, true);
		assertEquals(RhythmPrecision.NOT_READY, notReady.precision());
		assertTrue(notReady.precisionReasons().contains(PrecisionReason.CLOCKS_NOT_READY));
	}

	@Test
	public void theUncertaintyOfTheClocksNeverWidensAWindowNorMovesAnOnset() {
		long observed = TARGET + 90 * MS;
		TimingOutcome steady = context(LatencyCompensation.calibrated(0.0), 2.0, true, true).judgeOnset(window(), observed);
		TimingOutcome unstable = context(LatencyCompensation.calibrated(0.0), 500.0, true, true).judgeOnset(window(), observed);
		assertEquals(steady.verdict(), unstable.verdict());
		assertEquals(steady.offsetNanos(), unstable.offsetNanos());
		assertEquals(TimingVerdict.LATE, unstable.verdict(), "90 ms is outside the 75 ms tight window whatever the spread says");
	}

	@Test
	public void theSpreadIsCalledUnstableAtTheThreshold() {
		assertFalse(new ClockUncertainty(ClockUncertainty.UNSTABLE_SPREAD_MILLIS - 0.001).isUnstable());
		assertTrue(new ClockUncertainty(ClockUncertainty.UNSTABLE_SPREAD_MILLIS).isUnstable());
		assertFalse(new ClockUncertainty(0.0).isUnstable());
		assertThrows(IllegalArgumentException.class, () -> new ClockUncertainty(-1.0));
	}

	// ------------------------------------------------------------------ no device is looked at

	@Test
	public void noLatencyIsDeducedFromTheNameOfADevice() {
		// Bluetooth is never refused and never guessed at: none of these types takes a name, a type of device or a transport
		for( Class<?> type : new Class<?>[] { TimingContext.class, LatencyCompensation.class, ClockUncertainty.class, TimingTolerance.class, FollowTimeline.class } ) {
			for( Constructor<?> constructor : type.getDeclaredConstructors() ) {
				for( Class<?> parameter : constructor.getParameterTypes() ) {
					assertTrue(parameter != String.class, constructor + " takes a name");
				}
			}
			for( Method method : type.getMethods() ) {
				for( Class<?> parameter : method.getParameterTypes() ) {
					assertTrue(parameter != String.class, method + " takes a name");
				}
			}
		}
	}
}
