package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

import org.junit.jupiter.api.Test;

/**
 * Spec 022 (T021, FR-016, SC-008): every text of the amp window exists in English, French and Spanish, each control with a tooltip, and the tooltip of
 * every knob names its unit and its default, so that none reaches the screen as a bare key.
 */
public class AmpTextsTest {

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

	private static boolean empty(String value) {
		return value == null || value.trim().isEmpty();
	}

	@Test
	public void everyControlHasALabelAndATooltipInEveryLanguage() throws IOException {
		List<String> missing = new ArrayList<String>();
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : AmpTexts.KEYS) {
				if (empty(properties.getProperty(key))) {
					missing.add(file + ": " + key);
				}
				if (empty(properties.getProperty(key + ".tip"))) {
					missing.add(file + ": " + key + ".tip");
				}
			}
			for (String key : AmpTexts.PLAIN) {
				if (empty(properties.getProperty(key))) {
					missing.add(file + ": " + key);
				}
			}
		}
		assertTrue(missing.isEmpty(), "missing texts: " + missing);
	}

	@Test
	public void theTooltipOfEveryKnobNamesItsUnitAndItsDefault() throws IOException {
		assertTrue(!AmpTexts.KNOBS.isEmpty());
		for (String file : FILES) {
			Properties properties = load(file);
			for (AmpTexts.KnobText knob : AmpTexts.KNOBS) {
				assertTrue(AmpTexts.KEYS.contains(knob.key()), knob.key() + " is a control of the window");
				String tip = properties.getProperty(knob.key() + ".tip", "");
				String name = properties.getProperty(knob.key(), "");
				String unit = properties.getProperty(knob.unitKey(), "");
				assertTrue(!unit.isEmpty() && tip.contains(unit), file + ": the tooltip of " + knob.key() + " names its unit (" + unit + "): " + tip);
				assertTrue(tip.contains(knob.defaultText()), file + ": the tooltip of " + knob.key() + " names its default (" + knob.defaultText() + "): " + tip);
				assertTrue(tip.length() > name.length() + 10, file + ": the tooltip of " + knob.key() + " says what it changes: " + tip);
			}
		}
	}
}
