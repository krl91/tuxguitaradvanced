package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationScorer;
import app.tuxguitar.app.tools.custom.practice.calibration.EventJudge;
import app.tuxguitar.app.tools.custom.practice.calibration.ProfileSearch;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.calibration.SoundSegmenter;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T046, T057, analysis finding I1): the independent measure on the take the owner makes with the box "Keep a copy of this recording for diagnosis"
 * (a WAV in {@code tuxguitar-calibration-takes} of the home folder, or the file given by {@code -Dcalibration.take=<path>}). It does what
 * {@code BuiltinMicProfileTest} does on the older outside recording, which is left as it was: the search on the take, the rate with the default and with the
 * profile found, then the 21 built-in-microphone notes judged with each. Gated by {@code -Dcalibration.search=true}; it skips itself when there is no take.
 */
@Tag("dataset")
public class CalibrationTakeValidationTest {

	/** The file to measure: the one named, else the newest {@code calibration-*.wav} of the folder, else null. */
	static File take(String named, File folder) {
		if (named != null && !named.isEmpty()) {
			File file = new File(named);
			return file.isFile() ? file : null;
		}
		File[] found = folder.listFiles((dir, name) -> name.startsWith("calibration-") && name.endsWith(".wav"));
		if (found == null || found.length == 0) {
			return null;
		}
		Arrays.sort(found);
		return found[found.length - 1];
	}

	@Test
	public void theTakeIsTheFileNamedOrTheNewestOfTheFolder() throws IOException {
		Path folder = Files.createTempDirectory("takes");
		assertNull(take(null, folder.toFile()), "an empty folder: no take");
		File older = Files.createFile(folder.resolve("calibration-2026-10-19-140509.wav")).toFile();
		File newer = Files.createFile(folder.resolve("calibration-2026-10-20-091500.wav")).toFile();
		Files.createFile(folder.resolve("basic_test-old.wav"));
		assertEquals(newer, take(null, folder.toFile()));
		assertEquals(older, take(older.getPath(), folder.toFile()), "a named file wins");
		assertNull(take(folder.resolve("missing.wav").toString(), folder.toFile()), "a named file that is not there is no take");
		assertNull(take(null, folder.resolve("no-such-folder").toFile()));
	}

	@Test
	public void theSearchOnTheTakeThenTheCorpusJudgedWithTheProfileFound() throws IOException {
		assumeTrue(Boolean.getBoolean("calibration.search"), "run with -Dcalibration.search=true");
		File recording = take(System.getProperty("calibration.take"), new File(System.getProperty("user.home"), "tuxguitar-calibration-takes"));
		assumeTrue(recording != null, "no take in ~/tuxguitar-calibration-takes (and no -Dcalibration.take): skipped");
		RecordedAudio audio = RecordedAudio.load(recording.toPath());
		CalibrationPiece piece = CalibrationPiece.load();
		SoundSegmenter.Result cut = SoundSegmenter.segment(audio.samples, audio.sampleRate, piece.eventCount());
		System.out.println("take " + recording.getName() + ": " + audio.samples.length / (double) audio.sampleRate + " s, heard " + cut.getHeard() + " of "
				+ cut.getExpected() + (cut.isAccepted() ? "" : ", REFUSED: " + cut.getRefusal()));
		assumeTrue(cut.isAccepted(), "the take is refused: " + cut.getRefusal());
		CalibrationScorer scorer = CalibrationScorer.forRecording(piece.judge(new TGContext()), cut.getSegments(), audio.sampleRate);
		long began = System.nanoTime();
		ProfileSearch.Result result = new ProfileSearch(scorer::score).run(RecognitionSettings.defaults(), null);
		System.out.println("take: search " + (System.nanoTime() - began) / 1_000_000 + " ms, " + result.getCandidates() + " candidates, stopped at the limit "
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
		try (InputStream stream = CalibrationTakeValidationTest.class.getResourceAsStream("/benchmark/manifest.csv")) {
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
			try (InputStream stream = CalibrationTakeValidationTest.class.getResourceAsStream("/benchmark/" + rows.get(i)[0])) {
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
