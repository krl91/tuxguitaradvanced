package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** Regression: the evolved tuner showed English labels in other languages. */
public class TunerMessagesTest {

	private static final File LANG = new File("share/lang");
	private static final File LEGACY_LANG = new File("../TuxGuitar-tuner/share/lang");

	@Test
	public void keepsTheLegacyTunerMenuNameInEveryLanguage() throws IOException {
		File[] legacyFiles = LEGACY_LANG.listFiles((dir, name) -> name.startsWith("messages_"));
		assertTrue(legacyFiles != null && legacyFiles.length > 0);
		for( File legacy : legacyFiles ) {
			Properties legacyMessages = load(legacy);
			Properties messages = load(new File(LANG, legacy.getName()));
			assertEquals(legacyMessages.getProperty("tuner.tuner"), messages.getProperty("tuner.tuner"), legacy.getName());
		}
	}

	@Test
	public void frenchTranslatesEveryTunerMessage() throws IOException {
		Properties english = load(new File(LANG, "messages.properties"));
		Properties french = load(new File(LANG, "messages_fr.properties"));
		for( String key : english.stringPropertyNames() ) {
			assertTrue(french.containsKey(key), "missing French message: " + key);
		}
	}

	private static Properties load(File file) throws IOException {
		Properties properties = new Properties();
		try( Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8) ) {
			properties.load(reader);
		}
		return properties;
	}
}
