package app.tuxguitar.app.tools.custom.practice.arcade.ui;

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
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeVerdict;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneLabels;

/** The words of Arcade (spec 003 T054, T056): the labels of the scene and the lines of the result exist in the three languages for every verdict. */
public class ArcadeResultTextTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };

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

	private static ArcadeTexts of(Properties properties) {
		return (key, arguments) -> {
			String text = properties.getProperty(key);
			assertTrue(text != null, "the text " + key + " exists");
			return arguments.length == 0 ? text : MessageFormat.format(text.replace("'", "''"), (Object[]) arguments);
		};
	}

	@Test
	public void everyVerdictAndEveryLabelOfTheSceneHasAWordInEachLanguageAndTheLanguagesDiffer() throws IOException {
		java.util.Set<String> verdictWords = new java.util.HashSet<String>();
		for (String file : FILES) {
			SceneLabels labels = new ArcadeSceneLabels(of(load(file)));
			for (ArcadeVerdict verdict : ArcadeVerdict.values()) {
				String word = labels.verdict(verdict);
				assertFalse(word.trim().isEmpty(), file + " " + verdict);
				assertFalse(word.contains("arcade.label"), file + ": a bare key reached the screen for " + verdict);
			}
			assertTrue(labels.pitchSet("A–C–F♯").contains("A–C–F♯"));
			assertTrue(labels.missing("E3").contains("E3"));
			assertTrue(labels.nextNote(3, 5).contains("3") && labels.nextNote(3, 5).contains("5"));
			assertTrue(labels.nextChord("Am").contains("Am"));
			assertTrue(labels.streak(12).contains("12") && labels.speed(80).contains("80"));
			verdictWords.add(labels.verdict(ArcadeVerdict.ON_TIME));
		}
		assertEquals(3, verdictWords.size(), "the word of a verdict is not the same in the three languages");
	}

	@Test
	public void theResultSaysTheScoreTheShareTheBestStreakAndTheCountsInTheThreeLanguages() throws IOException {
		app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeResult result = app.tuxguitar.app.tools.custom.practice.session.ArcadeResultFixtures.some();
		for (String file : FILES) {
			List<ArcadeResultText.Line> lines = ArcadeResultText.of(result, of(load(file)));
			assertTrue(lines.size() >= 8, file + ": " + lines);
			assertEquals(Integer.toString(result.score()), lines.get(0).value());
			assertTrue(lines.get(1).value().contains(Integer.toString(result.percent())));
			assertEquals(Integer.toString(result.bestStreak()), lines.get(2).value());
			for (ArcadeResultText.Line line : lines) {
				assertFalse(line.label().contains("arcade.result"), file + ": a bare key in " + line);
			}
		}
	}
}
