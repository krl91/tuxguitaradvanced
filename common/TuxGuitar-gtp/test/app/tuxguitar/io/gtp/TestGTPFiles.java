package app.tuxguitar.io.gtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.io.base.TGSongWriterHandle;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.helpers.TGMeasureError;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGDivisionType;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.effects.TGEffectBend;

/**
 * Guitar Pro 3 and 4 files in small fixtures written by our own writers (spec 002 T145, T148): a valid song, a triplet and a quintuplet measure,
 * measures that are too long and too short, a tied note without a previous note, a bend with two equal points. Each is written, read back by
 * content, passed through the song normalisation, checked for measure errors, and its MIDI sequence generated: none of it may throw, and the
 * errors are reported (never raised). A file whose extension does not match its content is read by its content (T148).
 */
public class TestGTPFiles {

	private final TGFactory factory = new TGFactory();

	/** A one-measure 4/4 song under construction: notes are placed one after the other on string 1. */
	private final class Builder {
		final TGSongManager manager = new TGSongManager();
		final TGSong song;
		private long offset;

		Builder() {
			this.manager.setFactory(TestGTPFiles.this.factory);
			this.song = this.manager.newSong();
		}

		/** @param division 1 for a normal note, 3 for a triplet, 5 for a quintuplet */
		TGNote add(int durationValue, int division, int fret, boolean tied) {
			TGMeasure measure = this.song.getTrack(0).getMeasure(0);
			TGBeat beat = TestGTPFiles.this.factory.newBeat();
			beat.setPreciseStart(TGDuration.toPreciseTime(measure.getStart() + this.offset));
			measure.addBeat(beat);
			beat.getVoice(0).setEmpty(false);
			TGDuration duration = beat.getVoice(0).getDuration();
			duration.setValue(durationValue);
			if( division == 3 ) {
				duration.getDivision().setEnters(3);
				duration.getDivision().setTimes(2);
			} else if( division == 5 ) {
				duration.getDivision().setEnters(5);
				duration.getDivision().setTimes(4);
			}
			TGNote note = TestGTPFiles.this.factory.newNote();
			note.setString(1);
			note.setValue(fret);
			note.setTiedNote(tied);
			beat.getVoice(0).addNote(note);
			this.offset += duration.getTime();
			return note;
		}
	}

	private byte[] write(TGSong song, boolean version4) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		TGSongWriterHandle handle = new TGSongWriterHandle();
		handle.setFactory(this.factory);
		handle.setSong(song);
		handle.setOutputStream(out);
		(version4 ? new GP4OutputStream(new GTPSettings()) : new GP3OutputStream(new GTPSettings())).write(handle);
		return out.toByteArray();
	}

	/** Round trip in the given version; the read song is normalised and its sequence generated without exception. */
	private TGSong roundTrip(Builder builder, boolean version4) throws Exception {
		TGSong read = GtpTestSupport.read(write(builder.song, version4), version4 ? "gp4" : "gp3");
		new TGSongManager().updatePreciseStart(read);
		GtpTestSupport.midiNotes(read);
		return read;
	}

	private static List<TGMeasureError> errors(TGSong song) {
		return new TGSongManager().getMeasureErrors(song);
	}

	@Test
	public void aValidSongReadsBackWithoutErrorAndPlays() throws Exception {
		for( boolean version4 : new boolean[] { false, true } ) {
			Builder b = new Builder();
			for( int fret = 0 ; fret < 4 ; fret ++ ) {
				b.add(TGDuration.QUARTER, 1, fret, false);
			}
			TGSong read = roundTrip(b, version4);
			assertEquals(0, errors(read).size(), "version 4: " + version4);
			assertEquals(4, GtpTestSupport.midiNotes(read));
		}
	}

	@Test
	public void aTripletAndAQuintupletMeasureAreValid() throws Exception {
		for( boolean version4 : new boolean[] { false, true } ) {
			Builder b = new Builder();
			b.add(TGDuration.QUARTER, 1, 0, false);
			b.add(TGDuration.QUARTER, 1, 1, false);
			for( int i = 0 ; i < 3 ; i ++ ) {
				b.add(TGDuration.EIGHTH, 3, 2, false);
			}
			b.add(TGDuration.QUARTER, 1, 3, false);
			assertEquals(0, errors(roundTrip(b, version4)).size(), "triplet, version 4: " + version4);

			Builder q = new Builder();
			q.add(TGDuration.QUARTER, 1, 0, false);
			q.add(TGDuration.QUARTER, 1, 1, false);
			q.add(TGDuration.QUARTER, 1, 2, false);
			for( int i = 0 ; i < 5 ; i ++ ) {
				q.add(TGDuration.SIXTEENTH, 5, 3, false);
			}
			assertEquals(0, errors(roundTrip(q, version4)).size(), "quintuplet, version 4: " + version4);
		}
	}

	@Test
	public void aMeasureThatIsTooLongOrTooShortIsReportedAndNeverRaised() throws Exception {
		for( boolean version4 : new boolean[] { false, true } ) {
			Builder tooLong = new Builder();
			for( int i = 0 ; i < 5 ; i ++ ) {
				tooLong.add(TGDuration.QUARTER, 1, i, false);
			}
			List<TGMeasureError> longErrors = errors(roundTrip(tooLong, version4));
			assertEquals(1, longErrors.size(), "too long, version 4: " + version4);
			assertEquals(TGMeasureError.TYPE_VOICE_DURATION_ERROR, longErrors.get(0).getErrorType());

			Builder tooShort = new Builder();
			for( int i = 0 ; i < 3 ; i ++ ) {
				tooShort.add(TGDuration.QUARTER, 1, i, false);
			}
			List<TGMeasureError> shortErrors = errors(roundTrip(tooShort, version4));
			assertEquals(1, shortErrors.size(), "too short, version 4: " + version4);
			assertEquals(TGMeasureError.TYPE_VOICE_DURATION_ERROR, shortErrors.get(0).getErrorType());
		}
	}

	@Test
	public void aTiedNoteWithoutAPreviousNoteIsReportedAndPlays() throws Exception {
		for( boolean version4 : new boolean[] { false, true } ) {
			Builder b = new Builder();
			b.add(TGDuration.QUARTER, 1, 5, true);
			for( int fret = 0 ; fret < 3 ; fret ++ ) {
				b.add(TGDuration.QUARTER, 1, fret, false);
			}
			TGSong read = roundTrip(b, version4);
			boolean tiedError = false;
			for( TGMeasureError error : errors(read) ) {
				tiedError |= (error.getErrorType() == TGMeasureError.TYPE_TIED_NOTE_ERROR);
			}
			assertTrue(tiedError, "version 4: " + version4);
		}
	}

	@Test
	public void aBendWithTwoEqualPointsPlaysWithoutDividingByZero() throws Exception {
		Builder b = new Builder();
		TGNote bent = b.add(TGDuration.QUARTER, 1, 7, false);
		TGEffectBend bend = this.factory.newEffectBend();
		bend.addPoint(0, TGEffectBend.SEMITONE_LENGTH * 2);
		bend.addPoint(TGEffectBend.MAX_POSITION_LENGTH, TGEffectBend.SEMITONE_LENGTH * 2);
		bent.getEffect().setBend(bend);
		for( int fret = 0 ; fret < 3 ; fret ++ ) {
			b.add(TGDuration.QUARTER, 1, fret, false);
		}
		assertEquals(0, errors(roundTrip(b, true)).size());
	}

	@Test
	public void aFileIsReadByItsContentWhateverItsExtension() throws Exception {
		Builder b = new Builder();
		for( int fret = 0 ; fret < 4 ; fret ++ ) {
			b.add(TGDuration.QUARTER, 1, fret, false);
		}
		byte[] guitarPro4 = write(b.song, true);
		byte[] guitarPro3 = write(b.song, false);
		// the three files named .gp3 of the owner's corpus are Guitar Pro 4 files: read by content they open
		assertEquals(4, GtpTestSupport.midiNotes(GtpTestSupport.read(guitarPro4, "gp3")));
		assertEquals(4, GtpTestSupport.midiNotes(GtpTestSupport.read(guitarPro3, "gp4")));
	}
}
