package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps;
import app.tuxguitar.app.tools.custom.practice.session.ClockUncertainty;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.layout.UITableLayout;
import app.tuxguitar.ui.widget.UIButton;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIDropDownSelect;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UILabel;
import app.tuxguitar.ui.widget.UIPanel;
import app.tuxguitar.ui.widget.UISelectItem;
import app.tuxguitar.ui.widget.UISpinner;

/**
 * The part of the preparation window that is about the tempo-following mode (spec 002 T097, T098, FR-051, FR-052, FR-054, FR-057 to FR-059): the
 * mode, the percentage of the written tempo, the timing tolerance, the rhythm calibration of the route and the mention "reduced rhythmic precision".
 * <p>
 * What it shows is what {@link FollowSettings} holds, and what it changes is only that: the three values are separate, and changing one never
 * moves another. The calibration is announced all the time with its duration and a Cancel that stops the clicks at once; it never blocks anything,
 * and the wait mode does not depend on it. UI thread only.
 */
public final class FollowPanel {

	/** What the panel needs of the application: the route, its calibration, and the clicks of the calibration. */
	public interface Backend {
		/** The route now, or null when no input is selected yet. */
		AudioRoute route();

		RouteCalibrations calibrations();

		/** The spread of the arrival lag of the capture in milliseconds, or NaN while it is not known. */
		double lagSpreadMillis();

		/** A calibration on the clicks of the player, or null when there is no usable input or the player is playing. */
		CalibrationRun newCalibration();

		/** True when the player is playing: the calibration would sound over it, so it waits. */
		default boolean playerBusy() {
			return false;
		}

		/** Feeds the run with the frames until it ends; {@code changed} is called on the UI thread when its state or progress changed. */
		void begin(CalibrationRun run, Runnable changed);

		/** Stops feeding the run. */
		void end(CalibrationRun run);
	}

	private final FollowSettings settings;
	private final Backend backend;
	private final UIPanel panel;
	private final UIDropDownSelect<FollowSettings.Mode> mode;
	private final UILabel modeHint;
	private final UILabel percentLabel;
	private final UISpinner percent;
	private final UILabel markerLabel;
	private final UISpinner marker;
	private final UILabel farScaleLabel;
	private final UISpinner farScale;
	private final UILabel horizonLabel;
	private final UISpinner horizon;
	private final UILabel flamesLabel;
	private final UIDropDownSelect<FlameSetting> flames;
	/** The permissiveness of the Arcade recognition (spec 014): null when the panel is built without the remembered choices. */
	private final ArcadeChoices arcadeChoices;
	private final UILabel leniencyLabel;
	private final UIDropDownSelect<ArcadeLeniencyPosition> leniency;
	private final UILabel leniencyHint;
	private final UILabel percentHint;
	private final UILabel toleranceLabel;
	private final UIScale tolerance;
	private final UILabel toleranceHint;
	private final UILabel calibrationTitle;
	private final UILabel calibrationState;
	private final UILabel calibrationRoute;
	private final UILabel manualLabel;
	private final UISpinner manual;
	private final UIPanel manualRow;
	private final UIPanel buttonsRow;
	private final UIButton manualApply;
	private final UIButton manualForget;
	private final UILabel manualHint;
	private final UIButton calibrate;
	private final UIButton cancel;
	private final UILabel announce;
	private final UILabel progress;
	private final UILabel precision;
	private boolean updating;
	private CalibrationRun running;
	private Runnable contentListener;
	private Runnable modeListener;

	public FollowPanel(UIFactory uiFactory, UIContainer parent, FollowSettings settings, Backend backend) {
		this(uiFactory, parent, settings, backend, null);
	}

	/** @param arcadeChoices where the position of the Arcade permissiveness is remembered (spec 014); null: no such line */
	public FollowPanel(UIFactory uiFactory, UIContainer parent, FollowSettings settings, Backend backend, ArcadeChoices arcadeChoices) {
		this.arcadeChoices = arcadeChoices;
		this.settings = settings;
		this.backend = backend;
		this.panel = uiFactory.createPanel(parent, false);
		UITableLayout layout = new UITableLayout();
		// what is hidden takes no room: the wait mode shows the choice of the mode and nothing else
		layout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.panel.setLayout(layout);

		UILabel modeLabel = uiFactory.createLabel(this.panel);
		modeLabel.setText(TuxGuitar.getProperty("practice.mode"));
		layout.set(modeLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.mode = uiFactory.createDropDownSelect(this.panel);
		this.mode.addItem(new UISelectItem<FollowSettings.Mode>(TuxGuitar.getProperty("practice.mode.wait"), FollowSettings.Mode.WAIT));
		this.mode.addItem(new UISelectItem<FollowSettings.Mode>(TuxGuitar.getProperty("practice.mode.follow"), FollowSettings.Mode.FOLLOW));
		this.mode.addItem(new UISelectItem<FollowSettings.Mode>(TuxGuitar.getProperty("practice.mode.arcade", new String[] { TuxGuitar.getProperty("arcade.name") }),
				FollowSettings.Mode.ARCADE));
		layout.set(this.mode, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.modeHint = wrap(uiFactory, layout, 2);

		this.percentLabel = uiFactory.createLabel(this.panel);
		this.percentLabel.setText(TuxGuitar.getProperty("practice.follow.percent"));
		layout.set(this.percentLabel, 3, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.percent = uiFactory.createSpinner(this.panel);
		this.percent.setMinimum(FollowSettings.MIN_PERCENT);
		this.percent.setMaximum(FollowSettings.MAX_PERCENT);
		this.percent.setIncrement(5);
		layout.set(this.percent, 3, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.percentHint = wrap(uiFactory, layout, 4);
		this.percentHint.setText(TuxGuitar.getProperty("practice.follow.percent.hint",
				new String[] { Integer.toString(FollowSettings.MIN_PERCENT), Integer.toString(FollowSettings.MAX_PERCENT) }));

		this.markerLabel = uiFactory.createLabel(this.panel);
		this.markerLabel.setText(TuxGuitar.getProperty("arcade.marker.size"));
		layout.set(this.markerLabel, 15, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.marker = uiFactory.createSpinner(this.panel);
		this.marker.setMinimum(FollowSettings.MIN_MARKER_PERCENT);
		this.marker.setMaximum(FollowSettings.MAX_MARKER_PERCENT);
		this.marker.setIncrement(5);
		layout.set(this.marker, 15, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		// the tilt of the plane (owner, 2026-10-11): the scale at the horizon (the lower, the more tilted) and the height of the horizon
		this.farScaleLabel = uiFactory.createLabel(this.panel);
		this.farScaleLabel.setText(TuxGuitar.getProperty("arcade.tilt.scale"));
		layout.set(this.farScaleLabel, 16, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.farScale = uiFactory.createSpinner(this.panel);
		this.farScale.setMinimum(FollowSettings.MIN_FAR_SCALE_PERCENT);
		this.farScale.setMaximum(FollowSettings.MAX_FAR_SCALE_PERCENT);
		this.farScale.setIncrement(5);
		layout.set(this.farScale, 16, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.horizonLabel = uiFactory.createLabel(this.panel);
		this.horizonLabel.setText(TuxGuitar.getProperty("arcade.tilt.horizon"));
		layout.set(this.horizonLabel, 17, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.horizon = uiFactory.createSpinner(this.panel);
		this.horizon.setMinimum(FollowSettings.MIN_HORIZON_PERCENT);
		this.horizon.setMaximum(FollowSettings.MAX_HORIZON_PERCENT);
		this.horizon.setIncrement(2);
		layout.set(this.horizon, 17, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);

		// the stage flames behind the picture (spec 009 FR-008): off, discreet or full, read when a run starts
		this.flamesLabel = uiFactory.createLabel(this.panel);
		this.flamesLabel.setText(TuxGuitar.getProperty("arcade.flames.label"));
		layout.set(this.flamesLabel, 18, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.flames = uiFactory.createDropDownSelect(this.panel);
		this.flames.addItem(new UISelectItem<FlameSetting>(TuxGuitar.getProperty("arcade.flames.off"), FlameSetting.OFF));
		this.flames.addItem(new UISelectItem<FlameSetting>(TuxGuitar.getProperty("arcade.flames.discreet"), FlameSetting.DISCREET));
		this.flames.addItem(new UISelectItem<FlameSetting>(TuxGuitar.getProperty("arcade.flames.full"), FlameSetting.FULL));
		layout.set(this.flames, 18, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);

		// the permissiveness of the Arcade recognition (spec 014, FR-002): three positions, stored at once, read when a run starts
		if (arcadeChoices != null) {
			this.leniencyLabel = uiFactory.createLabel(this.panel);
			this.leniencyLabel.setText(TuxGuitar.getProperty("arcade.leniency.label"));
			layout.set(this.leniencyLabel, 19, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
			this.leniency = uiFactory.createDropDownSelect(this.panel);
			for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
				this.leniency.addItem(new UISelectItem<ArcadeLeniencyPosition>(TuxGuitar.getProperty("arcade.leniency." + position.name().toLowerCase(java.util.Locale.ROOT)), position));
			}
			layout.set(this.leniency, 19, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
			this.leniencyHint = wrap(uiFactory, layout, 20);
			this.leniency.setSelectedValue(arcadeChoices.leniency());
			showLeniencyHint();
		} else {
			this.leniencyLabel = null;
			this.leniency = null;
			this.leniencyHint = null;
		}

		this.toleranceLabel = uiFactory.createLabel(this.panel);
		this.toleranceLabel.setText(TuxGuitar.getProperty("practice.follow.tolerance"));
		layout.set(this.toleranceLabel, 5, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		// the timing window (spec 012): a slider in milliseconds each side at 120 beats a minute, one saved setting
		this.tolerance = uiFactory.createHorizontalScale(this.panel);
		this.tolerance.setMinimum(TimingTolerance.MIN_WIDTH_MILLIS);
		this.tolerance.setMaximum(TimingTolerance.MAX_WIDTH_MILLIS);
		this.tolerance.setIncrement(5);
		this.tolerance.setValue(settings.getWindowMillis());
		layout.set(this.tolerance, 5, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.toleranceHint = wrap(uiFactory, layout, 6);
		showWindowText();

		this.calibrationTitle = uiFactory.createLabel(this.panel);
		this.calibrationTitle.setText(TuxGuitar.getProperty("practice.follow.calibration"));
		layout.set(this.calibrationTitle, 7, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.calibrationState = uiFactory.createLabel(this.panel);
		layout.set(this.calibrationState, 7, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.calibrationRoute = wrap(uiFactory, layout, 8);
		this.calibrationRoute.setText(TuxGuitar.getProperty("practice.follow.calibration.route"));

		this.manualLabel = uiFactory.createLabel(this.panel);
		this.manualLabel.setText(TuxGuitar.getProperty("practice.follow.calibration.manual"));
		layout.set(this.manualLabel, 9, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		UITableLayout manualLayout = new UITableLayout(0f);
		manualLayout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.manualRow = uiFactory.createPanel(this.panel, false);
		this.manualRow.setLayout(manualLayout);
		layout.set(this.manualRow, 9, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false);
		this.manual = uiFactory.createSpinner(this.manualRow);
		this.manual.setMinimum((int) -LatencyCompensation.LIMIT_MILLIS);
		this.manual.setMaximum((int) LatencyCompensation.LIMIT_MILLIS);
		this.manual.setIncrement(5);
		manualLayout.set(this.manual, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.manualApply = uiFactory.createButton(this.manualRow);
		this.manualApply.setText(TuxGuitar.getProperty("practice.follow.calibration.manual.apply"));
		manualLayout.set(this.manualApply, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.manualForget = uiFactory.createButton(this.manualRow);
		this.manualForget.setText(TuxGuitar.getProperty("practice.follow.calibration.manual.clear"));
		manualLayout.set(this.manualForget, 1, 3, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.manualHint = wrap(uiFactory, layout, 10);
		this.manualHint.setText(TuxGuitar.getProperty("practice.follow.calibration.manual.hint",
				new String[] { Integer.toString((int) -LatencyCompensation.LIMIT_MILLIS), Integer.toString((int) LatencyCompensation.LIMIT_MILLIS) }));

		this.announce = wrap(uiFactory, layout, 11);
		this.announce.setText(TuxGuitar.getProperty("practice.follow.calibration.announce",
				new String[] { Integer.toString(CalibrationTaps.CLICKS), Integer.toString(CalibrationRun.announcedSeconds()) }));
		UITableLayout buttonsLayout = new UITableLayout(0f);
		buttonsLayout.set(UITableLayout.IGNORE_INVISIBLE, Boolean.TRUE);
		this.buttonsRow = uiFactory.createPanel(this.panel, false);
		this.buttonsRow.setLayout(buttonsLayout);
		layout.set(this.buttonsRow, 12, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		this.calibrate = uiFactory.createButton(this.buttonsRow);
		this.calibrate.setText(TuxGuitar.getProperty("practice.follow.calibration.start"));
		buttonsLayout.set(this.calibrate, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.cancel = uiFactory.createButton(this.buttonsRow);
		this.cancel.setText(TuxGuitar.getProperty("practice.follow.calibration.cancel"));
		buttonsLayout.set(this.cancel, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, false, false);
		this.progress = wrap(uiFactory, layout, 13);
		this.precision = wrap(uiFactory, layout, 14);

		this.mode.addSelectionListener(selected(() -> {
			FollowSettings.Mode chosen = this.mode.getSelectedValue();
			if (chosen != null) {
				this.settings.setMode(chosen);
				refresh();
				if (this.modeListener != null) {
					this.modeListener.run();
				}
			}
		}));
		this.percent.addSelectionListener(selected(() -> {
			int value = this.settings.setPercent(this.percent.getValue());
			if (value != this.percent.getValue()) {
				this.updating = true;
				this.percent.setValue(value);
				this.updating = false;
			}
		}));
		this.marker.addSelectionListener(selected(() -> {
			int value = this.settings.setMarkerPercent(this.marker.getValue());
			if (value != this.marker.getValue()) {
				this.updating = true;
				this.marker.setValue(value);
				this.updating = false;
			}
		}));
		this.farScale.addSelectionListener(selected(() -> {
			int value = this.settings.setFarScalePercent(this.farScale.getValue());
			if (value != this.farScale.getValue()) {
				this.updating = true;
				this.farScale.setValue(value);
				this.updating = false;
			}
		}));
		this.horizon.addSelectionListener(selected(() -> {
			int value = this.settings.setHorizonPercent(this.horizon.getValue());
			if (value != this.horizon.getValue()) {
				this.updating = true;
				this.horizon.setValue(value);
				this.updating = false;
			}
		}));
		if (this.leniency != null) {
			this.leniency.addSelectionListener(selected(() -> {
				ArcadeLeniencyPosition chosen = this.leniency.getSelectedValue();
				if (chosen != null) {
					this.arcadeChoices.setLeniency(chosen);
					showLeniencyHint();
				}
			}));
		}
		this.flames.addSelectionListener(selected(() -> {
			FlameSetting chosen = this.flames.getSelectedValue();
			if (chosen != null) {
				this.settings.setFlames(chosen);
			}
		}));
		this.tolerance.addSelectionListener(selected(() -> {
			this.settings.setWindowMillis(this.tolerance.getValue());
			showWindowText();
		}));
		this.manualApply.addSelectionListener(selected(this::applyManual));
		this.manualForget.addSelectionListener(selected(this::forget));
		this.calibrate.addSelectionListener(selected(this::startCalibration));
		this.cancel.addSelectionListener(selected(this::cancelCalibration));
		refresh();
	}

	private UILabel wrap(UIFactory uiFactory, UITableLayout layout, int row) {
		UILabel label = uiFactory.createWrapLabel(this.panel);
		layout.set(label, row, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, false, 1, 2);
		layout.set(label, UITableLayout.PACKED_WIDTH, InputSelectorPanel.WRAP_WIDTH);
		return label;
	}

	private UISelectionListener selected(Runnable action) {
		return new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				if (!FollowPanel.this.updating) {
					action.run();
				}
			}
		};
	}

	public UIPanel getControl() {
		return this.panel;
	}

	/** Called after the content changed size, so the window can refit. */
	public void setContentListener(Runnable contentListener) {
		this.contentListener = contentListener;
	}

	/** Called after the learner chose another mode (the window changes the text of its start button). */
	public void setModeListener(Runnable modeListener) {
		this.modeListener = modeListener;
	}

	/** Shows what the settings and the route say now: after a mode change, an input change, or a calibration. */
	public void refresh() {
		if (this.panel.isDisposed()) {
			return;
		}
		this.updating = true;
		try {
			// Arcade is the tempo-following run too: it shares the percentage, the tolerance and the calibration
			boolean follow = this.settings.isTimed();
			this.mode.setSelectedValue(this.settings.getMode());
			this.percent.setValue(this.settings.getPercent());
			this.marker.setValue(this.settings.getMarkerPercent());
			this.markerLabel.setVisible(this.settings.isArcade());
			this.marker.setVisible(this.settings.isArcade());
			this.farScale.setValue(this.settings.getFarScalePercent());
			this.horizon.setValue(this.settings.getHorizonPercent());
			this.farScaleLabel.setVisible(this.settings.isArcade());
			this.farScale.setVisible(this.settings.isArcade());
			this.horizonLabel.setVisible(this.settings.isArcade());
			this.horizon.setVisible(this.settings.isArcade());
			this.flames.setSelectedValue(this.settings.getFlames());
			this.flamesLabel.setVisible(this.settings.isArcade());
			this.flames.setVisible(this.settings.isArcade());
			if (this.leniency != null) {
				this.leniency.setSelectedValue(this.arcadeChoices.leniency());
				showLeniencyHint();
				this.leniencyLabel.setVisible(this.settings.isArcade());
				this.leniency.setVisible(this.settings.isArcade());
				this.leniencyHint.setVisible(this.settings.isArcade());
			}
			this.tolerance.setValue(this.settings.getWindowMillis());
			showWindowText();
			this.modeHint.setText(this.settings.isArcade() ? TuxGuitar.getProperty("practice.mode.arcade.hint") + "\n" + TuxGuitar.getProperty("arcade.monitoring.unavailable")
					: TuxGuitar.getProperty(follow ? "practice.mode.follow.hint" : "practice.mode.wait.hint"));
			boolean idle = (this.running == null);
			this.percentLabel.setVisible(follow);
			this.percent.setVisible(follow);
			this.percentHint.setVisible(follow);
			this.toleranceLabel.setVisible(follow);
			this.tolerance.setVisible(follow);
			this.toleranceHint.setVisible(follow);
			this.calibrationTitle.setVisible(follow);
			this.calibrationState.setVisible(follow);
			this.calibrationRoute.setVisible(follow);
			this.manualLabel.setVisible(follow);
			this.manualRow.setVisible(follow);
			this.manual.setVisible(follow);
			this.manualApply.setVisible(follow);
			this.manualForget.setVisible(follow);
			this.manualHint.setVisible(follow);
			this.announce.setVisible(follow);
			this.buttonsRow.setVisible(follow);
			this.calibrate.setVisible(follow);
			this.cancel.setVisible(follow);
			this.progress.setVisible(follow);
			this.precision.setVisible(follow);
			this.mode.setEnabled(idle);
			this.percent.setEnabled(idle);
			this.marker.setEnabled(idle);
			this.farScale.setEnabled(idle);
			this.horizon.setEnabled(idle);
			this.flames.setEnabled(idle);
			if (this.leniency != null) {
				this.leniency.setEnabled(idle);
			}
			this.tolerance.setEnabled(idle);
			this.manual.setEnabled(idle);
			AudioRoute route = this.backend.route();
			LatencyCompensation compensation = this.backend.calibrations().forRoute(route);
			this.calibrationState.setText(stateText(compensation));
			if (compensation.isCalibrated()) {
				this.manual.setValue((int) Math.round(compensation.offsetNanos() / 1e6));
			}
			this.manualApply.setEnabled(idle && route != null);
			this.manualForget.setEnabled(idle && route != null && compensation.isCalibrated());
			this.calibrate.setEnabled(idle && route != null);
			this.cancel.setEnabled(!idle);
			this.precision.setText(precisionText(compensation, route));
		} finally {
			this.updating = false;
		}
		this.panel.layout();
		if (this.contentListener != null) {
			this.contentListener.run();
		}
	}

	private static String stateText(LatencyCompensation compensation) {
		String millis = Long.toString(Math.round(compensation.offsetNanos() / 1e6));
		switch (compensation.source()) {
			case CALIBRATED:
				return TuxGuitar.getProperty("practice.follow.calibration.state.calibrated", new String[] { millis });
			case MANUAL:
				return TuxGuitar.getProperty("practice.follow.calibration.state.manual", new String[] { millis });
			default:
				return TuxGuitar.getProperty("practice.follow.calibration.state.none");
		}
	}

	/** "Reduced rhythmic precision" while the route is not calibrated, and the warning of an unstable delay: never a block. */
	private String precisionText(LatencyCompensation compensation, AudioRoute route) {
		StringBuilder text = new StringBuilder();
		if (!compensation.isCalibrated()) {
			text.append(TuxGuitar.getProperty("practice.follow.precision.reduced")).append(". ")
					.append(TuxGuitar.getProperty("practice.follow.precision.reason.NOT_CALIBRATED"));
		}
		double spread = this.backend.lagSpreadMillis();
		if (!Double.isNaN(spread) && new ClockUncertainty(spread).isUnstable()) {
			if (text.length() == 0) {
				text.append(TuxGuitar.getProperty("practice.follow.precision.reduced")).append(". ");
			} else {
				text.append("\n");
			}
			text.append(TuxGuitar.getProperty("practice.follow.precision.reason.UNSTABLE_ROUTE"));
		}
		return text.toString();
	}

	private void applyManual() {
		AudioRoute route = this.backend.route();
		if (route == null) {
			return;
		}
		int millis = Math.max((int) -LatencyCompensation.LIMIT_MILLIS, Math.min((int) LatencyCompensation.LIMIT_MILLIS, this.manual.getValue()));
		this.backend.calibrations().set(route, LatencyCompensation.manual(millis));
		this.progress.setText("");
		refresh();
	}

	private void forget() {
		AudioRoute route = this.backend.route();
		if (route != null) {
			this.backend.calibrations().forget(route);
			this.progress.setText("");
			refresh();
		}
	}

	private void startCalibration() {
		if (this.running != null || this.backend.route() == null) {
			return;
		}
		CalibrationRun run = this.backend.newCalibration();
		if (run == null) {
			this.progress.setText(TuxGuitar.getProperty(this.backend.playerBusy() ? "practice.follow.player-busy" : "practice.follow.calibration.no-input"));
			relayout();
			return;
		}
		this.running = run;
		run.start();
		this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.running", new String[] { "0", Integer.toString(run.clickCount()) }));
		refresh();
		this.backend.begin(run, this::calibrationChanged);
	}

	/** The run moved (UI thread). */
	void calibrationChanged() {
		CalibrationRun run = this.running;
		if (run == null || this.panel.isDisposed()) {
			return;
		}
		switch (run.state()) {
			case FINISHED:
				finish(run);
				break;
			case FAILED:
				this.backend.end(run);
				this.running = null;
				this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.no-input"));
				refresh();
				break;
			case CANCELLED:
				break;
			default:
				this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.running",
						new String[] { Integer.toString(run.clicksPlayed()), Integer.toString(run.clickCount()) }));
				relayout();
				break;
		}
	}

	private void finish(CalibrationRun run) {
		this.backend.end(run);
		this.running = null;
		CalibrationTaps.Result result = run.result();
		AudioRoute route = this.backend.route();
		switch (result.status()) {
			case CALIBRATED:
				if (route != null) {
					this.backend.calibrations().set(route, result.compensation());
				}
				this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.done",
						new String[] { Long.toString(Math.round(result.medianMillis())), Integer.toString(result.used()) }));
				break;
			case UNSTABLE:
			case OUT_OF_RANGE:
				this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.unstable", new String[] { Long.toString(Math.round(result.spreadMillis())) }));
				break;
			default:
				this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.too-few",
						new String[] { Integer.toString(result.used()), Integer.toString(result.total()) }));
				break;
		}
		refresh();
	}

	/** Cancel: the clicks stop at once and nothing is kept. */
	private void cancelCalibration() {
		CalibrationRun run = this.running;
		if (run == null) {
			return;
		}
		run.cancel();
		this.backend.end(run);
		this.running = null;
		this.progress.setText(TuxGuitar.getProperty("practice.follow.calibration.cancelled"));
		refresh();
	}

	/** A message of the run (why it ended, why it did not start) under the controls, in the language of the learner. */
	public void message(String text) {
		if (!this.panel.isDisposed()) {
			this.progress.setText(text);
			relayout();
		}
	}

	/** Whether a calibration is running now. */
	public boolean isCalibrating() {
		return this.running != null;
	}

	/** The window closes: a calibration that runs stops at once. */
	public void dispose() {
		CalibrationRun run = this.running;
		if (run != null) {
			run.cancel();
			this.backend.end(run);
			this.running = null;
		}
	}

	private void relayout() {
		this.panel.layout();
		if (this.contentListener != null) {
			this.contentListener.run();
		}
	}

	// ------------------------------------------------------------------ for the tests of the panel

	UIDropDownSelect<FollowSettings.Mode> modeSelect() {
		return this.mode;
	}

	UIDropDownSelect<FlameSetting> flamesSelect() {
		return this.flames;
	}

	/** The line of the Arcade permissiveness, or null when the panel has no remembered choices. */
	UIDropDownSelect<ArcadeLeniencyPosition> leniencySelect() {
		return this.leniency;
	}

	UILabel leniencyLabel() {
		return this.leniencyLabel;
	}

	UILabel leniencyHint() {
		return this.leniencyHint;
	}

	private void showLeniencyHint() {
		ArcadeLeniencyPosition position = this.leniency.getSelectedValue();
		this.leniencyHint.setText(TuxGuitar.getProperty("arcade.leniency.hint." + (position == null ? ArcadeLeniencyPosition.DEFAULT : position).name().toLowerCase(java.util.Locale.ROOT)));
	}

	UISpinner percentSpinner() {
		return this.percent;
	}

	/** The value of the window slider and what it means, with the hint (spec 012). */
	private void showWindowText() {
		this.toleranceHint.setText(TuxGuitar.getProperty("practice.follow.tolerance.value", new String[] { Integer.toString(this.tolerance.getValue()) }) + "\n"
				+ TuxGuitar.getProperty("practice.follow.tolerance.hint"));
	}

	UIScale windowSlider() {
		return this.tolerance;
	}

	UISpinner manualSpinner() {
		return this.manual;
	}

	UIButton manualApplyButton() {
		return this.manualApply;
	}

	UIButton manualForgetButton() {
		return this.manualForget;
	}

	UIButton calibrateButton() {
		return this.calibrate;
	}

	UIButton cancelButton() {
		return this.cancel;
	}

	UILabel calibrationStateLabel() {
		return this.calibrationState;
	}

	UILabel progressLabel() {
		return this.progress;
	}

	UILabel precisionLabel() {
		return this.precision;
	}

	UILabel modeHintLabel() {
		return this.modeHint;
	}

	UILabel announceLabel() {
		return this.announce;
	}
}
