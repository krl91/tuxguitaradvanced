package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowPlayback;
import app.tuxguitar.app.tools.custom.practice.session.FollowRun;

/**
 * The life of one Arcade run, without a widget (spec 003 T052, US1, US5): start, pause, resume, stop, and the end for every cause (Stop, an error, the play of
 * the learner, the end of the song, a new song, the closing of the window). The window listens to it and shows what it says. The end is reported exactly once;
 * a result exists only when the whole passage was judged and the song came to its end, and it is built once and handed to the listener, never kept (FR-026).
 * UI thread only.
 */
public final class ArcadeController {

	public enum State {
		/** Prepared, not started. */
		READY,
		RUNNING,
		PAUSED,
		/** Ended, whatever the cause: nothing more happens. */
		ENDED
	}

	/** What the window shows of the life of the run. */
	public interface Listener {
		void stateChanged(State state);

		/**
		 * The run ended. {@code result} is null unless the passage was judged to its end; {@code reason} says what the window must tell (nothing for the
		 * learner's own Stop or a shortcut).
		 */
		void ended(FollowRun.Ended reason, ArcadeResult result);

		/** The player could not start: nothing was started, everything was given back. */
		void startFailed(FollowPlayback.Result result);
	}

	/** What the controller needs to know of the run to decide about the result: whether the passage was judged to its end, and the result. */
	public interface Outcome {
		boolean isComplete();

		ArcadeResult result();
	}

	private final ArcadeRunPort run;
	private final Outcome session;
	private final Listener listener;
	private State state = State.READY;

	public ArcadeController(ArcadeRunPort run, Outcome session, Listener listener) {
		this.run = run;
		this.session = session;
		this.listener = listener;
	}

	public State state() {
		return this.state;
	}

	/** Starts the run: the count-in, then the backing and the judging together. Only from READY. */
	public void start() {
		if (this.state != State.READY) {
			return;
		}
		FollowPlayback.Result result = this.run.start();
		if (result == FollowPlayback.Result.STARTED) {
			change(State.RUNNING);
		} else {
			this.state = State.ENDED;
			this.listener.startFailed(result);
		}
	}

	/** Pauses the backing and the judging together. Only while running. */
	public void pause() {
		if (this.state == State.RUNNING) {
			this.run.pause();
			change(State.PAUSED);
		}
	}

	/** Resumes after a count-in. Only while paused; when the player cannot start again the run has ended by itself and {@link #ended} says so. */
	public void resume() {
		if (this.state == State.PAUSED && this.run.resume()) {
			change(State.RUNNING);
		}
	}

	/** The learner stops: everything is given back at once. */
	public void stop() {
		if (this.state != State.ENDED) {
			this.run.stop();
		}
	}

	/** The end of the run, from the run's own listener: every cause arrives here, the first one only. */
	public void ended(FollowRun.Ended reason) {
		if (this.state == State.ENDED) {
			return;
		}
		this.state = State.ENDED;
		this.listener.stateChanged(State.ENDED);
		ArcadeResult result = (reason == FollowRun.Ended.PLAYER_STOPPED || reason == FollowRun.Ended.STOPPED) ? this.session.result() : null;
		this.listener.ended(reason, result);
	}

	private void change(State next) {
		this.state = next;
		this.listener.stateChanged(next);
	}
}
