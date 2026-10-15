package app.tuxguitar.app.tools.custom.practice.calibration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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

	public PracticeSequence getSequence() {
		return this.sequence;
	}

	public EventJudge judge(TGContext context) {
		return new EventJudge(context, this.song, this.track, this.sequence);
	}
}
