package app.tuxguitar.app.tools.custom.practice.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.base.TGSongStreamContext;
import app.tuxguitar.io.base.TGSongWriterHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.io.tg.TGSongWriterImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * Builds small songs in code for practice tests: one six-string standard-tuning track
 * (string 1 = E4 = 64 … string 6 = E2 = 40), 4/4 measures of {@link #MEASURE_TICKS}.
 * Beats must be added in ascending time order within a measure; beat starts are set as precise
 * starts, as the .tg writer requires.
 */
public final class SongFixture {

	static final long QUARTER = TGDuration.QUARTER_TIME;
	static final long MEASURE_TICKS = 4 * QUARTER;

	final TGFactory factory = new TGFactory();
	final TGSongManager manager = new TGSongManager();
	public final TGSong song;

	public SongFixture(int measures) {
		this.song = this.manager.newSong();
		for( int i = 1 ; i < measures ; i ++ ) {
			this.manager.addNewMeasureBeforeEnd(this.song);
		}
	}

	public TGTrack track() {
		return this.song.getTrack(0);
	}

	TGMeasureHeader header(int measureIndex) {
		return this.song.getMeasureHeader(measureIndex);
	}

	/** Adds a track with its own channel (program 25, a guitar sound, unless changed). */
	TGTrack addTrack() {
		return this.manager.addTrack(this.song);
	}

	TGChannel channelOf(TGTrack track) {
		Iterator<TGChannel> channels = this.song.getChannels();
		while( channels.hasNext() ) {
			TGChannel channel = channels.next();
			if( channel.getChannelId() == track.getChannelId() ) {
				return channel;
			}
		}
		return null;
	}

	/** Adds a note at {@code quarterOffset} quarters into the measure; returns it for effects. */
	TGNote note(int measureIndex, int quarterOffset, int voice, int string, int fret, int durationValue) {
		return note(track(), measureIndex, quarterOffset, voice, string, fret, durationValue);
	}

	TGNote note(TGTrack track, int measureIndex, int quarterOffset, int voice, int string, int fret, int durationValue) {
		TGMeasure measure = track.getMeasure(measureIndex);
		long start = measure.getStart() + quarterOffset * QUARTER;
		TGBeat beat = null;
		for( TGBeat existing : measure.getBeats() ) {
			if( existing.getStart() == start ) {
				beat = existing;
			}
		}
		if( beat == null ) {
			beat = this.factory.newBeat();
			beat.setPreciseStart(TGDuration.toPreciseTime(start));
			measure.addBeat(beat);
		}
		beat.getVoice(voice).setEmpty(false);
		beat.getVoice(voice).getDuration().setValue(durationValue);
		TGNote note = this.factory.newNote();
		note.setString(string);
		note.setValue(fret);
		beat.getVoice(voice).addNote(note);
		return note;
	}

	public TGNote quarter(int measureIndex, int quarterOffset, int string, int fret) {
		return note(measureIndex, quarterOffset, 0, string, fret, TGDuration.QUARTER);
	}

	TrackTuning tuning() {
		return TrackTuning.of(track(), null);
	}

	public PracticeSequence sequence() {
		return PracticeSequenceBuilder.build(this.song, track(), tuning());
	}

	/** Writes the song as a .tg file in memory and reads it back. */
	TGSong roundTrip() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		TGSongWriterHandle write = new TGSongWriterHandle();
		write.setFactory(this.factory);
		write.setSong(this.song);
		write.setOutputStream(output);
		new TGSongWriterImpl().write(write);

		TGSongReaderHandle read = new TGSongReaderHandle();
		read.setContext(new TGSongStreamContext());
		read.setFactory(new TGFactory());
		read.setInputStream(new ByteArrayInputStream(output.toByteArray()));
		new TGSongReaderImpl().read(read);
		return read.getSong();
	}
}
