package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.player.base.MidiPlayerException;

/**
 * What the tempo-following mode needs from the player, and nothing more (spec 002 T096): the player is the clock and the backing track. It is a port
 * so that the lifecycle of a run is tested without a device and without real time ({@code FakeMidiPlayer}); {@link MidiFollowPlayer} is the real one.
 * <p>
 * Everything a run changes in the player is changed through this port and given back by {@link FollowPlayback} exactly once: the mute of the practised
 * track (player only: the song, its undo and the mute/solo the learner set are never touched), the tempo percentage, the count-down and the loop.
 */
public interface FollowPlayer {

	/** Notifications of the player. They may come from any thread, and the start comes after {@link #play()} has returned. */
	interface Listener {
		/** The player started to play (the notification of {@code NOTIFY_STARTED}: only when it was not running). */
		void playbackStarted();

		/** The player stopped, whoever asked and whatever the reason (the end of the song included). */
		void playbackStopped();
	}

	void addListener(Listener listener);

	void removeListener(Listener listener);

	boolean isRunning();

	int tempoPercent();

	void setTempoPercent(int percent);

	boolean countDownEnabled();

	void setCountDownEnabled(boolean enabled);

	boolean loopEnabled();

	void setLoopEnabled(boolean enabled);

	/** Mutes (or gives back) the track in the player only. */
	void setPlayerOnlyMute(int trackNumber, boolean muted);

	boolean isPlayerOnlyMuted(int trackNumber);

	/** Where the next {@link #play()} starts, in sequencer ticks (the first measure of the song is at tick 960). */
	void setTickPosition(long tick);

	void play() throws MidiPlayerException;

	void stop();

	/** The clock of the player. Poll it with {@link #pollClock()}. */
	TempoClock clock();

	/** Reads the player's position now (the practice frames come every 5 ms and poll). */
	void pollClock();
}
