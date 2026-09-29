package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.AutoPassReason;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.Kind;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent.Technique;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.effects.TGEffectBend;
import app.tuxguitar.song.models.effects.TGEffectGrace;

public class PracticeSequenceBuilderTest {

	@Test
	public void followsRepeatsInPlaybackOrder() {
		SongFixture fixture = new SongFixture(3);
		fixture.header(0).setRepeatOpen(true);
		fixture.header(1).setRepeatClose(1);
		for( int m = 0 ; m < 3 ; m ++ ) {
			fixture.quarter(m, 0, 1, m);
		}

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(List.of(0, 1, 0, 1, 2), measureIndexes(events));
		assertEquals(List.of(0, 1, 2, 3, 4), playIndexes(events));
		assertEquals(List.of(64, 65, 64, 65, 66), expectedPitches(events));
	}

	@Test
	public void followsAlternateEndings() {
		SongFixture fixture = new SongFixture(4);
		fixture.header(0).setRepeatOpen(true);
		fixture.header(1).setRepeatAlternative(1);
		fixture.header(1).setRepeatClose(1);
		fixture.header(2).setRepeatAlternative(2);
		for( int m = 0 ; m < 4 ; m ++ ) {
			fixture.quarter(m, 0, 1, m);
		}

		assertEquals(List.of(0, 1, 0, 2, 3), measureIndexes(fixture.sequence().getEvents()));
	}

	@Test
	public void noteOverANoteStillSoundingInAnotherVoiceIsAChordToleratingTheSustainedPitch() {
		SongFixture fixture = new SongFixture(1);
		fixture.note(0, 0, 0, 5, 0, TGDuration.HALF);
		fixture.note(0, 1, 1, 1, 0, TGDuration.QUARTER);
		fixture.note(0, 2, 1, 1, 2, TGDuration.QUARTER);

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(List.of(Kind.EXPECTED_NOTE, Kind.EXPECTED_CHORD, Kind.EXPECTED_NOTE), kinds(events));
		assertEquals(45, events.get(0).getExpectedMidi());
		assertEquals(PracticeEvent.NO_PITCH, events.get(1).getExpectedMidi());
		assertArrayEquals(new int[] { 64 }, events.get(1).getRequiredPitches());
		assertArrayEquals(new int[] { 45 }, events.get(1).getToleratedPitches());
		assertEquals(66, events.get(2).getExpectedMidi());
	}

	@Test
	public void anOpenChordRequiresItsDistinctPitchesWithStringAndFretAsInstruction() {
		SongFixture fixture = new SongFixture(1);
		// open E major: 0 2 2 1 0 0 from string 6 to string 1
		int[][] shape = { { 6, 0 }, { 5, 2 }, { 4, 2 }, { 3, 1 }, { 2, 0 }, { 1, 0 } };
		for( int[] note : shape ) {
			fixture.quarter(0, 0, note[0], note[1]);
		}

		PracticeEvent chord = fixture.sequence().getEvents().get(0);

		assertEquals(Kind.EXPECTED_CHORD, chord.getKind());
		assertArrayEquals(new int[] { 40, 47, 52, 56, 59, 64 }, chord.getRequiredPitches());
		assertEquals(0, chord.getToleratedPitches().length);
		assertEquals(6, chord.getChordNotes().size());
		assertEquals(6, chord.getNotes().size());
		assertEquals(6, chord.getChordNotes().get(0).getStringNumber());
		assertEquals(0, chord.getChordNotes().get(0).getFret());
		assertFalse(chord.hasDuplicatePitches());
		assertFalse(chord.isSameAsPrevious());
	}

	@Test
	public void theSamePitchOnTwoStringsCountsOnce() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 5);
		fixture.quarter(0, 0, 3, 2);

		PracticeEvent chord = fixture.sequence().getEvents().get(0);

		assertArrayEquals(new int[] { 57, 64 }, chord.getRequiredPitches());
		assertEquals(3, chord.getChordNotes().size());
		assertTrue(chord.hasDuplicatePitches());
	}

	@Test
	public void deadStringsInAChordAreIgnoredAndADeadOnlyRemainderPassesAutomatically() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 6, 0).getEffect().setDeadNote(true);
		fixture.quarter(0, 0, 5, 0);
		fixture.quarter(0, 0, 4, 2);
		fixture.note(0, 1, 0, 5, 3, TGDuration.HALF);
		fixture.note(0, 2, 1, 1, 0, TGDuration.QUARTER).getEffect().setDeadNote(true);

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(Kind.EXPECTED_CHORD, events.get(0).getKind());
		assertArrayEquals(new int[] { 45, 52 }, events.get(0).getRequiredPitches());
		assertEquals(2, events.get(0).getChordNotes().size());
		assertEquals(3, events.get(0).getNotes().size());
		assertEquals(Kind.EXPECTED_NOTE, events.get(1).getKind());
		assertEquals(AutoPassReason.DEAD, events.get(2).getAutoPassReason());
		assertEquals(3, events.size());
	}

	@Test
	public void aChordWithABentNoteStaysAMultiNoteEvent() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 2, 3);
		TGEffectBend bend = fixture.factory.newEffectBend();
		bend.addPoint(0, 0);
		bend.addPoint(6, 4);
		fixture.quarter(0, 0, 3, 5).getEffect().setBend(bend);

		PracticeEvent event = fixture.sequence().getEvents().get(0);

		assertEquals(Kind.MULTI_NOTE, event.getKind());
		assertEquals(0, event.getRequiredPitches().length);
		assertEquals(2, event.getNotes().size());
	}

	@Test
	public void aNoteOrChordAlreadySoundingInThePreviousChordNeedsANewArticulation() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 0);
		fixture.quarter(0, 1, 1, 0);
		fixture.quarter(0, 2, 1, 0);
		fixture.quarter(0, 2, 2, 0);
		fixture.quarter(0, 3, 1, 1);

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(List.of(Kind.EXPECTED_CHORD, Kind.EXPECTED_NOTE, Kind.EXPECTED_CHORD, Kind.EXPECTED_NOTE), kinds(events));
		assertFalse(events.get(0).isSameAsPrevious());
		assertTrue(events.get(1).isSameAsPrevious());
		assertFalse(events.get(2).isSameAsPrevious());
		assertFalse(events.get(3).isSameAsPrevious());
	}

	@Test
	public void singleNoteEventsCarryTheirScoreNote() {
		SongFixture fixture = new SongFixture(1);
		TGNote note = fixture.quarter(0, 0, 6, 3);

		PracticeEvent event = fixture.sequence().getEvents().get(0);

		assertEquals(List.of(note), event.getNotes());
		assertEquals(0, event.getChordNotes().size());
	}

	@Test
	public void tiedContinuationsAndRestsProduceNoEvent() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 6, 3);
		fixture.quarter(0, 1, 6, 3).setTiedNote(true);
		fixture.quarter(0, 3, 6, 5);

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(List.of(43, 45), expectedPitches(events));
	}

	@Test
	public void classifiesChordsDeadNotesAndUnsupportedTechniques() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 1);
		fixture.quarter(0, 1, 3, 0).getEffect().setDeadNote(true);
		fixture.quarter(0, 2, 4, 0).getEffect().setDeadNote(true);
		fixture.quarter(0, 2, 5, 0).getEffect().setDeadNote(true);
		TGEffectBend bend = fixture.factory.newEffectBend();
		bend.addPoint(0, 0);
		bend.addPoint(6, 4);
		fixture.quarter(0, 3, 3, 7).getEffect().setBend(bend);
		fixture.quarter(1, 0, 2, 12).getEffect().setHarmonic(fixture.factory.newEffectHarmonic());
		fixture.quarter(1, 1, 1, 5).getEffect().setTremoloBar(fixture.factory.newEffectTremoloBar());
		fixture.quarter(1, 2, 1, 5).getEffect().setTrill(fixture.factory.newEffectTrill());

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(Kind.EXPECTED_CHORD, events.get(0).getKind());
		assertEquals(PracticeEvent.NO_PITCH, events.get(0).getExpectedMidi());
		assertArrayEquals(new int[] { 60, 64 }, events.get(0).getRequiredPitches());
		assertEquals(AutoPassReason.DEAD, events.get(1).getAutoPassReason());
		assertEquals(AutoPassReason.DEAD_ONLY_CHORD, events.get(2).getAutoPassReason());
		assertEquals(AutoPassReason.BEND, events.get(3).getAutoPassReason());
		assertEquals(AutoPassReason.HARMONIC, events.get(4).getAutoPassReason());
		assertEquals(AutoPassReason.TREMOLO_BAR, events.get(5).getAutoPassReason());
		assertEquals(AutoPassReason.TRILL, events.get(6).getAutoPassReason());
		assertEquals(7, events.size());
	}

	@Test
	public void graceNotesPrecedeTheirPrincipalNote() {
		SongFixture fixture = new SongFixture(1);
		TGEffectGrace pitched = fixture.factory.newEffectGrace();
		pitched.setFret(2);
		fixture.quarter(0, 0, 3, 4).getEffect().setGrace(pitched);
		TGEffectGrace dead = fixture.factory.newEffectGrace();
		dead.setFret(0);
		dead.setDead(true);
		fixture.quarter(0, 1, 3, 4).getEffect().setGrace(dead);

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(4, events.size());
		assertTrue(events.get(0).isGrace());
		assertEquals(57, events.get(0).getExpectedMidi());
		assertEquals(59, events.get(1).getExpectedMidi());
		assertFalse(events.get(1).isGrace());
		assertEquals(AutoPassReason.GRACE_WITHOUT_PITCH, events.get(2).getAutoPassReason());
		assertEquals(59, events.get(3).getExpectedMidi());
	}

	@Test
	public void expectedPitchUsesOffsetAndMarksRepeatedPitches() {
		for( int offset : new int[] { 0, 2, -1, -8 } ) {
			SongFixture fixture = new SongFixture(1);
			fixture.track().setOffset(offset);
			fixture.quarter(0, 0, 6, 0);
			fixture.quarter(0, 1, 6, 0);
			TGNote hammered = fixture.quarter(0, 2, 5, 3);
			hammered.getEffect().setHammer(true);

			List<PracticeEvent> events = fixture.sequence().getEvents();

			assertEquals(List.of(40 + offset, 40 + offset, 48 + offset), expectedPitches(events));
			assertFalse(events.get(0).isSameAsPrevious());
			assertTrue(events.get(1).isSameAsPrevious());
			assertFalse(events.get(2).isSameAsPrevious());
			assertEquals(java.util.Set.of(Technique.HAMMER_ON_PULL_OFF), events.get(2).getTechniques());
			assertEquals(6, events.get(0).getStringNumber());
			assertEquals(0, events.get(0).getFret());
		}
	}

	@Test
	public void sequenceInvariantsHold() {
		SongFixture fixture = richFixture();

		assertInvariants(fixture.sequence().getEvents());
	}

	@Test
	public void tgFileRoundTripGivesTheSameSequence() {
		SongFixture fixture = richFixture();
		TGSong reloaded = fixture.roundTrip();

		PracticeSequence original = fixture.sequence();
		PracticeSequence fromFile = PracticeSequenceBuilder.build(reloaded, reloaded.getTrack(0),
				TrackTuning.of(reloaded.getTrack(0), null));

		assertEquals(kinds(original.getEvents()), kinds(fromFile.getEvents()));
		assertEquals(expectedPitches(original.getEvents()), expectedPitches(fromFile.getEvents()));
		assertEquals(measureIndexes(original.getEvents()), measureIndexes(fromFile.getEvents()));
		assertInvariants(fromFile.getEvents());
	}

	@Test
	public void startsExactlyOnTheEventUnderTheCursor() {
		SongFixture fixture = fourNotes();
		assertEquals(2, startAt(fixture, fixture.track(), 0, 2));
	}

	@Test
	public void startsAtTheFirstEventAfterACursorBetweenEvents() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 2, 1, 1);
		fixture.quarter(1, 0, 1, 2);
		assertEquals(1, startAt(fixture, fixture.track(), 0, 1));
		assertEquals(2, startAt(fixture, fixture.track(), 0, 3));
	}

	@Test
	public void cursorOnAnotherTrackOrAfterTheLastNoteStartsAtTheBeginning() {
		SongFixture fixture = fourNotes();
		TGTrack other = fixture.addTrack();
		assertEquals(0, startAt(fixture, other, 0, 2));
		assertEquals(0, PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), null, 0).getStartIndex());

		SongFixture trailing = new SongFixture(2);
		trailing.quarter(0, 0, 1, 0);
		assertEquals(0, startAt(trailing, trailing.track(), 1, 2));
	}

	@Test
	public void startingInsideARepeatUsesItsFirstOccurrenceWithoutRewinding() {
		SongFixture fixture = new SongFixture(3);
		fixture.header(0).setRepeatOpen(true);
		fixture.header(1).setRepeatClose(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.quarter(1, 2, 1, 2);
		fixture.quarter(2, 0, 1, 3);

		PracticeSequence sequence = PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(),
				fixture.track().getMeasure(1), fixture.track().getMeasure(1).getStart() + 2 * SongFixture.QUARTER);

		assertEquals(2, sequence.getStartIndex());
		List<PracticeEvent> rest = sequence.getEvents().subList(sequence.getStartIndex(), sequence.getEvents().size());
		assertEquals(List.of(66, 64, 65, 66, 67), expectedPitches(rest));
	}

	private static SongFixture fourNotes() {
		SongFixture fixture = new SongFixture(1);
		for( int q = 0 ; q < 4 ; q ++ ) {
			fixture.quarter(0, q, 1, q);
		}
		return fixture;
	}

	private static int startAt(SongFixture fixture, TGTrack caretTrack, int measureIndex, int quarter) {
		TGMeasure measure = caretTrack.getMeasure(measureIndex);
		return PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), measure,
				measure.getStart() + quarter * SongFixture.QUARTER).getStartIndex();
	}

	private static SongFixture richFixture() {
		SongFixture fixture = new SongFixture(3);
		fixture.header(0).setRepeatOpen(true);
		fixture.header(1).setRepeatClose(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 0);
		fixture.quarter(0, 2, 2, 1);
		fixture.quarter(0, 2, 3, 2);
		fixture.quarter(1, 0, 4, 2).getEffect().setDeadNote(true);
		TGEffectGrace grace = fixture.factory.newEffectGrace();
		grace.setFret(5);
		fixture.quarter(1, 1, 2, 7).getEffect().setGrace(grace);
		fixture.quarter(2, 0, 6, 3);
		return fixture;
	}

	private static void assertInvariants(List<PracticeEvent> events) {
		for( int i = 0 ; i < events.size() ; i ++ ) {
			PracticeEvent event = events.get(i);
			assertEquals(i, event.getSequenceIndex());
			if( event.getKind() == Kind.EXPECTED_NOTE ) {
				assertTrue(event.getExpectedMidi() >= 0 && event.getExpectedMidi() <= 127);
			} else {
				assertEquals(PracticeEvent.NO_PITCH, event.getExpectedMidi());
			}
			assertEquals(event.getKind() == Kind.EXPECTED_CHORD, event.getRequiredPitches().length > 0);
		}
	}

	private static List<Kind> kinds(List<PracticeEvent> events) {
		List<Kind> kinds = new ArrayList<Kind>();
		for( PracticeEvent event : events ) {
			kinds.add(event.getKind());
		}
		return kinds;
	}

	private static List<Integer> expectedPitches(List<PracticeEvent> events) {
		List<Integer> pitches = new ArrayList<Integer>();
		for( PracticeEvent event : events ) {
			pitches.add(event.getExpectedMidi());
		}
		return pitches;
	}

	private static List<Integer> measureIndexes(List<PracticeEvent> events) {
		List<Integer> indexes = new ArrayList<Integer>();
		for( PracticeEvent event : events ) {
			indexes.add(event.getMeasureIndex());
		}
		return indexes;
	}

	private static List<Integer> playIndexes(List<PracticeEvent> events) {
		List<Integer> indexes = new ArrayList<Integer>();
		for( PracticeEvent event : events ) {
			indexes.add(event.getPlayIndex());
		}
		return indexes;
	}
}
