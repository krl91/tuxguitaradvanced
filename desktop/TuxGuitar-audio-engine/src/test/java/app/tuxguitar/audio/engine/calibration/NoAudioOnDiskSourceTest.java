package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Spec 013 (T026, FR-017): the calibration recording is never written to a file. The code that records, counts, cuts, judges and searches does not use a file
 * API at all; only the library of profiles touches the configuration, through the application's own configuration manager.
 */
public class NoAudioOnDiskSourceTest {

	private static final String[] FORBIDDEN = { "java.io.File", "java.nio.file", "FileOutputStream", "FileWriter", "RandomAccessFile", "createTempFile", "AudioSystem.write",
			"Files." };

	private static void check(Path root) throws IOException {
		assertTrue(Files.isDirectory(root), "the sources are there: " + root);
		List<Path> files;
		try (Stream<Path> walk = Files.walk(root)) {
			files = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertFalse(files.isEmpty());
		for (Path file : files) {
			String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
			for (String word : FORBIDDEN) {
				assertFalse(text.contains(word), file.getFileName() + " must not use " + word + ": no audio of the calibration is ever written");
			}
		}
	}

	@Test
	public void theEngineSideOfTheCalibrationUsesNoFileApi() throws IOException {
		check(Paths.get(System.getProperty("user.dir"), "src/main/java/app/tuxguitar/audio/engine/calibration"));
	}

	@Test
	public void thePracticeSideOfTheCalibrationUsesNoFileApi() throws IOException {
		check(Paths.get(System.getProperty("user.dir"), "../TuxGuitar-practice/src/app/tuxguitar/app/tools/custom/practice/calibration"));
	}
}
