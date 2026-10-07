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

	@Override
	public void stop() {
		this.player.stop();
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
