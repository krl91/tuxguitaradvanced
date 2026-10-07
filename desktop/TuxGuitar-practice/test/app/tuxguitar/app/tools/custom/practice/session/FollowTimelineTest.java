package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The target times of the events for a tempo percentage (spec 002 T087, T094): from the start of each played measure and the tempo of that
 * measure, in the order of the player's repeats and alternate endings, every measure taking its time, empty ones too.
 */
public class FollowTimelineTest {

	private static final long MS = 1_000_000L;

	private static PracticeSequence sequence(SongFixture fixture) {
		return fixture.sequence();
	}

	@Test
	public void timesAreFromTheStartOfTheFirstMeasureAtTheTempoOfTheScore() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 2, 1, 2);
		fixture.quarter(0, 3, 1, 3);
		fixture.quarter(1, 0, 1, 4);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		assertEquals(5, timeline.size());
		// 120 beats a minute: a quarter note is 500 ms, a measure 2 s
		long[] expected = { 0, 500, 1000, 1500, 2000 };
		for( int i = 0 ; i < expected.length ; i ++ ) {
			assertEquals(expected[i] * MS, timeline.targetNanos(i), "event " + i);
			assertEquals(960L * i, timeline.targetTicks(i), "tick of event " + i);
			assertEquals(500 * MS, timeline.beatNanos(i));
		}
		assertEquals(100, timeline.percent());
	}

	@Test
	public void thePercentageScalesTheTimesAndNotTheTicks() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 3, 1, 3);
		for( int percent : new int[] { 50, 70, 100, 120 } ) {
			FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), percent);
			double scale = 100.0 / percent;
			assertEquals(Math.round(1500 * MS * scale), timeline.targetNanos(2), 2, "at " + percent + " %");
			assertEquals(Math.round(500 * MS * scale), timeline.beatNanos(0), 2, "the local beat at " + percent + " %");
			assertEquals(2880L, timeline.targetTicks(2), "the ticks do not depend on the percentage");
		}
	}

	@Test
	public void aTempoChangeIsFollowedMeasureByMeasure() {
		SongFixture fixture = new SongFixture(3);
		fixture.tempoChange(1, 60);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.quarter(1, 2, 1, 2);
		fixture.quarter(2, 0, 1, 3);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		// measure 0 at 120 (2 s), measure 1 at 60 (4 s): events at 0, 2000, 2000 + 2000, 2000 + 4000
		assertEquals(0, timeline.targetNanos(0));
		assertEquals(2000 * MS, timeline.targetNanos(1));
		assertEquals(4000 * MS, timeline.targetNanos(2));
		assertEquals(6000 * MS, timeline.targetNanos(3));
		assertEquals(500 * MS, timeline.beatNanos(0));
		assertEquals(1000 * MS, timeline.beatNanos(1));
		assertEquals(1000 * MS, timeline.beatNanos(2));
		// the tempo is that of the measure; at 50 % everything is twice as long
		FollowTimeline half = FollowTimeline.build(fixture.song, sequence(fixture), 50);
		// measure 0 lasts 4 s at 50 % of 120, measure 1 lasts 8 s at 50 % of 60
		assertEquals(12000 * MS, half.targetNanos(3));
		assertEquals(2000 * MS, half.beatNanos(1));
	}

	@Test
	public void aRepeatedPassageIsPlayedTwiceAndTakesItsTimeTwice() {
		SongFixture fixture = new SongFixture(2);
		fixture.repeat(0, 0, 2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		PracticeSequence sequence = sequence(fixture);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence, 100);

		// measure 0, measure 0 again, measure 1: the same beat of the score is two events
		assertEquals(3, timeline.size());
		assertEquals(0, timeline.targetNanos(0));
		assertEquals(2000 * MS, timeline.targetNanos(1));
		assertEquals(4000 * MS, timeline.targetNanos(2));
		assertEquals(0L, timeline.targetTicks(0));
		assertEquals(3840L, timeline.targetTicks(1), "the player's ticks are expanded: the repeat goes on, it does not rewind");
		assertEquals(7680L, timeline.targetTicks(2));
	}

	@Test
	public void alternateEndingsFollowThePlayersOrder() {
		SongFixture fixture = new SongFixture(3);
		fixture.song.getMeasureHeader(0).setRepeatOpen(true);
		fixture.song.getMeasureHeader(1).setRepeatAlternative(1);
		fixture.song.getMeasureHeader(1).setRepeatClose(1);
		fixture.song.getMeasureHeader(2).setRepeatAlternative(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.quarter(2, 0, 1, 2);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		// first time through: measure 0 then the first ending (measure 1); second time: measure 0 then the second ending (measure 2)
		assertEquals(4, timeline.size());
		assertEquals(0, timeline.targetNanos(0));
		assertEquals(2000 * MS, timeline.targetNanos(1));
		assertEquals(4000 * MS, timeline.targetNanos(2));
		assertEquals(6000 * MS, timeline.targetNanos(3));
	}

	@Test
	public void anEmptyMeasureTakesItsTime() {
		SongFixture fixture = new SongFixture(4);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(3, 0, 1, 1);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		assertEquals(6000 * MS, timeline.targetNanos(1));
		assertEquals(8000 * MS, timeline.durationNanos(), "the whole played length, empty measures included");
	}

	@Test
	public void theGapToTheNeighbouringEventsIsToTheNearestEventOfAnotherTime() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 3, 1, 3);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		assertEquals(Long.MAX_VALUE, timeline.gapBeforeNanos(0), "nothing before the first event");
		assertEquals(500 * MS, timeline.gapAfterNanos(0));
		assertEquals(500 * MS, timeline.gapBeforeNanos(1));
		assertEquals(1000 * MS, timeline.gapAfterNanos(1));
		assertEquals(1000 * MS, timeline.gapBeforeNanos(2));
		assertEquals(Long.MAX_VALUE, timeline.gapAfterNanos(2), "nothing after the last event");
	}

	@Test
	public void aTimelineNeedsAUsablePercentage() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		PracticeSequence sequence = sequence(fixture);
		assertThrows(IllegalArgumentException.class, () -> FollowTimeline.build(fixture.song, sequence, 0));
		assertThrows(IllegalArgumentException.class, () -> FollowTimeline.build(fixture.song, sequence, -50));
		assertTrue(FollowTimeline.build(fixture.song, sequence, 25).targetNanos(0) == 0);
	}

	@Test
	public void anyTickIsTurnedIntoATimeMeasureByMeasure() {
		SongFixture fixture = new SongFixture(3);
		fixture.tempoChange(1, 60);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 2, 1, 1);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		// the ticks of the events give their own times back
		assertEquals(timeline.targetNanos(0), timeline.nanosAtTick(timeline.targetTicks(0)));
		assertEquals(timeline.targetNanos(1), timeline.nanosAtTick(timeline.targetTicks(1)));
		// half a quarter into measure 0 (120 beats a minute) and into measure 1 (60 beats a minute)
		assertEquals(250 * MS, timeline.nanosAtTick(480));
		assertEquals(2000 * MS + 500 * MS, timeline.nanosAtTick(3840 + 480));
		// before the start and after the end the nearest tempo goes on
		assertEquals(-500 * MS, timeline.nanosAtTick(-960));
		assertEquals(2000 * MS + 4000 * MS + 4000 * MS, timeline.nanosAtTick(3840 + 3840 + 3840), "after the last measure (tempo 60 goes on in measure 2)");
	}

	@Test
	public void thePlayStartTickIsWhereTheMeasureOfTheEventBeginsInTheExpandedTicks() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 2, 1, 0);
		fixture.quarter(1, 1, 1, 1);
		fixture.repeat(0, 1, 2);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, sequence(fixture), 100);

		assertEquals(0L, timeline.playStartTicks(0));
		assertEquals(3840L, timeline.playStartTicks(1));
		assertEquals(3840L * 2, timeline.playStartTicks(2), "the repeat plays measure 0 again, further in the expanded ticks");
		assertEquals(timeline.targetTicks(1) - 960L, timeline.playStartTicks(1));
	}
}
