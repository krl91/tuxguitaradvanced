package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.app.TuxGuitar;
import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationRun;
import app.tuxguitar.app.tools.custom.practice.session.CalibrationTaps;

/**
 * One rhythm calibration on the clicks of the player, from its start to its result (spec 002 T098, moved out of {@link FollowPanel} by spec 016 T019 so
 * that the start window runs the same one): a result is kept for the route as from the Mode tab, and the texts are the same. The window that shows it
 * gives the place of its sentence and what to redraw.
 */
final class CalibrationFlow {

	/** What the window does for the calibration (user-interface thread). */
	interface View {
		/** The sentence about the calibration: running, done, why it did not start or failed. */
		void progress(String text);

		/** The calibration began or ended: the controls change. */
		void refresh();

		/** Only the sentence changed: the window refits. */
		void relayout();

		/** The calibration ended; {@code calibrated} when the route now has a measured delay. */
		default void ended(boolean calibrated) {
		}
	}

	private final FollowPanel.Backend backend;
	private final View view;
	private CalibrationRun running;

	CalibrationFlow(FollowPanel.Backend backend, View view) {
		this.backend = backend;
		this.view = view;
	}

	boolean isRunning() {
		return this.running != null;
	}

	/** @return true when the clicks began; otherwise the sentence says why (a player that plays, no usable input) */
	boolean start() {
		if (this.running != null || this.backend.route() == null) {
			return false;
		}
		CalibrationRun run = this.backend.newCalibration();
		if (run == null) {
			this.view.progress(TuxGuitar.getProperty(this.backend.playerBusy() ? "practice.follow.player-busy" : "practice.follow.calibration.no-input"));
			this.view.relayout();
			return false;
		}
		this.running = run;
		run.start();
		this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.running", new String[] { "0", Integer.toString(run.clickCount()) }));
		this.view.refresh();
		this.backend.begin(run, this::changed);
		return true;
	}

	/** The run moved (UI thread). */
	void changed() {
		CalibrationRun run = this.running;
		if (run == null) {
			return;
		}
		switch (run.state()) {
			case FINISHED:
				finish(run);
				break;
			case FAILED:
				this.backend.end(run);
				this.running = null;
				this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.no-input"));
				this.view.refresh();
				this.view.ended(false);
				break;
			case CANCELLED:
				break;
			default:
				this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.running",
						new String[] { Integer.toString(run.clicksPlayed()), Integer.toString(run.clickCount()) }));
				this.view.relayout();
				break;
		}
	}

	private void finish(CalibrationRun run) {
		this.backend.end(run);
		this.running = null;
		CalibrationTaps.Result result = run.result();
		AudioRoute route = this.backend.route();
		boolean calibrated = false;
		switch (result.status()) {
			case CALIBRATED:
				if (route != null) {
					this.backend.calibrations().set(route, result.compensation());
					calibrated = true;
				}
				this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.done",
						new String[] { Long.toString(Math.round(result.medianMillis())), Integer.toString(result.used()) }));
				break;
			case UNSTABLE:
			case OUT_OF_RANGE:
				this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.unstable", new String[] { Long.toString(Math.round(result.spreadMillis())) }));
				break;
			default:
				this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.too-few",
						new String[] { Integer.toString(result.used()), Integer.toString(result.total()) }));
				break;
		}
		this.view.refresh();
		this.view.ended(calibrated);
	}

	/** Cancel: the clicks stop at once and nothing is kept. */
	void cancel() {
		CalibrationRun run = this.running;
		if (run == null) {
			return;
		}
		run.cancel();
		this.backend.end(run);
		this.running = null;
		this.view.progress(TuxGuitar.getProperty("practice.follow.calibration.cancelled"));
		this.view.refresh();
		this.view.ended(false);
	}

	/** The window closes: a calibration that runs stops at once, silently. */
	void dispose() {
		CalibrationRun run = this.running;
		if (run != null) {
			run.cancel();
			this.backend.end(run);
			this.running = null;
		}
	}
}
