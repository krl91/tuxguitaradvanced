package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationController.RefusalCause;
import app.tuxguitar.audio.engine.calibration.RecordingQuality;

/**
 * Spec 013 (T035, FR-015, SC-009): the texts of the calibration exist in English, French and Spanish under the prefix {@code practice.profile.}, with the
 * same keys and the same numbers to fill in, really translated, and every text the window asks for is there.
 */
public class CalibrationStringsTest {

	private static final String PREFIX = "practice.profile.";
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d)\\}");
	/** short words that are the same in the language */
	private static final Set<String> SAME_AS_ENGLISH = Set.of("ok", "stop.no", "quality.no");

	private static Properties load(String file) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/" + file)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}

	private static Set<String> keys(Properties properties) {
		Set<String> keys = new TreeSet<String>();
		for (String key : properties.stringPropertyNames()) {
			if (key.startsWith(PREFIX)) {
				keys.add(key.substring(PREFIX.length()));
			}
		}
		return keys;
	}

	private static Set<String> placeholders(String text) {
		Set<String> found = new TreeSet<String>();
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	public void theThreeLanguagesHaveTheSameKeysWithTheSameNumbersToFillIn() throws IOException {
		Properties en = load("messages.properties");
		Properties fr = load("messages_fr.properties");
		Properties es = load("messages_es.properties");
		assertFalse(keys(en).isEmpty());
		assertEquals(keys(en), keys(fr), "French has the same keys");
		assertEquals(keys(en), keys(es), "Spanish has the same keys");
		for (String key : keys(en)) {
			assertEquals(placeholders(en.getProperty(PREFIX + key)), placeholders(fr.getProperty(PREFIX + key)), "the numbers of " + key + " in French");
			assertEquals(placeholders(en.getProperty(PREFIX + key)), placeholders(es.getProperty(PREFIX + key)), "the numbers of " + key + " in Spanish");
		}
	}

	@Test
	public void frenchAndSpanishAreTranslatedNotCopiedFromEnglish() throws IOException {
		Properties en = load("messages.properties");
		Properties fr = load("messages_fr.properties");
		Properties es = load("messages_es.properties");
		for (String key : keys(en)) {
			if (SAME_AS_ENGLISH.contains(key)) {
				continue;
			}
			assertNotEquals(en.getProperty(PREFIX + key), fr.getProperty(PREFIX + key), "not translated in French: " + key);
			assertNotEquals(en.getProperty(PREFIX + key), es.getProperty(PREFIX + key), "not translated in Spanish: " + key);
		}
	}

	@Test
	public void everyTextTheWindowAsksForExists() throws IOException {
		Properties en = load("messages.properties");
		String source = new String(Files.readAllBytes(Paths.get("src/app/tuxguitar/app/tools/custom/practice/ui/CalibrationWindow.java")), StandardCharsets.UTF_8);
		Set<String> used = new TreeSet<String>();
		Matcher direct = Pattern.compile("\\bt\\(\"([A-Za-z0-9.\\-_]+)\"").matcher(source);
		while (direct.find()) {
			used.add(direct.group(1));
		}
		Matcher prefixed = Pattern.compile("P \\+ \"([A-Za-z0-9.\\-_]+)\"").matcher(source);
		while (prefixed.find()) {
			used.add(prefixed.group(1));
		}
		// a key that ends with a dot is the beginning of a key made from a value: the refusal, the noise word; those are listed below
		used.removeIf(key -> key.endsWith("."));
		// the keys made from a value: the refusal, the noise word
		for (RefusalCause cause : RefusalCause.values()) {
			used.add("refused." + cause.name());
		}
		for (RecordingQuality.NoiseWord word : RecordingQuality.NoiseWord.values()) {
			used.add("quality.noise." + word.name().toLowerCase(java.util.Locale.ROOT));
		}
		used.add("choice.NOT_BETTER");
		used.add("choice.BETTER_UNDER_60");
		assertTrue(used.size() > 30, "the window uses its texts: " + used.size());
		for (String key : used) {
			assertTrue(en.containsKey(PREFIX + key), "the window asks for a text that does not exist: " + key);
		}
		// and the start window's own texts it reuses for the tuning question
		for (String key : new String[] { "practice.prerun.tuned.question", "practice.prerun.tuned.yes", "practice.prerun.tuned.no" }) {
			assertTrue(en.containsKey(key), key);
		}
	}

	@Test
	public void noTextOfTheCalibrationUsesAnotherPrefixThanTheOneOfTheLanguageTests() throws IOException {
		// the existing language tests list the keys by prefix (practice.follow., practice.mode, arcade., practice.prerun., recognition.): none of ours starts with one
		for (String key : keys(load("messages.properties"))) {
			String full = PREFIX + key;
			for (String reserved : new String[] { "practice.follow.", "practice.mode", "arcade.", "practice.prerun.", "recognition." }) {
				assertFalse(full.startsWith(reserved), full);
			}
		}
	}
}
