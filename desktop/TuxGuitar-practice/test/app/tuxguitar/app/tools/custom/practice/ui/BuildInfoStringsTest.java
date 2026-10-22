package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Spec 015 (T013, FR-010, FR-013, SC-006): the texts of the About window's version and commit lines and of the Copy button exist in English, French and
 * Spanish, with the same placeholders, and the French and the Spanish are not the English.
 */
public class BuildInfoStringsTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };

	private static final String[] KEYS = { "help.about.copy-version", "help.about.copy-version.done", "help.about.copy-version.error", "help.about.commit",
			"help.about.commit.full", "help.about.commit.modified", "help.about.commit.unknown", "settings.config.main.window-title.var.description.appversion" };

	private static Properties load(String file) throws IOException {
		Path path = Paths.get("..", "..", "common", "resources", "lang", file);
		if (!Files.exists(path)) {
			path = Paths.get("common", "resources", "lang", file);
		}
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static TreeSet<String> placeholders(String text) {
		TreeSet<String> found = new TreeSet<String>();
		Matcher matcher = Pattern.compile("\\{(\\d+)\\}").matcher(text);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	public void everyTextExistsInTheThreeLanguagesWithTheSamePlaceholders() throws IOException {
		Properties english = load(FILES[0]);
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : KEYS) {
				String text = properties.getProperty(key);
				assertNotNull(text, file + " has " + key);
				assertFalse(text.trim().isEmpty(), file + " " + key);
				assertEquals(placeholders(english.getProperty(key)), placeholders(text), file + " " + key + ": the same placeholders");
			}
		}
	}

	@Test
	public void theFrenchAndTheSpanishAreNotTheEnglish() throws IOException {
		Properties english = load(FILES[0]);
		List<String> untranslated = new ArrayList<String>();
		for (int i = 1; i < FILES.length; i++) {
			Properties other = load(FILES[i]);
			for (String key : KEYS) {
				if (english.getProperty(key).equals(other.getProperty(key))) {
					untranslated.add(FILES[i] + " " + key);
				}
			}
		}
		assertEquals(new ArrayList<String>(), untranslated, "the same text as the English");
	}

	@Test
	public void theCommitLinesCarryTheCommitPlaceholder() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			assertEquals(new TreeSet<String>(java.util.Arrays.asList("0")), placeholders(properties.getProperty("help.about.commit")), file);
			assertEquals(new TreeSet<String>(java.util.Arrays.asList("0")), placeholders(properties.getProperty("help.about.commit.full")), file);
		}
	}
}
