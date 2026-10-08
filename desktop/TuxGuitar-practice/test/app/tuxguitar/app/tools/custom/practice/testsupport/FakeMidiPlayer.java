package app.tuxguitar.app.tools.custom.practice.testsupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import app.tuxguitar.app.tools.custom.practice.session.FollowPlayer;
import app.tuxguitar.app.tools.custom.practice.session.TempoClock;
import app.tuxguitar.player.base.MidiPlayerException;

/**
 * The {@link FollowPlayer} of the tests (spec 002 T128): a player that does exactly what the test says, with the notifications, the count-down and the
 * clock of the real one but no device, no thread and no sleep. Everything the run changes in it is visible ({@link #calls()} keeps the order), and
 * what the real player does by itself (the start notification that comes after {@code play()}, the end of the song, a play of the learner) is
 * an explicit call of the test.
 */
public final class FakeMidiPlayer implements FollowPlayer {

	private final FakeTempoClock clock;
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
	private final List<String> calls = new ArrayList<String>();
	private final Set<Integer> playerOnlyMutes = new HashSet<Integer>();
	/** A track the song itself mutes: the player-only mute never touches it. */
	private final Set<Integer> songMutes = new HashSet<Integer>();

	private boolean running;
	private int percent = 100;
	private boolean countDown;
	private boolean loop;
	private long tickPosition = 960L;
	private long countDownNanos = 2_000_000_000L;
	private long countDownRemaining;
	private boolean counting;
	private boolean autoStartNotification = true;
	private boolean failOnPlay;
	private boolean failOnTickPosition;
	private int listenerAdds;
	private int listenerRemoves;
	private boolean clockPolled;
	private boolean failOnResume;
	private boolean autoStopNotification = true;

	public FakeMidiPlayer(FakeTempoClock clock) {
		this.clock = clock;
	}

	// ----------------------------------------------------------------------------------------------- the test's controls

	/** How long the count-down lasts when it is enabled. */
	public FakeMidiPlayer countDownNanos(long nanos) {
		this.countDownNanos = nanos;
		return this;
	}

	/** False: the start notification comes only when the test calls {@link #deliverStartNotification()}, as the real one comes later from another thread. */
	public FakeMidiPlayer autoStartNotification(boolean auto) {
		this.autoStartNotification = auto;
		return this;
	}

	public FakeMidiPlayer failOnPlay(boolean fail) {
		this.failOnPlay = fail;
		return this;
	}

	/** The player refuses to be positioned, with a runtime exception (a player that is being closed). */
	public FakeMidiPlayer failOnTickPosition(boolean fail) {
		this.failOnTickPosition = fail;
		return this;
	}

	/** The player cannot start again after a pause. */
	public FakeMidiPlayer failOnResume(boolean fail) {
		this.failOnResume = fail;
		return this;
	}

	/** False: the stop notification of a pause comes only when the test calls {@link #deliverStopNotification()}, as the real one comes later. */
	public FakeMidiPlayer autoStopNotification(boolean auto) {
		this.autoStopNotification = auto;
		return this;
	}

	public FakeMidiPlayer songMutes(int trackNumber) {
		this.songMutes.add(trackNumber);
		return this;
	}

	public FakeMidiPlayer withInitialState(int percent, boolean countDown, boolean loop) {
		this.percent = percent;
		this.countDown = countDown;
		this.loop = loop;
		return this;
	}

	/** Moves the time by the nanoseconds: the count-down runs out, then the clock of the player moves. */
	public void advance(long nanos) {
		long left = nanos;
		if (this.counting) {
			long used = Math.min(left, this.countDownRemaining);
			this.clock.advance(used);
			this.countDownRemaining -= used;
			left -= used;
			if (this.countDownRemaining == 0) {
				this.counting = false;
				this.clock.start(this.tickPosition);
				this.calls.add("clock-started");
			}
		}
		if (left > 0) {
			this.clock.advance(left);
		}
	}

	/** The notification of the start of the player's own play, which the real player sends later. */
	public void deliverStartNotification() {
		for (Listener listener : this.listeners) {
			listener.playbackStarted();
		}
	}

	/** The stop notification of the player, late: after a pause it may arrive when the run has already asked to play again. */
	public void deliverStopNotification() {
		for (Listener listener : this.listeners) {
			listener.playbackStopped();
		}
	}

	/** The learner presses Play: the player is not running (or was stopped), it starts and says so. */
	public void learnerPlays() {
		this.calls.add("learner-play");
		this.running = true;
		deliverStartNotification();
	}

	/** The song ends, or the learner presses Stop: the player stops by itself and says so. */
	public void stopsByItself() {
		this.calls.add("stopped-by-itself");
		this.running = false;
		this.counting = false;
		this.clock.stopClock();
		for (Listener listener : this.listeners) {
			listener.playbackStopped();
		}
	}

	public List<String> calls() {
		return new ArrayList<String>(this.calls);
	}

	public int listenerCount() {
		return this.listeners.size();
	}

	public int listenerAdds() {
		return this.listenerAdds;
	}

	public int listenerRemoves() {
		return this.listenerRemoves;
	}

	public boolean isCounting() {
		return this.counting;
	}

	public long tickPosition() {
		return this.tickPosition;
	}

	public FakeTempoClock fakeClock() {
		return this.clock;
	}

	/** True when a track is silent in the player, whichever way: by the song or by the run. */
	public boolean isSilent(int trackNumber) {
		return this.songMutes.contains(trackNumber) || this.playerOnlyMutes.contains(trackNumber);
	}

	// ----------------------------------------------------------------------------------------------- FollowPlayer

	@Override
	public void addListener(Listener listener) {
		this.listenerAdds++;
		this.listeners.add(listener);
	}

	@Override
	public void removeListener(Listener listener) {
		if (this.listeners.remove(listener)) {
			this.listenerRemoves++;
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@Override
	public int tempoPercent() {
		return this.percent;
	}

	@Override
	public void setTempoPercent(int percent) {
		this.calls.add("percent=" + percent);
		this.percent = percent;
	}

	@Override
	public boolean countDownEnabled() {
		return this.countDown;
	}

	@Override
	public void setCountDownEnabled(boolean enabled) {
		this.calls.add("countdown=" + enabled);
		this.countDown = enabled;
	}

	@Override
	public boolean loopEnabled() {
		return this.loop;
	}

	@Override
	public void setLoopEnabled(boolean enabled) {
		this.calls.add("loop=" + enabled);
		this.loop = enabled;
	}

	@Override
	public void setPlayerOnlyMute(int trackNumber, boolean muted) {
		this.calls.add("mute-" + trackNumber + "=" + muted);
		if (muted) {
			this.playerOnlyMutes.add(trackNumber);
		} else {
			this.playerOnlyMutes.remove(trackNumber);
		}
	}

	@Override
	public boolean isPlayerOnlyMuted(int trackNumber) {
		return this.playerOnlyMutes.contains(trackNumber);
	}

	@Override
	public void setTickPosition(long tick) {
		this.calls.add("tick=" + tick);
		if (this.failOnTickPosition) {
			throw new IllegalStateException("the player is closing");
		}
		this.tickPosition = tick;
	}

	@Override
	public void play() throws MidiPlayerException {
		this.calls.add("play");
		if (this.failOnPlay) {
			throw new MidiPlayerException("the player cannot start", new RuntimeException("test"));
		}
		boolean notify = !this.running;
		this.running = true;
		if (this.countDown) {
			this.counting = true;
			this.countDownRemaining = this.countDownNanos;
		} else {
			this.clock.start(this.tickPosition);
			this.calls.add("clock-started");
		}
		if (notify && this.autoStartNotification) {
			deliverStartNotification();
		}
	}

	@Override
	public void stop() {
		this.calls.add("stop");
		boolean wasRunning = this.running;
		this.running = false;
		this.counting = false;
		this.clock.stopClock();
		if (wasRunning) {
			for (Listener listener : this.listeners) {
				listener.playbackStopped();
			}
		}
	}

	@Override
	public void pause() {
		this.calls.add("pause");
		if (this.clock.isStarted()) {
			this.tickPosition = (long) Math.floor(this.clock.positionNow());
			this.clock.pause();
		}
		boolean wasRunning = this.running;
		this.running = false;
		this.counting = false;
		if (wasRunning && this.autoStopNotification) {
			deliverStopNotification();
		}
	}

	@Override
	public void resume() throws MidiPlayerException {
		this.calls.add("resume");
		if (this.failOnResume) {
			throw new MidiPlayerException("the player cannot start again", new RuntimeException("test"));
		}
		boolean notify = !this.running;
		this.running = true;
		if (this.countDown) {
			this.counting = true;
			this.countDownRemaining = this.countDownNanos;
		} else {
			this.clock.start(this.tickPosition);
			this.calls.add("clock-started");
		}
		if (notify && this.autoStartNotification) {
			deliverStartNotification();
		}
	}

	@Override
	public TempoClock clock() {
		return this.clock;
	}

	@Override
	public void pollClock() {
		this.clockPolled = true;
	}

	public boolean clockPolled() {
		return this.clockPolled;
	}
}
