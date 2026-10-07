package app.tuxguitar.app.tools.custom.practice.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;

/**
 * The fakes are tools of the tests of the tempo-following mode (spec 002 T128): a test that trusts a fake whose arithmetic is wrong proves nothing,
 * so the clock is checked against the timeline of the song, which is tested on its own (tempo changes, repeats).
 */
public class FakeClockAndPlayerTest {

	private static final long MS = 1_000_000L;
	private static final long BASE = 960L;

	private static FakeTempoClock clockOf(FollowTimeline timeline, long lengthTicks) {
		return new FakeTempoClock(ticks -> {
			long whole = (long) Math.floor(ticks);
			double part = ticks - whole;
			return timeline.nanosAtTick(whole) * (1.0 - part) + timeline.nanosAtTick(whole + 1) * part;
		}, BASE, lengthTicks);
	}

	private static FollowTimeline timelineOf(SongFixture fixture, int percent) {
		return FollowTimeline.build(fixture.song, fixture.sequence(), percent);
	}

	@Test
	public void theClockIsNotReadyAndSaysNothingBeforeThePlayerStarts() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		FakeTempoClock clock = clockOf(timelineOf(fixture, 100), 7680);
		assertFalse(clock.isReady());
		assertTrue(Double.isNaN(clock.tickAt(0)));
		assertEquals(Long.MIN_VALUE, clock.wallNanosAt(BASE));
		assertTrue(clock.isStalled(0));
	}

	@Test
	public void thePositionMovesWithTheWallClockOnlyWhenTheTestAdvancesIt() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		FakeTempoClock clock = clockOf(timelineOf(fixture, 100), 7680);
		clock.setNow(5_000 * MS);
		clock.start(BASE);
		assertTrue(clock.isReady());
		assertEquals(BASE, clock.tickAt(5_000 * MS), 1e-6);
		clock.advance(500 * MS);
		// 120 beats a minute: a quarter note (960 ticks) every 500 ms
		assertEquals(BASE + 960.0, clock.positionNow(), 1.0);
		assertEquals(5_000 * MS + 1_000 * MS, clock.wallNanosAt(BASE + 1920.0), MS);
	}

	@Test
	public void aTempoChangeAndThePercentageChangeTheRateOfThePosition() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.tempoChange(1, 60);
		FakeTempoClock half = clockOf(timelineOf(fixture, 50), 7680);
		half.start(BASE);
		// measure 0 at 120 beats a minute and 50 %: 60 beats a minute, a measure takes 4 s
		half.advance(4_000 * MS);
		assertEquals(BASE + 3840.0, half.positionNow(), 2.0);
		// measure 1 at 60 beats a minute and 50 %: 30 beats a minute, a quarter note takes 2 s
		half.advance(2_000 * MS);
		assertEquals(BASE + 3840.0 + 960.0, half.positionNow(), 2.0);
	}

	@Test
	public void aRepeatGoesOnInExpandedTicksAndTakesItsTimeTwice() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.repeat(0, 0, 2);
		FollowTimeline timeline = timelineOf(fixture, 100);
		FakeTempoClock clock = clockOf(timeline, 3 * 3840);
		clock.start(BASE);
		clock.advance(2_000 * MS);
		assertEquals(BASE + 3840.0, clock.positionNow(), 2.0);
		clock.advance(2_000 * MS);
		assertEquals(BASE + 2 * 3840.0, clock.positionNow(), 2.0);
		assertEquals(clock.wallNanosAt(BASE + timeline.targetTicks(2)), timeline.targetNanos(2), 1.0, "the third event is the first of measure 1, after the repeat");
	}

	@Test
	public void pauseHoldsThePositionResumeContinuesAndSeekJumps() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		FakeTempoClock clock = clockOf(timelineOf(fixture, 100), 7680);
		clock.start(BASE);
		clock.advance(500 * MS);
		clock.pause();
		double held = clock.positionNow();
		clock.advance(3_000 * MS);
		assertEquals(held, clock.positionNow(), 1e-9);
		assertTrue(clock.isStalled(clock.nowNanos()));
		clock.resume();
		clock.advance(500 * MS);
		assertEquals(held + 960.0, clock.positionNow(), 2.0);
		clock.seek(BASE + 3840);
		assertEquals(BASE + 3840.0, clock.positionNow(), 1e-6);
		clock.stopClock();
		assertTrue(Double.isNaN(clock.positionNow()));
	}

	@Test
	public void theCountDownRunsOutBeforeThePlayersClockStartsAndTheStartNotificationComesBySelfOrOnCommand() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		FakeTempoClock clock = clockOf(timelineOf(fixture, 100), 7680);
		FakeMidiPlayer player = new FakeMidiPlayer(clock).withInitialState(100, true, false).countDownNanos(2_000 * MS);
		player.setTickPosition(BASE);
		try {
			player.play();
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
		assertTrue(player.isRunning());
		assertTrue(player.isCounting());
		assertFalse(clock.isReady(), "no position during the count-down");
		player.advance(1_999 * MS);
		assertFalse(clock.isReady());
		player.advance(1 * MS);
		assertTrue(clock.isReady());
		assertEquals(BASE, clock.positionNow(), 1e-6);
		player.advance(500 * MS);
		assertEquals(BASE + 960.0, clock.positionNow(), 2.0);
		assertEquals("[tick=960, play, clock-started]", player.calls().toString(), "the order of what happened");
	}
}
