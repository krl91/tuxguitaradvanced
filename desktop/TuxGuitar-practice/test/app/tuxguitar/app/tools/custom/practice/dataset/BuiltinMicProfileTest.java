package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationScore;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationScorer;
import app.tuxguitar.app.tools.custom.practice.calibration.EventJudge;
import app.tuxguitar.app.tools.custom.practice.calibration.ProfileSearch;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.calibration.SoundSegmenter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T024): the whole path of the calibration on a recording of the piece: cut, scored, searched. Three parts.
 * 1. (always) a synthetic performance of the piece is cut into its 13 sounds and scored with the default settings: the machinery, not a claim.
 * 2. (with {@code -Dcalibration.search=true}) the search on that synthetic performance: its cost, measured.
 * 3. (with {@code -Dcalibration.search=true}, and only when the owner's recording of basic_test.tg is there, outside git) the search on the owner's recording,
 *    then the default and the profile judged on the 21 notes of the built-in microphone corpus, which the search never saw.
 * Nothing is promised and nothing is asserted about a rate: the figures are printed and recorded by hand in benchmark/search-results.txt.
 */
@Tag("dataset")
public class BuiltinMicProfileTest {

	private static final int RATE = 48000;

	private static double frequency(int midi) {
		return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
	}

	/** the piece played by Karplus-Strong strings: a lead of one second, 2.5 s per sound, 1.5 s of silence between */
	private static float[] synthetic(CalibrationPiece piece) {
		List<float[]> parts = new ArrayList<float[]>();
		parts.add(new float[RATE]);
		for (int i = 0; i < piece.eventCount(); i++) {
			PracticeEvent event = piece.getSequence().getEvents().get(i);
			// a single note has no "required pitches" (only a chord does): its pitch is the expected midi
			int[] pitches = event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ? new int[] { event.getExpectedMidi() } : event.getRequiredPitches();
			float[] sound = pitches.length == 1
					? SyntheticSignals.karplusStrong(frequency(pitches[0]), RATE, RATE * 5 / 2, 100L + i)
					: SyntheticSignals.strum(pitches, RATE, RATE * 5 / 2, 650, 1.0, 11L + i);
			parts.add(normalised(sound, 0.4f));
			parts.add(new float[RATE * 3 / 2]);
		}
		int total = 0;
		for (float[] part : parts) {
			total += part.length;
		}
		float[] all = new float[total];
		int at = 0;
		for (float[] part : parts) {
			System.arraycopy(part, 0, all, at, part.length);
			at += part.length;
		}
		return all;
	}

	/** the sound scaled so that its peak is {@code peak} (five strings added together would otherwise clip) */
	private static float[] normalised(float[] sound, float peak) {
		float max = 0f;
		for (float v : sound) {
			max = Math.max(max, Math.abs(v));
		}
		float[] scaled = new float[sound.length];
		for (int i = 0; i < sound.length; i++) {
			scaled[i] = max == 0f ? 0f : sound[i] * peak / max;
		}
		return scaled;
	}

	@Test
	public void aSyntheticPerformanceOfThePieceIsCutIntoItsThirteenSoundsAndScoredWithTheDefaultSettings() {
		CalibrationPiece piece = CalibrationPiece.load();
		SoundSegmenter.Result cut = SoundSegmenter.segment(synthetic(piece), RATE, piece.eventCount());
		assertTrue(cut.isAccepted(), "refusal: " + cut.getRefusal() + ", heard " + cut.getHeard() + " of " + cut.getExpected());
		assertEquals(13, cut.getSegments().size());
		EventJudge judge = piece.judge(new TGContext());
		CalibrationScorer scorer = CalibrationScorer.forRecording(judge, cut.getSegments(), RATE);
		long began = System.nanoTime();
		CalibrationScore score = scorer.score(RecognitionSettings.defaults());
		System.out.println("synthetic piece, default settings: " + score.getRate() + ", wrong " + score.getWrong() + ", score " + score.getObjective() + ", "
				+ (System.nanoTime() - began) / 1_000_000 + " ms for the first candidate (26 analyses)");
	}

	@Test
	public void theSearchOnTheSyntheticPerformanceCostIsMeasured() {
		assumeTrue(Boolean.getBoolean("calibration.search"), "run with -Dcalibration.search=true");
		CalibrationPiece piece = CalibrationPiece.load();
		SoundSegmenter.Result cut = SoundSegmenter.segment(synthetic(piece), RATE, piece.eventCount());
		assertTrue(cut.isAccepted());
		CalibrationScorer scorer = CalibrationScorer.forRecording(piece.judge(new TGContext()), cut.getSegments(), RATE);
		long began = System.nanoTime();
		ProfileSearch.Result result = new ProfileSearch(scorer::score).run(RecognitionSettings.defaults(),
				(done, budget) -> { });
		System.out.println("synthetic piece: search " + (System.nanoTime() - began) / 1_000_000 + " ms, " + result.getCandidates() + " candidates, "
				+ scorer.getAnalysesDone() + " analyses, stopped at the limit " + result.isStoppedAtLimit() + ", better than the default "
				+ result.isBetterThanDefault() + ", default " + result.getDefaultScore().getRate() + " score " + result.getDefaultScore().getObjective()
				+ ", found " + result.getBestScore().getRate() + " score " + result.getBestScore().getObjective());
		for (ProfileSearch.Line line : result.getReport()) {
			if (line.isChanged()) {
				System.out.println("   changed " + line.getKey() + ": " + line.getDefaultValue() + " -> " + line.getFoundValue());
			}
		}
	}

	private static File ownersRecording() {
		File folder = new File(System.getProperty("user.home"), "tuxguitar-owner-takes-backup/calibration");
		File[] found = folder.listFiles((dir, name) -> name.startsWith("basic_test-") && name.endsWith(".wav"));
		if (found == null || found.length == 0) {
			return null;
		}
		Arrays.sort(found);
		return found[found.length - 1];
	}

	@Test
	public void theSearchOnTheOwnersRecordingThenTheCorpusJudgedWithTheProfileFound() throws IOException {
		assumeTrue(Boolean.getBoolean("calibration.search"), "run with -Dcalibration.search=true");
		File recording = ownersRecording();
		assumeTrue(recording != null, "the owner's recording of basic_test.tg is not here (outside git): skipped");
		RecordedAudio audio = RecordedAudio.load(recording.toPath());
		CalibrationPiece piece = CalibrationPiece.load();
		SoundSegmenter.Result cut = SoundSegmenter.segment(audio.samples, audio.sampleRate, piece.eventCount());
		System.out.println("owner's recording " + recording.getName() + ": " + audio.samples.length / (double) audio.sampleRate + " s, heard " + cut.getHeard()
				+ " of " + cut.getExpected() + (cut.isAccepted() ? "" : ", REFUSED: " + cut.getRefusal()));
		assumeTrue(cut.isAccepted(), "the recording is refused: " + cut.getRefusal());
		CalibrationScorer scorer = CalibrationScorer.forRecording(piece.judge(new TGContext()), cut.getSegments(), audio.sampleRate);
		long began = System.nanoTime();
		ProfileSearch.Result result = new ProfileSearch(scorer::score).run(RecognitionSettings.defaults(), null);
		System.out.println("owner's recording: search " + (System.nanoTime() - began) / 1_000_000 + " ms, " + result.getCandidates() + " candidates, stopped at the limit "
				+ result.isStoppedAtLimit() + "\n   default " + result.getDefaultScore().getRate() + " score " + result.getDefaultScore().getObjective() + ", wrong "
				+ result.getDefaultScore().getWrong() + "\n   found   " + result.getBestScore().getRate() + " score " + result.getBestScore().getObjective() + ", wrong "
				+ result.getBestScore().getWrong() + ", better than the default " + result.isBetterThanDefault());
		for (ProfileSearch.Line line : result.getReport()) {
			if (line.isChanged()) {
				System.out.println("   changed " + line.getKey() + ": " + line.getDefaultValue() + " -> " + line.getFoundValue());
			}
		}
		corpusWith("default", RecognitionSettings.defaults());
		corpusWith("profile found", result.getBest());
	}

	private static void corpusWith(String label, RecognitionSettings settings) throws IOException {
		List<String[]> rows = new ArrayList<String[]>();
		try (InputStream stream = BuiltinMicProfileTest.class.getResourceAsStream("/benchmark/manifest.csv")) {
			for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				if (line.startsWith("builtin-mic/standard/")) {
					String[] cells = line.split(",", 11);
					if (!cells[5].isEmpty()) {
						rows.add(cells);
					}
				}
			}
		}
		SongFixture fixture = new SongFixture(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			fixture.quarter(i, 0, Integer.parseInt(rows.get(i)[6]), Integer.parseInt(rows.get(i)[7]));
		}
		EventJudge judge = new EventJudge(new TGContext(), fixture.song, fixture.track(), fixture.sequence());
		int accepted = 0;
		for (int i = 0; i < rows.size(); i++) {
			Path temporary = Files.createTempFile("builtin-mic", ".wav");
			try (InputStream stream = BuiltinMicProfileTest.class.getResourceAsStream("/benchmark/" + rows.get(i)[0])) {
				Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
				RecordedAudio audio = RecordedAudio.load(temporary);
				if (judge.accepts(audio.samples, audio.sampleRate, i, settings)) {
					accepted++;
				}
			} finally {
				Files.deleteIfExists(temporary);
			}
		}
		System.out.println("builtin-mic corpus with the " + label + " settings: " + accepted + " of " + rows.size() + " notes accepted");
	}
}
