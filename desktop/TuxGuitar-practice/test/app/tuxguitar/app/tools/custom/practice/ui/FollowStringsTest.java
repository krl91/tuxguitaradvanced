package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.FollowSession;
import app.tuxguitar.app.tools.custom.practice.session.TimingContext;

/**
 * The texts of the tempo-following mode (spec 002 T100): every key exists in English, French and Spanish with the same placeholders, and every state,
 * verdict and reason the session can report has its text, so a new one cannot reach the screen as a bare key.
 */
public class FollowStringsTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

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

	private static Set<String> followKeys(Properties properties) {
		Set<String> keys = new TreeSet<String>();
		for (String key : properties.stringPropertyNames()) {
			if (key.startsWith("practice.follow.") || key.startsWith("practice.mode") || key.equals("practice.start.follow")) {
				keys.add(key);
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
	public void everyKeyOfTheModeExistsInTheThreeLanguagesWithTheSamePlaceholders() throws IOException {
		Properties english = load(FILES[0]);
		Set<String> keys = followKeys(english);
		assertTrue(keys.size() > 60, "the texts of the mode are there: " + keys.size());
		for (String file : FILES) {
			Properties properties = load(file);
			assertEquals(keys, followKeys(properties), file + " has the same keys as the English texts");
			for (String key : keys) {
				String text = properties.getProperty(key);
				assertFalse(text.trim().isEmpty(), file + ": " + key + " is empty");
				assertEquals(placeholders(english.getProperty(key)), placeholders(text), file + ": the placeholders of " + key);
			}
		}
	}

	@Test
	public void everyStateVerdictAndReasonTheSessionReportsHasItsText() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			for (FollowSession.State state : FollowSession.State.values()) {
				assertTrue(properties.containsKey("practice.follow.state." + state.name()), file + ": state " + state);
			}
			for (FollowSession.Status status : FollowSession.Status.values()) {
				if (status == FollowSession.Status.PENDING || status == FollowSession.Status.AUTO_PASSED || status == FollowSession.Status.MATCHED) {
					continue;
				}
				assertTrue(properties.containsKey("practice.follow.event." + status.name()), file + ": status " + status);
			}
			for (String verdict : new String[] { "ON_TIME", "EARLY", "LATE", "EXTRA" }) {
				assertTrue(properties.containsKey("practice.follow.event." + verdict), file + ": verdict " + verdict);
			}
			for (TimingContext.PrecisionReason reason : TimingContext.PrecisionReason.values()) {
				assertTrue(properties.containsKey("practice.follow.precision.reason." + reason.name()), file + ": reason " + reason);
			}
			assertTrue(properties.containsKey("practice.follow.precision.NOT_READY"), file);
		}
	}

	@Test
	public void theFrenchAndSpanishTextsAreNotTheEnglishOnes() throws IOException {
		Properties english = load(FILES[0]);
		for (String file : new String[] { FILES[1], FILES[2] }) {
			Properties other = load(file);
			int same = 0;
			for (String key : followKeys(english)) {
				same += (english.getProperty(key).equals(other.getProperty(key)) ? 1 : 0);
			}
			// a few texts (a bare number, "Normal") may be alike; a whole untranslated file may not
			assertTrue(same < 6, file + ": " + same + " texts are still the English ones");
		}
	}
}
