package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.session.ClickPlayer;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIPanel;

/**
 * The tempo-following part of the preparation window on a headless toolkit (spec 002 T097, T098, T141): the mode, the percentage and the tolerance
 * the learner picks are what the settings hold; each value stays when another one changes; the calibration is announced, runs, ends or is cancelled,
 * and is kept only for its route; the reduced precision is said and never blocks. The native SWT test of the same panel is {@code FollowPanelSwtTest}.
 */
public class FollowPanelTest {

	private static final long MS = 1_000_000L;
	private static final long HOP = 5 * MS;
	private static final long LATENCY = 65 * MS;
	static final AudioRoute ROUTE = new AudioRoute("microphone", "speakers", 48000, "1ch 16bit");

	static final class FakeClicks implements ClickPlayer {
		long now;
		int clicks;
		int silenced;

		public long click() {
			this.clicks++;
			return this.now;
		}

		public void silence() {
			this.silenced++;
		}
	}

	static final class FakeBackend implements FollowPanel.Backend {
		AudioRoute route = ROUTE;
		final RouteCalibrations calibrations = new RouteCalibrations();
		double lagSpread = Double.NaN;
		final FakeClicks clicks = new FakeClicks();
		boolean noInput;
		CalibrationRun begun;
		Runnable changed;
		int ended;

		public AudioRoute route() {
			return this.route;
		}

		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		public double lagSpreadMillis() {
			return this.lagSpread;
		}

		public CalibrationRun newCalibration() {
			return this.noInput ? null : new CalibrationRun(this.clicks, LATENCY);
		}

		public void begin(CalibrationRun run, Runnable changed) {
			this.begun = run;
			this.changed = changed;
		}

		public void end(CalibrationRun run) {
			this.ended++;
			this.changed = null;
		}
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final FollowSettings settings = settings();
	private final FakeBackend backend = new FakeBackend();
	private FollowPanel panel;

	static FollowSettings settings() {
		try {
			java.lang.reflect.Constructor<FollowSettings> constructor = FollowSettings.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private FollowPanel open() {
		UIPanel root = (UIPanel) this.toolkit.create(UIPanel.class, null);
		root.setLayout(new UITableLayout());
		this.panel = new FollowPanel(this.toolkit.factory(), root, this.settings, this.backend);
		return this.panel;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") == Boolean.TRUE;
	}

	private static boolean enabled(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Enabled") == Boolean.TRUE;
	}

	private void chooseMode(FollowSettings.Mode mode) {
		this.panel.modeSelect().setSelectedValue(mode);
		SwtContractToolkit.userSelects(this.panel.modeSelect());
	}

	private void choosePercent(int value) {
		this.panel.percentSpinner().setValue(value);
		SwtContractToolkit.userSelects(this.panel.percentSpinner());
	}

	private void chooseWindow(int millis) {
		this.panel.windowSlider().setValue(millis);
		SwtContractToolkit.userSelects(this.panel.windowSlider());
	}

	// ------------------------------------------------------------------ mode, percentage, tolerance

	@Test
	public void theWaitModeIsShownFirstAndTheFollowControlsAreOnlyThereInTheFollowMode() {
		open();

		assertEquals(FollowSettings.Mode.WAIT, this.panel.modeSelect().getSelectedValue());
		assertFalse(visible(this.panel.percentSpinner()));
		assertFalse(visible(this.panel.windowSlider()));
		assertFalse(visible(this.panel.calibrateButton()));
		assertFalse(visible(this.panel.precisionLabel()), "the wait mode says nothing about the rhythm");

		chooseMode(FollowSettings.Mode.FOLLOW);

		assertEquals(FollowSettings.Mode.FOLLOW, this.settings.getMode());
		assertTrue(visible(this.panel.percentSpinner()));
		assertTrue(visible(this.panel.windowSlider()));
		assertTrue(visible(this.panel.calibrateButton()));
		assertTrue(this.panel.announceLabel().getText().contains("practice.follow.calibration.announce"));
	}

	@Test
	public void theStageFlamesControlOffersTheThreeStatesShowsTheSettingAndWritesAChoiceBack() {
		this.settings.setFlames(FlameSetting.FULL);
		open();

		assertEquals(3, SwtContractToolkit.state(this.panel.flamesSelect()).items.size(), "off, discreet and full");
		assertFalse(visible(this.panel.flamesSelect()), "a setting of the Arcade picture: not shown in the wait mode");
		chooseMode(FollowSettings.Mode.ARCADE);
		assertTrue(visible(this.panel.flamesSelect()));
		assertEquals(FlameSetting.FULL, this.panel.flamesSelect().getSelectedValue(), "it shows the setting");

		for (FlameSetting setting : new FlameSetting[] { FlameSetting.OFF, FlameSetting.DISCREET, FlameSetting.FULL }) {
			this.panel.flamesSelect().setSelectedValue(setting);
			SwtContractToolkit.userSelects(this.panel.flamesSelect());
			assertEquals(setting, this.settings.getFlames(), "a choice is the setting");
		}
		this.settings.setFlames(FlameSetting.OFF);
		this.panel.refresh();
		assertEquals(FlameSetting.OFF, this.panel.flamesSelect().getSelectedValue(), "and the panel follows the setting");
	}

	@Test
	public void thePercentageAndTheWindowTheLearnerPicksAreTheSettingsAndTheSliderGoesFromFortyToTwoHundred() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);

		choosePercent(70);
		chooseWindow(60);

		assertEquals(70, this.settings.getPercent());
		assertEquals(60, this.settings.getWindowMillis());
		assertEquals(TimingTolerance.MIN_WIDTH_MILLIS, this.panel.windowSlider().getMinimum());
		assertEquals(TimingTolerance.MAX_WIDTH_MILLIS, this.panel.windowSlider().getMaximum());
		assertEquals(FollowSettings.MIN_PERCENT, this.panel.percentSpinner().getMinimum());
		assertEquals(FollowSettings.MAX_PERCENT, this.panel.percentSpinner().getMaximum());
	}

	@Test
	public void aPercentageOutsideItsBoundsIsBroughtBackAndShown() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);

		choosePercent(5);
		assertEquals(FollowSettings.MIN_PERCENT, this.settings.getPercent());
		assertEquals(FollowSettings.MIN_PERCENT, this.panel.percentSpinner().getValue(), "the spinner shows what the settings kept");

		choosePercent(400);
		assertEquals(FollowSettings.MAX_PERCENT, this.settings.getPercent());
		assertEquals(FollowSettings.MAX_PERCENT, this.panel.percentSpinner().getValue());
	}

	@Test
	public void changingOneControlNeverMovesTheOthersNorTheCalibration() {
		open();
		this.backend.calibrations.set(ROUTE, LatencyCompensation.calibrated(66.0));
		chooseMode(FollowSettings.Mode.FOLLOW);
		choosePercent(85);
		chooseWindow(180);
		this.panel.refresh();

		chooseWindow(60);
		assertEquals(85, this.panel.percentSpinner().getValue());
		assertEquals(85, this.settings.getPercent());

		choosePercent(60);
		assertEquals(60, this.panel.windowSlider().getValue());
		assertEquals(60, this.settings.getWindowMillis());

		chooseMode(FollowSettings.Mode.WAIT);
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertEquals(60, this.panel.percentSpinner().getValue(), "the choices are still there after a visit to the wait mode");
		assertEquals(60, this.panel.windowSlider().getValue());
		assertEquals(66_000_000L, this.backend.calibrations.forRoute(ROUTE).offsetNanos(), "none of them touches the calibration");
	}

	@Test
	public void theWindowIsToldWhenTheModeChangesSoItsStartButtonFollows() {
		open();
		int[] changes = new int[1];
		this.panel.setModeListener(() -> changes[0]++);

		chooseMode(FollowSettings.Mode.FOLLOW);
		chooseMode(FollowSettings.Mode.WAIT);

		assertEquals(2, changes[0]);
	}

	// ------------------------------------------------------------------ the calibration by hand, and the precision

	@Test
	public void withoutACalibrationTheReducedPrecisionIsSaidAndNothingIsBlocked() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);

		assertTrue(this.panel.calibrationStateLabel().getText().contains("practice.follow.calibration.state.none"));
		assertTrue(this.panel.precisionLabel().getText().contains("practice.follow.precision.reduced"));
		assertTrue(this.panel.precisionLabel().getText().contains("NOT_CALIBRATED"));
		assertTrue(enabled(this.panel.calibrateButton()), "the calibration is offered, never imposed");
		assertTrue(enabled(this.panel.percentSpinner()), "and everything else stays usable");
	}

	@Test
	public void aDelayAdjustedByHandIsKeptForTheRouteAndForgottenOnDemand() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);

		this.panel.manualSpinner().setValue(-40);
		SwtContractToolkit.userSelects(this.panel.manualApplyButton());

		LatencyCompensation kept = this.backend.calibrations.forRoute(ROUTE);
		assertEquals(LatencyCompensation.Source.MANUAL, kept.source());
		assertEquals(-40_000_000L, kept.offsetNanos(), "a negative delay is allowed");
		assertTrue(this.panel.calibrationStateLabel().getText().contains("practice.follow.calibration.state.manual"));
		assertEquals("", this.panel.precisionLabel().getText(), "a route that was adjusted is not 'reduced'");

		SwtContractToolkit.userSelects(this.panel.manualForgetButton());
		assertFalse(this.backend.calibrations.forRoute(ROUTE).isCalibrated());
		assertTrue(this.panel.precisionLabel().getText().contains("practice.follow.precision.reduced"));
	}

	@Test
	public void theDelayAdjustedByHandStaysWithinTheLimits() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);

		assertEquals((int) -LatencyCompensation.LIMIT_MILLIS, this.panel.manualSpinner().getMinimum());
		assertEquals((int) LatencyCompensation.LIMIT_MILLIS, this.panel.manualSpinner().getMaximum());
		this.panel.manualSpinner().setValue(5000);
		SwtContractToolkit.userSelects(this.panel.manualApplyButton());
		assertEquals(Math.round(LatencyCompensation.LIMIT_MILLIS * 1e6), this.backend.calibrations.forRoute(ROUTE).offsetNanos());
	}

	@Test
	public void aCalibrationBelongsToItsRouteAndAnotherRouteStartsUncalibrated() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		this.backend.calibrations.set(ROUTE, LatencyCompensation.calibrated(66.0));
		this.panel.refresh();
		assertEquals("", this.panel.precisionLabel().getText());

		this.backend.route = new AudioRoute("USB interface", "speakers", 48000, "1ch 16bit");
		this.panel.refresh();

		assertTrue(this.panel.calibrationStateLabel().getText().contains("practice.follow.calibration.state.none"));
		assertTrue(this.panel.precisionLabel().getText().contains("practice.follow.precision.reduced"));
		assertEquals(66_000_000L, this.backend.calibrations.forRoute(ROUTE).offsetNanos(), "the first route is not forgotten, it is another route");
	}

	@Test
	public void noInputMeansNothingToCalibrateButStillNothingBlocked() {
		this.backend.route = null;
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);

		assertFalse(enabled(this.panel.calibrateButton()));
		assertFalse(enabled(this.panel.manualApplyButton()));
		assertTrue(this.panel.precisionLabel().getText().contains("practice.follow.precision.reduced"));
	}

	@Test
	public void anUnstableRouteIsWarnedAboutEvenWhenCalibrated() {
		open();
		this.backend.calibrations.set(ROUTE, LatencyCompensation.calibrated(66.0));
		this.backend.lagSpread = 150.0;
		chooseMode(FollowSettings.Mode.FOLLOW);

		assertTrue(this.panel.precisionLabel().getText().contains("UNSTABLE_ROUTE"));
		assertTrue(this.panel.precisionLabel().getText().contains("practice.follow.precision.reduced"));
		assertFalse(this.panel.precisionLabel().getText().contains("NOT_CALIBRATED"), "the two reasons are told apart");
		assertTrue(enabled(this.panel.calibrateButton()));
	}

	// ------------------------------------------------------------------ the calibration by clicks

	private void playCalibration(int delayMillis, int notes) {
		CalibrationRun run = this.backend.begun;
		assertNotNull(run);
		long t0 = 10_000_000_000L;
		long end = t0 + CalibrationRun.LEAD_NANOS + 8 * CalibrationRun.PERIOD_NANOS + 2_000 * MS;
		for (long wall = t0; wall < end; wall += HOP) {
			this.backend.clicks.now = wall;
			boolean onset = false;
			double f0 = Double.NaN;
			double confidence = 0.0;
			for (int k = 0; k < notes; k++) {
				long start = t0 + CalibrationRun.LEAD_NANOS + k * CalibrationRun.PERIOD_NANOS + delayMillis * MS;
				if (wall >= start && wall < start + 250 * MS) {
					f0 = (k % 2 == 0 ? 329.63 : 392.0);
					confidence = 0.95;
				}
				onset |= (wall == start + LATENCY);
			}
			run.onFrame(new AnalysisFrame(wall - t0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, onset), wall);
			if (this.backend.changed != null) {
				this.backend.changed.run();
			}
			if (run.state() != CalibrationRun.State.RUNNING) {
				break;
			}
		}
	}

	@Test
	public void theCalibrationIsAnnouncedWithItsDurationRunsAndKeepsTheDelayForTheRoute() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertTrue(this.panel.announceLabel().getText().contains("practice.follow.calibration.announce"));
		assertFalse(enabled(this.panel.cancelButton()), "nothing to cancel before it runs");

		SwtContractToolkit.userSelects(this.panel.calibrateButton());

		assertTrue(this.panel.isCalibrating());
		assertTrue(enabled(this.panel.cancelButton()));
		assertFalse(enabled(this.panel.calibrateButton()), "one calibration at a time");
		assertFalse(enabled(this.panel.modeSelect()), "the choices wait for the end of the sound");
		playCalibration(120, 8);

		assertFalse(this.panel.isCalibrating());
		LatencyCompensation kept = this.backend.calibrations.forRoute(ROUTE);
		assertEquals(LatencyCompensation.Source.CALIBRATED, kept.source());
		assertEquals(120.0, kept.offsetNanos() / 1e6, 2.5);
		assertTrue(this.panel.progressLabel().getText().contains("practice.follow.calibration.done"));
		assertTrue(this.panel.calibrationStateLabel().getText().contains("practice.follow.calibration.state.calibrated"));
		assertEquals("", this.panel.precisionLabel().getText());
		assertEquals(1, this.backend.ended, "the run stopped being fed");
		assertTrue(enabled(this.panel.calibrateButton()));
		assertTrue(enabled(this.panel.modeSelect()));
	}

	@Test
	public void cancelStopsTheClicksAtOnceAndKeepsNothing() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		SwtContractToolkit.userSelects(this.panel.calibrateButton());
		CalibrationRun run = this.backend.begun;
		long t0 = 10_000_000_000L;
		for (long wall = t0; wall < t0 + 3_000 * MS; wall += HOP) {
			this.backend.clicks.now = wall;
			run.onFrame(new AnalysisFrame(wall - t0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, false), wall);
		}
		int played = this.backend.clicks.clicks;
		assertTrue(played >= 1 && played < CalibrationTaps.CLICKS);

		SwtContractToolkit.userSelects(this.panel.cancelButton());

		assertEquals(CalibrationRun.State.CANCELLED, run.state());
		assertEquals(1, this.backend.clicks.silenced, "the sound is cut");
		assertFalse(this.panel.isCalibrating());
		assertEquals(1, this.backend.ended);
		assertFalse(this.backend.calibrations.forRoute(ROUTE).isCalibrated(), "nothing was kept");
		assertTrue(this.panel.progressLabel().getText().contains("practice.follow.calibration.cancelled"));
		assertTrue(enabled(this.panel.calibrateButton()));
		for (long wall = t0 + 3_000 * MS; wall < t0 + 12_000 * MS; wall += HOP) {
			this.backend.clicks.now = wall;
			run.onFrame(new AnalysisFrame(wall - t0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, false), wall);
		}
		assertEquals(played, this.backend.clicks.clicks, "no click after the cancel");
	}

	@Test
	public void aCalibrationWithTooFewNotesOrNoInputIsNotKeptAndSaysWhy() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		SwtContractToolkit.userSelects(this.panel.calibrateButton());
		playCalibration(120, 3);

		assertFalse(this.backend.calibrations.forRoute(ROUTE).isCalibrated());
		assertTrue(this.panel.progressLabel().getText().contains("practice.follow.calibration.too-few"));
		assertTrue(enabled(this.panel.calibrateButton()), "it can be tried again");

		this.backend.noInput = true;
		SwtContractToolkit.userSelects(this.panel.calibrateButton());
		assertTrue(this.panel.progressLabel().getText().contains("practice.follow.calibration.no-input"));
		assertFalse(this.panel.isCalibrating());
	}

	@Test
	public void closingTheWindowDuringACalibrationStopsTheSound() {
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		SwtContractToolkit.userSelects(this.panel.calibrateButton());
		CalibrationRun run = this.backend.begun;

		this.panel.dispose();
		this.panel.dispose();

		assertEquals(CalibrationRun.State.CANCELLED, run.state());
		assertEquals(1, this.backend.clicks.silenced);
		assertEquals(1, this.backend.ended);
	}

	@Test
	public void arcadeIsOfferedAsAThirdModeWithTheControlsOfTheTimedModesAndItsOwnExplanation() {
		open();
		chooseMode(FollowSettings.Mode.ARCADE);

		assertEquals(FollowSettings.Mode.ARCADE, this.settings.getMode());
		assertTrue(visible(this.panel.percentSpinner()), "the speed is shared with the following mode");
		assertTrue(visible(this.panel.windowSlider()));
		assertTrue(visible(this.panel.calibrateButton()));
		assertTrue(this.panel.modeHintLabel().getText().contains("practice.mode.arcade.hint"));
		assertTrue(this.panel.modeHintLabel().getText().contains("arcade.monitoring.unavailable"), "the learner is told that hearing the guitar through the application is not offered");

		chooseMode(FollowSettings.Mode.FOLLOW);
		assertFalse(this.panel.modeHintLabel().getText().contains("arcade."), "the following mode says nothing of Arcade");
		chooseMode(FollowSettings.Mode.WAIT);
		assertFalse(visible(this.panel.percentSpinner()));
	}

	@Test
	public void theSliderShowsTheSavedWindowWhenThePanelOpensAndFollowsAChangeMadeElsewhere() {
		this.settings.setWindowMillis(130);
		open();
		chooseMode(FollowSettings.Mode.FOLLOW);
		assertEquals(130, this.panel.windowSlider().getValue(), "the saved window, not a default");

		this.settings.setWindowMillis(45); // the Configuration window changed the same setting
		this.panel.refresh();
		assertEquals(45, this.panel.windowSlider().getValue());
	}

	@Test
	public void theWindowIsOnlyForTheTimedModesAndOnlyWhenIdle() {
		open();
		chooseMode(FollowSettings.Mode.WAIT);
		assertFalse(visible(this.panel.windowSlider()), "the Wait mode has no tempo");
		chooseMode(FollowSettings.Mode.ARCADE);
		assertTrue(visible(this.panel.windowSlider()), "Arcade shares the window with Follow");
	}
}
