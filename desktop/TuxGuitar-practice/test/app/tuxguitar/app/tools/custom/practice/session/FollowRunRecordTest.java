package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/** The record of a Follow run (spec 016 FR-003, contracts/run-record.md): its four header lines, its columns, one line per event, dot decimals. */
public class FollowRunRecordTest {

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static String record(FakePlayerRun run, FollowRunRecord.Header header) {
		return FollowRunRecord.text(header, run.sequence.getEvents(), run.last.results(), i -> run.timeline.targetNanos(i) / 1e6, run.session::heard,
				run.session.timelineOriginWall());
	}

	private static FollowRunRecord.Header header(String audio) {
		return new FollowRunRecord.Header("Étude, n°1.tg", 1, 70, 100, 300, "PERMISSIVE", "Rocksmith|USB", "Speakers", "48000/24/1", "NONE", 0.0, audio, 48000, -1234.5, false);
	}

	@Test
	public void theHeaderTheColumnsAndOneLinePerEvent() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, -150);
		run.play(run.target(2), 70, 300 * MS);
		run.play(run.target(3) + 250 * MS, 67, 150 * MS);
		run.runAll();
		Locale saved = Locale.getDefault();
		String text;
		try {
			Locale.setDefault(Locale.FRANCE);   // a comma for the decimals in French: the record keeps the dot
			text = record(run, header("follow-2026-10-29-201500.wav"));
		} finally {
			Locale.setDefault(saved);
		}
		String[] lines = text.split("\n");
		assertEquals("# follow-run-record 1", lines[0]);
		assertEquals("# score=Étude  n°1.tg,track=1,percent=70,window_ms=100,play_time_ms=300,pitch_position=PERMISSIVE", lines[1], "the separators are taken out of the names");
		assertEquals("# route=Rocksmith USB|Speakers|48000/24/1,calibration=NONE,offset_ms=0.0", lines[2]);
		assertEquals("# audio=follow-2026-10-29-201500.wav,sample_rate=48000,first_sample_ms=-1234.5,audio_truncated=false", lines[3]);
		assertEquals(FollowRunRecord.COLUMNS, lines[4]);
		assertEquals(5 + 5, lines.length, "one line per event");
		for (int i = 5; i < lines.length; i++) {
			assertEquals(13, lines[i].split(",", -1).length, "13 fields: " + lines[i]);
		}
		assertEquals("0,EXPECTED_NOTE,64,0.0,MATCHED,ON_TIME,0.0,NONE,,0.0,64,0.95,-20.0", lines[5]);
		assertTrue(lines[6].startsWith("1,EXPECTED_NOTE,65,500.0,MATCHED,EARLY,-150.0,NONE,,350.0,65,"), lines[6]);
		assertTrue(lines[7].startsWith("2,EXPECTED_NOTE,66,1000.0,WRONG_PITCH,ON_TIME,0.0,OTHER_PITCH,70,1000.0,70,"), lines[7]);
		assertTrue(lines[8].startsWith("3,EXPECTED_NOTE,67,1500.0,MISSED,MISSED,,LATE_OUTSIDE,250,1750.0,67,"), lines[8]);
		assertEquals("4,EXPECTED_NOTE,68,2000.0,MISSED,MISSED,,NO_ATTACK,,,,,", lines[9], "nothing heard: empty fields");
		assertTrue(new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).contains("Étude"));
	}

	@Test
	public void withoutAnAudioCopyTheHeaderSaysNone() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.runAll();
		String text = record(run, header(null));
		assertTrue(text.contains("\n# audio=none,"), text);
	}
}
