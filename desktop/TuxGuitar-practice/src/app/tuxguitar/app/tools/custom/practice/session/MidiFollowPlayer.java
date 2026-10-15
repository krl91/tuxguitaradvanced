package app.tuxguitar.app.tools.custom.practice.session;

import java.util.concurrent.CopyOnWriteArrayList;

import app.tuxguitar.event.TGEvent;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.player.base.MidiPlayerException;

/** The application's {@link MidiPlayer} as the player of the tempo-following mode. */
public final class MidiFollowPlayer implements FollowPlayer, TGEventListener {

	private final MidiPlayer player;
	private final PlayerTempoClock clock;
	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
	private boolean attached;
	/**
	 * How long a stop of a running player waits before it returns. The count-in of the player (its clicks) and its play loop poll the player every 10 ms and only end when they
	 * see it stopped; a run restarted at once, in the same instant, is never seen stopped, so that the thread of the earlier run goes on: it starts the sequencer before the new
	 * run's own count-in has ended and writes its stale position over the one the new run has just set, and the music then starts early and from a place that changes with each
	 * press of Restart (the owner's bug of 2026-10-15). Waiting here, before anything of the new run is done, gives those threads the polls they need (eight are far more than one).
	 */
	static final long STOP_SETTLE_MILLIS = 80L;

	public MidiFollowPlayer(MidiPlayer player) {
		this.player = player;
		this.clock = PlayerTempoClock.of(player);
	}

	@Override
	public synchronized void addListener(Listener listener) {
		this.listeners.add(listener);
		if (!this.attached) {
			this.attached = true;
			this.player.addListener(this);
		}
	}

	@Override
	public synchronized void removeListener(Listener listener) {
		this.listeners.remove(listener);
		if (this.listeners.isEmpty() && this.attached) {
			this.attached = false;
			this.player.removeListener(this);
		}
	}

	@Override
	public void processEvent(TGEvent event) {
		if (!MidiPlayerEvent.EVENT_TYPE.equals(event.getEventType())) {
			return;
		}
		int type = ((Integer) event.getAttribute(MidiPlayerEvent.PROPERTY_NOTIFICATION_TYPE)).intValue();
		for (Listener listener : this.listeners) {
			if (type == MidiPlayerEvent.NOTIFY_STARTED) {
				listener.playbackStarted();
			} else if (type == MidiPlayerEvent.NOTIFY_STOPPED) {
				listener.playbackStopped();
			}
		}
	}

	@Override
	public boolean isRunning() {
		return this.player.isRunning();
	}

	@Override
	public int tempoPercent() {
		return this.player.getMode().getCurrentPercent();
	}

	@Override
	public void setTempoPercent(int percent) {
		this.player.getMode().setCurrentPercent(percent);
	}

	@Override
	public boolean countDownEnabled() {
		return this.player.getCountDown().isEnabled();
	}

	@Override
	public void setCountDownEnabled(boolean enabled) {
		this.player.getCountDown().setEnabled(enabled);
	}

	@Override
	public boolean loopEnabled() {
		return this.player.getMode().isLoop();
	}

	@Override
	public void setLoopEnabled(boolean enabled) {
		this.player.getMode().setLoop(enabled);
	}

	@Override
	public void setPlayerOnlyMute(int trackNumber, boolean muted) {
		this.player.setPlayerOnlyMute(trackNumber, muted);
	}

	@Override
	public boolean isPlayerOnlyMuted(int trackNumber) {
		return this.player.isPlayerOnlyMuted(trackNumber);
	}

	@Override
	public void setTickPosition(long tick) {
		this.player.setTickPosition(tick);
	}

	@Override
	public void play() throws MidiPlayerException {
		this.player.play();
	}

	private long pausedTick = -1L;

	@Override
	public void pause() {
		this.pausedTick = this.player.getTickPosition();
		this.player.pause();
	}

	@Override
	public void resume() throws MidiPlayerException {
		if (this.pausedTick >= 0) {
			this.player.setTickPosition(this.pausedTick);
		}
		this.player.play();
	}

	@Override
	public void stop() {
		boolean interrupted = this.player.isRunning();
		this.player.stop();
		if (interrupted) {
			try {
				Thread.sleep(STOP_SETTLE_MILLIS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public TempoClock clock() {
		return this.clock;
	}

	@Override
	public void pollClock() {
		this.clock.poll();
	}
}
