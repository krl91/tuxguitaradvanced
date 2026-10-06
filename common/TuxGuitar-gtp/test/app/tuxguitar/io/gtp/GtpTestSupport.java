package app.tuxguitar.io.gtp;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.base.TGSongReaderHelper;
import app.tuxguitar.player.base.MidiSequenceHandler;
import app.tuxguitar.player.base.MidiSequenceParser;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTimeSignature;
import app.tuxguitar.util.TGContext;

/** What the Guitar Pro tests share: reading by content as the application does, and generating the MIDI sequence of a song. */
final class GtpTestSupport {

	private GtpTestSupport() {
	}

	/** Reads by content, as the application does: the readers and their detectors are registered like the plugins do. */
	static TGSong read(byte[] bytes, String formatCode) throws Exception {
		TGContext context = new TGContext();
		new GP3InputStreamPlugin().connect(context);
		new GP4InputStreamPlugin().connect(context);
		TGSongReaderHandle handle = new TGSongReaderHandle();
		handle.setContext(new app.tuxguitar.io.base.TGSongStreamContext());
		handle.setFactory(new TGFactory());
		handle.setInputStream(new ByteArrayInputStream(bytes));
		handle.getContext().setAttribute(app.tuxguitar.io.base.TGSongPersistenceHelper.ATTRIBUTE_FORMAT_CODE, formatCode);
		new TGSongReaderHelper(context).read(handle);
		return handle.getSong();
	}

	/** Generates the MIDI sequence of a song with a handler that only counts; returns the number of notes. */
	static int midiNotes(TGSong song) {
		TGSongManager manager = new TGSongManager();
		AtomicInteger notes = new AtomicInteger();
		MidiSequenceHandler handler = new MidiSequenceHandler(song.countTracks() + 1) {
			public void addNoteOn(long tick, int track, int channel, int note, int velocity, int voice, boolean bendMode) {
				notes.incrementAndGet();
			}

			public void addNoteOff(long tick, int track, int channel, int note, int velocity, int voice, boolean bendMode) {
			}

			public void addPitchBend(long tick, int track, int channel, int value, int voice, boolean bendMode) {
			}

			public void addControlChange(long tick, int track, int channel, int controller, int value) {
			}

			public void addProgramChange(long tick, int track, int channel, int instrument) {
			}

			public void addTempoInUSQ(long tick, int track, int usq) {
			}

			public void addTrackName(long tick, int track, String name) {
			}

			public void addTimeSignature(long tick, int track, TGTimeSignature ts) {
			}

			public void notifyFinish() {
			}
		};
		new MidiSequenceParser(song, manager, MidiSequenceParser.DEFAULT_EXPORT_FLAGS).parse(handler);
		return notes.get();
	}

}
