package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;

/**
 * Spec 013, T005: the recognition rate of the DEFAULT settings on the 22 recordings of the owner's folk guitar through the MacBook's built-in microphone
 * (the committed corpus of the audio engine, reached through its test-jar). It is the reference of SC-006, a measurement: it promises and asserts no rate,
 * only that the corpus is there (21 notes and one recording of silence). The figures go to the standard output.
 */
@Tag("dataset")
public class BuiltinMicDefaultRateTest {

	private static final String PREFIX = "builtin-mic/standard/";
	/** the note a session expects while the recording of silence is fed: it must not accept it */
	private static final int SILENCE_EXPECTS = 64;

	private static List<String> manifest() throws IOException {
		try (InputStream stream = BuiltinMicDefaultRateTest.class.getResourceAsStream("/benchmark/manifest.csv")) {
			String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			List<String> rows = new ArrayList<String>();
			for (String line : text.split("\\R")) {
				if (line.startsWith(PREFIX)) {
					rows.add(line);
				}
			}
			return rows;
		}
	}

	private static RecordedAudio load(String file) throws IOException {
		Path temporary = Files.createTempFile("builtin-mic", ".wav");
		try (InputStream stream = BuiltinMicDefaultRateTest.class.getResourceAsStream("/benchmark/" + file)) {
			Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
			return RecordedAudio.load(temporary);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	@Test
	public void theDefaultSettingsOnTheBuiltInMicrophoneCorpus() throws IOException {
		List<String> rows = manifest();
		int notes = 0;
		int accepted = 0;
		int silences = 0;
		boolean silenceAccepted = false;
		StringBuilder report = new StringBuilder("builtin-mic default settings (spec 013, T005)\n");
		for (String row : rows) {
			String[] cells = row.split(",", 11);
			String file = cells[0];
			String note = cells[4];
			RecordedAudio audio = load(file);
			if (cells[5].isEmpty()) {
				silences++;
				silenceAccepted = DatasetJudge.accepts(audio, new int[] { SILENCE_EXPECTS });
				report.append(String.format("  %-34s silence, expecting %d: %s%n", file.substring(PREFIX.length()), SILENCE_EXPECTS, silenceAccepted ? "ACCEPTED (wrong)" : "not accepted (right)"));
				continue;
			}
			notes++;
			boolean ok = DatasetJudge.accepts(audio, new int[] { Integer.parseInt(cells[5]) });
			if (ok) {
				accepted++;
			}
			report.append(String.format("  %-34s %-4s midi %s: %s%n", file.substring(PREFIX.length()), note, cells[5], ok ? "accepted" : "MISSED"));
		}
		report.append(String.format("accepted %d of %d notes (%.1f %%); the silence recording is %s%n", accepted, notes, 100.0 * accepted / notes, silenceAccepted ? "accepted (a false acceptance)" : "not accepted"));
		System.out.println(report);
		assertEquals(22, rows.size(), "the corpus: 22 recordings");
		assertEquals(21, notes, "21 notes");
		assertEquals(1, silences, "and one recording of silence");
	}
}
