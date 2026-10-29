package app.tuxguitar.app.tools.custom.practice.session;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.util.TGContext;

/**
 * One run of the tempo-following mode, without a window (spec 002 T096): the session, its thread, the player and what ends it. The window starts it,
 * posts the frames and the commands to it, renders its snapshots and is told once, whatever the cause, that it ended; every temporary state of the
 * player is given back exactly once ({@link FollowPlayback}).
 * <p>
 * Ends: {@link #stop()} (Stop, closing the window, a failure, a change of mode), a play of the learner (the run stops cleanly and the normal playback
 * goes on), the player stopping. The run is never restarted by itself.
 */
public final class FollowRun {

	/** Why a run ended; the window shows the matching message (a play of the learner has its own). */
	public enum Ended {
		/** The learner or the window ended it ({@link #stop()}): nothing to say. */
		STOPPED,
		/** The learner played the score meanwhile: the run ended and the playback goes on. */
		MANUAL_PLAY,
		/** The player stopped (the end of the song, or Stop of the transport). */
		PLAYER_STOPPED,
		/** The session or the display threw: the run ended and everything was given back. */
		FAILED,
		/** A Stop command of the learner (a shortcut) or a new song ended it: the window goes back to the preparation without a message. */
		COMMAND
	}

	private final FollowHandler handler;
	private final SessionLoop<FollowSession.Snapshot> loop;
	private final FollowSession session;
	private final FollowPlayback playback;
	private final FollowEvents events;
	private final Consumer<Ended> endListener;
	private final AtomicBoolean finished = new AtomicBoolean();
	private boolean started;
	private volatile Consumer<FollowSession> endHook;

	/**
	 * @param uiSink receives the snapshots, on {@code uiExecutor}
	 * @param endListener told once when the run ends, on {@code uiExecutor}; for {@link Ended#STOPPED} too
	 * @param eventsFactory connects the input, tuner and song events; null in tests that do not need them
	 */
	public FollowRun(TGContext context, FollowSession session, FollowPlayer player, int trackNumber, int percent, long startTick,
			Consumer<FollowSession.Snapshot> uiSink, Executor uiExecutor, Consumer<Ended> endListener, EventsFactory eventsFactory,
			Runnable restart) {
		this.session = session;
		this.endListener = endListener;
		this.uiExecutor = uiExecutor;
		FollowHandler handler = new FollowHandler(session, player, restart, exception -> failed(), this::commanded);
		this.handler = handler;
		this.loop = new SessionLoop<FollowSession.Snapshot>(context, handler, snapshot -> {
			try {
				uiSink.accept(snapshot);
			} catch (RuntimeException exception) {
				failed();
			}
		}, uiExecutor);
		this.events = (eventsFactory != null ? eventsFactory.create(this.loop, handler) : null);
		this.playback = new FollowPlayback(player, trackNumber, percent, startTick, reason -> ended(
				reason == FollowPlayback.EndReason.MANUAL_PLAY ? Ended.MANUAL_PLAY : Ended.PLAYER_STOPPED, uiExecutor));
	}

	private final Executor uiExecutor;

	/** Builds the events of a run on its loop and handler. */
	public interface EventsFactory {
		FollowEvents create(SessionLoop<FollowSession.Snapshot> loop, FollowHandler handler);
	}

	/**
	 * Starts the session thread and the player. When the player cannot start, everything is given back and the result says why.
	 */
	public FollowPlayback.Result start() {
		if (this.started) {
			return FollowPlayback.Result.NOT_IDLE;
		}
		this.started = true;
		this.loop.start();
		if (this.events != null) {
			this.events.connect();
		}
		this.loop.postCommand(PracticeCommand.START);
		FollowPlayback.Result result = this.playback.start();
		if (result != FollowPlayback.Result.STARTED) {
			finish();
		}
		return result;
	}

	/**
	 * The learner pauses (Arcade, spec 003 US5.2): the player stops where it is and the session stops judging, together. The events that go by
	 * meanwhile are not judged. Nothing happens unless the run is going.
	 */
	public void pause() {
		if (!this.finished.get() && this.playback.state() == FollowPlayback.State.PLAYING) {
			this.playback.pause();
			this.loop.post(() -> this.handler.pause(PracticeSession.PauseReason.USER));
		}
	}

	/**
	 * The learner resumes: the player counts in and plays again from where it stopped, and the session goes on when no other cause of pause remains
	 * (an unusable input keeps it paused). The session does not judge during the count-in: the clock of the player is stalled until it moves.
	 *
	 * @return false when the run was not paused or the player could not start again (the run is then ended and everything given back)
	 */
	public boolean resume() {
		if (this.finished.get() || this.playback.state() != FollowPlayback.State.PAUSED) {
			return false;
		}
		if (!this.playback.resume()) {
			ended(Ended.FAILED, this.uiExecutor);
			return false;
		}
		this.loop.post(() -> {
			this.handler.resolve(PracticeSession.PauseReason.USER);
			return this.handler.onCommand(PracticeCommand.RESUME);
		});
		return true;
	}

	/**
	 * Told once, on the thread of the session, when the run ends for whatever reason, before the session stops: the record of the run (spec 016
	 * FR-003) reads the session there, where nothing else changes it. Set before {@link #start()}; what it throws is ignored.
	 */
	public void setEndHook(Consumer<FollowSession> endHook) {
		this.endHook = endHook;
	}

	/** A frame of the analysis (any thread). */
	public void postFrame(AnalysisFrame frame) {
		this.loop.postFrame(frame);
	}

	public void postCommand(PracticeCommand command) {
		this.loop.postCommand(command);
	}

	/** Ends the run now, for whatever reason of the owner; calling it again does nothing. */
	public void stop() {
		this.playback.stop();
		ended(Ended.STOPPED, this.uiExecutor);
	}

	/** The Stop command reached the session: the whole run ends, the player does not go on without it. */
	private void commanded() {
		this.playback.stop();
		ended(Ended.COMMAND, this.uiExecutor);
	}

	private void failed() {
		this.playback.stop();
		ended(Ended.FAILED, this.uiExecutor);
	}

	public boolean isFinished() {
		return this.finished.get();
	}

	private void ended(Ended reason, Executor uiExecutor) {
		if (finish()) {
			uiExecutor.execute(() -> this.endListener.accept(reason));
		}
	}

	/** @return true for the call that finished the run */
	private boolean finish() {
		if (!this.finished.compareAndSet(false, true)) {
			return false;
		}
		this.playback.stop();
		if (this.events != null) {
			this.events.disconnect();
		}
		final Consumer<FollowSession> hook = this.endHook;
		this.loop.post(() -> {
			if (hook != null) {
				try {
					hook.accept(this.session);
				} catch (RuntimeException exception) {
					// the record of a run never changes how it ends
				}
			}
			this.session.stop();
			return this.session.snapshot();
		});
		this.loop.stop();
		return true;
	}
}
