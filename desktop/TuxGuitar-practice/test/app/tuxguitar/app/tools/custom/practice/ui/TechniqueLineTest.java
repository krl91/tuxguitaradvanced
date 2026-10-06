package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueTarget;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.app.tools.custom.practice.session.TechniqueJudge;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;

/** Spec 002 T140: the technique line says what was heard in every language of the application, and "cannot be checked yet" when detection is off. */
public class TechniqueLineTest {

	private static Properties load(String file) throws IOException {
		Properties properties = new Properties();
		try( Reader reader = new InputStreamReader(Files.newInputStream(Paths.get("share/lang/" + file)), StandardCharsets.UTF_8) ) {
			properties.load(reader);
		}
		return properties;
	}

	/** The application's lookup: the language's own text, the English one when the language lacks the key. */
	private static Function<String, String> lookup(Properties language, Properties english) {
		return key -> language.getProperty(key, english.getProperty(key, "!" + key + "!"));
	}

	private static TechniqueResult result(PlayingTechnique technique, TechniqueVerdict verdict) {
		return new TechniqueResult(technique, verdict, 0.5, Collections.<String, Double>emptyMap());
	}

	@Test
	public void everyTechniqueAndEveryVerdictHasItsOwnTextInEnglishFrenchAndSpanish() throws IOException {
		Properties english = load("messages.properties");
		for( String file : new String[] { "messages.properties", "messages_fr.properties", "messages_es.properties" } ) {
			Properties language = load(file);
			List<String> missing = new ArrayList<String>();
			List<String> verdictTexts = new ArrayList<String>();
			for( PlayingTechnique technique : PlayingTechnique.values() ) {
				if( technique.isJudged() && language.getProperty("practice.technique.name." + technique.name()) == null ) {
					missing.add("practice.technique.name." + technique.name());
				}
			}
			for( TechniqueVerdict verdict : TechniqueVerdict.values() ) {
				String text = language.getProperty("practice.technique.verdict." + verdict.name());
				if( text == null ) {
					missing.add("practice.technique.verdict." + verdict.name());
				} else {
					verdictTexts.add(text);
				}
			}
			assertEquals(new ArrayList<String>(), missing, file);
			assertEquals(TechniqueVerdict.values().length, verdictTexts.stream().distinct().count(), "the four verdicts read differently in " + file);
			assertTrue(!english.isEmpty());
		}
	}

	@Test
	public void theLineNamesEachTechniqueAndItsVerdict() throws IOException {
		Properties english = load("messages.properties");
		Function<String, String> en = lookup(english, english);
		Function<String, String> fr = lookup(load("messages_fr.properties"), english);
		Function<String, String> es = lookup(load("messages_es.properties"), english);
		List<TechniqueResult> hammer = Arrays.asList(result(PlayingTechnique.HAMMER_ON, TechniqueVerdict.NOT_VERIFIABLE));
		assertEquals("Hammer-on: cannot be checked yet", TechniqueLine.of(hammer, en));
		assertEquals("Hammer-on: pas encore vérifiable", TechniqueLine.of(hammer, fr));
		assertEquals("Hammer-on: todavía no verificable", TechniqueLine.of(hammer, es));
		List<TechniqueResult> two = Arrays.asList(result(PlayingTechnique.VIBRATO, TechniqueVerdict.DETECTED), result(PlayingTechnique.SLIDE, TechniqueVerdict.NOT_DETECTED));
		assertEquals("Vibrato: heard" + TechniqueLine.SEPARATOR + "Slide: not heard", TechniqueLine.of(two, en));
		assertEquals("Vibrato: entendu" + TechniqueLine.SEPARATOR + "Slide: non entendu", TechniqueLine.of(two, fr));
		assertEquals("Vibrato: oído" + TechniqueLine.SEPARATOR + "Slide: no oído", TechniqueLine.of(two, es));
		assertEquals("Bend: uncertain", TechniqueLine.of(Arrays.asList(result(PlayingTechnique.BEND, TechniqueVerdict.UNCERTAIN)), en));
		assertEquals(" ", TechniqueLine.of(Collections.<TechniqueResult>emptyList(), en), "no technique: a blank line, never a stale text");
	}

	@Test
	public void withDetectionOffEveryWrittenTechniqueSaysItCannotBeCheckedYet() throws IOException {
		Properties english = load("messages.properties");
		Function<String, String> en = lookup(english, english);
		TechniqueJudge off = new TechniqueJudge(TechniqueCatalog.defaults(), false);
		List<TechniqueTarget> targets = new ArrayList<TechniqueTarget>();
		for( PlayingTechnique technique : PlayingTechnique.values() ) {
			targets.add(TechniqueTarget.of(technique));
		}
		List<TechniqueResult> results = off.judge(targets, null);
		assertTrue(!results.isEmpty());
		for( TechniqueResult result : results ) {
			assertEquals(TechniqueVerdict.NOT_VERIFIABLE, result.getVerdict(), result.getTechnique().name());
		}
		String line = TechniqueLine.of(results, en);
		assertEquals(results.size(), line.split(java.util.regex.Pattern.quote(TechniqueLine.SEPARATOR)).length);
		assertTrue(!line.contains("heard: ") || line.contains("cannot be checked yet"));
		assertEquals(results.size(), (line.length() - line.replace("cannot be checked yet", "").length()) / "cannot be checked yet".length());
	}
}
