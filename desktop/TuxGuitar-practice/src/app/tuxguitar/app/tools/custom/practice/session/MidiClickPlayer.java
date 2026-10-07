package app.tuxguitar.app.tools.custom.practice.session;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerException;

/**
 * The clicks of the calibration (spec 002 T097, FR-059) through the player's own output, the way the backing is heard: a short percussion note on the
 * percussion channel of the song, sent to the player's transmitter and ended after {@link #CLICK_MILLIS}. It never blocks (the frames of the analysis
 * call it) and {@link #silence()} ends the click that rings at once.
 * <p>
 * The time of a click is the moment it is handed to the transmitter; the delay of the synthesizer and of the output line comes after it, which is
 * exactly what the calibration measures.
 */
public final class MidiClickPlayer implements ClickPlayer {

	/** High wood block of the General MIDI percussion map: short and easy to hear. */
	static final int KEY = 76;
	static final int VELOCITY = 100;
	static final long CLICK_MILLIS = 50L;

	private final MidiPlayer player;
	private final ScheduledExecutorService ender = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "practice-calibration-click");
		thread.setDaemon(true);
		return thread;
	});
	private ScheduledFuture<?> pendingEnd;
	private int channel = -1;
	private boolean sounding;
	private boolean prepared;

	public MidiClickPlayer(MidiPlayer player) {
		this.player = player;
	}

	@Override
	public synchronized long click() {
		try {
			if (!this.prepared) {
				this.player.updateChannels();
				this.channel = this.player.getPercussionChannelId();
				this.prepared = true;
			}
			if (this.channel < 0) {
				return Long.MIN_VALUE;
			}
			endNow();
			long handed = System.nanoTime();
			this.player.getOutputTransmitter().sendNoteOn(this.channel, KEY, VELOCITY, -1, false);
			this.sounding = true;
			this.pendingEnd = this.ender.schedule(this::endNow, CLICK_MILLIS, TimeUnit.MILLISECONDS);
			return handed;
		} catch (MidiPlayerException | RuntimeException exception) {
			return Long.MIN_VALUE;
		}
	}

	@Override
	public synchronized void silence() {
		endNow();
		this.ender.shutdownNow();
	}

	private synchronized void endNow() {
		if (this.pendingEnd != null) {
			this.pendingEnd.cancel(false);
			this.pendingEnd = null;
		}
		if (this.sounding) {
			this.sounding = false;
			try {
				this.player.getOutputTransmitter().sendNoteOff(this.channel, KEY, VELOCITY, -1, false);
			} catch (MidiPlayerException | RuntimeException exception) {
				// the output is gone: there is nothing left to silence
			}
		}
	}
}
