package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps.Result;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps.Status;

/** The delay of a route out of the notes played along the clicks (spec 002 T097): the median, the outliers set aside, the cases not kept. */
public class CalibrationTapsTest {

	private static Result of(double... offsets) {
		CalibrationTaps taps = new CalibrationTaps();
		for (double offset : offsets) {
			taps.add(offset);
		}
		return taps.evaluate();
	}

	@Test
	public void theMedianOfSteadyNotesIsTheDelay() {
		Result result = of(118, 122, 120, 119, 121, 120, 123, 117);

		assertEquals(Status.CALIBRATED, result.status());
		assertEquals(120.0, result.medianMillis(), 1.0);
		assertEquals(8, result.used());
		assertEquals(8, result.total());
		assertTrue(result.spreadMillis() < 10.0);
		assertEquals(LatencyCompensation.Source.CALIBRATED, result.compensation().source());
		assertEquals(Math.round(result.medianMillis() * 1e6), result.compensation().offsetNanos());
	}

	@Test
	public void aNoteOnTheWrongClickOrANoiseDoesNotMoveTheDelay() {
		Result result = of(118, 122, 120, 119, 121, 120, 560, -300);

		assertEquals(Status.CALIBRATED, result.status());
		assertEquals(120.0, result.medianMillis(), 1.5, "the two outliers are set aside, not averaged");
		assertEquals(6, result.used());
		// without the rejection the mean of all eight notes would be 105 ms off
		assertTrue(Math.abs(result.medianMillis() - 120.0) < 2.0);
	}

	@Test
	public void theDelayIsTheMedianOfTheUsableNotesNotTheirMean() {
		// a late note that is still usable pulls the mean to 108.75 ms; the median stays on the delay the steady notes show
		Result result = of(100, 100, 100, 100, 100, 100, 130, 140);

		assertEquals(Status.CALIBRATED, result.status());
		assertEquals(100.0, result.medianMillis(), 1e-9);
		assertEquals(8, result.used());
	}

	@Test
	public void aSteadyPlayerIsNotMadeOfOutliers() {
		// every note within a few milliseconds of the others: none may be rejected, though the median deviation is tiny
		Result result = of(100, 101, 100, 102, 100, 101, 100, 101);

		assertEquals(8, result.used());
	}

	@Test
	public void fewerThanFiveUsableNotesAreNotKept() {
		assertEquals(Status.TOO_FEW, of(120, 119, 121, 120).status());
		assertEquals(Status.TOO_FEW, of().status());
		Result five = of(120, 119, 121, 120, 118);
		assertEquals(Status.CALIBRATED, five.status(), "five is enough");
		assertFalse(of(120, 119, 121, 120).compensation().isCalibrated(), "nothing is kept when the calibration failed");
	}

	@Test
	public void notesTooScatteredAroundTheClicksAreNotKept() {
		Result result = of(20, 180, 60, 230, 10, 190, 70, 220);

		assertEquals(Status.UNSTABLE, result.status());
		assertTrue(result.spreadMillis() > CalibrationTaps.UNSTABLE_SPREAD_MILLIS);
		assertFalse(result.compensation().isCalibrated());
	}

	@Test
	public void aDelayOutsideWhatTheCompensationAcceptsIsNotKept() {
		Result result = of(1500, 1502, 1498, 1501, 1499, 1500);

		assertEquals(Status.OUT_OF_RANGE, result.status());
		assertFalse(result.compensation().isCalibrated());
	}

	@Test
	public void aNegativeDelayIsAllowed() {
		Result result = of(-40, -42, -38, -41, -39, -40);

		assertEquals(Status.CALIBRATED, result.status());
		assertEquals(-40.0, result.medianMillis(), 1.0);
	}

	@Test
	public void notANumberIsIgnored() {
		CalibrationTaps taps = new CalibrationTaps();
		taps.add(Double.NaN);
		taps.add(Double.POSITIVE_INFINITY);
		assertEquals(0, taps.count());
	}

	@Test
	public void theMedianOfAnEvenNumberIsTheMiddleOfTheTwoMiddleOnes() {
		List<Double> values = new ArrayList<Double>(List.of(1.0, 2.0, 3.0, 10.0));
		assertEquals(2.5, CalibrationTaps.median(values), 1e-12);
	}
}
