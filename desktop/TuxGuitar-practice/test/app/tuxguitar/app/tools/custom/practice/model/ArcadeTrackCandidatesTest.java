package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.ArcadeTrackCandidates.Exclusion;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGTrack;

/** The tracks Arcade can offer (spec 003 T004): any string count, never percussion or empty, the choice left to the learner. */
public class ArcadeTrackCandidatesTest {

	private static TGTrack bass(SongFixture fixture) {
		TGTrack bass = fixture.addTrack();
		bass.setStrings(fixture.manager.createDefaultInstrumentStrings(4));
		fixture.note(bass, 0, 0, 0, 1, 0, TGDuration.QUARTER);
		return bass;
	}

	@Test
	public void aBassAndASevenStringGuitarCanBePerformedWhichTheOtherModesRefuse() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		TGTrack bass = bass(fixture);
		TGTrack seven = fixture.addTrack();
		seven.setStrings(fixture.manager.createDefaultInstrumentStrings(7));
		fixture.note(seven, 0, 0, 0, 1, 0, TGDuration.QUARTER);

		ArcadeTrackCandidates arcade = ArcadeTrackCandidates.evaluate(fixture.song, null);
		PracticeTrackCandidates others = PracticeTrackCandidates.evaluate(fixture.song, null);

		assertEquals(3, arcade.getCandidates().size());
		assertTrue(arcade.getExclusions().isEmpty());
		assertEquals(1, others.getCandidates().size(), "the wait and following modes are unchanged");
		assertEquals(PracticeTrackCandidates.Exclusion.NOT_SIX_STRINGS, others.getExclusions().get(bass));
	}

	@Test
	public void percussionAnEmptyTrackAndAnUndefinedStringAreExcludedWithTheirReason() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		TGTrack drums = fixture.addTrack();
		fixture.channelOf(drums).setBank(TGChannel.DEFAULT_PERCUSSION_BANK);
		fixture.note(drums, 0, 0, 0, 1, 36, TGDuration.QUARTER);
		TGTrack undefined = fixture.addTrack();
		undefined.getString(1).setValue(200);
		fixture.note(undefined, 0, 0, 0, 1, 0, TGDuration.QUARTER);
		TGTrack empty = fixture.addTrack();

		ArcadeTrackCandidates result = ArcadeTrackCandidates.evaluate(fixture.song, null);

		assertEquals(1, result.getCandidates().size());
		assertEquals(Exclusion.PERCUSSION, result.getExclusions().get(drums));
		assertEquals(Exclusion.UNDEFINED_STRING_PITCH, result.getExclusions().get(undefined));
		assertEquals(Exclusion.NO_PRACTICE_EVENTS, result.getExclusions().get(empty));
	}

	@Test
	public void theCursorTrackIsPreselectedThenAnInstrumentSoundAndNothingElseIsChosenForTheLearner() {
		SongFixture fixture = new SongFixture(1);
		TGTrack first = fixture.track();
		fixture.channelOf(first).setProgram((short) 0);
		fixture.quarter(0, 0, 1, 0);
		TGTrack bass = bass(fixture);
		fixture.channelOf(bass).setProgram((short) 33);

		assertSame(first, ArcadeTrackCandidates.evaluate(fixture.song, first).getPreselected().getTrack(), "the cursor first");
		assertSame(bass, ArcadeTrackCandidates.evaluate(fixture.song, null).getPreselected().getTrack(), "then a guitar or bass sound");
		assertTrue(ArcadeTrackCandidates.evaluate(fixture.song, null).getCandidates().get(1).hasGuitarHint());

		fixture.channelOf(bass).setProgram((short) 0);
		assertNull(ArcadeTrackCandidates.evaluate(fixture.song, null).getPreselected(), "no sound hints at all: the learner picks");
	}
}
