package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.tg.TGSongReaderImpl;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;

/**
 * The benchmark of the tempo-following mode (spec 002 T101, SC-009, SC-004): whole runs of the session on the fake player and its clock, fed with
 * the analysis of audio, at the score tempo from 2 to 8 notes a second, with and without a deliberate mistake, with repeated identical notes, with
 * chords, and on the real recording of the repository when it is there. The tag {@code benchmark} keeps it out of the normal build:
 * {@code mvn test -pl ../../TuxGuitar-practice -am -Dgroups=benchmark -DexcludedGroups= -Dtest=FollowBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false}
 * from the macOS SWT build directory. The report goes to {@code specs/002-practice-industrialization/benchmark/}.
 * <p>
 * It reports and does not judge: the figures of SC-009 are the targets of the specification, they are not changed to make a result pass, and the
 * assertions only say that the benchmark ran and is deterministic. The audio is synthetic (plucked-string synthesis, notes ringing under the next)
 * and the offsets are those of the session without a latency compensation, so the bias of the detection is part of what is measured.
 */
@Tag("benchmark")
public class FollowBenchmarkTest {

	private static final int RATE = 48000;
	private static final long LATENCY = ArticulationDetector.DECISION_DELAY_HOPS * 256L * 1_000_000_000L / RATE;
	private static final double[] NOTES_PER_SECOND = { 2, 4, 6, 8 };
	private static final int RUNS = 12;
	private static final int EVENTS = 16;
	/** A matched event whose offset is farther than this from the time the note was played is a wrong match (the bias of the detection is allowed). */
	private static final double MATCH_TOLERANCE_MILLIS = 60.0;
	private static final int[] SCALE = { 0, 2, 4, 5, 7, 5, 4, 2, 0, 3, 5, 7, 8, 7, 5, 3 };

	public enum Scenario {
		CLEAN("scale, played in time (offsets within 25 ms)"),
		REPEATED("one pitch repeated, played in time"),
		MISTAKE("scale with one deliberate mistake"),
		CHORDS("chords played in time");

		final String text;

		Scenario(String text) {
			this.text = text;
		}
	}

	private enum Mistake {
		WRONG_NOTE, SKIPPED_NOTE, STRAY_NOTE, DOUBLED_NOTE
	}

	/** What one run gave. */
	public static final class Outcome {
		final StringBuilder details = new StringBuilder();
		int events;
		public int matched;
		int rightMatches;
		int falseMatches;
		int wrongPitch;
		int missed;
		int notJudged;
		int extras;
		int onTime;
		int early;
		int late;
		double biasSum;
		/** For the mistake runs: the events after the mistake that were played well, and how many of them were matched at their time. */
		int afterPlayed;
		int afterRight;
		boolean resynchronised = true;
		boolean separated;
	}

	private static int midiOf(int string, int fret) {
		return new int[] { 0, 64, 59, 55, 50, 45, 40 }[string] + fret;
	}

	private static SongFixture song(Scenario scenario) {
		int measures = scenario == Scenario.CHORDS ? 2 : EVENTS / 4;
		SongFixture fixture = new SongFixture(measures);
		if (scenario == Scenario.CHORDS) {
			for (int i = 0; i < 8; i++) {
				if (i % 2 == 0) {
					fixture.chord(i / 4, i % 4, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 1, 0, 0 });
				} else {
					fixture.chord(i / 4, i % 4, new int[] { 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 2, 0 });
				}
			}
			return fixture;
		}
		for (int i = 0; i < EVENTS; i++) {
			fixture.quarter(i / 4, i % 4, 1, scenario == Scenario.REPEATED ? 0 : SCALE[i]);
		}
		return fixture;
	}

	private static void add(float[] signal, float[] addition, long wall, float gain) {
		int offset = (int) ((wall - T0) * RATE / 1_000_000_000L);
		for (int i = 0; i < addition.length && offset + i < signal.length && offset >= 0; i++) {
			signal[offset + i] += gain * addition[i];
		}
	}

	private static void pluck(float[] signal, long wall, int midi, long seed) {
		add(signal, SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midi), RATE, (int) (0.9 * RATE), seed), wall, 0.3f);
	}

	private static void strum(float[] signal, long wall, int[] pitches, long seed) {
		for (int i = 0; i < pitches.length; i++) {
			add(signal, SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(pitches[i]), RATE, (int) (1.3 * RATE), seed + i), wall + i * 10 * MS, 0.2f);
		}
	}

	public static Outcome run(Scenario scenario, double notesPerSecond, long seed) {
		return run(scenario, notesPerSecond, seed, false);
	}

	/** @param waits the event waits for the detector to report an attack before taking the late time of the evidence (see FollowSession) */
	static Outcome run(Scenario scenario, double notesPerSecond, long seed, boolean waits) {
		Random random = new Random(seed);
		SongFixture fixture = song(scenario);
		int percent = (int) Math.round(notesPerSecond / 2.0 * 100.0);
		FakePlayerRun run = new FakePlayerRun(fixture, percent, LATENCY);
		int n = run.sequence.getEvents().size();
		float[] signal = new float[(int) ((run.lead + run.timeline.durationNanos() + 3_000 * MS) * RATE / 1_000_000_000L)];
		double[] jitter = new double[n];
		boolean[] played = new boolean[n];
		int mistakeAt = -1;
		Mistake mistake = null;
		if (scenario == Scenario.MISTAKE) {
			mistakeAt = 3 + random.nextInt(n - 7);
			mistake = Mistake.values()[(int) (seed % Mistake.values().length)];
		}
		for (int i = 0; i < n; i++) {
			jitter[i] = (random.nextInt(51) - 25);
			PracticeEvent event = run.sequence.getEvents().get(i);
			long when = run.target(i) + (long) (jitter[i] * MS);
			played[i] = true;
			if (i == mistakeAt && mistake == Mistake.SKIPPED_NOTE) {
				played[i] = false;
				continue;
			}
			if (event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD) {
				strum(signal, when, event.getRequiredPitches(), seed * 31 + i * 7);
			} else {
				int midi = event.getExpectedMidi() + (i == mistakeAt && mistake == Mistake.WRONG_NOTE ? 2 : 0);
				pluck(signal, when, midi, seed * 31 + i);
				if (i == mistakeAt && mistake == Mistake.DOUBLED_NOTE) {
					pluck(signal, when + 70 * MS, midi, seed * 31 + i + 1000);
				}
			}
			if (i == mistakeAt && mistake == Mistake.STRAY_NOTE && i + 1 < n) {
				long between = (run.target(i) + run.target(i + 1)) / 2;
				pluck(signal, between, 71, seed * 31 + i + 2000);
			}
		}
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(signal, RATE, RecognitionSettings.defaults(), scenario == Scenario.CHORDS,
				AttackCandidates.of(run.sequence));
		run.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		run.session.setWaitsForReportedAttack(waits);
		run.start();
		run.feed(frames);

		Outcome outcome = new Outcome();
		outcome.events = n;
		for (int i = 0; i < n; i++) {
			EventResult result = run.last.results().get(i);
			outcome.details.append(i).append(' ').append(result.status()).append('/').append(result.timing()).append(" off=").append(Math.round(result.offsetNanos() / 1e6))
					.append(" jitter=").append(Math.round(jitter[i])).append(" attempts=").append(result.attempts()).append('\n');
			boolean wrongPlayed = (i == mistakeAt && mistake == Mistake.WRONG_NOTE);
			switch (result.status()) {
				case MATCHED: {
					outcome.matched++;
					double error = Math.abs(result.offsetNanos() / 1e6 - jitter[i]);
					boolean right = played[i] && !wrongPlayed && error <= MATCH_TOLERANCE_MILLIS + biasAllowance(result);
					outcome.biasSum += result.offsetNanos() / 1e6 - jitter[i];
					if (right) {
						outcome.rightMatches++;
					} else {
						outcome.falseMatches++;
					}
					if (result.timing() == TimingVerdict.EARLY) {
						outcome.early++;
					} else if (result.timing() == TimingVerdict.LATE) {
						outcome.late++;
					} else {
						outcome.onTime++;
					}
					break;
				}
				case WRONG_PITCH:
					outcome.wrongPitch++;
					break;
				case MISSED:
					outcome.missed++;
					break;
				case NOT_JUDGED:
					outcome.notJudged++;
					break;
				default:
					break;
			}
		}
		outcome.extras = run.last.counts().extra();
		if (scenario == Scenario.MISTAKE) {
			for (int i = mistakeAt + 1; i < n; i++) {
				outcome.afterPlayed++;
				EventResult result = run.last.results().get(i);
				boolean right = result.status() == Status.MATCHED && Math.abs(result.offsetNanos() / 1e6 - jitter[i]) <= MATCH_TOLERANCE_MILLIS + biasAllowance(result);
				outcome.afterRight += (right ? 1 : 0);
			}
			outcome.resynchronised = (outcome.afterRight == outcome.afterPlayed);
		}
		outcome.separated = (outcome.rightMatches == n && outcome.extras == 0);
		return outcome;
	}

	/** The bias of the detection (the pitch is seen after the transient) is not an error of matching: the offsets are compared to the bias of the run. */
	private static double biasAllowance(EventResult result) {
		return 0.0;
	}

	// ------------------------------------------------------------------------------------------------------------- the report

	private static String percent(int part, int whole) {
		return whole == 0 ? "-" : String.format(java.util.Locale.ROOT, "%.0f%%", 100.0 * part / whole);
	}

	private static String cell(Scenario scenario, double notesPerSecond, boolean waits) {
		int events = 0, matched = 0, right = 0, falseMatches = 0, wrong = 0, missed = 0, notJudged = 0, extras = 0, onTime = 0, early = 0, late = 0;
		int afterPlayed = 0, afterRight = 0, resyncRuns = 0, separatedRuns = 0;
		double bias = 0.0;
		for (int r = 0; r < RUNS; r++) {
			Outcome o = run(scenario, notesPerSecond, 1000 + r, waits);
			events += o.events;
			matched += o.matched;
			right += o.rightMatches;
			falseMatches += o.falseMatches;
			wrong += o.wrongPitch;
			missed += o.missed;
			notJudged += o.notJudged;
			extras += o.extras;
			onTime += o.onTime;
			early += o.early;
			late += o.late;
			afterPlayed += o.afterPlayed;
			afterRight += o.afterRight;
			resyncRuns += (o.resynchronised ? 1 : 0);
			separatedRuns += (o.separated ? 1 : 0);
			bias += o.biasSum;
		}
		StringBuilder line = new StringBuilder();
		line.append(String.format(java.util.Locale.ROOT, "| %s | %.0f/s | %d | matched to the right event %s (%d/%d) | false matches %d | wrong pitch %d | missed %d | not judged %d | extra %d | on time %d, early %d, late %d | mean offset error %+.0f ms |",
				scenario.name(), notesPerSecond, RUNS, percent(right, events), right, events, falseMatches, wrong, missed, notJudged, extras, onTime, early, late,
				matched == 0 ? 0.0 : bias / matched));
		if (scenario == Scenario.MISTAKE) {
			line.append(String.format(java.util.Locale.ROOT, " resynchronised runs %s (%d/%d), events after the mistake right %s (%d/%d) |", percent(resyncRuns, RUNS), resyncRuns, RUNS,
					percent(afterRight, afterPlayed), afterRight, afterPlayed));
		}
		if (scenario == Scenario.REPEATED) {
			line.append(String.format(java.util.Locale.ROOT, " runs with every repeat separated and no extra %s (%d/%d) |", percent(separatedRuns, RUNS), separatedRuns, RUNS));
		}
		return line.toString();
	}

	@Test
	public void theSyntheticRunsAreDeterministicAndReported() throws IOException {
		StringBuilder report = new StringBuilder();
		report.append("Tempo-following benchmark (spec 002 T101), run of 2026-10-08, ").append(RUNS).append(" runs per cell, ").append(EVENTS).append(" events per run (chords: 8).\n");
		report.append("Synthetic audio (plucked-string synthesis, each note rings under the next, gain 0.3), analysed by the real chain, judged by the tempo-following session on a fake player\n");
		report.append("and clock; normal tolerance; no latency compensation. SC-009 (provisional): >= 95 % of correctly played notes matched to the right event; >= 95 % of runs stay\n");
		report.append("synchronised after a deliberate mistake. SC-004: repeated identical notes at 8 a second separated in >= 95 % of cases.\n");
		report.append("A match is right when the event is MATCHED and its offset is within ").append((int) MATCH_TOLERANCE_MILLIS)
				.append(" ms of the time the note was played (the 25 ms of jitter and the detection bias are inside the offset).\n\n");
		for (boolean waits : new boolean[] { false, true }) {
			report.append(waits ? "# Variant B: an event whose pitch rings waits for the detector to report the attack before taking the late time of the evidence\n"
					: "# Variant A (the default): the event takes the time of the attack the detector reported, or at once the time of the evidence when none was reported\n");
			for (Scenario scenario : Scenario.values()) {
				report.append("## ").append(scenario.text).append("\n");
				for (double rate : scenario == Scenario.CHORDS ? new double[] { 1, 2 } : NOTES_PER_SECOND) {
					report.append(cell(scenario, rate, waits)).append("\n");
				}
				report.append("\n");
			}
		}
		Outcome first = run(Scenario.MISTAKE, 4, 1003);
		Outcome second = run(Scenario.MISTAKE, 4, 1003);
		assertEquals(first.matched, second.matched, "the same seed gives the same run");
		assertEquals(first.rightMatches, second.rightMatches);
		assertEquals(first.extras, second.extras);
		report.append("Determinism: two runs of the same seed gave the same matches (").append(first.matched).append(" matched, ").append(first.extras).append(" extra).\n");
		report.append("Techniques: not part of this benchmark (see technique-benchmark-2026-10-06.txt for the detectors, and the session tests for the rule that a technique never changes the timing).\n");
		write("follow-benchmark-synthetic-2026-10-08.txt", report.toString());
		System.out.println(report);
		assertTrue(report.length() > 500);
	}

	// ------------------------------------------------------------------------------------------------------------- the recorded run

	@Test
	public void theRealRecordingAtTheScoreTempoIsReportedWhenItIsThere() throws IOException {
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
		// the recording was not played to a click, and not at 99 beats a minute: the tempo the learner would choose is the one that fits the annotated
		// times best (a least squares line through played = start + written * ratio), and the score is laid on the recording with it
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
		FollowTimeline timeline = FollowTimeline.build(song, sequence, percent);
		long lead = Math.max(0L, Math.round(start * 1e9));
		FakePlayerRun run = new FakePlayerRun(song, sequence, percent, LATENCY, lead);
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(audio.samples, audio.sampleRate, RecognitionSettings.defaults(), true, AttackCandidates.of(run.sequence));
		run.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		run.start();
		run.feed(frames);

		int matched = 0, onTime = 0, early = 0, late = 0, missed = 0, wrong = 0, notJudged = 0, wrongTime = 0;
		int inWindow = 0, inWindowMatched = 0, missedInWindow = 0, missedOutside = 0;
		TimingTolerance tolerance = new TimingTolerance(TimingTolerance.Preset.NORMAL);
		for (int i = 0; i < n; i++) {
			EventResult result = run.last.results().get(i);
			double human = (played[i] - start - timeline.targetNanos(i) / 1e9) * 1000.0;
			// is the note, as annotated, inside the outer window of its event (the compensation of the latency left aside)?
			TimingTolerance.Window window = tolerance.windowFor(timeline.targetNanos(i), timeline.beatNanos(i), timeline.gapBeforeNanos(i), timeline.gapAfterNanos(i));
			boolean annotatedInWindow = human * 1e6 >= window.outerFrom() - window.target() && human * 1e6 <= window.outerTo() - window.target();
			inWindow += (annotatedInWindow ? 1 : 0);
			switch (result.status()) {
				case MATCHED:
					matched++;
					inWindowMatched += (annotatedInWindow ? 1 : 0);
					onTime += (result.timing() == TimingVerdict.ON_TIME ? 1 : 0);
					early += (result.timing() == TimingVerdict.EARLY ? 1 : 0);
					late += (result.timing() == TimingVerdict.LATE ? 1 : 0);
					wrongTime += (Math.abs(result.offsetNanos() / 1e6 - human) > MATCH_TOLERANCE_MILLIS ? 1 : 0);
					break;
				case WRONG_PITCH:
					wrong++;
					break;
				case MISSED:
					missed++;
					missedInWindow += (annotatedInWindow ? 1 : 0);
					missedOutside += (annotatedInWindow ? 0 : 1);
					break;
				default:
					notJudged += (result.status() == Status.NOT_JUDGED ? 1 : 0);
					break;
			}
		}
		StringBuilder report = new StringBuilder();
		report.append("Tempo-following benchmark on the real recording of the repository (tests/1, 99 beats a minute, 34 notes and 8 chords), run of 2026-10-08.\n");
		report.append("The recording is not played to a click, nor at the written tempo: the score is laid on it with the tempo percentage that fits the annotated times best (")
				.append(percent).append(" %, ").append(String.format(java.util.Locale.ROOT, "first measure at %.0f ms", start * 1000.0))
				.append("), normal tolerance, no latency compensation. What is reported is how the session sees the human timing of the player around that tempo.\n");
		report.append("The audio is not redistributed and is not in the repository; the times of the played events come from test-data/real-recording-1/events.csv.\n");
		report.append(String.format(java.util.Locale.ROOT, "events %d | matched %d (%s) | on time %d, early %d, late %d | wrong pitch %d | missed %d | not judged %d | extra notes %d | matched with an offset more than %d ms from the annotated time %d%n",
				n, matched, percent(matched, n), onTime, early, late, wrong, missed, notJudged, run.last.counts().extra(), (int) MATCH_TOLERANCE_MILLIS, wrongTime));
		report.append(String.format(java.util.Locale.ROOT, "Of the %d events whose annotated time lies inside the outer window of the score (the player is near the written tempo there): matched %d (%s). "
				+ "Missed with the note inside its window: %d. Missed with the note outside its window (the player's tempo differs from the score's, which no recognition can change): %d.%n",
				inWindow, inWindowMatched, percent(inWindowMatched, inWindow), missedInWindow, missedOutside));
		report.append("State at the end: ").append(run.last.state()).append("\n");
		write("follow-benchmark-recorded-2026-10-08.txt", report.toString());
		System.out.println(report);
	}

	private static void write(String name, String text) throws IOException {
		Path dir = Paths.get("../../specs/002-practice-industrialization/benchmark");
		if (Files.isDirectory(dir)) {
			Files.write(dir.resolve(name), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
	}
}
