package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates.Exclusion;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.util.TGContext;

public class PracticeTrackCandidatesTest {

	@Test
	public void excludesStructurallyIncompatibleTracksWithAReason() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);

		TGTrack drums = fixture.addTrack();
		fixture.channelOf(drums).setBank(TGChannel.DEFAULT_PERCUSSION_BANK);
		fixture.note(drums, 0, 0, 0, 1, 36, TGDuration.QUARTER);

		TGTrack bass = fixture.addTrack();
		bass.setStrings(fixture.manager.createDefaultInstrumentStrings(4));
		fixture.note(bass, 0, 0, 0, 1, 0, TGDuration.QUARTER);

		TGTrack undefined = fixture.addTrack();
		undefined.getString(1).setValue(200);
		fixture.note(undefined, 0, 0, 0, 1, 0, TGDuration.QUARTER);

		TGTrack empty = fixture.addTrack();

		PracticeTrackCandidates result = PracticeTrackCandidates.evaluate(fixture.song, null);

		assertEquals(1, result.getCandidates().size());
		assertSame(fixture.track(), result.getCandidates().get(0).getTrack());
		assertEquals(Exclusion.PERCUSSION, result.getExclusions().get(drums));
		assertEquals(Exclusion.NOT_SIX_STRINGS, result.getExclusions().get(bass));
		assertEquals(Exclusion.UNDEFINED_STRING_PITCH, result.getExclusions().get(undefined));
		assertEquals(Exclusion.NO_PRACTICE_EVENTS, result.getExclusions().get(empty));
	}

	@Test
	public void preselectsTheCaretTrackFirstThenAGuitarHint() {
		SongFixture fixture = new SongFixture(1);
		TGTrack first = fixture.track();
		fixture.channelOf(first).setProgram((short) 0);
		fixture.quarter(0, 0, 1, 0);
		TGTrack guitar = fixture.addTrack();
		fixture.channelOf(guitar).setProgram((short) 24);
		fixture.note(guitar, 0, 0, 0, 1, 0, TGDuration.QUARTER);

		PracticeTrackCandidates withCaret = PracticeTrackCandidates.evaluate(fixture.song, first);
		assertSame(first, withCaret.getPreselected().getTrack());
		assertFalse(withCaret.getPreselected().hasGuitarHint());

		PracticeTrackCandidates withoutCaret = PracticeTrackCandidates.evaluate(fixture.song, null);
		assertSame(guitar, withoutCaret.getPreselected().getTrack());
		assertTrue(withoutCaret.getPreselected().hasGuitarHint());

		fixture.channelOf(guitar).setProgram((short) 32);
		assertNull(PracticeTrackCandidates.evaluate(fixture.song, null).getPreselected());
	}

	@Test
	public void acceptanceIsAlwaysExplicitAndRememberedPerSongAndTrack() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		TGTrack other = fixture.addTrack();
		fixture.note(other, 0, 0, 0, 1, 0, TGDuration.QUARTER);
		PracticeTunerContext memory = PracticeTunerContext.getInstance(new TGContext());
		List<PracticeTrackCandidate> candidates = PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getCandidates();

		PracticeTrackSelection preselected = new PracticeTrackSelection(fixture.song, candidates.get(0), memory);
		assertTrue(candidates.get(0).isPreselected());
		assertTrue(candidates.get(0).hasGuitarHint());
		assertFalse(preselected.isAccepted());

		preselected.accept();
		assertTrue(preselected.isAccepted());
		assertTrue(new PracticeTrackSelection(fixture.song, candidates.get(0), memory).isAccepted());
		assertFalse(new PracticeTrackSelection(fixture.song, candidates.get(1), memory).isAccepted());

		SongFixture reopened = new SongFixture(1);
		reopened.quarter(0, 0, 1, 0);
		PracticeTrackCandidate sameTrackOtherDocument = PracticeTrackCandidates.evaluate(reopened.song, null).getCandidates().get(0);
		assertFalse(new PracticeTrackSelection(reopened.song, sameTrackOtherDocument, memory).isAccepted());
	}
}
