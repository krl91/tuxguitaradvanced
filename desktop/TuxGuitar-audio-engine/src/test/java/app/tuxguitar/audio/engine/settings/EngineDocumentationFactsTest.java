package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The engines' documentation states every parameter's facts as the code holds them (spec 026, T032, FR-008): each key of
 * {@link RecognitionParameters#ALL} has a table row in {@code docs/architecture/recognition-engines.md}, under the heading of its
 * {@code Section}, whose columns give its unit, its stored default, its bounds (or options) and when a change takes effect. "Listening restart"
 * is what {@code AudioEngine.engineValuesDiffer} says of a change of that key alone; every other key takes effect "at once" or at the "next run".
 * Extends {@code EngineDocumentationTest} (spec 024), which only requires the key to appear.
 */
public class EngineDocumentationFactsTest {

	private static final List<String> TAKES_EFFECT = Arrays.asList("at once", "next run", "listening restart");
	private static final Pattern NUMBER = Pattern.compile("^-?\\d+(?:\\.\\d+)?(?:/\\d+)?");

	@Test
	public void everyParameterHasItsFactsInTheDocumentation() throws Exception {
		String[] lines = ConstantInventory.read(ConstantInventory.DOCUMENT).split("\n");
		Method differ = Class.forName("app.tuxguitar.audio.engine.AudioEngine").getDeclaredMethod("engineValuesDiffer", RecognitionSettings.class,
				RecognitionSettings.class);
		differ.setAccessible(true);
		List<String> problems = new ArrayList<String>();
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			int row = rowOf(lines, parameter.getKey());
			if( row < 0 ) {
				problems.add(parameter.getKey() + ": no table row");
				continue;
			}
			String section = sectionOf(lines, row);
			if( !section.contains("`Section." + parameter.getSection().name() + "`") ) {
				problems.add(parameter.getKey() + ": under \"" + section.trim() + "\", not Section." + parameter.getSection().name());
			}
			String[] header = ConstantInventory.cells(headerOf(lines, row));
			String[] cells = ConstantInventory.cells(lines[row]);
			String unit = cell(header, cells, "Unit");
			String defaultValue = cell(header, cells, "Default");
			String bounds = cell(header, cells, "Bounds");
			String takesEffect = cell(header, cells, "Takes effect");
			problems.addAll(checkValue(parameter, unit, defaultValue, bounds));
			boolean restarts = (Boolean) differ.invoke(null, RecognitionSettings.defaults(), changed(parameter, bounds));
			if( takesEffect == null || !TAKES_EFFECT.contains(takesEffect) ) {
				problems.add(parameter.getKey() + ": takes effect \"" + takesEffect + "\" is not one of " + TAKES_EFFECT);
			} else if( restarts != takesEffect.equals("listening restart") ) {
				problems.add(parameter.getKey() + ": takes effect \"" + takesEffect + "\", the engine " + (restarts ? "restarts" : "does not restart")
						+ " the listening");
			}
		}
		assertEquals(new ArrayList<String>(), problems, "facts of " + ConstantInventory.DOCUMENT + " that differ from the code");
	}

	@Test
	public void theDocumentNumbersAreRead() {
		assertEquals(-40.0, number("−40"));
		assertEquals(1.0 / 3.0, number("1/3 (0.333…)"));
		assertEquals(1500.0, number("1,500"));
		assertEquals(0.005, number("0.005"));
	}

	private static List<String> checkValue(RecognitionParameter parameter, String unit, String defaultValue, String bounds) {
		List<String> problems = new ArrayList<String>();
		String key = parameter.getKey();
		if( unit == null || defaultValue == null || bounds == null ) {
			problems.add(key + ": the table has no Unit, Default or Bounds column");
			return problems;
		}
		if( parameter instanceof RecognitionParameter.Number ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			String expectedUnit = number.getUnit();
			boolean unitMatches = expectedUnit.isEmpty() ? (unit.equals("—") || unit.startsWith("count")) : unit.startsWith(expectedUnit);
			if( !unitMatches ) {
				problems.add(key + ": unit \"" + unit + "\", the code says \"" + expectedUnit + "\"");
			}
			if( !same(number(defaultValue), number.getDefault()) ) {
				problems.add(key + ": default \"" + defaultValue + "\", the code says " + number.getDefault());
			}
			String[] range = bounds.split(" to ");
			if( range.length < 2 || !same(number(range[0]), number.getMinimum()) || !same(number(range[1]), number.getMaximum()) ) {
				problems.add(key + ": bounds \"" + bounds + "\", the code says " + number.getMinimum() + " to " + number.getMaximum());
			}
		} else if( parameter instanceof RecognitionParameter.Flag ) {
			if( !unit.equals("on/off") ) {
				problems.add(key + ": unit \"" + unit + "\", a flag is on/off");
			}
			String expected = ((Boolean) parameter.getDefault()) ? "on" : "off";
			if( !defaultValue.equals(expected) ) {
				problems.add(key + ": default \"" + defaultValue + "\", the code says " + expected);
			}
		} else {
			RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
			if( !unit.startsWith("choice") ) {
				problems.add(key + ": unit \"" + unit + "\", a choice");
			}
			if( !defaultValue.startsWith("`" + choice.getDefault() + "`") ) {
				problems.add(key + ": default \"" + defaultValue + "\", the code says " + choice.getDefault());
			}
			for( String option : choice.getOptions() ) {
				if( !bounds.contains("`" + option + "`") ) {
					problems.add(key + ": the option " + option + " is not in \"" + bounds + "\"");
				}
			}
		}
		return problems;
	}

	/**
	 * The settings with this one value moved away from its default: a number to its minimum (its maximum when the default is the minimum), a flag
	 * inverted, a choice to another value the code accepts, among its options and the values its Bounds cell lists (the engines of the menu).
	 */
	private static RecognitionSettings changed(RecognitionParameter parameter, String bounds) {
		RecognitionSettings defaults = RecognitionSettings.defaults();
		if( parameter instanceof RecognitionParameter.Number ) {
			RecognitionParameter.Number number = (RecognitionParameter.Number) parameter;
			double value = number.getDefault() != number.getMinimum() ? number.getMinimum() : number.getMaximum();
			return defaults.with(number, value);
		}
		if( parameter instanceof RecognitionParameter.Flag ) {
			return defaults.with((RecognitionParameter.Flag) parameter, !((Boolean) parameter.getDefault()));
		}
		RecognitionParameter.Choice choice = (RecognitionParameter.Choice) parameter;
		List<String> values = new ArrayList<String>(choice.getOptions());
		Matcher listed = Pattern.compile("`([^`]+)`").matcher(bounds == null ? "" : bounds);
		while( listed.find() ) {
			values.add(listed.group(1));
		}
		for( String value : values ) {
			if( !value.equals(choice.getDefault()) && choice.validate(value).getRepair() == RecognitionParameter.Repair.NONE ) {
				return defaults.with(choice, value);
			}
		}
		return defaults;
	}

	private static int rowOf(String[] lines, String key) {
		for( int i = 0; i < lines.length; i++ ) {
			if( lines[i].startsWith("| `" + key + "` |") ) {
				return i;
			}
		}
		return -1;
	}

	private static String sectionOf(String[] lines, int row) {
		for( int i = row; i >= 0; i-- ) {
			if( lines[i].startsWith("#") ) {
				return lines[i];
			}
		}
		return "";
	}

	private static String headerOf(String[] lines, int row) {
		for( int i = row; i >= 0; i-- ) {
			if( lines[i].startsWith("| Key |") ) {
				return lines[i];
			}
		}
		return "| |";
	}

	private static String cell(String[] header, String[] cells, String name) {
		for( int i = 0; i < header.length && i < cells.length; i++ ) {
			if( header[i].equals(name) ) {
				return cells[i];
			}
		}
		return null;
	}

	/** The number a cell starts with: a Unicode minus, thousands commas and a fraction {@code a/b} read. */
	static double number(String text) {
		String plain = text.trim().replace('−', '-').replaceAll("(?<=\\d),(?=\\d{3})", "");
		Matcher m = NUMBER.matcher(plain);
		if( !m.find() ) {
			return Double.NaN;
		}
		String[] parts = m.group().split("/");
		return parts.length == 2 ? Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]) : Double.parseDouble(parts[0]);
	}

	private static boolean same(double written, double value) {
		return Math.abs(written - value) <= 1e-9 * Math.max(1.0, Math.abs(value));
	}
}
