package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;
import app.tuxguitar.app.tools.custom.practice.ui.RhythmOffer.State;

/**
 * Spec 016 (T018, FR-004, SC-005, contracts/calibration-offer.md): the step of the start window that offers the rhythm calibration, after the level. It
 * is the object {@link RhythmOffer} beside {@link PreRunCheck}, which stays as it is (its table test counts its states): the window enters the step once
 * the check is ready, and the mode begins only when the step allows it. Follow and Arcade only; a route calibrated, declined or unknown is not asked.
 */
public class PreRunCheckRhythmTest {

	private static final AudioRoute ROUTE = new AudioRoute("Rocksmith USB Guitar Adapter", "MacBook Speakers", 48000, "1ch 16bit");

	private final RouteCalibrations calibrations = new RouteCalibrations();
	private final DeclinedRoutes declined = new DeclinedRoutes();
	private AudioRoute route = ROUTE;

	private RhythmOffer offer(FollowSettings.Mode mode) {
		return new RhythmOffer(mode, () -> this.route, this.calibrations, this.declined);
	}

	@Test
	public void anUncalibratedRouteIsAskedInFollowAndArcadeAndNeverInWait() {
		for (FollowSettings.Mode mode : new FollowSettings.Mode[] { FollowSettings.Mode.FOLLOW, FollowSettings.Mode.ARCADE }) {
			RhythmOffer offer = offer(mode);
			assertEquals(State.NOT_ASKED, offer.state());
			assertFalse(offer.allowsStart(), "nothing begins before the check is ready");
			assertTrue(offer.enter(), mode + " asks");
			assertEquals(State.OFFER, offer.state());
			assertFalse(offer.allowsStart());
		}
		RhythmOffer waitMode = offer(FollowSettings.Mode.WAIT);
		assertFalse(waitMode.enter(), "the wait mode is never asked");
		assertTrue(waitMode.allowsStart());
	}

	@Test
	public void aCalibratedRouteADeclinedRouteOrAnUnknownRouteIsNotAsked() {
		this.calibrations.set(ROUTE, LatencyCompensation.calibrated(42.0));
		assertFalse(offer(FollowSettings.Mode.FOLLOW).enter(), "calibrated");
		this.calibrations.forget(ROUTE);
		this.calibrations.set(ROUTE, LatencyCompensation.manual(30.0));
		assertFalse(offer(FollowSettings.Mode.FOLLOW).enter(), "a delay typed by hand is a calibration");
		this.calibrations.forget(ROUTE);
		this.declined.decline(ROUTE);
		RhythmOffer declinedOffer = offer(FollowSettings.Mode.ARCADE);
		assertFalse(declinedOffer.enter(), "declined in this launch");
		assertTrue(declinedOffer.allowsStart());
		this.route = null;
		assertFalse(offer(FollowSettings.Mode.FOLLOW).enter(), "no route known: as today, the run shows the reduced precision");
	}

	@Test
	public void aRouteCalibratedWithAnotherInputOutputOrFormatIsNotCalibrated() {
		this.calibrations.set(new AudioRoute("Rocksmith USB Guitar Adapter", "Headphones", 48000, "1ch 16bit"), LatencyCompensation.calibrated(42.0));
		this.calibrations.set(new AudioRoute("Rocksmith USB Guitar Adapter", "MacBook Speakers", 44100, "1ch 16bit"), LatencyCompensation.calibrated(42.0));
		this.calibrations.set(new AudioRoute("Built-in Microphone", "MacBook Speakers", 48000, "1ch 16bit"), LatencyCompensation.calibrated(42.0));
		assertTrue(offer(FollowSettings.Mode.FOLLOW).enter());
	}

	@Test
	public void aSuccessfulCalibrationLetsTheRunBegin() {
		RhythmOffer offer = offer(FollowSettings.Mode.FOLLOW);
		offer.enter();
		offer.calibrate();
		assertEquals(State.CALIBRATING, offer.state());
		assertFalse(offer.allowsStart(), "not while the clicks run");
		offer.calibrationEnded(true);
		assertEquals(State.DONE, offer.state());
		assertTrue(offer.allowsStart());
		assertFalse(this.declined.isDeclined(ROUTE));
	}

	@Test
	public void aCancelledOrFailedCalibrationOffersTheTwoButtonsAgain() {
		RhythmOffer offer = offer(FollowSettings.Mode.FOLLOW);
		offer.enter();
		offer.calibrate();
		offer.calibrationEnded(false);
		assertEquals(State.OFFER, offer.state(), "back to the offer: the window shows the reason and the buttons");
		assertFalse(offer.allowsStart());
		offer.calibrate();
		assertEquals(State.CALIBRATING, offer.state(), "a second try");
	}

	@Test
	public void goingOnWithoutLetsTheRunBeginAndDeclinesTheRouteForTheLaunch() {
		RhythmOffer offer = offer(FollowSettings.Mode.ARCADE);
		offer.enter();
		offer.goOnWithout();
		assertEquals(State.DONE, offer.state());
		assertTrue(offer.allowsStart());
		assertTrue(this.declined.isDeclined(ROUTE));
		assertFalse(offer(FollowSettings.Mode.FOLLOW).enter(), "not asked again in this launch");
	}

	@Test
	public void cancellingTheStartDuringTheStepBeginsNothing() {
		RhythmOffer offer = offer(FollowSettings.Mode.FOLLOW);
		offer.enter();
		offer.cancel();
		assertEquals(State.CANCELLED, offer.state());
		assertFalse(offer.allowsStart());
		offer.goOnWithout();
		assertEquals(State.CANCELLED, offer.state(), "final");
		RhythmOffer calibrating = offer(FollowSettings.Mode.FOLLOW);
		calibrating.enter();
		calibrating.calibrate();
		calibrating.cancel();
		calibrating.calibrationEnded(true);
		assertFalse(calibrating.allowsStart(), "a calibration that ends after the cancel begins nothing");
		RhythmOffer done = offer(FollowSettings.Mode.FOLLOW);
		done.enter();
		done.goOnWithout();
		done.cancel();
		assertTrue(done.allowsStart(), "a cancel never undoes a decision taken");
	}

	@Test
	public void theEventsOutsideTheirStateChangeNothing() {
		RhythmOffer offer = offer(FollowSettings.Mode.FOLLOW);
		offer.calibrate();
		offer.goOnWithout();
		offer.calibrationEnded(true);
		assertEquals(State.NOT_ASKED, offer.state(), "before the step");
		offer.enter();
		offer.calibrationEnded(true);
		assertEquals(State.OFFER, offer.state(), "no calibration ran");
		assertTrue(offer.enter(), "entering again: the step is still shown");
		assertEquals(State.OFFER, offer.state());
	}

	@Test
	public void theCalibrationOfTheStepEndsInUnderFifteenSeconds() {
		// SC-005: one action starts the clicks, and the whole schedule (lead, the clicks a second apart, the last note's window) is under 15 s
		assertEquals(8, CalibrationTaps.CLICKS);
		long nanos = CalibrationRun.LEAD_NANOS + (CalibrationTaps.CLICKS - 1) * CalibrationRun.PERIOD_NANOS + CalibrationRun.LATE_WINDOW_NANOS;
		assertTrue(nanos < 15_000_000_000L, nanos / 1e9 + " s");
		assertTrue(CalibrationRun.announcedSeconds() < 15, CalibrationRun.announcedSeconds() + " s announced");
	}
}
