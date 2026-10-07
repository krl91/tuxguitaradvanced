package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.util.TGContext;

/**
 * The real {@link MidiPlayer} behind the port of the tempo-following mode (spec 002 T096): what a run changes in it, and the notifications it sends,
 * without playing anything (no sequencer, no device).
 */
public class MidiFollowPlayerTest {

	@Test
	public void thePortChangesAndGivesBackTheTempoTheCountDownTheLoopAndThePlayerOnlyMute() {
		MidiPlayer player = MidiPlayer.getInstance(new TGContext());
		MidiFollowPlayer port = new MidiFollowPlayer(player);

		int percent = port.tempoPercent();
		boolean countDown = port.countDownEnabled();
		boolean loop = port.loopEnabled();

		port.setTempoPercent(70);
		port.setCountDownEnabled(!countDown);
		port.setLoopEnabled(!loop);
		port.setPlayerOnlyMute(3, true);

		assertEquals(70, player.getMode().getCurrentPercent());
		assertEquals(!countDown, player.getCountDown().isEnabled());
		assertEquals(!loop, player.getMode().isLoop());
		assertTrue(player.isPlayerOnlyMuted(3));
		assertTrue(port.isPlayerOnlyMuted(3));
		assertFalse(port.isPlayerOnlyMuted(2), "the other tracks are not muted");

		port.setPlayerOnlyMute(3, false);
		port.setTempoPercent(percent);
		port.setCountDownEnabled(countDown);
		port.setLoopEnabled(loop);
		assertFalse(player.isPlayerOnlyMuted(3));
		assertEquals(percent, port.tempoPercent());
		assertEquals(countDown, port.countDownEnabled());
		assertEquals(loop, port.loopEnabled());
	}

	@Test
	public void theNotificationsOfThePlayerReachTheListenersAndStopReachingThemOnceRemoved() {
		MidiPlayer player = MidiPlayer.getInstance(new TGContext());
		MidiFollowPlayer port = new MidiFollowPlayer(player);
		final List<String> heard = new ArrayList<String>();
		FollowPlayer.Listener listener = new FollowPlayer.Listener() {
			public void playbackStarted() {
				heard.add("started");
			}

			public void playbackStopped() {
				heard.add("stopped");
			}
		};
		port.addListener(listener);

		player.notifyStarted();
		player.notifyStopped();
		port.removeListener(listener);
		player.notifyStarted();

		assertEquals(List.of("started", "stopped"), heard);
		assertFalse(port.isRunning());
	}

	@Test
	public void theClockOfThePortIsNotReadyUntilThePlayerHasShownItsPosition() {
		MidiFollowPlayer port = new MidiFollowPlayer(MidiPlayer.getInstance(new TGContext()));
		port.pollClock();
		assertFalse(port.clock().isReady(), "no sequencer, no position, no verdict");
	}
}
