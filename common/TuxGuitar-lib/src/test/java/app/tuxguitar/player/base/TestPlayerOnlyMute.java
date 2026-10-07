package app.tuxguitar.player.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import app.tuxguitar.document.TGDocumentManager;
import app.tuxguitar.player.impl.sequencer.MidiSequencerImpl;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.thread.TGMultiThreadHandler;
import app.tuxguitar.thread.TGThreadManager;
import app.tuxguitar.util.TGContext;

/**
 * The player-only mute of a track (spec 002 T092): the track is silent in the player whatever the model says, the others stay audible,
 * and the song, the tracks and the undo stack are never touched. It survives {@code updateTracks()}, which {@code play()} and every
 * track edit call and which rewrites the mutes of the sequencer from the model.
 */
public class TestPlayerOnlyMute {

	private final TGContext context = new TGContext();
	private final TGSongManager manager = new TGSongManager();
	private final TGSong song = this.manager.newSong();
	private final TGTrack first;
	private final TGTrack second;
	private final MidiSequencerImpl sequencer;
	private final MidiPlayer player;

	public TestPlayerOnlyMute() throws MidiPlayerException {
		TGThreadManager.getInstance(this.context).setThreadHandler(new TGMultiThreadHandler());
		this.first = this.song.getTrack(0);
		this.second = this.manager.addTrack(this.song);
		TGDocumentManager.getInstance(this.context).setSong(this.song);
		this.sequencer = new MidiSequencerImpl(this.context);
		this.player = MidiPlayer.getInstance(this.context);
		assertTrue(this.player.loadSequencer(this.sequencer));
		// the sequencer knows its tracks (the info track, the two tracks of the song, the metronome) once a sequence exists
		this.sequencer.createSequence(4);
	}

	private boolean mutedInSequencer(TGTrack track) {
		return this.sequencer.getMidiTrackController().isMute(track.getNumber());
	}

	@Test
	public void withoutAPlayerOnlyMuteTheModelDecidesAsBefore() {
		this.player.updateTracks();
		assertFalse(mutedInSequencer(this.first));
		assertFalse(mutedInSequencer(this.second));

		this.first.setMute(true);
		this.player.updateTracks();
		assertTrue(mutedInSequencer(this.first));
		assertFalse(mutedInSequencer(this.second));

		this.first.setMute(false);
		this.player.updateTracks();
		assertFalse(mutedInSequencer(this.first));
		assertFalse(this.player.isPlayerOnlyMuted(this.first.getNumber()));
	}

	@Test
	public void aPlayerOnlyMuteSilencesTheTrackInTheSequencerAndLeavesTheModelAlone() {
		this.player.setPlayerOnlyMute(this.second.getNumber(), true);

		assertTrue(this.player.isPlayerOnlyMuted(this.second.getNumber()));
		assertTrue(mutedInSequencer(this.second));
		assertFalse(mutedInSequencer(this.first));
		assertFalse(this.second.isMute(), "the model of the track was changed");
		assertFalse(this.first.isMute());
	}

	@Test
	public void theMuteSurvivesUpdateTracksAndAnEditOfTheTracks() {
		this.player.setPlayerOnlyMute(this.second.getNumber(), true);

		// what play() does, then what the application does after the mute or the solo of a track is edited
		this.player.updateTracks();
		assertTrue(mutedInSequencer(this.second));
		this.second.setMute(true);
		this.player.updateTracks();
		this.second.setMute(false);
		this.player.updateTracks();
		this.first.setSolo(true);
		this.player.updateTracks();
		this.first.setSolo(false);
		this.player.updateTracks();

		assertTrue(mutedInSequencer(this.second), "the player-only mute was lost");
		assertFalse(this.second.isMute(), "the model of the track was changed");
	}

	@Test
	public void clearingGivesTheTracksBackToTheModel() {
		this.second.setMute(true);
		this.player.setPlayerOnlyMute(this.second.getNumber(), true);
		this.player.setPlayerOnlyMute(this.first.getNumber(), true);

		this.player.clearPlayerOnlyMutes();

		assertFalse(this.player.isPlayerOnlyMuted(this.first.getNumber()));
		assertFalse(this.player.isPlayerOnlyMuted(this.second.getNumber()));
		assertFalse(mutedInSequencer(this.first), "the track stayed muted after the session");
		assertTrue(mutedInSequencer(this.second), "the mute of the model was lost");
		assertTrue(this.second.isMute());
	}

	@Test
	public void closingThePlayerForgetsTheMutes() {
		this.player.setPlayerOnlyMute(this.second.getNumber(), true);
		this.player.close();
		assertFalse(this.player.isPlayerOnlyMuted(this.second.getNumber()));
	}

	@Test
	public void theMutedTrackIsSilentAndTheOthersAreAudibleInARealSequence() throws Exception {
		final List<Integer> heard = new CopyOnWriteArrayList<Integer>();
		final CountDownLatch audible = new CountDownLatch(1);
		this.sequencer.setTransmitter(new RecordingTransmitter(heard, audible, 1));
		app.tuxguitar.player.base.MidiSequenceHandler handler = this.sequencer.createSequence(4);
		// the event of the muted track comes first in the list: it would be sent before the audible one if it were not dropped
		handler.addNoteOn(0, this.second.getNumber(), 0, 60, 64, 0, false);
		handler.addNoteOn(0, this.first.getNumber(), 1, 62, 64, 0, false);
		handler.notifyFinish();

		this.player.setPlayerOnlyMute(this.second.getNumber(), true);
		this.player.updateTracks();
		this.sequencer.setTickPosition(0);
		this.sequencer.start();
		try {
			assertTrue(audible.await(10, TimeUnit.SECONDS), "the audible track was never played");
		} finally {
			this.sequencer.stop();
		}

		assertEquals(Collections.singletonList(1), heard, "channels heard: the muted track (channel 0) must be silent");
	}

	private static final class RecordingTransmitter extends MidiTransmitter {
		private final List<Integer> heard;
		private final CountDownLatch latch;
		private final int latchChannel;

		RecordingTransmitter(List<Integer> heard, CountDownLatch latch, int latchChannel) {
			this.heard = heard;
			this.latch = latch;
			this.latchChannel = latchChannel;
		}

		public void sendNoteOn(int channel, int key, int velocity, int voice, boolean bendMode) {
			this.heard.add(channel);
			if( channel == this.latchChannel ) {
				this.latch.countDown();
			}
		}

		public void sendNoteOff(int channel, int key, int velocity, int voice, boolean bendMode) {}
		public void sendPitchBend(int channel, int value, int voice, boolean bendMode) {}
		public void sendProgramChange(int channel, int value) {}
		public void sendControlChange(int channel, int controller, int value) {}
		public void sendSystemReset() {}
		public void sendAllNotesOff() {}
		public void sendPitchBendReset() {}
		public void sendParameter(int channel, String key, String value) {}
	}
}
