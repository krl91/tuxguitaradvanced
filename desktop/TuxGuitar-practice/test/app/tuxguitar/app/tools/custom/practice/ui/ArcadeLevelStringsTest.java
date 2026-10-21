package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Spec 014 (T033, FR-013, SC-005): the texts of the levels and of the permissiveness exist in English, French and Spanish, with the same placeholders, and the
 * French and the Spanish are not the English (the numbers aside).
 */
public class ArcadeLevelStringsTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };

	private static final String[] KEYS = { "practice.arcade.level.label", "practice.arcade.level.1", "practice.arcade.level.2", "practice.arcade.level.3", "practice.arcade.level.4",
			"practice.arcade.level.5", "arcade.leniency.label", "arcade.leniency.like_practice", "arcade.leniency.fun", "arcade.leniency.very_permissive",
			"arcade.leniency.hint.like_practice", "arcade.leniency.hint.fun", "arcade.leniency.hint.very_permissive", "arcade.result.level", "arcade.result.level.value",
			"arcade.result.leniency" };

	private static Properties load(String file) throws IOException {
		Path path = Paths.get("share", "lang", file);
		if (!Files.exists(path)) {
			path = Paths.get("desktop", "TuxGuitar-practice", "share", "lang", file);
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
				if (other.getProperty(key).equals(english.getProperty(key))) {
					untranslated.add(FILES[i] + " " + key);
				}
			}
		}
		// a word that is the same in the three languages is allowed: the texts of the levels and the permissiveness all carry words of the language
		assertEquals(new ArrayList<String>(), untranslated, "the same text as the English");
	}

	@Test
	public void theThreePositionsAndTheFiveLevelsAreDifferentTexts() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			assertNotEquals(properties.getProperty("arcade.leniency.fun"), properties.getProperty("arcade.leniency.like_practice"));
			assertNotEquals(properties.getProperty("arcade.leniency.fun"), properties.getProperty("arcade.leniency.very_permissive"));
			for (int i = 1; i < 5; i++) {
				assertNotEquals(properties.getProperty("practice.arcade.level." + i), properties.getProperty("practice.arcade.level." + (i + 1)), file);
			}
		}
	}
}
