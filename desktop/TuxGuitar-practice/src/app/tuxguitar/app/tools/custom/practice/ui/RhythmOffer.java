package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.function.Supplier;

import app.tuxguitar.app.tools.custom.practice.session.AudioRoute;
import app.tuxguitar.app.tools.custom.practice.session.FollowSettings;
import app.tuxguitar.app.tools.custom.practice.session.RouteCalibrations;

/**
 * The step of the start window that offers the rhythm calibration (spec 016 FR-004, contracts/calibration-offer.md): after the level, for Follow and
 * Arcade, on a route that is known, not calibrated and not declined in this launch. No widget. It lives beside {@link PreRunCheck}, which stays as it
 * is: the window enters the step once the check is ready, and the mode begins only when {@link #allowsStart()}. Events outside their state are ignored;
 * {@link State#DONE} and {@link State#CANCELLED} are final.
 */
final class RhythmOffer {

	enum State {
		/** The check is not ready yet. */
		NOT_ASKED,
		/** The sentence with Calibrate now and Go on without. */
		OFFER,
		/** The clicks of the calibration run. */
		CALIBRATING,
		/** The mode may begin: the route needs nothing, was calibrated, or the learner went on without. Final. */
		DONE,
		/** The start was cancelled during the step: nothing begins. Final. */
		CANCELLED
	}

	private final FollowSettings.Mode mode;
	private final Supplier<AudioRoute> route;
	private final RouteCalibrations calibrations;
	private final DeclinedRoutes declined;
	private State state = State.NOT_ASKED;
	private AudioRoute asked;

	RhythmOffer(FollowSettings.Mode mode, Supplier<AudioRoute> route, RouteCalibrations calibrations, DeclinedRoutes declined) {
		this.mode = mode;
		this.route = route;
		this.calibrations = calibrations;
		this.declined = declined;
	}

	synchronized State state() {
		return this.state;
	}

	/**
	 * The check is ready: the step is shown when the mode is a timed one and the route now is known, not calibrated (for this input, output and format)
	 * and not declined; otherwise it is done at once.
	 *
	 * @return true when the step is shown (also when it already was)
	 */
	synchronized boolean enter() {
		if (this.state == State.NOT_ASKED) {
			AudioRoute now = this.route.get();
			boolean timed = this.mode == FollowSettings.Mode.FOLLOW || this.mode == FollowSettings.Mode.ARCADE;
			if (timed && now != null && !this.calibrations.forRoute(now).isCalibrated() && !this.declined.isDeclined(now)) {
				this.asked = now;
				this.state = State.OFFER;
			} else {
				this.state = State.DONE;
			}
		}
		return this.state == State.OFFER || this.state == State.CALIBRATING;
	}

	/** Calibrate now: the clicks begin. */
	synchronized void calibrate() {
		if (this.state == State.OFFER) {
			this.state = State.CALIBRATING;
		}
	}

	/** The calibration ended: calibrated, the run begins; cancelled or failed, the offer again (the window shows why). */
	synchronized void calibrationEnded(boolean calibrated) {
		if (this.state == State.CALIBRATING) {
			this.state = calibrated ? State.DONE : State.OFFER;
		}
	}

	/** Go on without: the run begins, and this route is not asked again until TuxGuitar starts again. */
	synchronized void goOnWithout() {
		if (this.state == State.OFFER) {
			this.declined.decline(this.asked);
			this.state = State.DONE;
		}
	}

	/** Close or Escape during the step: nothing begins; it never undoes {@link State#DONE}. */
	synchronized void cancel() {
		if (this.state != State.DONE) {
			this.state = State.CANCELLED;
		}
	}

	/** True when the mode may begin as far as the rhythm is concerned. */
	synchronized boolean allowsStart() {
		return this.state == State.DONE;
	}
}
