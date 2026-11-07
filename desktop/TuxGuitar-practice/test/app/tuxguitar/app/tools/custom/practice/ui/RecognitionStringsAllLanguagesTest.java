package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;

/**
 * Every recognition setting and every section has a name and a tip in English, French and Spanish, and the French and Spanish texts are translated
 * (spec 026, T011): with about two hundred settings, a family task cannot leave one language behind.
 */
public class RecognitionStringsAllLanguagesTest {

	/** Texts that are the same word in another language: the guitar terms and the section "Passages" (already allowed in French by spec 024). */
	private static final Set<String> SAME_IN_SPANISH = new HashSet<String>(Arrays.asList("recognition.note.legato.name"));
	private static final Set<String> SAME_IN_FRENCH = new HashSet<String>(Arrays.asList("recognition.section.PASSAGES"));

	@Test
	public void everySettingAndSectionIsWrittenAndTranslatedInTheThreeLanguages() throws IOException {
		Properties en = load("share/lang/messages.properties");
		Properties fr = load("share/lang/messages_fr.properties");
		Properties es = load("share/lang/messages_es.properties");
		List<String> keys = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			keys.add(RecognitionControls.nameKey(parameter));
			keys.add(RecognitionControls.tipKey(parameter));
		}
		for( RecognitionParameter.Section section : RecognitionParameter.Section.values() ) {
			keys.add(RecognitionControls.sectionKey(section));
		}
		List<String> problems = new ArrayList<String>();
		for( String key : keys ) {
			String english = text(en, key);
			String french = text(fr, key);
			String spanish = text(es, key);
			if( english.isEmpty() || french.isEmpty() || spanish.isEmpty() ) {
				problems.add("missing: " + key);
				continue;
			}
			if( french.equals(english) && !SAME_IN_FRENCH.contains(key) ) {
				problems.add("French not translated: " + key);
			}
			if( spanish.equals(english) && !SAME_IN_SPANISH.contains(key) ) {
				problems.add("Spanish not translated: " + key);
			}
		}
		assertEquals(new ArrayList<String>(), problems);
	}

	private static String text(Properties properties, String key) {
		String value = properties.getProperty(key);
		return value == null ? "" : value.trim();
	}

	private static Properties load(String path) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}
}
