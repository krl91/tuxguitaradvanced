package app.tuxguitar.audio.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * Every parameter an engine stage reads rebuilds the Java analysis when it changes during listening (spec 026, T007): each key whose row of the
 * inventory says "listening restart", once it is a setting, makes {@link AudioEngine#engineValuesDiffer} true.
 */
public class AudioEngineEngineValuesTest {

	private static final Path INVENTORY = Paths.get("../../specs/026-engine-external-parameters/inventory.md");

	@Test
	public void everyListeningRestartParameterRebuildsTheAnalysis() throws Exception {
		assertTrue(Files.isRegularFile(INVENTORY), "the inventory exists: " + INVENTORY.toAbsolutePath().normalize());
		List<String> missing = new ArrayList<String>();
		RecognitionSettings defaults = RecognitionSettings.defaults();
		for( String line : Files.readAllLines(INVENTORY, StandardCharsets.UTF_8) ) {
			if( !line.startsWith("| `recognition.") || !line.contains("| listening restart |") ) {
				continue;
			}
			String key = line.substring(3, line.indexOf('`', 3));
			RecognitionParameter parameter = parameterOf(key);
			if( parameter == null ) {
				continue; // not a setting yet: its family task adds it
			}
			if( !AudioEngine.engineValuesDiffer(defaults, defaults.with(parameter, otherValue(parameter))) ) {
				missing.add(key);
			}
		}
		assertEquals(new ArrayList<String>(), missing, "listening-restart parameters missing from AudioEngine.engineValuesDiffer");
	}

	private static RecognitionParameter parameterOf(String key) {
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			if( parameter.getKey().equals(key) ) {
				return parameter;
			}
		}
		return null;
	}

	private static Object otherValue(RecognitionParameter parameter) {
		if( parameter instanceof RecognitionParameter.Number ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			return number.getDefault() != number.getMaximum() ? number.getMaximum() : number.getMinimum();
		}
		if( parameter instanceof RecognitionParameter.Flag ) {
			return !((RecognitionParameter.Flag) parameter).getDefault();
		}
		RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
		for( String option : choice.getOptions() ) {
			if( !option.equals(choice.getDefault()) ) {
				return option;
			}
		}
		return choice.getDefault();
	}
}
