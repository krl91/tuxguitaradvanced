package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
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
 * The owner's recorded Follow run, replayed (spec 016 T012, SC-002, SC-003, contracts/run-record.md). Tag {@code dataset}; skipped without a record. Reads the
 * newest record of {@code ~/tuxguitar-follow-takes/} (or {@code -Dfollow.take=<file>}) and its sound, lays the score ({@code -Dfollow.score=<file>}, by
 * default the file of the record's name in {@code tests/1}) with the record's percentage and the time of its first sample, and replays the sound through
 * {@link FakePlayerRun} with {@link OfflineAnalysis}. It lives here and not in {@code replay} because {@link FakePlayerRun} belongs to this package and
 * stays unchanged (T002); the loading and the replay are package helpers, so that story 3 measures its positions in a test of its own.
 * <p>
 * First check: the replay with the record's own settings gives the record's own verdicts (at least {@link #AGREEMENT} of the events; the replay uses the
 * default recognition settings, the run used the device profile in force, and the dating of the first sample is within one hop). Then it prints the share
 * of each reason (SC-002) and, for {@code -Dfollow.playTime} (300 ms by default), the accepted share of the events the record heard at the right pitch
 * no earlier than 50 ms before their red and no later than that time after it (SC-003).
 */
@Tag("dataset")
public class FollowTakeReplayTest {

	static final double AGREEMENT = 0.90;
	private static final int RATE = 48000;
	private static final long LATENCY = ArticulationDetector.DECISION_DELAY_HOPS * 256L * 1_000_000_000L / RATE;

	/** A record read back: its header and its rows. */
	static final class Take {
		final Path file;
		final Map<String, String> header = new HashMap<String, String>();
		final List<String[]> rows = new ArrayList<String[]>();
		final Map<String, Integer> column = new HashMap<String, Integer>();

		Take(Path file) {
			this.file = file;
		}

		String value(int row, String name) {
			return this.rows.get(row)[this.column.get(name)];
		}

		int percent() {
			return Integer.parseInt(this.header.get("percent"));
		}

		int windowMillis() {
			return Integer.parseInt(this.header.get("window_ms"));
		}

		LatencyCompensation calibration() {
			double offset = Double.parseDouble(this.header.get("offset_ms"));
			switch (this.header.get("calibration")) {
				case "CALIBRATED":
					return LatencyCompensation.calibrated(offset);
				case "MANUAL":
					return LatencyCompensation.manual(offset);
				default:
					return LatencyCompensation.none();
			}
		}
	}

	/** The record asked for, or the newest one; the test is skipped without one. */
	static Take load() throws IOException {
		String asked = System.getProperty("follow.take");
		Path file = (asked != null ? Paths.get(asked) : newest(Paths.get(System.getProperty("user.home"), "tuxguitar-follow-takes")));
		assumeTrue(file != null && Files.isRegularFile(file), "no record of a Follow run (tick Record this run in the Mode tab)");
		return load(file);
	}

	static Take load(Path file) throws IOException {
		Take take = new Take(file);
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		assertEquals(FollowRunRecord.FIRST_LINE, lines.get(0), "a record of a Follow run");
		for (int i = 1; i <= 3; i++) {
			for (String pair : lines.get(i).substring(2).split(",")) {
				int at = pair.indexOf('=');
				take.header.put(pair.substring(0, at), pair.substring(at + 1));
			}
		}
		String[] names = lines.get(4).split(",");
		for (int i = 0; i < names.length; i++) {
			take.column.put(names[i], i);
		}
		for (int i = 5; i < lines.size(); i++) {
			if (!lines.get(i).isEmpty()) {
				take.rows.add(lines.get(i).split(",", -1));
			}
		}
		return take;
	}

	private static Path newest(Path folder) throws IOException {
		if (!Files.isDirectory(folder)) {
			return null;
		}
		Path newest = null;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "follow-*.csv")) {
			for (Path file : files) {
				if (newest == null || file.getFileName().toString().compareTo(newest.getFileName().toString()) > 0) {
					newest = file;
				}
			}
		}
		return newest;
	}

	/**
	 * The run of the record replayed on its sound; {@code configure} changes the run before it starts (story 3: the time to play and the pitch position).
	 * Skipped when the record has no sound or the score is not found.
	 */
	static FakePlayerRun replay(Take take, Consumer<FakePlayerRun> configure) throws Exception {
		String audio = take.header.get("audio");
		assumeTrue(audio != null && !audio.equals("none"), "the record has no sound (tick Keep a copy of the sound too)");
		Path sound = take.file.resolveSibling(audio);
		RecordedAudio recorded = RecordedAudio.load(sound);
		assumeTrue(recorded != null, "the sound of the record is not readable: " + sound);
		Path score = Paths.get(System.getProperty("follow.score", "../../tests/1/" + take.header.get("score")));
		assumeTrue(Files.isRegularFile(score), "score not found (-Dfollow.score=<file>): " + score.toAbsolutePath());
		TGSong song;
		try (InputStream in = new BufferedInputStream(Files.newInputStream(score))) {
			TGSongReaderHandle handle = new TGSongReaderHandle();
			handle.setFactory(new TGFactory());
			handle.setInputStream(in);
			new TGSongReaderImpl().read(handle);
			song = handle.getSong();
		}
		TGTrack track = song.getTrack(Integer.parseInt(take.header.get("track")) - 1);
		PracticeSequence full = PracticeSequenceBuilder.build(song, track, TrackTuning.of(track, null));
		assertTrue(full.getEvents().size() >= take.rows.size(), "the score has the events of the record");
		// a run judges from where it started: the record's events are the last ones of the track
		PracticeSequence sequence = full.startingAt(full.getEvents().size() - take.rows.size());
		// the first sample lies first_sample_ms on the timeline: the timeline begins that long after the start of the sound
		long lead = Math.round(-Double.parseDouble(take.header.get("first_sample_ms")) * 1e6);
		assertTrue(lead >= 0, "the sound begins before the timeline (during the count-in)");
		Constructor<FakePlayerRun> constructor = FakePlayerRun.class.getDeclaredConstructor(app.tuxguitar.app.tools.custom.practice.model.SongFixture.class, TGSong.class,
				PracticeSequence.class, int.class, long.class, long.class, TimingTolerance.class);
		constructor.setAccessible(true);
		FakePlayerRun run = constructor.newInstance(null, song, sequence, take.percent(), LATENCY, lead, TimingTolerance.ofMillis(take.windowMillis()));
		run.compensation = take.calibration();
		run.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		if (configure != null) {
			configure.accept(run);
		}
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(recorded.samples, recorded.sampleRate, RecognitionSettings.defaults(), true, AttackCandidates.of(run.sequence));
		run.start();
		run.feed(frames);
		return run;
	}

	/** The events the record heard at the right pitch, from 50 ms before their red to {@code playTimeMillis} after it (SC-003). */
	static List<Integer> rightPitchInTime(Take take, int playTimeMillis) {
		List<Integer> events = new ArrayList<Integer>();
		for (int i = 0; i < take.rows.size(); i++) {
			String expected = take.value(i, "expected");
			String heard = take.value(i, "heard");
			String attack = take.value(i, "attack_ms");
			if (expected.isEmpty() || !expected.equals(heard) || attack.isEmpty()) {
				continue;
			}
			double after = Double.parseDouble(attack) - Double.parseDouble(take.value(i, "target_ms"));
			if (after >= -50.0 && after <= playTimeMillis) {
				events.add(i);
			}
		}
		return events;
	}

	static String shares(Map<RefusalReason, Integer> counts, int total) {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<RefusalReason, Integer> entry : counts.entrySet()) {
			text.append(String.format(java.util.Locale.ROOT, "%n  %-14s %3d  %5.1f %%", entry.getKey(), entry.getValue(), total == 0 ? 0.0 : 100.0 * entry.getValue() / total));
		}
		return text.toString();
	}

	@Test
	public void theRecordedRunReplayedGivesItsOwnVerdictsAndTheSharesOfItsReasons() throws Exception {
		Take take = load();
		int same = check(take);
		assertTrue(same >= AGREEMENT * take.rows.size(), "the replay gives the record's own verdicts: " + same + "/" + take.rows.size());
	}

	/**
	 * The way from a run to its record and back, on the reference recording (tests/1), without the owner's take: a record made from a replayed run, written
	 * with its sound, read back and replayed gives exactly its verdicts. It proves the reading of the record and the laying of the score on its sound.
	 */
	@Test
	public void aRecordMadeFromTheReferenceRecordingReplaysToItsOwnVerdicts() throws Exception {
		FakePlayerRun reference = FollowReasonParityTest.referenceRun();
		RecordedAudio audio = RecordedAudio.load(Paths.get(System.getProperty("tuxguitar.recording.dir", "../../tests/1"), "audio_15ps.m4a"));
		Path folder = Files.createTempDirectory("follow-takes");
		writeWav(folder.resolve("follow-seed.wav"), audio);
		int n = reference.sequence.getEvents().size();
		StringBuilder seed = new StringBuilder(header(reference.timeline.percent(), -reference.lead / 1e6, "follow-seed.wav", audio.sampleRate));
		for (int i = 0; i < n; i++) {
			seed.append(i).append(",,,,,,,NONE,,,,,\n");
		}
		Path seedFile = folder.resolve("follow-seed.csv");
		Files.write(seedFile, seed.toString().getBytes(StandardCharsets.UTF_8));
		FakePlayerRun run = replay(load(seedFile), null);
		FollowRunRecord.Header header = new FollowRunRecord.Header("_TEST_verifie.tg", 1, reference.timeline.percent(), 100, -1, "", "in", "out", "fmt", "NONE", 0.0,
				"follow-seed.wav", audio.sampleRate, -reference.lead / 1e6, false);
		String record = FollowRunRecord.text(header, run.sequence.getEvents(), run.last.results(), i -> run.timeline.targetNanos(i) / 1e6, run.session::heard,
				run.session.timelineOriginWall());
		Path recordFile = folder.resolve("follow-record.csv");
		Files.write(recordFile, record.getBytes(StandardCharsets.UTF_8));
		Take take = load(recordFile);
		assertEquals(n, check(take), "every verdict replayed");
	}

	private static String header(int percent, double firstSampleMillis, String audio, int rate) {
		return FollowRunRecord.FIRST_LINE + "\n# score=_TEST_verifie.tg,track=1,percent=" + percent + ",window_ms=100,play_time_ms=,pitch_position=\n"
				+ "# route=in|out|fmt,calibration=NONE,offset_ms=0.0\n# audio=" + audio + ",sample_rate=" + rate + ",first_sample_ms="
				+ String.format(java.util.Locale.ROOT, "%.1f", firstSampleMillis) + ",audio_truncated=false\n" + FollowRunRecord.COLUMNS + "\n";
	}

	private static void writeWav(Path file, RecordedAudio audio) throws IOException {
		byte[] bytes = new byte[audio.samples.length * 2];
		for (int i = 0; i < audio.samples.length; i++) {
			int value = Math.round(Math.max(-1f, Math.min(1f, audio.samples[i])) * 32767f);
			bytes[2 * i] = (byte) (value & 0xff);
			bytes[2 * i + 1] = (byte) ((value >> 8) & 0xff);
		}
		javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(audio.sampleRate, 16, 1, true, false);
		try (javax.sound.sampled.AudioInputStream stream = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(bytes), format, audio.samples.length)) {
			javax.sound.sampled.AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, file.toFile());
		}
	}

	/** Replays the record with its own settings, prints the agreement, the reasons (SC-002) and the share of SC-003; @return the events of the same verdict */
	static int check(Take take) throws Exception {
		FakePlayerRun run = replay(take, null);
		List<EventResult> results = run.last.results();
		assertEquals(take.rows.size(), results.size());
		int same = 0;
		Map<RefusalReason, Integer> recorded = new EnumMap<RefusalReason, Integer>(RefusalReason.class);
		Map<RefusalReason, Integer> replayed = new EnumMap<RefusalReason, Integer>(RefusalReason.class);
		int refusedRecorded = 0;
		int refusedReplayed = 0;
		StringBuilder differences = new StringBuilder();
		for (int i = 0; i < results.size(); i++) {
			String status = take.value(i, "status");
			if (status.equals(results.get(i).status().name())) {
				same++;
			} else {
				differences.append("\n  event ").append(i).append(": recorded ").append(status).append(", replayed ").append(results.get(i).status());
			}
			RefusalReason reason = RefusalReason.valueOf(take.value(i, "reason"));
			if (reason != RefusalReason.NONE) {
				recorded.merge(reason, 1, Integer::sum);
				refusedRecorded++;
			}
			if (results.get(i).reason() != RefusalReason.NONE) {
				replayed.merge(results.get(i).reason(), 1, Integer::sum);
				refusedReplayed++;
			}
		}
		int playTime = Integer.getInteger("follow.playTime", 300);
		List<Integer> inTime = rightPitchInTime(take, playTime);
		int accepted = 0;
		for (int i : inTime) {
			if (results.get(i).status() == FollowSession.Status.MATCHED) {
				accepted++;
			}
		}
		System.out.println("FOLLOW TAKE " + take.file.getFileName() + " (" + results.size() + " events, percent " + take.percent() + ", window " + take.windowMillis()
				+ " ms, calibration " + take.header.get("calibration") + " " + take.header.get("offset_ms") + " ms)"
				+ "\n same verdict replayed: " + same + "/" + results.size() + differences
				+ "\n reasons of the record (SC-002), " + refusedRecorded + " refused:" + shares(recorded, refusedRecorded)
				+ "\n reasons of the replay, " + refusedReplayed + " refused:" + shares(replayed, refusedReplayed)
				+ "\n right pitch from -50 to " + playTime + " ms after the red (SC-003): " + accepted + "/" + inTime.size() + " accepted with the run's own settings");
		return same;
	}
}
