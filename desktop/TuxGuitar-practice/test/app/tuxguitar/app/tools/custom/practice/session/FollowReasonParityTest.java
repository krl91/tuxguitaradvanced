package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * The reasons of spec 016 never change a verdict (FR-001, contracts/refusal-reasons.md). Written and seen green on the code before the reasons
 * (task T005): the statuses, timings and offsets below are the ones that code gave, as literal values; they must stay the same once the reasons
 * are there. With the tag {@code dataset}, the reference recording replayed in Follow gives the figures of
 * specs/002-practice-industrialization/benchmark/follow-benchmark-recorded-2026-10-08.txt.
 */
public class FollowReasonParityTest {

	private static final int RATE = 48000;
	private static final long LATENCY = ArticulationDetector.DECISION_DELAY_HOPS * 256L * 1_000_000_000L / RATE;

	/** Five quarter notes of 64, 65, 66, 67 (measure 0) and 68 (measure 1), on the first string. */
	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	/** Eight quarter notes at 240 beats a minute: 250 ms apart, the windows cut between them. */
	private static SongFixture closeNotes() {
		SongFixture fixture = new SongFixture(2);
		fixture.tempoChange(0, 240);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
			fixture.quarter(1, beat, 1, beat + 4);
		}
		return fixture;
	}

	/** A chord of three strings, then two notes. */
	private static SongFixture chordThenNotes() {
		SongFixture fixture = new SongFixture(2);
		fixture.chord(0, 0, new int[] { 1, 2, 3 }, new int[] { 0, 1, 2 });
		fixture.quarter(0, 1, 1, 3);
		fixture.quarter(0, 2, 1, 5);
		return fixture;
	}

	/** index:STATUS/TIMING/offset in ms, for every event. */
	static String describe(List<EventResult> results) {
		StringBuilder text = new StringBuilder();
		for (EventResult r : results) {
			text.append(text.length() > 0 ? " " : "").append(r.eventIndex()).append(':').append(r.status()).append('/').append(r.timing()).append('/')
					.append(Math.round(r.offsetNanos() / 1e6));
		}
		return text.toString();
	}

	@Test
	public void theVerdictsOfEachKindOfPlayingAreTheOnesOfTheCodeBeforeTheReasons() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		harness.session.start();
		harness.playEvent(0, 0);
		harness.playEvent(1, -150);
		harness.playEvent(2, 150);
		harness.play(harness.target(3), 70, 600 * MS);
		// event 4 is not played; an extra note between two windows
		harness.play(harness.target(0) + 250 * MS, 71, 120 * MS);
		harness.runAll();
		assertEquals("0:MATCHED/ON_TIME/0 1:MATCHED/EARLY/-150 2:MATCHED/LATE/150 3:WRONG_PITCH/ON_TIME/0 4:MISSED/MISSED/0", describe(harness.last.results()));
		assertEquals(1, harness.last.extras().size());
	}

	@Test
	public void theVerdictsOfANoteOutsideItsWindowAreTheOnesOfTheCodeBeforeTheReasons() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		harness.session.start();
		harness.playEvent(0, 0);
		harness.playEvent(1, 0);
		harness.play(harness.target(2) + 250 * MS, 66, 200 * MS);
		harness.play(harness.target(4) - 250 * MS, 68, 150 * MS);
		harness.runAll();
		assertEquals("0:MATCHED/ON_TIME/0 1:MATCHED/ON_TIME/0 2:MISSED/MISSED/0 3:MISSED/MISSED/0 4:MISSED/MISSED/0", describe(harness.last.results()));
	}

	@Test
	public void theVerdictsOfCloseNotesAreTheOnesOfTheCodeBeforeTheReasons() {
		FollowHarness harness = new FollowHarness(closeNotes(), 100);
		harness.session.start();
		long[] offsets = { 0, 40, -40, 90, -90, 0, 110, 0 };
		for (int i = 0; i < offsets.length; i++) {
			harness.play(harness.target(i) + offsets[i] * MS, harness.sequence.getEvents().get(i).getExpectedMidi(), 200 * MS);
		}
		harness.runAll();
		assertEquals("0:MATCHED/ON_TIME/0 1:MATCHED/ON_TIME/40 2:MATCHED/ON_TIME/-40 3:MATCHED/LATE/90 4:MATCHED/EARLY/-90 5:MATCHED/ON_TIME/0 6:MATCHED/LATE/110 7:MATCHED/ON_TIME/0", describe(harness.last.results()));
	}

	@Test
	public void theVerdictsOfAChordAndItsNotesAreTheOnesOfTheCodeBeforeTheReasons() {
		FollowHarness harness = new FollowHarness(chordThenNotes(), 100);
		harness.session.start();
		harness.play(harness.target(0), 64, 400 * MS);
		harness.playEvent(1, 0);
		harness.play(harness.target(2), 60, 300 * MS);
		harness.runAll();
		// the harness gives no spectrum: the chord cannot be heard and stays open
		assertEquals("0:PENDING/null/0 1:MATCHED/ON_TIME/0 2:WRONG_PITCH/ON_TIME/0", describe(harness.last.results()));
	}

	@Test
	@Tag("dataset")
	public void theReferenceRecordingGivesTheFiguresOfItsBenchmark() throws IOException {
		FakePlayerRun run = referenceRun();
		int matched = 0, missed = 0, wrong = 0, notJudged = 0;
		for (EventResult result : run.last.results()) {
			switch (result.status()) {
				case MATCHED: matched++; break;
				case MISSED: missed++; break;
				case WRONG_PITCH: wrong++; break;
				case NOT_JUDGED: notJudged++; break;
				default: break;
			}
		}
		assertEquals(42, run.last.results().size());
		assertEquals(35, matched);
		assertEquals(7, missed);
		assertEquals(0, wrong);
		assertEquals(0, notJudged);
	}

	@Test
	@Tag("dataset")
	public void everyEventOfTheReferenceRecordingThatIsNotMatchedHasAReason() throws IOException {
		// spec 016 SC-001: 100 % of the events not accepted have a reason from the fixed list
		FakePlayerRun run = referenceRun();
		int refused = 0;
		StringBuilder reasons = new StringBuilder();
		for (EventResult result : run.last.results()) {
			if (result.status() == FollowSession.Status.MATCHED || result.status() == FollowSession.Status.AUTO_PASSED) {
				assertEquals(RefusalReason.NONE, result.reason(), "event " + result.eventIndex());
				continue;
			}
			refused++;
			assertNotEquals(RefusalReason.NONE, result.reason(), "event " + result.eventIndex() + " " + result.status() + " has a reason");
			reasons.append("\n  event ").append(result.eventIndex()).append(' ').append(result.status()).append(": ").append(result.reason())
					.append(result.reasonDetail() != 0 ? " " + result.reasonDetail() : "");
		}
		assertEquals(7, refused);
		System.out.println("FOLLOW REASONS on the reference recording (" + refused + " not matched):" + reasons);
	}

	/**
	 * The reference recording (tests/1) laid on its score as {@code FollowBenchmarkTest#theRealRecordingAtTheScoreTempoIsReportedWhenItIsThere} lays it:
	 * the tempo and the start that fit the annotated times best. Skipped when the recording is not there.
	 */
	static FakePlayerRun referenceRun() throws IOException {
		Path dir = Paths.get(System.getProperty("tuxguitar.recording.dir", "../../tests/1"));
		Path score = dir.resolve("_TEST_verifie.tg");
		assumeTrue(Files.isRegularFile(score), "reference score not found: " + score.toAbsolutePath());
		RecordedAudio audio = RecordedAudio.load(dir.resolve("audio_15ps.m4a"));
		assumeTrue(audio != null, "reference audio not readable here (needs tests/1/audio_15ps.m4a and macOS afconvert)");
		TGSong song;
		try (InputStream in = new BufferedInputStream(Files.newInputStream(score))) {
			TGSongReaderHandle handle = new TGSongReaderHandle();
			handle.setFactory(new TGFactory());
			handle.setInputStream(in);
			new TGSongReaderImpl().read(handle);
			song = handle.getSong();
		}
		TGTrack track = song.getTrack(0);
		PracticeSequence sequence = PracticeSequenceBuilder.build(song, track, TrackTuning.of(track, null));
		int n = sequence.getEvents().size();
		List<String> lines = Files.readAllLines(Paths.get("test-data/real-recording-1/events.csv"));
		assertEquals(n + 1, lines.size());
		double[] played = new double[n];
		for (int i = 0; i < n; i++) {
			played[i] = Double.parseDouble(lines.get(i + 1).split(",")[1]);
		}
		FollowTimeline unit = FollowTimeline.build(song, sequence, 100);
		double meanWritten = 0.0, meanPlayed = 0.0;
		for (int i = 0; i < n; i++) {
			meanWritten += unit.targetNanos(i) / 1e9 / n;
			meanPlayed += played[i] / n;
		}
		double covariance = 0.0, variance = 0.0;
		for (int i = 0; i < n; i++) {
			covariance += (unit.targetNanos(i) / 1e9 - meanWritten) * (played[i] - meanPlayed);
			variance += (unit.targetNanos(i) / 1e9 - meanWritten) * (unit.targetNanos(i) / 1e9 - meanWritten);
		}
		double ratio = covariance / variance;
		int percent = (int) Math.max(1, Math.round(100.0 / ratio));
		double start = meanPlayed - meanWritten * ratio;
		long lead = Math.max(0L, Math.round(start * 1e9));
		FakePlayerRun run = new FakePlayerRun(song, sequence, percent, LATENCY, lead);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(audio.samples, audio.sampleRate, RecognitionSettings.defaults(), true, AttackCandidates.of(run.sequence));
		run.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		run.start();
		run.feed(frames);
		return run;
	}
}
