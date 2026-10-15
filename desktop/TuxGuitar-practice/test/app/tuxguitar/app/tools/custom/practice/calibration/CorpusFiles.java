package app.tuxguitar.app.tools.custom.practice.calibration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;

/** The committed corpus of the built-in microphone (the audio engine's test resources, reached through its test-jar). */
final class CorpusFiles {

	private CorpusFiles() {
	}

	static RecordedAudio load(String file) throws IOException {
		Path temporary = Files.createTempFile("builtin-mic", ".wav");
		try (InputStream stream = CorpusFiles.class.getResourceAsStream("/benchmark/builtin-mic/standard/" + file)) {
			Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
			return RecordedAudio.load(temporary);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
