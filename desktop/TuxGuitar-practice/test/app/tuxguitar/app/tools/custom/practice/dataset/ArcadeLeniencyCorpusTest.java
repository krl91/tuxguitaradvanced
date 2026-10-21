package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniency;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLeniencyPosition;
import app.tuxguitar.app.tools.custom.practice.calibration.EventJudge;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * Spec 014 (T036, SC-002, research R6): the built-in-microphone corpus (21 notes with an expected note) judged at each position of the Arcade permissiveness, with the
 * negative control (the audio of a note judged against the next note, a wrong acceptance if accepted) next to it. REPORTED, WITHOUT A LIMIT: with the default
 * settings the control already accepts 1 of 21 (4.8 %), so this corpus cannot carry the limit of SC-002, which is measured on the reference recording. Gated by
 * {@code -Darcade.measure=true}. It copies the corpus code of {@code BuiltinMicProfileTest}, which stays as it is.
 */
@Tag("dataset")
public class ArcadeLeniencyCorpusTest {

	private static String line(String label, RecognitionSettings settings, List<String[]> rows, EventJudge judge, List<float[]> samples, List<Integer> rates) {
		int accepted = 0;
		int wrong = 0;
		int controls = 0;
		for (int i = 0; i < rows.size(); i++) {
			if (judge.accepts(samples.get(i), rates.get(i), i, settings)) {
				accepted++;
			}
			if (i + 1 < rows.size() && !rows.get(i + 1)[6].equals(rows.get(i)[6]) || i + 1 < rows.size() && !rows.get(i + 1)[7].equals(rows.get(i)[7])) {
				controls++;
				if (judge.accepts(samples.get(i), rates.get(i), i + 1, settings)) {
					wrong++;
				}
			}
		}
		return String.format("ARCADE CORPUS %s: %d of %d notes accepted, negative control accepted %d of %d", label, accepted, rows.size(), wrong, controls);
	}

	@Test
	public void theBuiltInMicrophoneCorpusAtEachPosition() throws IOException {
		assumeTrue(Boolean.getBoolean("arcade.measure"), "run with -Darcade.measure=true");
		List<String[]> rows = new ArrayList<String[]>();
		try (InputStream stream = ArcadeLeniencyCorpusTest.class.getResourceAsStream("/benchmark/manifest.csv")) {
			for (String text : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				if (text.startsWith("builtin-mic/standard/")) {
					String[] cells = text.split(",", 11);
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
		List<float[]> samples = new ArrayList<float[]>();
		List<Integer> rates = new ArrayList<Integer>();
		for (int i = 0; i < rows.size(); i++) {
			Path temporary = Files.createTempFile("builtin-mic", ".wav");
			try (InputStream stream = ArcadeLeniencyCorpusTest.class.getResourceAsStream("/benchmark/" + rows.get(i)[0])) {
				Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
				RecordedAudio audio = RecordedAudio.load(temporary);
				samples.add(audio.samples);
				rates.add(audio.sampleRate);
			} finally {
				Files.deleteIfExists(temporary);
			}
		}
		RecognitionSettings base = RecognitionSettings.defaults();
		String baseLine = line("base (the default settings)", base, rows, judge, samples, rates);
		System.out.println(baseLine);
		for (ArcadeLeniencyPosition position : ArcadeLeniencyPosition.values()) {
			System.out.println(line(position.name(), ArcadeLeniency.apply(position, base), rows, judge, samples, rates));
		}
		assertEquals(baseLine.replace("base (the default settings)", "LIKE_PRACTICE"), line("LIKE_PRACTICE", ArcadeLeniency.apply(ArcadeLeniencyPosition.LIKE_PRACTICE, base), rows, judge, samples, rates));
	}
}
