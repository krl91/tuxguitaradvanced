package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Spec 019 (T008, FR-015, SC-009, research R11): no number that shapes the fire is left in its code outside the tuning. The number literals of the three
 * classes of the fire are read from their source, method by method:
 * <ul>
 * <li>a literal in a field declaration is a named constant: a fixed value of research R4 or the default a parameter refers to;</li>
 * <li>inside the pure mathematical helpers every number is allowed: they set no look;</li>
 * <li>0 and 1 are allowed anywhere;</li>
 * <li>every other literal must be on the list below, each line classified in research R11 with its reason, as fixed or neutral.</li>
 * </ul>
 */
public class FireSourceScanTest {

	static final Path SOURCES = Paths.get("src/app/tuxguitar/app/tools/custom/practice/arcade/view");

	/** The pure mathematical helpers (R11): any number. */
	private static final Set<String> HELPERS = new HashSet<String>(Arrays.asList("value", "hash", "smooth", "lerp", "clamp", "mix", "gap"));

	/** "File method literal": each line of research R11 classified fixed or neutral. */
	private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
			// neutral: centring, units, rounding guards, halves, the middle of a cell, the octaves of the noise
			"FireField FireField 2.0", "FireField FireField 1e9", "FireField tongueRows 1e-9", "FireField weight 2", "FireField heat 0.5",
			"FireField noise 2.0", "FireField noise 4.0",
			// fixed (R4): the BALANCED tier has cells twice as big
			"FireField cell 2.0",
			// neutral: the gaps between steps and colours, the bytes of a colour
			"FireColours colour 2", "FireColours colour 16", "FireColours colour 8",
			// fixed (R4): the item code
			"FlameDrawing items 3", "FlameDrawing items 2", "FlameDrawing items 4",
			// fixed (R4): the hash that draws each spark; neutral: the drift centred, a spark too faint, units, rounding guards
			"FlameDrawing sparks 31L", "FlameDrawing sparks 0xFFFF", "FlameDrawing sparks 65536.0", "FlameDrawing sparks 17", "FlameDrawing sparks 33",
			"FlameDrawing sparks 0.5", "FlameDrawing sparks 1e-6", "FlameDrawing sparkCount 1e-9", "FlameDrawing sparkLifeNanos 1e9",
			// neutral: the place across the width, and the bit-identical d * d of the default curve
			"FlameDrawing weight 2.0",
			// the reading guard: indices (neutral), its smoothstep (fixed, R4)
			"FlameDrawing faded 2", "FlameDrawing faded 3", "FlameDrawing faded 3.0", "FlameDrawing faded 2.0",
			// the boxes of the texts: indices, halves and corners (neutral), the estimate of a text's box (fixed, R4)
			"FlameDrawing textBoxes 2", "FlameDrawing textBoxes 2.0", "FlameDrawing textBoxes 8", "FlameDrawing textBoxes 0.66",
			"FlameDrawing textBoxes 0.6"));

	private static final Pattern LITERAL = Pattern.compile("(?<![\\w.])(0[xX][0-9A-Fa-f_]+[lL]?|(?:\\d[\\d_]*)?\\.?\\d[\\d_]*(?:[eE][-+]?\\d+)?[lLdDfF]?)(?![\\w.])");

	private static final Pattern MEMBER = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

	/** The literals of a source file that are neither allowed nor in a helper, as "File method literal (line n)". */
	static List<String> unexplained(String file) throws IOException {
		String source = new String(Files.readAllBytes(SOURCES.resolve(file + ".java")), StandardCharsets.UTF_8);
		String code = withoutCommentsAndStrings(source);
		List<String> found = new ArrayList<String>();
		int depth = 0;
		String method = null;
		StringBuilder signature = new StringBuilder();
		String[] lines = code.split("\n", -1);
		for (int number = 0; number < lines.length; number++) {
			String line = lines[number];
			if (depth == 1) {
				signature.append(line).append(' ');
			}
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (c == '{') {
					depth++;
					if (depth == 2) {
						method = isFieldInitialiser(signature.toString()) ? FIELD : memberName(signature.toString());
					}
				} else if (c == '}') {
					depth--;
					if (depth == 1) {
						method = null;
						signature.setLength(0);
					}
				} else if (c == ';' && depth == 1) {
					signature.setLength(0);
				}
			}
			if (method == null || method == FIELD || HELPERS.contains(method)) {
				continue;
			}
			Matcher matcher = LITERAL.matcher(line);
			while (matcher.find()) {
				String literal = matcher.group(1);
				if (!literal.matches(".*\\d.*") || isZeroOrOne(literal)) {
					continue;
				}
				String entry = file + " " + method + " " + literal;
				if (!ALLOWED.contains(entry)) {
					found.add(entry + " (line " + (number + 1) + ")");
				}
			}
		}
		return found;
	}

	private static boolean isZeroOrOne(String literal) {
		String digits = literal.replace("_", "").replaceAll("[lLdDfF]$", "");
		try {
			double value = digits.startsWith("0x") || digits.startsWith("0X") ? Long.parseLong(digits.substring(2), 16) : Double.parseDouble(digits);
			return value == 0.0 || value == 1.0;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	/** The name given to the braces of a field's initial value: a named constant, allowed like a field declaration. */
	private static final String FIELD = "<field>";

	/** A field whose initial value opens braces: an array of constants, an equals sign with no call before it. */
	private static boolean isFieldInitialiser(String signature) {
		int equals = signature.indexOf('=');
		int call = signature.indexOf('(');
		return equals >= 0 && (call < 0 || call > equals);
	}

	private static String memberName(String signature) {
		String head = signature.contains("(") ? signature.substring(0, signature.indexOf('(') + 1) : signature;
		Matcher matcher = MEMBER.matcher(head);
		String name = null;
		while (matcher.find()) {
			name = matcher.group(1);
		}
		return name != null ? name : "?";
	}

	/** The code with its comments, strings and characters replaced by spaces, the lines kept. */
	static String withoutCommentsAndStrings(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int i = 0;
		while (i < source.length()) {
			char c = source.charAt(i);
			if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
				while (i < source.length() && source.charAt(i) != '\n') {
					out.append(' ');
					i++;
				}
			} else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
				while (i < source.length() && !(source.charAt(i) == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/')) {
					out.append(source.charAt(i) == '\n' ? '\n' : ' ');
					i++;
				}
				out.append("  ");
				i += 2;
			} else if (c == '"' || c == '\'') {
				char quote = c;
				out.append(' ');
				i++;
				while (i < source.length() && source.charAt(i) != quote) {
					out.append(' ');
					i += (source.charAt(i) == '\\' ? 2 : 1);
				}
				out.append(' ');
				i++;
			} else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	@Test
	public void noNumberShapesTheFireOutsideTheTuning() throws IOException {
		List<String> found = new ArrayList<String>();
		for (String file : new String[] { "FireField", "FireColours", "FlameDrawing" }) {
			found.addAll(unexplained(file));
		}
		assertTrue(found.isEmpty(), "numbers outside the tuning, to read from it or to classify in research R11:\n" + String.join("\n", found));
	}
}
