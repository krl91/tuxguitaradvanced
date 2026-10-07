package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.player.base.MidiPlayerException;

/**
 * The life of one tempo-following run on the player (spec 002 T096): it takes the player (the practised track muted in the player only, the tempo
 * percentage, a count-down, no loop), starts it, tells why it ended, and gives everything back <b>exactly once</b>, whatever ends it: {@link #stop()},
 * a start that failed, a player that stopped by itself or by someone else, or a play asked by the learner meanwhile.
 * <p>
 * What it changes is only what the {@link FollowPlayer} port offers: the song, its tracks, their mute and solo and the undo stack are never touched.
 * The start notification of the run's own {@code play()} comes later and from another thread: it is told apart from a play of the learner by
 * being the first one after {@link #start()}.
 */
public final class FollowPlayback implements FollowPlayer.Listener {

	public enum State {
		IDLE,
		STARTING,
		PLAYING,
		ENDED
	}

	public enum Result {
		STARTED,
		/** The player is already playing: the learner's own playback is never taken over. */
		ALREADY_PLAYING,
		/** The player could not start; everything was given back. */
		FAILED,
		/** This run was already started or ended: a run is used once. */
		NOT_IDLE
	}

	/** Why a run that was not stopped by {@link #stop()} ended. */
	public enum EndReason {
		/** The learner started a playback while the run was going: the run is over and the normal playback goes on. */
		MANUAL_PLAY,
		/** The player stopped (the end of the song, or the learner stopped it). */
		PLAYER_STOPPED
	}

	public interface Listener {
		/** Called once, after everything was given back; never for a {@link #stop()} of the owner. */
		void ended(EndReason reason);
	}

	private final FollowPlayer player;
	private final int trackNumber;
	private final int percent;
	private final long startTick;
	private final Listener listener;

	private State state = State.IDLE;
	private boolean ownStartPending;
	private boolean ownStopAsked;
	private int previousPercent;
	private boolean previousCountDown;
	private boolean previousLoop;
	private boolean changedPercent;
	private boolean changedCountDown;
	private boolean changedLoop;
	private boolean mutedTrack;

	/**
	 * @param trackNumber the number of the practised track
	 * @param percent the tempo percentage of the run
	 * @param startTick the sequencer tick the run starts at
	 */
	public FollowPlayback(FollowPlayer player, int trackNumber, int percent, long startTick, Listener listener) {
		this.player = player;
		this.trackNumber = trackNumber;
		this.percent = percent;
		this.startTick = startTick;
		this.listener = listener;
	}

	public synchronized State state() {
		return this.state;
	}

	public Result start() {
		synchronized (this) {
			if (this.state != State.IDLE) {
				return Result.NOT_IDLE;
			}
			if (this.player.isRunning()) {
				this.state = State.ENDED;
				return Result.ALREADY_PLAYING;
			}
			this.state = State.STARTING;
			this.ownStartPending = true;
		}
		try {
			this.player.addListener(this);
			// the run changes the player first, so that nothing of the practised track is sent before the mute
			this.player.setPlayerOnlyMute(this.trackNumber, true);
			this.mutedTrack = true;
			this.previousPercent = this.player.tempoPercent();
			this.player.setTempoPercent(this.percent);
			this.changedPercent = true;
			this.previousCountDown = this.player.countDownEnabled();
			this.player.setCountDownEnabled(true);
			this.changedCountDown = true;
			this.previousLoop = this.player.loopEnabled();
			this.player.setLoopEnabled(false);
			this.changedLoop = true;
			this.player.setTickPosition(this.startTick);
			this.player.play();
		} catch (MidiPlayerException | RuntimeException exception) {
			giveBack(true);
			return Result.FAILED;
		}
		synchronized (this) {
			if (this.state == State.STARTING) {
				this.state = State.PLAYING;
			}
		}
		return Result.STARTED;
	}

	/** Ends the run and gives everything back; calling it again, or after the run ended by itself, does nothing. */
	public void stop() {
		synchronized (this) {
			if (this.state == State.ENDED) {
				return;
			}
			if (this.state == State.IDLE) {
				this.state = State.ENDED;
				return;
			}
			this.ownStopAsked = true;
		}
		giveBack(true);
	}

	@Override
	public void playbackStarted() {
		EndReason reason = null;
		synchronized (this) {
			if (this.state != State.STARTING && this.state != State.PLAYING) {
				return;
			}
			if (this.ownStartPending) {
				// the start of the run's own play
				this.ownStartPending = false;
				return;
			}
			reason = EndReason.MANUAL_PLAY;
		}
		endBy(reason);
	}

	@Override
	public void playbackStopped() {
		synchronized (this) {
			if (this.state != State.STARTING && this.state != State.PLAYING) {
				return;
			}
			if (this.ownStopAsked) {
				return;
			}
		}
		endBy(EndReason.PLAYER_STOPPED);
	}

	private void endBy(EndReason reason) {
		// the learner's own playback goes on: only the player's end is the player's to finish
		if (giveBack(reason != EndReason.MANUAL_PLAY)) {
			this.listener.ended(reason);
		}
	}

	/**
	 * @param stopPlayer false when the player is playing for the learner now and must go on
	 * @return true for the call that gave everything back
	 */
	private boolean giveBack(boolean stopPlayer) {
		synchronized (this) {
			if (this.state == State.ENDED) {
				return false;
			}
			this.state = State.ENDED;
		}
		try {
			this.player.removeListener(this);
			if (stopPlayer && this.player.isRunning()) {
				this.player.stop();
			}
		} finally {
			if (this.mutedTrack) {
				this.player.setPlayerOnlyMute(this.trackNumber, false);
			}
			if (this.changedPercent) {
				this.player.setTempoPercent(this.previousPercent);
			}
			if (this.changedCountDown) {
				this.player.setCountDownEnabled(this.previousCountDown);
			}
			if (this.changedLoop) {
				this.player.setLoopEnabled(this.previousLoop);
			}
		}
		return true;
	}
}
