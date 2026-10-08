package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;

/**
 * The geometry of the neck (spec 003 T031, FR-027, SC-011): fret positions from the equal-tempered formula, never from the generated concept images; the
 * string count from the track; conventional markers; plausible gauges and widths.
 */
public class NeckModelTest {

	private static final int[] GUITAR = { 64, 59, 55, 50, 45, 40 };
	private static final int[] BASS = { 43, 38, 33, 28 };

	private static NeckModel guitar() {
		return NeckModel.of(new TrackTuning(GUITAR, 0, null), 24);
	}

	private static NeckModel bass() {
		return NeckModel.of(TrackTuningFactory.any(BASS), 24);
	}

	@Test
	public void theTwelfthFretIsAtHalfTheScaleLengthAndEveryFretFollowsTheFormula() {
		for (NeckModel neck : new NeckModel[] { guitar(), bass() }) {
			double length = neck.scaleLength();
			assertEquals(length / 2.0, neck.fretX(12), 1e-9);
			assertEquals(0.0, neck.fretX(0), 1e-12);
			for (int n = 0; n <= neck.fretCount(); n++) {
				assertEquals(length * (1.0 - Math.pow(2.0, -n / 12.0)), neck.fretX(n), 1e-9, "fret " + n);
			}
			assertEquals(length * 0.75, neck.fretX(24), 1e-9, "the 24th fret is at three quarters of the scale length");
		}
	}

	@Test
	public void fretsGetProgressivelyNarrower() {
		NeckModel neck = guitar();
		double previous = Double.MAX_VALUE;
		for (int n = 1; n <= neck.fretCount(); n++) {
			double width = neck.fretX(n) - neck.fretX(n - 1);
			assertTrue(width < previous, "fret " + n + " is narrower than the one before");
			previous = width;
		}
		assertEquals(neck.fretX(1) - neck.fretX(0), 647.7 * (1.0 - Math.pow(2.0, -1.0 / 12.0)), 1e-6);
	}

	@Test
	public void theScaleLengthIsThatOfTheInstrumentFamilyAndTheStringCountThatOfTheTrack() {
		assertEquals(647.7, guitar().scaleLength(), 0.01, "25.5 inches");
		assertEquals(863.6, bass().scaleLength(), 0.01, "34 inches");
		assertEquals(6, guitar().layout().count());
		assertEquals(4, bass().layout().count());
		assertEquals(7, NeckModel.of(TrackTuningFactory.any(new int[] { 64, 59, 55, 50, 45, 40, 35 }), 24).layout().count());
	}

	@Test
	public void theCentreOfAFretIsBetweenItsTwoFretWiresAndTheOpenStringIsBeforeTheNut() {
		NeckModel neck = guitar();
		for (int n = 1; n <= 24; n++) {
			assertEquals((neck.fretX(n - 1) + neck.fretX(n)) / 2.0, neck.fretCentreX(n), 1e-9);
		}
		assertTrue(neck.fretCentreX(0) < 0.0, "an open string is played before the nut");
	}

	@Test
	public void markersAreSingleAtThreeFiveSevenNineFifteenSeventeenNineteenTwentyOneAndDoubleAtTwelveAndTwentyFour() {
		java.util.Map<Integer, Boolean> markers = new java.util.TreeMap<Integer, Boolean>();
		for (NeckModel.Marker marker : guitar().markers()) {
			markers.put(marker.fret(), marker.isDouble());
		}
		assertEquals(java.util.List.of(3, 5, 7, 9, 12, 15, 17, 19, 21, 24), new java.util.ArrayList<Integer>(markers.keySet()));
		assertEquals(true, markers.get(12));
		assertEquals(true, markers.get(24));
		assertEquals(false, markers.get(5));
		// a shorter neck has only the markers it has
		int last = 0;
		for (NeckModel.Marker marker : NeckModel.of(new TrackTuning(GUITAR, 0, null), 12).markers()) {
			last = Math.max(last, marker.fret());
		}
		assertEquals(12, last);
	}

	@Test
	public void gaugesGrowFromTheHighStringToTheLowOneAndStayPlausible() {
		for (NeckModel neck : new NeckModel[] { guitar(), bass() }) {
			double previous = 0.0;
			for (int row = neck.layout().count() - 1; row >= 0; row--) {
				double gauge = neck.gauge(row);
				assertTrue(gauge > previous, "a lower string is thicker");
				assertTrue(gauge >= 0.2 && gauge <= 2.2, "plausible gauge in millimetres: " + gauge);
				previous = gauge;
			}
		}
	}

	@Test
	public void theNeckWidensTowardsTheBodyAndTheLowStringIsBelowTheHighOne() {
		NeckModel neck = guitar();
		assertTrue(neck.widthAt(neck.fretX(12)) > neck.widthAt(0.0));
		assertTrue(neck.widthAt(neck.fretX(24)) > neck.widthAt(neck.fretX(12)));
		for (double x : new double[] { 0.0, neck.fretX(12), neck.fretX(24) }) {
			for (int row = 1; row < neck.layout().count(); row++) {
				assertTrue(neck.stringY(row, x) > neck.stringY(row - 1, x), "the higher string is higher on the neck");
			}
			double centre = (neck.stringY(0, x) + neck.stringY(neck.layout().count() - 1, x)) / 2.0;
			assertEquals(0.0, centre, 1e-9, "the strings are centred on the neck");
		}
	}
}
