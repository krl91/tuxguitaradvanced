package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Spec 014 (T016, FR-008, research R4): the sequence of the events a level keeps. They are the same objects, in order, so their times still come from the song;
 * the start index follows the first kept event at or after the original start; a kept note whose legato origin was removed is judged as a plain note; the
 * original sequence is unchanged.
 */
public class PracticeSequenceKeepingTest {

	private static SongFixture eightQuarters() {
		SongFixture fixture = new SongFixture(2);
		for (int i = 0; i < 8; i++) {
			fixture.quarter(i / 4, i % 4, 1, i);
		}
		return fixture;
	}

	private static PracticeSequence sequenceOf(SongFixture fixture) {
		return PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), null, 0L);
	}

	private static boolean[] mask(int size, int... kept) {
		boolean[] mask = new boolean[size];
		for (int index : kept) {
			mask[index] = true;
		}
		return mask;
	}

	@Test
	public void theKeptEventsAreTheSameObjectsInOrderAndTheOriginalIsUnchanged() {
		PracticeSequence full = sequenceOf(eightQuarters());
		PracticeSequence kept = full.keeping(mask(8, 0, 3, 5));
		assertEquals(3, kept.getEvents().size());
		assertSame(full.getEvents().get(0), kept.getEvents().get(0));
		assertSame(full.getEvents().get(3), kept.getEvents().get(1));
		assertSame(full.getEvents().get(5), kept.getEvents().get(2));
		assertEquals(8, full.getEvents().size(), "the original keeps its events");
		assertSame(full.getTrackTuning(), kept.getTrackTuning());
	}

	@Test
	public void keepingEverythingGivesTheSameEventsAndKeepingNothingGivesAnEmptySequence() {
		PracticeSequence full = sequenceOf(eightQuarters());
		PracticeSequence all = full.keeping(mask(8, 0, 1, 2, 3, 4, 5, 6, 7));
		assertEquals(full.getEvents(), all.getEvents());
		assertEquals(full.getStartIndex(), all.getStartIndex());
		PracticeSequence none = full.keeping(mask(8));
		assertTrue(none.isEmpty());
		assertEquals(0, none.getStartIndex());
	}

	@Test
	public void theStartIndexIsTheFirstKeptEventAtOrAfterTheOriginalStart() {
		SongFixture fixture = new SongFixture(3);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.quarter(1, 2, 1, 2);
		fixture.quarter(2, 0, 1, 3);
		PracticeSequence full = PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), fixture.track().getMeasure(1),
				fixture.track().getMeasure(1).getStart() + 2 * SongFixture.QUARTER);
		assertEquals(2, full.getStartIndex(), "the cursor is on the third event");
		// the third event is kept: it is still the start, now at its new rank
		PracticeSequence keepsStart = full.keeping(mask(4, 0, 2, 3));
		assertEquals(1, keepsStart.getStartIndex());
		assertSame(full.getEvents().get(2), keepsStart.getEvents().get(keepsStart.getStartIndex()));
		// the third event is removed: the start is the next kept one
		PracticeSequence next = full.keeping(mask(4, 0, 1, 3));
		assertEquals(2, next.getStartIndex());
		assertSame(full.getEvents().get(3), next.getEvents().get(next.getStartIndex()));
		// nothing is kept at or after the start: nothing to play
		PracticeSequence before = full.keeping(mask(4, 0, 1));
		assertEquals(2, before.getStartIndex());
		assertEquals(2, before.getEvents().size(), "the start is past the last event: an empty run");
	}

	@Test
	public void aKeptNoteWhoseLegatoOriginWasRemovedIsJudgedAsAPlainNote() {
		SongFixture fixture = new SongFixture(1);
		fixture.legatoPair(0, 0, 1, 2, 4);
		PracticeSequence full = sequenceOf(fixture);
		assertEquals(2, full.getEvents().size());
		assertFalse(full.targetsOf(1).isEmpty(), "with its origin the destination is a hammer-on");
		PracticeSequence alone = full.keeping(mask(2, 1));
		assertEquals(1, alone.getEvents().size());
		assertTrue(alone.targetsOf(0).isEmpty(), "without its origin it is a plain note");
	}

	@Test
	public void aMaskOfTheWrongLengthIsRefused() {
		PracticeSequence full = sequenceOf(eightQuarters());
		assertThrows(IllegalArgumentException.class, () -> full.keeping(new boolean[3]));
		assertThrows(IllegalArgumentException.class, () -> full.keeping(null));
	}
}
