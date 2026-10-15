package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Preset;

/**
 * The adjustable window changes nothing else (spec 012 FR-010, SC-004, SC-003): the same played performance on the fake player gives the same judgements, score data and
 * results with a width of 75 ms as with the old Normal preset, and a width of 100 ms (the new default) judges on time a note that the old Normal judges late.
 */
public class TimingWindowParityTest {

	/** A passage of quarter notes at 120 beats a minute (a beat of 500 ms), each played with the offset given for it (negative: early). */
	private static String digestOf(TimingTolerance tolerance, int[] offsetsMillis) {
		SongFixture song = new SongFixture(8);
		song.tempoChange(0, 120);
		for (int i = 0; i < 28; i++) {
			song.quarter(i / 4, i % 4, 3, 5 + (i % 2));
		}
		FakePlayerRun run = new FakePlayerRun(song, 100, tolerance);
		run.start();
		int events = run.sequence.getEvents().size();
		for (int i = 0; i < events; i++) {
			if (i % 9 != 4) { // one note in nine is not played
				run.playEvent(i, offsetsMillis[i % offsetsMillis.length]);
			}
		}
		run.runAll();
		StringBuilder digest = new StringBuilder();
		List<FollowSession.EventResult> results = run.last.results();
		for (FollowSession.EventResult result : results) {
			digest.append(result.eventIndex()).append('|').append(result.status()).append('|').append(result.timing()).append('|').append(result.offsetNanos()).append('\n');
		}
		digest.append("extras=").append(run.last.extras().size());
		return digest.toString();
	}

	/**
	 * The fake player adds 65 ms of its own to every note (the articulation latency of the harness): an offset played of 0 is measured at +65 ms. These offsets are what is played;
	 * they give measured offsets from about -110 ms (early) to +195 ms (late), with several close to the 75 ms and 100 ms limits.
	 */
	private static final int[] MIXED = { 0, 20, -30, 60, 90, 110, -90, 5, -60, 10, 11, -150, -170, 130, -140, -141, 134, 136, -200, -330 };
	/** A played offset of 25 ms is a measured offset of 90 ms (25 + 65). */
	private static final int NINETY_MEASURED = 25;

	@Test
	public void aWidthOfSeventyFiveMillisecondsJudgesTheSamePerformanceExactlyAsTheOldNormal() {
		String old = digestOf(new TimingTolerance(Preset.NORMAL), MIXED);
		String now = digestOf(TimingTolerance.ofMillis(75), MIXED);
		assertTrue(old.contains("ON_TIME") && old.contains("LATE") && old.contains("EARLY"), "the performance has the three verdicts, so that the comparison is not empty:\n" + old);
		assertEquals(old, now, "a width of 75 ms changed a judgement of the performance");
	}

	@Test
	public void theNewDefaultJudgesOnTimeANoteNinetyMillisecondsLateThatTheOldNormalJudgesLate() {
		int[] ninetyLate = { NINETY_MEASURED };
		String old = digestOf(new TimingTolerance(Preset.NORMAL), ninetyLate);
		String widened = digestOf(TimingTolerance.ofMillis(FollowSettings.DEFAULT_WINDOW_MILLIS), ninetyLate);
		assertTrue(old.contains("LATE") && !old.contains("ON_TIME"), "with the old Normal (75 ms) a note 90 ms late is late:\n" + old);
		assertTrue(widened.contains("ON_TIME") && !widened.contains("LATE"), "with the new default (100 ms) it is on time:\n" + widened);
	}

	@Test
	public void theSameWidthAndTheSamePerformanceAlwaysGiveTheSameResult() {
		assertEquals(digestOf(TimingTolerance.ofMillis(120), MIXED), digestOf(TimingTolerance.ofMillis(120), MIXED));
	}
}
