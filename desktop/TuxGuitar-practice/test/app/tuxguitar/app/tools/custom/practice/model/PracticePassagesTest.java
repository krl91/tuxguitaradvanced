package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Passage segmentation (owner policy 2026-09-28, FR-088): 2 played measures, repeats separate,
 * anchored at the start event.
 */
public class PracticePassagesTest {

	@Test
	public void cutsEveryTwoPlayedMeasures() {
		SongFixture fixture = new SongFixture(5);
		for( int m = 0 ; m < 5 ; m ++ ) {
			fixture.quarter(m, 0, 1, 0);
			fixture.quarter(m, 1, 1, 1);
		}

		PracticePassages passages = PracticePassages.build(fixture.sequence(), 2);

		assertEquals(List.of("1-2:4", "3-4:4", "5-5:2"), describe(passages));
		assertEquals(0, passages.passageOf(0));
		assertEquals(0, passages.passageOf(3));
		assertEquals(1, passages.passageOf(4));
		assertEquals(2, passages.passageOf(9));
		assertEquals(-1, passages.passageOf(10));
	}

	@Test
	public void emptyMeasuresCountAsPlayedAndUnjudgedEventsAreNotCounted() {
		SongFixture fixture = new SongFixture(4);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 2, 0).getEffect().setDeadNote(true);
		// measure 2 is a rest measure
		fixture.quarter(2, 0, 1, 1);
		fixture.quarter(3, 0, 1, 2);

		PracticePassages passages = PracticePassages.build(fixture.sequence(), 2);

		// measure 1 with its dead note: 1 judged of 2 events; the rest measure still counts as played
		assertEquals(List.of("1-1:1", "3-4:2"), describe(passages));
	}

	@Test
	public void eachOccurrenceOfARepeatedSectionIsItsOwnPassage() {
		SongFixture fixture = new SongFixture(4);
		fixture.header(1).setRepeatOpen(true);
		fixture.header(2).setRepeatClose(1);
		for( int m = 0 ; m < 4 ; m ++ ) {
			fixture.quarter(m, 0, 1, m);
		}

		PracticePassages passages = PracticePassages.build(fixture.sequence(), 2);

		// played 1, 2, 3, 2, 3, 4: the repeat sign and the jump back both start a passage
		assertEquals(List.of("1-1:1", "2-3:2", "2-3:2", "4-4:1"), describe(passages));
	}

	@Test
	public void alternateEndingsAreSeparate() {
		SongFixture fixture = new SongFixture(4);
		fixture.header(0).setRepeatOpen(true);
		fixture.header(1).setRepeatAlternative(1);
		fixture.header(1).setRepeatClose(1);
		fixture.header(2).setRepeatAlternative(2);
		for( int m = 0 ; m < 4 ; m ++ ) {
			fixture.quarter(m, 0, 1, m);
		}

		// played 1, 2, 1, 3, 4
		assertEquals(List.of("1-2:2", "1-1:1", "3-4:2"), describe(PracticePassages.build(fixture.sequence(), 2)));
	}

	@Test
	public void theFirstPassageStartsAtTheStartEvent() {
		SongFixture fixture = new SongFixture(4);
		for( int m = 0 ; m < 4 ; m ++ ) {
			fixture.quarter(m, 0, 1, 0);
			fixture.quarter(m, 2, 1, 1);
		}
		PracticeSequence sequence = PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(),
				fixture.track().getMeasure(1), fixture.track().getMeasure(1).getStart() + 2 * SongFixture.QUARTER);

		PracticePassages passages = PracticePassages.build(sequence, 2);

		assertEquals(3, sequence.getStartIndex());
		assertEquals(-1, passages.passageOf(2));
		assertEquals(List.of("2-3:3", "4-4:2"), describe(passages));
		assertEquals(3, passages.getPassages().get(0).getFirstIndex());
	}

	@Test
	public void passageLengthIsAParameter() {
		SongFixture fixture = new SongFixture(3);
		for( int m = 0 ; m < 3 ; m ++ ) {
			fixture.quarter(m, 0, 1, m);
		}

		assertEquals(List.of("1-1:1", "2-2:1", "3-3:1"), describe(PracticePassages.build(fixture.sequence(), 1)));
		assertEquals(List.of("1-3:3"), describe(PracticePassages.build(fixture.sequence(), 4)));
	}

	/** "firstMeasure-lastMeasure:judged" per passage. */
	private static List<String> describe(PracticePassages passages) {
		List<String> result = new ArrayList<String>();
		for( PracticePassages.Passage passage : passages.getPassages() ) {
			result.add(passage.getFirstMeasure() + "-" + passage.getLastMeasure() + ":" + passage.getJudgedCount());
		}
		return result;
	}
}
