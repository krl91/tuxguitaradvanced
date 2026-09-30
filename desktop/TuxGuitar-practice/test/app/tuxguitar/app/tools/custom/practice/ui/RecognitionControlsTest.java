package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.view.dialog.settings.TGSettingsOptionFactory;
import app.tuxguitar.app.view.dialog.settings.TGSettingsOptionRegistry;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * Display-independent checks of the "Audio recognition" rubric (task T102): slider mapping of
 * every numeric setting, bounded section size (no control outside the window), every text in the
 * translation file, and the Configuration-window extension point.
 */
public class RecognitionControlsTest {

	@Test
	public void everySliderRunsFromZeroAndCoversExactlyTheValidRange() {
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( !(parameter instanceof RecognitionParameter.Number) ) {
				continue;
			}
			RecognitionParameter.Number n = (RecognitionParameter.Number) parameter;
			int max = RecognitionControls.sliderMaximum(n);
			// SWT's Scale ignores negative minimums and bounds set in the wrong order (P13)
			assertTrue(max > 0, n.getKey());
			assertEquals(0, RecognitionControls.toSlider(n, n.getMinimum()), n.getKey());
			assertEquals(max, RecognitionControls.toSlider(n, n.getMaximum()), n.getKey());
			// both ends map back to the exact bounds
			assertEquals(n.getMinimum(), RecognitionControls.fromSlider(n, 0), n.getKey());
			assertEquals(n.getMaximum(), RecognitionControls.fromSlider(n, max), n.getKey());
			// every position is a valid value, exactly, and maps back to itself
			for( int position = 0 ; position <= max ; position ++ ) {
				double value = RecognitionControls.fromSlider(n, position);
				RecognitionSettings s = RecognitionSettings.defaults().with(n, value);
				assertEquals(value, s.get(n), n.getKey() + " @" + position + " was changed by validation");
				assertEquals(position, RecognitionControls.toSlider(n, s.get(n)), n.getKey() + " @" + position);
			}
			// the recommended value sits on the slider
			int recommended = RecognitionControls.toSlider(n, n.getDefault());
			assertTrue(recommended >= 0 && recommended <= max, n.getKey());
		}
	}

	@Test
	public void valuesAreShownInTheLearnersUnits() {
		assertEquals("35", RecognitionControls.displayNumber(RecognitionParameters.NOTE_ACCEPT, 35));
		assertEquals("80", RecognitionControls.displayNumber(RecognitionParameters.NOTE_CONFIDENCE, 0.80));
		assertEquals("33", RecognitionControls.displayNumber(RecognitionParameters.CHORD_EVIDENCE, 1.0 / 3.0));
		assertEquals("4.5", RecognitionControls.displayNumber(RecognitionParameters.NOTE_ATTACK, 4.5));
		assertEquals("-40", RecognitionControls.displayNumber(RecognitionParameters.INPUT_LEVEL_MIN, -40));
		assertEquals("10", RecognitionControls.displayNumber(RecognitionParameters.LEVEL_CLIP_SHARE, 0.10));
		assertEquals("recognition.unit.percent", RecognitionControls.unitKey("%"));
		assertEquals(null, RecognitionControls.unitKey(""));
	}

	@Test
	public void untouchedSlidersKeepTheExactStoredValue() {
		// the slider shows 33 % for one third; only moving it changes the stored value
		RecognitionSettings s = RecognitionSettings.defaults();
		assertEquals(1.0 / 3.0, s.get(RecognitionParameters.CHORD_EVIDENCE));
		// 33 % on a 20…80 % slider: position 13 from its minimum
		assertEquals(13, RecognitionControls.toSlider(RecognitionParameters.CHORD_EVIDENCE, s.get(RecognitionParameters.CHORD_EVIDENCE)));
		assertEquals("33", RecognitionControls.displayNumber(RecognitionParameters.CHORD_EVIDENCE, s.get(RecognitionParameters.CHORD_EVIDENCE)));
	}

	@Test
	public void everySectionFitsWithoutScrolling() {
		// one section is shown at a time; each stays within the row budget of the window
		for( Section section : Section.values() ) {
			int rows = RecognitionControls.rows(section);
			assertTrue(rows <= RecognitionControls.MAX_SECTION_ROWS, section + ": " + rows + " rows");
			assertTrue(rows >= 2, section.name());
		}
	}

	@Test
	public void everyTextOfTheRubricIsInTheTranslationFile() throws IOException {
		Properties messages = messages();
		List<String> missing = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			require(messages, RecognitionControls.nameKey(parameter), missing);
			require(messages, RecognitionControls.tipKey(parameter), missing);
			if( parameter instanceof RecognitionParameter.Choice ) {
				RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
				for( String option : choice.getOptions() ) {
					require(messages, RecognitionControls.optionKey(choice, option), missing);
				}
			}
			if( parameter instanceof RecognitionParameter.Number ) {
				String unitKey = RecognitionControls.unitKey(parameter.getUnit());
				if( unitKey != null ) {
					require(messages, unitKey, missing);
				}
			}
		}
		for( Section section : Section.values() ) {
			require(messages, RecognitionControls.sectionKey(section), missing);
		}
		// every "recognition.*" key used literally by the rubric exists
		String source = new String(Files.readAllBytes(Paths.get("src/app/tuxguitar/app/tools/custom/practice/ui/AudioRecognitionOption.java")), StandardCharsets.UTF_8)
				+ new String(Files.readAllBytes(Paths.get("src/app/tuxguitar/app/tools/custom/practice/ui/RecognitionSettingsPanel.java")), StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile("\"(recognition\\.[a-z.\\-]+)\"").matcher(source);
		while( matcher.find() ) {
			require(messages, matcher.group(1), missing);
		}
		assertTrue(missing.isEmpty(), "missing messages: " + missing);
	}

	@Test
	public void tooltipsDoNotRepeatNumbersThatComeFromTheParameters() throws IOException {
		// ranges and recommended values are composed from the parameter definitions, so a tip
		// never states a stale number
		Properties messages = messages();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			String tip = messages.getProperty(RecognitionControls.tipKey(parameter));
			assertNotNull(tip, parameter.getKey());
			assertFalse(tip.matches(".*\\d+\\s*(cents|ms|dB|%).*"), parameter.getKey() + ": " + tip);
		}
	}

	@Test
	public void theHelpPageDocumentsEverySettingWithItsCurrentRangeAndRecommendedValue() throws IOException {
		checkHelp("../TuxGuitar/share/help/tools_audio_recognition.html", messages());
		checkHelp("../TuxGuitar/share/help/tools_audio_recognition_fr.html", load("share/lang/messages_fr.properties"));
	}

	private static void checkHelp(String path, Properties messages) throws IOException {
		String help = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			String name = messages.getProperty(RecognitionControls.nameKey(parameter));
			int start = help.indexOf("<tr><td>" + name + "</td>");
			assertTrue(start >= 0, "help row for " + name);
			String row = help.substring(start, help.indexOf("</tr>", start));
			if( parameter instanceof RecognitionParameter.Number ) {
				RecognitionParameter.Number n = (RecognitionParameter.Number) parameter;
				List<String> values = new ArrayList<String>();
				Matcher cells = Pattern.compile("<td>(.*?)</td>").matcher(row);
				while( cells.find() ) {
					values.add(cells.group(1));
				}
				assertEquals(RecognitionControls.displayNumber(n, n.getMinimum()), values.get(2), name + " minimum");
				assertEquals(RecognitionControls.displayNumber(n, n.getMaximum()), values.get(3), name + " maximum");
				assertEquals(RecognitionControls.displayNumber(n, n.getDefault()), values.get(4), name + " recommended");
			}
		}
		for( Section section : Section.values() ) {
			assertTrue(help.contains(">" + messages.getProperty(RecognitionControls.sectionKey(section)) + "</h2>"), section.name());
		}
	}

	@Test
	public void theRubricIsRegisteredOnceAndRemovedWithThePlugin() {
		TGContext context = new TGContext();
		TGSettingsOptionRegistry registry = TGSettingsOptionRegistry.getInstance(context);
		TGSettingsOptionFactory factory = AudioRecognitionOption.factory();
		for( int i = 0 ; i < 5 ; i ++ ) {
			// repeated plugin connections, or the tuner and practice both registering
			registry.register(factory);
			registry.register(AudioRecognitionOption.factory());
		}
		assertEquals(1, registry.getFactories().size());
		assertEquals(AudioRecognitionOption.ID, registry.getFactories().get(0).getId());
		registry.unregister(AudioRecognitionOption.ID);
		assertTrue(registry.getFactories().isEmpty());
		registry.unregister(AudioRecognitionOption.ID);
		assertTrue(registry.getFactories().isEmpty());
		// the Configuration window gets a copy: changing it does not change the registry
		registry.register(factory);
		registry.getFactories().clear();
		assertEquals(1, registry.getFactories().size());
	}

	private static void require(Properties messages, String key, List<String> missing) {
		String value = messages.getProperty(key);
		if( value == null || value.trim().isEmpty() ) {
			missing.add(key);
		}
	}

	private static Properties messages() throws IOException {
		return load("share/lang/messages.properties");
	}

	private static Properties load(String path) throws IOException {
		Properties properties = new Properties();
		try (Reader reader = new InputStreamReader(Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return properties;
	}
}
