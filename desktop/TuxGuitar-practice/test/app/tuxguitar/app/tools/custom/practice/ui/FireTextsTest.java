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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;

/**
 * Spec 019 (T033, FR-018, SC-009): every text of the Advanced tab exists in English, French and Spanish: the name and the tooltip of every setting of each
 * group, the titles of the groups and sections, the units, and the fixed texts of the tab, so that none reaches the screen as a bare key.
 */
public class FireTextsTest {

	private static final String[] FILES = { "messages.properties", "messages_fr.properties", "messages_es.properties" };

	private static final List<String> FIXED = Arrays.asList("practice.tab.advanced", "practice.fire.group.arcade", "practice.fire.group.score", "practice.fire.defaults",
			"practice.fire.defaults.tip", "practice.fire.preview.sparks", "practice.fire.preview.sparks.tip", "practice.fire.preview.growth",
			"practice.fire.preview.growth.tip", "practice.fire.score.setting", "practice.fire.score.setting.tip", "practice.fire.tooltip.unit",
			"practice.fire.tooltip.default", "practice.fire.colour.choose");

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

	private static List<String> keys() {
		List<String> keys = new ArrayList<String>(FIXED);
		for (FireTuning.Group group : FireTuning.Group.values()) {
			for (FireParameter parameter : FireParameter.values()) {
				if (parameter.in(group) && !keys.contains(parameter.labelKey())) {
					keys.add(parameter.labelKey());
					keys.add(parameter.tooltipKey());
				}
			}
			if (!keys.contains(group.labelKey())) {
				keys.add(group.labelKey());
			}
		}
		for (FireParameter.Section section : FireParameter.Section.values()) {
			keys.add(section.labelKey());
		}
		for (FireParameter.Unit unit : FireParameter.Unit.values()) {
			if (unit.labelKey() != null) {
				keys.add(unit.labelKey());
			}
		}
		return keys;
	}

	@Test
	public void everyTextOfTheAdvancedTabIsInEveryLanguage() throws IOException {
		List<String> missing = new ArrayList<String>();
		for (String file : FILES) {
			Properties properties = load(file);
			for (String key : keys()) {
				String value = properties.getProperty(key);
				if (value == null || value.trim().isEmpty()) {
					missing.add(file + ": " + key);
				}
			}
		}
		assertTrue(missing.isEmpty(), "missing texts: " + missing);
	}

	@Test
	public void theTooltipsOfTheSettingsSayMoreThanTheirName() throws IOException {
		for (String file : FILES) {
			Properties properties = load(file);
			for (FireParameter parameter : FireParameter.values()) {
				String name = properties.getProperty(parameter.labelKey(), "");
				String tip = properties.getProperty(parameter.tooltipKey(), "");
				assertTrue(tip.length() > name.length() + 10, file + ": the tooltip of " + parameter.key() + " says what it changes: " + tip);
			}
			assertTrue(properties.getProperty("practice.fire.tooltip.default", "").contains("{0}"), file);
			assertTrue(properties.getProperty("practice.fire.tooltip.unit", "").contains("{0}"), file);
		}
	}
}
