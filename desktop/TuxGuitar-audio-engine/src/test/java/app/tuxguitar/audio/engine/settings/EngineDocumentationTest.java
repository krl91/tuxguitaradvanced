package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The engines' documentation stays complete (spec 024, task T038, FR-014): every key of {@link RecognitionParameters} appears in
 * {@code docs/architecture/recognition-engines.md}, so a new setting cannot be added without being documented.
 */
public class EngineDocumentationTest {

	private static final Path DOCUMENT = Paths.get("../../docs/architecture/recognition-engines.md");

	@Test
	public void everyRecognitionKeyIsDocumented() throws Exception {
		assertTrue(Files.isRegularFile(DOCUMENT), "the engines' documentation exists: " + DOCUMENT.toAbsolutePath().normalize());
		String text = new String(Files.readAllBytes(DOCUMENT), StandardCharsets.UTF_8);
		List<String> missing = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( !text.contains("`" + parameter.getKey() + "`") ) {
				missing.add(parameter.getKey());
			}
		}
		assertEquals(new ArrayList<String>(), missing, "keys missing from " + DOCUMENT);
	}
}
