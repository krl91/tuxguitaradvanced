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

import app.tuxguitar.app.tools.custom.practice.session.RefusalReason;

/**
 * The texts of the refusal reasons of Follow (spec 016 FR-001, FR-010): every reason has its text and its summary line in English, French and
 * Spanish with the same placeholders, and the text of a sound not clear enough never says that the note was wrong (constitution XIV).
 */
public class FollowReasonStringsTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

	static Properties load(String file) throws IOException {
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

	static Set<String> placeholders(String text) {
		Set<String> found = new TreeSet<String>();
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@Test
	public void everyReasonHasItsTextAndItsSummaryLineInTheThreeLanguages() throws IOException {
		Properties english = load(FILES[0]);
		for (String file : FILES) {
			Properties properties = load(file);
			for (RefusalReason reason : RefusalReason.values()) {
				if (reason == RefusalReason.NONE) {
					continue;
				}
				for (String key : new String[] { "practice.follow.reason." + reason.name(), "practice.follow.summary.reason." + reason.name() }) {
					String text = properties.getProperty(key);
					assertTrue(text != null && !text.trim().isEmpty(), file + ": " + key);
					assertEquals(placeholders(english.getProperty(key)), placeholders(text), file + ": the placeholders of " + key);
				}
			}
			String unnamed = properties.getProperty("practice.follow.reason.OTHER_PITCH_UNNAMED");
			assertTrue(unnamed != null && unnamed.contains("{1}"), file + ": a wrong chord names what was expected");
		}
	}

	@Test
	public void aReasonWithADetailShowsIt() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			for (String reason : new String[] { "UNCLEAR", "EARLY_OUTSIDE", "LATE_OUTSIDE" }) {
				assertTrue(properties.getProperty("practice.follow.reason." + reason).contains("{0}"), file + ": " + reason + " shows its number");
			}
			String other = properties.getProperty("practice.follow.reason.OTHER_PITCH");
			assertTrue(other.contains("{0}") && other.contains("{1}"), file + ": the note heard and the note expected");
		}
	}

	@Test
	public void aSoundNotClearEnoughIsNeverCalledWrong() throws IOException {
		String[][] wrongWords = { { "wrong", "incorrect" }, { "mauvais", "faux", "fausse", "incorrect" }, { "incorrect", "equivocad", "mal" } };
		for (int f = 0; f < FILES.length; f++) {
			String text = load(FILES[f]).getProperty("practice.follow.reason.UNCLEAR").toLowerCase(java.util.Locale.ROOT);
			for (String word : wrongWords[f]) {
				assertFalse(text.contains(word), FILES[f] + ": UNCLEAR must not say '" + word + "': " + text);
			}
		}
	}

	@Test
	public void theOfferOfTheRhythmAndTheRecordOfARunHaveTheirTextsInTheThreeLanguages() throws IOException {
		// spec 016 T011, T019: the boxes of the record, its failure, and the step of the start window
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : new String[] { "practice.prerun.rhythm.sentence", "practice.prerun.rhythm.calibrate", "practice.prerun.rhythm.go-on",
					"practice.follow.record.run", "practice.follow.record.audio" }) {
				String text = properties.getProperty(key);
				assertTrue(text != null && !text.trim().isEmpty(), file + " " + key);
				assertTrue(placeholders(text).isEmpty(), file + " " + key + " takes no value");
			}
			String failed = properties.getProperty("practice.follow.record.failed");
			assertTrue(failed != null && placeholders(failed).contains("0"), file + " says what went wrong");
		}
	}

}
