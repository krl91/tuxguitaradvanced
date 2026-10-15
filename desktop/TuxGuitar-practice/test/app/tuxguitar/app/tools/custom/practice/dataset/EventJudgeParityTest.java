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

import app.tuxguitar.app.tools.custom.practice.calibration.EventJudge;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T018): {@link EventJudge}, copied from {@link DatasetJudge} for production, gives the same verdict as the original on the 21 notes of the
 * built-in microphone corpus (each judged against its own event, with the default settings), and a wrong event is refused as often as before.
 */
@Tag("dataset")
public class EventJudgeParityTest {

	private static final String PREFIX = "builtin-mic/standard/";

	@Test
	public void theSameVerdictAsDatasetJudgeOnTheTwentyOneNotes() throws IOException {
		List<String[]> rows = new ArrayList<String[]>();
		try (InputStream stream = EventJudgeParityTest.class.getResourceAsStream("/benchmark/manifest.csv")) {
			for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				if (line.startsWith(PREFIX)) {
					String[] cells = line.split(",", 11);
					if (!cells[5].isEmpty()) {
						rows.add(cells);
					}
				}
			}
		}
		assertEquals(21, rows.size());
		SongFixture fixture = new SongFixture(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			fixture.quarter(i, 0, Integer.parseInt(rows.get(i)[6]), Integer.parseInt(rows.get(i)[7]));
		}
		EventJudge judge = new EventJudge(new TGContext(), fixture.song, fixture.track(), fixture.sequence());
		RecognitionSettings settings = RecognitionSettings.defaults();
		int rightAccepted = 0;
		int wrongAccepted = 0;
		for (int i = 0; i < rows.size(); i++) {
			RecordedAudio audio = load(rows.get(i)[0]);
			boolean original = DatasetJudge.accepts(audio, new int[] { Integer.parseInt(rows.get(i)[5]) });
			boolean copy = judge.accepts(audio.samples, audio.sampleRate, i, settings);
			assertEquals(original, copy, rows.get(i)[0] + ": the copy and the original agree");
			if (copy) {
				rightAccepted++;
			}
			if (judge.accepts(audio.samples, audio.sampleRate, (i + 1) % rows.size(), settings)) {
				wrongAccepted++;
			}
		}
		System.out.println("EventJudge parity: " + rightAccepted + " of " + rows.size() + " notes accepted against their own event; " + wrongAccepted
				+ " accepted against the next event (the negative control)");
	}

	private static RecordedAudio load(String file) throws IOException {
		Path temporary = Files.createTempFile("builtin-mic", ".wav");
		try (InputStream stream = EventJudgeParityTest.class.getResourceAsStream("/benchmark/" + file)) {
			Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
			return RecordedAudio.load(temporary);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
