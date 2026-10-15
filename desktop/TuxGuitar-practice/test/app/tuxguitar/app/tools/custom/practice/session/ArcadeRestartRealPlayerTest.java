package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.stress.SilentMidi;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The owner's bug of 2026-10-15 (spec 011 FU-07): in Arcade, pressing Restart several times while a run is going on, the piece does not start from the beginning of the
 * passage and the place it starts from changes from one press to the next. This runs the restarts the way {@code PracticeWindow} makes them (the run stops, the next
 * run starts at once, the count-in of the player running) on the <em>real</em> {@code MidiPlayer} with the real sequencer and a silent output, and watches where the
 * sequencer really is once the last run plays.
 */
public class ArcadeRestartRealPlayerTest {

	private static final long COUNT_IN_MILLIS = 2000L;
	private static final long MEASURE = 3840L;
	/** The first measure of the song is at tick 960; the passage starts at the fifth measure. */
	private static final long START_TICK = FollowStarter.FIRST_TICK + 4 * MEASURE;

	/** A song like the owner's: it has a percussion channel, so the player's count-in (the clicks) really runs. */
	private static SongFixture song() {
		SongFixture song = new SongFixture(20);
		app.tuxguitar.song.models.TGChannel percussion = new app.tuxguitar.song.factory.TGFactory().newChannel();
		percussion.setChannelId((short) 9);
		percussion.setBank(app.tuxguitar.song.models.TGChannel.DEFAULT_PERCUSSION_BANK);
		percussion.setProgram(app.tuxguitar.song.models.TGChannel.DEFAULT_PERCUSSION_PROGRAM);
		percussion.setName("percussion");
		song.song.addChannel(percussion);
		for (int i = 0; i < 60; i++) {
			song.quarter(i / 4, i % 4, 3, 5);
		}
		return song;
	}

	/** The moment the sequencer really started, in milliseconds after the last press (about the count-in when the last restart took effect; much less when an earlier run's count-in thread started the sequencer). */
	private static long startedAfterLastPress(long spacingMillis, int presses, List<String> log) throws Exception {
		SongFixture fixture = song();
		SilentMidi midi = new SilentMidi(fixture.song);
		MidiFollowPlayer player = new MidiFollowPlayer(midi.player);
		int track = fixture.track().getNumber();
		FollowPlayback.Listener none = reason -> {
		};
		FollowPlayback playback = null;
		for (int press = 0; press < presses; press++) {
			if (playback != null) {
				playback.stop(); // what teardownFollow does: the run is stopped and given back
			}
			playback = new FollowPlayback(player, track, 100, START_TICK, none);
			playback.start();
			if (press < presses - 1) {
				Thread.sleep(spacingMillis);
			}
		}
		long lastPress = System.nanoTime();
		long found = Long.MIN_VALUE;
		for (int i = 0; i < 120 && found == Long.MIN_VALUE; i++) {
			Thread.sleep(50);
			long elapsed = (System.nanoTime() - lastPress) / 1_000_000L;
			long tick = midi.player.getTickPosition();
			if (tick > START_TICK + 300L) {
				// 1920 ticks a second at 120 beats a minute: the sequencer left the start that long ago
				found = elapsed - (tick - START_TICK) * 1000L / 1920L;
			}
		}
		playback.stop();
		log.add(String.format(Locale.ROOT, "%d presses %4d ms apart: the sequencer really started %+d ms after the last press (the count-in of the player takes about 2000 ms)", presses, spacingMillis, found));
		return found;
	}

	@Test
	public void afterSeveralQuickRestartsTheLastRunStartsAfterItsOwnCountInNotBeforeTheLastPress() throws Exception {
		List<String> log = new ArrayList<String>();
		long single = startedAfterLastPress(0, 1, log);
		long slow = startedAfterLastPress(3000, 3, log);
		long quick = startedAfterLastPress(400, 4, log);
		System.out.println("ARCADE-RESTART " + String.join("\n ARCADE-RESTART ", log));
		// the count-in of this song takes two seconds (4 beats at 120 beats a minute): a run only starts its music after its own count-in, whatever came before it
		assertTrue(single >= COUNT_IN_MILLIS - 200L, "a single run started its music before its count-in ended: " + log);
		assertTrue(slow >= COUNT_IN_MILLIS - 200L, "restarts 3 s apart: the last run started its music before its own count-in ended: " + log);
		assertTrue(quick >= COUNT_IN_MILLIS - 200L, "restarts 0.4 s apart (during the count-in): the last run started its music " + quick + " ms after the last press instead of after its own count-in of about "
				+ COUNT_IN_MILLIS + " ms: an earlier run's count-in thread started the sequencer (the owner's bug of 2026-10-15): " + log);
	}
}
