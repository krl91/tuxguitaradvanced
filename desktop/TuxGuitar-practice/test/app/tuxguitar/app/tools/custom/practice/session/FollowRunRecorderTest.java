package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.audio.engine.analysis.HopListener;

/**
 * Spec 016 (T010, FR-003): what a Follow run keeps for its record. The sound only when asked, from the count-in to the end, its hops let go once;
 * the first sample dated on the timeline; no record for a run that ended before its count-in did; the settings of the run in the header.
 */
public class FollowRunRecorderTest {

	private static final AudioRoute ROUTE = new AudioRoute("Rocksmith USB Guitar Adapter", "MacBook Speakers", 48000, "1ch 16bit");

	private static final class FakeHops implements FollowRunRecorder.Hops {
		final List<HopListener> listeners = new ArrayList<HopListener>();
		int removed;

		public void add(HopListener listener) {
			this.listeners.add(listener);
		}

		public void remove(HopListener listener) {
			if (this.listeners.remove(listener)) {
				this.removed++;
			}
		}
	}

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static FollowRunRecorder.Start start() {
		return new FollowRunRecorder.Start("_TEST_verifie.tg", 1, 70, 100, ROUTE, LatencyCompensation.none());
	}

	@Test
	public void aCompleteRunGivesItsRecordWithTheSettingsOfTheRun() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		FakeHops hops = new FakeHops();
		FollowRunRecorder recorder = new FollowRunRecorder(hops, false, harness.timeline.durationNanos(), start());
		recorder.begin();
		assertTrue(hops.listeners.isEmpty(), "no sound asked: no hop is kept");
		harness.session.start();
		for (int i = 0; i < 4; i++) {
			harness.playEvent(i, 0);
		}
		harness.runAll();
		FollowRunRecorder.Take take = recorder.ended(harness.session, harness.sequence.getEvents(), harness.timeline, "follow-2026-10-29-180407");
		assertNotNull(take);
		assertNull(take.samples());
		String[] lines = take.record().split("\n");
		assertEquals(FollowRunRecord.FIRST_LINE, lines[0]);
		assertEquals("# score=_TEST_verifie.tg,track=1,percent=70,window_ms=100,play_time_ms=,pitch_position=", lines[1]);
		assertEquals("# route=Rocksmith USB Guitar Adapter|MacBook Speakers|48000 Hz 1ch 16bit,calibration=NONE,offset_ms=0.0", lines[2]);
		assertEquals("# audio=none,sample_rate=0,first_sample_ms=0.0,audio_truncated=false", lines[3]);
		assertEquals(FollowRunRecord.COLUMNS, lines[4]);
		assertEquals(5 + 5, lines.length, "one line per event");
		assertTrue(lines[9].startsWith("4,EXPECTED_NOTE,68,2000.0,MISSED,MISSED,,NO_ATTACK,"), lines[9]);
	}

	@Test
	public void theRecordOfAFollowRunNamesItsTimeToPlayAndItsPosition() {
		// spec 016 T033, FR-007: the start of a Follow run gives the time to play and the pitch position to the header
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		FollowRunRecorder recorder = new FollowRunRecorder(new FakeHops(), false, harness.timeline.durationNanos(),
				new FollowRunRecorder.Start("_TEST_verifie.tg", 1, 70, 100, 300, FollowLeniencyPosition.PERMISSIVE, ROUTE, LatencyCompensation.none()));
		recorder.begin();
		harness.session.start();
		harness.playEvent(0, 0);
		harness.runAll();
		String[] lines = recorder.ended(harness.session, harness.sequence.getEvents(), harness.timeline, "follow-x").record().split("\n");
		assertEquals("# score=_TEST_verifie.tg,track=1,percent=70,window_ms=100,play_time_ms=300,pitch_position=PERMISSIVE", lines[1]);
	}

	@Test
	public void aRunEndedDuringItsCountInHasNoRecordAndLetsTheHopsGo() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		FakeHops hops = new FakeHops();
		FollowRunRecorder recorder = new FollowRunRecorder(hops, true, harness.timeline.durationNanos(), start());
		recorder.begin();
		assertEquals(1, hops.listeners.size());
		harness.session.start();
		assertNull(recorder.ended(harness.session, harness.sequence.getEvents(), harness.timeline, "follow-x"));
		assertTrue(hops.listeners.isEmpty());
		recorder.release();
		assertEquals(1, hops.removed, "let go once");
	}

	@Test
	public void theSoundIsKeptAndItsFirstSampleIsDatedOnTheTimeline() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		FakeHops hops = new FakeHops();
		FollowRunRecorder recorder = new FollowRunRecorder(hops, true, harness.timeline.durationNanos(), start());
		recorder.begin();
		HopListener listener = hops.listeners.get(0);
		listener.configure(48000);
		float[] hop = new float[240];
		java.util.Arrays.fill(hop, 0.25f);
		for (int i = 0; i < 4; i++) {
			listener.hop(hop, 0, hop.length);
		}
		harness.session.start();
		long origin = harness.session.timelineOriginWall();
		// 960 samples at 48 kHz are 20 ms: the first frame after them, dated 500 ms after the origin, puts the first sample 480 ms after it
		recorder.frame(origin + 500 * MS);
		recorder.frame(origin + 900 * MS);
		harness.playEvent(0, 0);
		harness.runAll();
		FollowRunRecorder.Take take = recorder.ended(harness.session, harness.sequence.getEvents(), harness.timeline, "follow-2026-10-29-180407");
		assertNotNull(take);
		assertEquals(48000, take.sampleRate());
		assertEquals(960, take.samples().length);
		assertEquals(0.25f, take.samples()[100]);
		assertEquals("# audio=follow-2026-10-29-180407.wav,sample_rate=48000,first_sample_ms=480.0,audio_truncated=false", take.record().split("\n")[3]);
		assertTrue(hops.listeners.isEmpty());
	}

	@Test
	public void theSoundIsKeptForTheTimelineAndAMarginAtMostTenMinutes() {
		assertEquals(60_000 + 30_000, FollowRunRecorder.capacityMillis(30_000 * MS));
		assertEquals(FollowRunRecorder.MAX_MILLIS, FollowRunRecorder.capacityMillis(20L * 60_000 * MS));
		assertFalse(FollowRunRecorder.followed(FollowSession.State.COUNT_IN));
		assertTrue(FollowRunRecorder.followed(FollowSession.State.COMPLETE));
	}
}
