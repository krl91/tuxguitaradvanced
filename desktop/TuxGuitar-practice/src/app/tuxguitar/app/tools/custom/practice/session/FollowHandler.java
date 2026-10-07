package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;

/**
 * The session thread of the tempo-following mode (the same thread model as the wait mode, {@link SessionLoop}): each frame first reads the player's
 * position, then goes to the {@link FollowSession}. The only writer of the session.
 */
public final class FollowHandler implements SessionLoop.Handler<FollowSession.Snapshot>, InputEvents.Pausable<FollowSession.Snapshot> {

	private final FollowSession session;
	private final FollowPlayer player;
	private final Runnable restart;
	private final java.util.function.Consumer<RuntimeException> failure;

	/**
	 * @param restart what a Restart of the learner does (it ends this run and starts another: not this thread's to do); may be null
	 * @param failure told when the session throws: the run must end, the player must not go on without it; may be null
	 */
	public FollowHandler(FollowSession session, FollowPlayer player, Runnable restart, java.util.function.Consumer<RuntimeException> failure) {
		this.session = session;
		this.player = player;
		this.restart = restart;
		this.failure = failure;
	}

	private FollowSession.Snapshot failed(RuntimeException exception) {
		if (this.failure != null) {
			this.failure.accept(exception);
		}
		return null;
	}

	@Override
	public FollowSession.Snapshot onFrame(AnalysisFrame frame) {
		try {
			this.player.pollClock();
			return this.session.onFrame(frame);
		} catch (RuntimeException exception) {
			return failed(exception);
		}
	}

	@Override
	public FollowSession.Snapshot onCommand(PracticeCommand command) {
		try {
			return command(command);
		} catch (RuntimeException exception) {
			return failed(exception);
		}
	}

	private FollowSession.Snapshot command(PracticeCommand command) {
		switch (command) {
			case START:
				this.session.start();
				break;
			case RESUME:
				this.session.resume(null);
				break;
			case STOP:
				this.session.stop();
				break;
			case RESTART:
				if (this.restart != null) {
					this.restart.run();
				}
				break;
			default:
				// Skip: an event of the following mode passes by itself, there is nothing to skip
				break;
		}
		return this.session.snapshot();
	}

	@Override
	public void setAssessment(InputAssessment assessment) {
		this.session.setAssessment(assessment);
	}

	@Override
	public FollowSession.Snapshot pause(PracticeSession.PauseReason reason) {
		this.session.pause(reason);
		return this.session.snapshot();
	}

	@Override
	public FollowSession.Snapshot resolve(PracticeSession.PauseReason reason) {
		this.session.resolve(reason);
		return this.session.snapshot();
	}
}
