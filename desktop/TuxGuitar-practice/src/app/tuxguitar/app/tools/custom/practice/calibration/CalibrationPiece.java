package app.tuxguitar.app.tools.custom.practice.calibration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.PitchNames;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * The score the player plays to calibrate (spec 013, FR-016): the owner's {@code basic_test.tg}, shipped with the plugin as the classpath resource
 * {@value #RESOURCE}, read as the events of a practice sequence (a note or a chord each). The number of events is the number of sounds the listening
 * mode expects.
 */
public final class CalibrationPiece {

	public static final String RESOURCE = "/calibration/guitar.tg";

	private final TGSong song;
	private final TGTrack track;
	private final PracticeSequence sequence;

	private CalibrationPiece(TGSong song, TGTrack track, PracticeSequence sequence) {
		this.song = song;
		this.track = track;
		this.sequence = sequence;
	}

	/** The piece shipped with the plugin. */
	public static CalibrationPiece load() {
		try (InputStream stream = CalibrationPiece.class.getResourceAsStream(RESOURCE)) {
			if (stream == null) {
				throw new IllegalStateException("the calibration piece is not shipped: " + RESOURCE);
			}
			return read(new ByteArrayInputStream(stream.readAllBytes()));
		} catch (IOException exception) {
			throw new IllegalStateException("the calibration piece cannot be read", exception);
		}
	}

	public static CalibrationPiece read(InputStream stream) {
		TGSongReaderHandle handle = new TGSongReaderHandle();
		handle.setFactory(new TGFactory());
		handle.setInputStream(stream);
		try {
			new TGSongReaderImpl().read(handle);
		} catch (Exception exception) {
			throw new IllegalStateException("the calibration piece is not a readable score", exception);
		}
		TGSong song = handle.getSong();
		TGTrack track = song.getTrack(0);
		return new CalibrationPiece(song, track, PracticeSequenceBuilder.build(song, track, TrackTuning.ofAnyStrings(track)));
	}

	public int eventCount() {
		return this.sequence.getEvents().size();
	}

	public boolean isChord(int eventIndex) {
		return this.sequence.getEvents().get(eventIndex).getKind() != PracticeEvent.Kind.EXPECTED_NOTE;
	}

	/** For each event, whether it is a chord. */
	public boolean[] chordFlags() {
		boolean[] flags = new boolean[eventCount()];
		for (int i = 0; i < flags.length; i++) {
			flags[i] = isChord(i);
		}
		return flags;
	}

	/** A note of an event as it is shown: its name ("E2"), its string (1 is the high E) and its fret (0 is open). */
	public static final class PieceNote {
		private final String name;
		private final int string;
		private final int fret;

		PieceNote(String name, int string, int fret) {
			this.name = name;
			this.string = string;
			this.fret = fret;
		}

		public String getName() {
			return this.name;
		}

		public int getString() {
			return this.string;
		}

		public int getFret() {
			return this.fret;
		}
	}

	/** The notes of one event, lowest string last as written in the score: one for a single note, several for a chord. */
	public List<PieceNote> eventNotes(int eventIndex) {
		PracticeEvent event = this.sequence.getEvents().get(eventIndex);
		List<PieceNote> notes = new ArrayList<PieceNote>();
		if (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE) {
			notes.add(new PieceNote(PitchNames.of(event.getExpectedMidi()), event.getStringNumber(), event.getFret()));
		} else {
			for (PracticeEvent.ChordNote note : event.getChordNotes()) {
				notes.add(new PieceNote(PitchNames.of(note.getMidi()), note.getStringNumber(), note.getFret()));
			}
		}
		return notes;
	}

	public PracticeSequence getSequence() {
		return this.sequence;
	}

	public EventJudge judge(TGContext context) {
		return new EventJudge(context, this.song, this.track, this.sequence);
	}
}
