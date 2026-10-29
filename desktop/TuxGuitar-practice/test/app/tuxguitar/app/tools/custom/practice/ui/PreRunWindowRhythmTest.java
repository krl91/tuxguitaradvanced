package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation;
import app.tuxguitar.audio.engine.analysis.StrumClipMeter;
import app.tuxguitar.audio.engine.analysis.StrumVerdict;

/**
 * Spec 016 (T019, contracts/calibration-offer.md) on the headless toolkit: once the level is good, the start window of Follow or Arcade on a route not
 * calibrated shows the offer instead of closing; Calibrate now starts the clicks of the same calibration as the Mode tab at once; its cancel offers the
 * two buttons again with the reason; Go on without lets the mode begin and declines the route; Escape begins nothing. Without an offer, or in Wait, the
 * window closes at the good level as before.
 */
public class PreRunWindowRhythmTest {

	private static final class Source implements PreRunSource {
		StrumVerdict verdict = StrumVerdict.NO_STRUM;

		public StrumClipMeter.Reading reading() {
			return new StrumClipMeter.Reading(this.verdict, 0.0, -20.0);
		}

		public double levelDbfs() {
			return -20.0;
		}

		public boolean boostActive() {
			return false;
		}

		public void reset() {
			this.verdict = StrumVerdict.NO_STRUM;
		}

		public void close() {
		}
	}

	private static final class Tuner implements PreRunWindow.TunerOpener {
		public boolean available() {
			return true;
		}

		public void open() {
		}

		public void toFront() {
		}

		public void setClosedListener(Runnable listener) {
		}
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final FollowPanelTest.FakeBackend backend = new FollowPanelTest.FakeBackend();
	private final DeclinedRoutes declined = new DeclinedRoutes();
	private final AtomicInteger finished = new AtomicInteger();
	private final Source source = new Source();
	private PreRunWindow window;

	private PreRunWindow open(FollowSettings.Mode mode, boolean withOffer) {
		PreRunCheck check = new PreRunCheck(mode);
		RhythmOffer offer = new RhythmOffer(mode, this.backend::route, this.backend.calibrations, this.declined);
		this.window = new PreRunWindow(this.toolkit.factory(), null, check, this.source, (key, arguments) -> key, () -> 0L, new Tuner(), this.finished::incrementAndGet,
				null, withOffer ? offer : null, withOffer ? this.backend : null);
		this.window.refresh();
		check.answerTuned(true);
		this.source.verdict = StrumVerdict.GOOD;
		this.window.refresh();
		return this.window;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	@Test
	public void aGoodLevelOnARouteNotCalibratedShowsTheOfferInsteadOfClosing() {
		open(FollowSettings.Mode.FOLLOW, true);
		assertEquals(0, this.finished.get(), "the window stays");
		assertFalse(this.window.mayBegin());
		assertTrue(visible(this.window.rhythmSentenceLabel()));
		assertEquals("practice.prerun.rhythm.sentence", text(this.window.rhythmSentenceLabel()));
		assertTrue(visible(this.window.calibrateNowButton()));
		assertTrue(visible(this.window.goOnWithoutButton()));
		assertFalse(visible(this.window.calibrationCancelButton()));
		assertFalse(visible(this.window.meterBar()), "the strum gave way");
		assertFalse(visible(this.window.continueButton()));
	}

	@Test
	public void oneClickOnCalibrateNowStartsTheClicksOfTheSameCalibrationAsTheModeTab() {
		open(FollowSettings.Mode.ARCADE, true);
		SwtContractToolkit.userSelects(this.window.calibrateNowButton());
		CalibrationRun run = this.backend.begun;
		assertNotNull(run, "one click: the calibration runs through the backend of the Mode tab");
		assertEquals(CalibrationRun.State.RUNNING, run.state());
		assertTrue(visible(this.window.calibrationCancelButton()));
		assertFalse(visible(this.window.calibrateNowButton()));
		assertEquals("practice.follow.calibration.running", text(this.window.rhythmProgressLabel()), "the sentence of the Mode tab");
		assertFalse(this.window.mayBegin());
	}

	@Test
	public void cancellingTheClicksOffersTheTwoButtonsAgainWithTheReason() {
		open(FollowSettings.Mode.FOLLOW, true);
		SwtContractToolkit.userSelects(this.window.calibrateNowButton());
		SwtContractToolkit.userSelects(this.window.calibrationCancelButton());
		assertEquals(CalibrationRun.State.CANCELLED, this.backend.begun.state());
		assertEquals(1, this.backend.ended);
		assertEquals("practice.follow.calibration.cancelled", text(this.window.rhythmProgressLabel()));
		assertTrue(visible(this.window.calibrateNowButton()));
		assertTrue(visible(this.window.goOnWithoutButton()));
		assertEquals(0, this.finished.get());
	}

	@Test
	public void aCalibrationThatCannotStartSaysWhyAndKeepsTheOffer() {
		this.backend.noInput = true;
		open(FollowSettings.Mode.FOLLOW, true);
		SwtContractToolkit.userSelects(this.window.calibrateNowButton());
		assertNull(this.backend.begun);
		assertEquals("practice.follow.calibration.no-input", text(this.window.rhythmProgressLabel()));
		assertTrue(visible(this.window.calibrateNowButton()));
		assertEquals(0, this.finished.get());
	}

	@Test
	public void goOnWithoutLetsTheModeBeginAndDeclinesTheRoute() {
		open(FollowSettings.Mode.FOLLOW, true);
		SwtContractToolkit.userSelects(this.window.goOnWithoutButton());
		assertEquals(1, this.finished.get());
		assertTrue(this.window.mayBegin());
		assertTrue(this.declined.isDeclined(FollowPanelTest.ROUTE));
	}

	@Test
	public void escapeDuringTheOfferOrTheClicksBeginsNothing() {
		open(FollowSettings.Mode.FOLLOW, true);
		SwtContractToolkit.userSelects(this.window.calibrateNowButton());
		this.window.escapePressed();
		assertEquals(1, this.finished.get());
		assertFalse(this.window.mayBegin());
		assertEquals(CalibrationRun.State.CANCELLED, this.backend.begun.state(), "the clicks stop at once");
		assertFalse(this.declined.isDeclined(FollowPanelTest.ROUTE));
	}

	@Test
	public void aCalibratedRouteTheWaitModeOrNoOfferCloseAtTheGoodLevelAsBefore() {
		this.backend.calibrations.set(FollowPanelTest.ROUTE, LatencyCompensation.calibrated(40.0));
		open(FollowSettings.Mode.FOLLOW, true);
		assertEquals(1, this.finished.get());
		assertTrue(this.window.mayBegin());
		this.backend.calibrations.forget(FollowPanelTest.ROUTE);
		open(FollowSettings.Mode.WAIT, true);
		assertEquals(2, this.finished.get());
		assertTrue(this.window.mayBegin());
		open(FollowSettings.Mode.FOLLOW, false);
		assertEquals(3, this.finished.get());
		assertTrue(this.window.mayBegin());
	}
}
