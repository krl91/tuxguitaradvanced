package app.tuxguitar.audio.engine.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The source scan of the inventory tests (spec 026, T029 and T030, research R9): it reads the numbers of the Java sources and tells which of them
 * the inventory and the engines' documentation do not account for. Shared by {@code EngineConstantInventoryTest} (audio engine) and
 * {@code SessionConstantInventoryTest} (practice), which give it their folders.
 * <p>
 * The rule, for every file scanned:
 * <ul>
 * <li>a numeric {@code static final} field is either named in a <b>parameter</b> row of {@code inventory.md} (column "Replaces", by its file and
 * name), whose key is in {@link RecognitionParameters#ALL} and whose default equals the field's value when that value is a plain number (in the
 * parameter's unit, or that unit turned into nanoseconds or milliseconds), or a <b>fixed</b> row of section 6 of
 * {@code docs/architecture/recognition-engines.md} (by its file and name);</li>
 * <li>a public numeric field with a value (a {@code Parameters} default) follows the same rule, its value compared with the parameter's default;</li>
 * <li>any other number written in the code (a method body, a constructor call, an array) is in the mathematical and unit allow-list below, or
 * is the default of a parameter row naming the file when it is written in a {@code defaults()} method, or is listed by file and value in the
 * inventory's "Inline numbers that stay inline".</li>
 * </ul>
 * {@code settings/RecognitionParameters.java} is the parameter list itself: its numbers are the defaults and bounds the documentation test checks.
 */
public final class ConstantInventory {

	/** Mathematical and unit values written inline anywhere (research R1): MIDI and cents, decibels, time conversions, numerical epsilons. */
	public static final Set<Double> ALLOWED = Collections.unmodifiableSet(new HashSet<Double>(Arrays.asList(0.0, 0.5, 1.0, 2.0, 4.0, 10.0, 12.0,
			20.0, 60.0, 69.0, 100.0, 440.0, 1000.0, 1200.0, 3600.0, 1e6, 1e9, 1e-9, 1e-12)));

	public static final Path INVENTORY = Paths.get("../../specs/026-engine-external-parameters/inventory.md");
	public static final Path DOCUMENT = Paths.get("../../docs/architecture/recognition-engines.md");

	private static final Pattern NUMBER = Pattern.compile(
			"(?<![\\w$.])(0[xX][0-9a-fA-F_]+[lL]?|(?:\\d[\\d_]*\\.?[\\d_]*|\\.\\d[\\d_]*)(?:[eE][+-]?\\d+)?[lLfFdD]?)(?![\\w.])");
	private static final Pattern STATIC_FINAL = Pattern.compile("static\\s+final\\s+(?:int|long|double|float|short|byte)\\s+(\\w+)\\s*=\\s*([^;]+);");
	private static final Pattern DEFAULT_FIELD = Pattern.compile("\\bpublic\\s+(?:final\\s+)?(?:double|int|long|float)\\s+(\\w+)\\s*=\\s*[^;]+;");
	private static final Pattern DEFAULTS_METHOD = Pattern.compile("\\bdefaults\\s*\\(\\s*\\)\\s*\\{");
	private static final Pattern ANNOTATION = Pattern.compile("@\\w+(\\([^)]*\\))?");
	private static final Pattern ROW = Pattern.compile("^\\| `([^`]+)` \\| `([^`]+)` \\|");
	private static final double[] UNIT_FACTORS = { 1.0, 1e3, 1e6, 1e9, 1e-3 };

	/** A parameter row of the inventory: its key, default, and the constants it replaces (file name, constant or field name). */
	public static final class ParameterRow {
		public final String key;
		public final double defaultValue;
		public final String replaces;
		public final String nativeColumn;

		ParameterRow(String key, double defaultValue, String replaces, String nativeColumn) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.replaces = replaces;
			this.nativeColumn = nativeColumn;
		}

		/** The file names and constant names of the "Replaces" column: {@code `File.java` `NAME`} or {@code `Parameters.field`}. */
		public List<String[]> names() {
			List<String[]> names = new ArrayList<String[]>();
			String file = null;
			Matcher m = Pattern.compile("`([^`]+)`").matcher(this.replaces);
			while( m.find() ) {
				String token = m.group(1);
				if( token.endsWith(".java") || token.endsWith(".rs") ) {
					file = token;
				} else if( file != null ) {
					names.add(new String[] { file, token.substring(token.lastIndexOf('.') + 1) });
				}
			}
			return names;
		}

		public boolean names(String file, String name) {
			for( String[] pair : names() ) {
				if( pair[0].equals(file) && pair[1].equals(name) ) {
					return true;
				}
			}
			return false;
		}

		public boolean namesFile(String file) {
			return this.replaces.contains("`" + file + "`");
		}
	}

	private final List<ParameterRow> parameters = new ArrayList<ParameterRow>();
	private final Map<String, Set<Double>> inline = new HashMap<String, Set<Double>>();
	private final Set<String> fixed = new HashSet<String>();
	private final Set<String> keys = new HashSet<String>();

	private ConstantInventory() {
	}

	/** Reads the inventory and section 6 of the documentation. */
	public static ConstantInventory read() throws IOException {
		ConstantInventory inventory = new ConstantInventory();
		String text = read(INVENTORY);
		for( String line : between(text, "## Parameters", "## Ordered pairs").split("\n") ) {
			if( line.startsWith("| `recognition.") ) {
				String[] cells = cells(line);
				inventory.parameters.add(new ParameterRow(cells[0].replace("`", ""), Double.parseDouble(cells[2]), cells[7], cells[8]));
			}
		}
		for( String line : between(text, "## Inline numbers that stay inline", null).split("\n") ) {
			Matcher m = ROW.matcher(line);
			if( m.find() ) {
				inventory.inline.computeIfAbsent(m.group(1), k -> new HashSet<Double>()).add(value(m.group(2)));
			}
		}
		for( String line : between(read(DOCUMENT), "## 6. ", null).split("\n") ) {
			Matcher m = ROW.matcher(line);
			if( m.find() ) {
				inventory.fixed.add(m.group(1) + " " + m.group(2));
			}
		}
		for( RecognitionParameter parameter : RecognitionParameters.ALL ) {
			inventory.keys.add(parameter.getKey());
		}
		return inventory;
	}

	public List<ParameterRow> getParameters() {
		return this.parameters;
	}

	/** Whether section 6 of the documentation lists this constant, by its file ({@code AE/…}, {@code PR/…}, {@code CRATE/…}) and name. */
	public boolean isFixed(String file, String name) {
		return this.fixed.contains(file + " " + name);
	}

	/**
	 * The numbers of the Java files under {@code root} that nothing accounts for, one line each.
	 *
	 * @param root the folder scanned
	 * @param prefix the folder's name in the inventory ({@code AE/}, {@code PR/session/}, …)
	 * @param excluded the sub-folders left out ({@code monitor/}: the amplifier, not recognition)
	 */
	public List<String> unaccounted(Path root, String prefix, String... excluded) throws IOException {
		List<String> problems = new ArrayList<String>();
		List<Path> files;
		try( Stream<Path> walk = Files.walk(root) ) {
			files = walk.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
		}
		for( Path path : files ) {
			String relative = root.relativize(path).toString().replace('\\', '/');
			boolean skip = relative.equals("settings/RecognitionParameters.java");
			for( String folder : excluded ) {
				skip |= relative.startsWith(folder);
			}
			if( !skip ) {
				scan(prefix + relative, path.getFileName().toString(), read(path), problems);
			}
		}
		return problems;
	}

	private void scan(String file, String fileName, String source, List<String> problems) {
		char[] code = ANNOTATION.matcher(stripped(source)).replaceAll("").toCharArray();
		String text = new String(code);
		List<int[]> declarations = new ArrayList<int[]>();
		Matcher m = STATIC_FINAL.matcher(text);
		while( m.find() ) {
			declarations.add(new int[] { m.start(), m.end() });
			checkConstant(file, fileName, m.group(1), m.group(2).trim(), problems);
		}
		List<int[]> defaultsBodies = new ArrayList<int[]>();
		m = DEFAULTS_METHOD.matcher(text);
		while( m.find() ) {
			defaultsBodies.add(new int[] { m.end() - 1, closingBrace(text, m.end() - 1) });
		}
		List<Object[]> fields = new ArrayList<Object[]>();
		m = DEFAULT_FIELD.matcher(text);
		while( m.find() ) {
			fields.add(new Object[] { m.start(), m.end(), m.group(1) });
		}
		m = NUMBER.matcher(text);
		while( m.find() ) {
			int at = m.start();
			if( inside(declarations, at) ) {
				continue;
			}
			double value = signed(text, at, value(m.group(1)));
			if( ALLOWED.contains(Math.abs(value)) || this.inline.getOrDefault(file, Collections.<Double>emptySet()).contains(Math.abs(value)) ) {
				continue;
			}
			String line = "line " + (1 + lineOf(text, at));
			Object[] field = null;
			for( Object[] f : fields ) {
				if( (Integer) f[0] <= at && at < (Integer) f[1] ) {
					field = f;
				}
			}
			if( field != null ) {
				String name = (String) field[2];
				if( !isFixed(file, name) && !matchesParameter(fileName, name, value) ) {
					problems.add(file + " field " + name + " = " + m.group(1) + ": neither a parameter's default nor a fixed constant of section 6");
				}
				continue;
			}
			if( inside(defaultsBodies, at) && matchesDefaultOfFile(fileName, value) ) {
				continue;
			}
			problems.add(file + " " + line + ": inline " + m.group(1) + " is not in the allow-list nor in inventory.md \"Inline numbers that stay inline\"");
		}
	}

	private void checkConstant(String file, String fileName, String name, String initializer, List<String> problems) {
		if( isFixed(file, name) ) {
			return;
		}
		List<ParameterRow> rows = new ArrayList<ParameterRow>();
		for( ParameterRow row : this.parameters ) {
			if( row.names(fileName, name) ) {
				rows.add(row);
			}
		}
		if( rows.isEmpty() ) {
			problems.add(file + " " + name + " = " + initializer + ": neither a parameter row of inventory.md nor a fixed constant of section 6");
			return;
		}
		for( ParameterRow row : rows ) {
			if( !this.keys.contains(row.key) ) {
				problems.add(file + " " + name + ": its parameter " + row.key + " is not in RecognitionParameters.ALL");
			}
		}
		Double value = evaluate(initializer);
		if( value != null ) {
			boolean equal = false;
			for( ParameterRow row : rows ) {
				equal |= sameInUnit(value, row.defaultValue);
			}
			if( !equal ) {
				problems.add(file + " " + name + " = " + initializer + ": differs from the default of its parameter row");
			}
		}
	}

	private boolean matchesParameter(String fileName, String name, double value) {
		for( ParameterRow row : this.parameters ) {
			if( row.names(fileName, name) && this.keys.contains(row.key) && sameInUnit(value, row.defaultValue) ) {
				return true;
			}
		}
		return false;
	}

	private boolean matchesDefaultOfFile(String fileName, double value) {
		for( ParameterRow row : this.parameters ) {
			if( row.namesFile(fileName) && this.keys.contains(row.key) && sameInUnit(value, row.defaultValue) ) {
				return true;
			}
		}
		return false;
	}

	/** The value equals the default in the parameter's unit, or that unit turned into milliseconds or nanoseconds (a {@code …_NANOS} constant). */
	public static boolean sameInUnit(double value, double defaultValue) {
		for( double factor : UNIT_FACTORS ) {
			double expected = defaultValue * factor;
			if( Math.abs(value - expected) <= 1e-9 * Math.max(1.0, Math.abs(expected)) ) {
				return true;
			}
		}
		return false;
	}

	/** A plain arithmetic of numbers ({@code 60L * 1_000_000L}), or null when the initializer names anything else. */
	static Double evaluate(String initializer) {
		String expression = initializer.replaceAll("\\((?:double|long|int|float)\\)", "");
		Matcher m = NUMBER.matcher(expression);
		StringBuilder plain = new StringBuilder();
		int last = 0;
		while( m.find() ) {
			plain.append(expression, last, m.start()).append(value(m.group(1)));
			last = m.end();
		}
		plain.append(expression.substring(last));
		String text = plain.toString().replaceAll("\\s", "");
		if( !text.matches("[0-9.eE+\\-*/()]+") ) {
			return null;
		}
		try {
			return new Arithmetic(text).parse();
		} catch( RuntimeException e ) {
			return null;
		}
	}

	/** The numeric value of a Java or Rust literal. */
	public static double value(String literal) {
		String text = literal.replace("_", "");
		if( text.startsWith("0x") || text.startsWith("0X") ) {
			return Long.parseLong(text.substring(2).replaceAll("[lL]$", ""), 16);
		}
		boolean decimal = text.contains(".") || text.contains("e") || text.contains("E");
		text = decimal ? text.replaceAll("[fFdD]$", "") : text.replaceAll("[lLfFdD]$", "");
		if( text.endsWith(".") ) {
			text = text.substring(0, text.length() - 1);
		}
		return Double.parseDouble(text);
	}

	/** A literal right after a unary minus ({@code (-70.0}, {@code = -3}) is negative; after a value ({@code x - 3}) it is not. */
	static double signed(String text, int at, double value) {
		int i = at - 1;
		while( i >= 0 && Character.isWhitespace(text.charAt(i)) ) {
			i--;
		}
		if( i < 0 || text.charAt(i) != '-' ) {
			return value;
		}
		int j = i - 1;
		while( j >= 0 && Character.isWhitespace(text.charAt(j)) ) {
			j--;
		}
		char before = j < 0 ? '(' : text.charAt(j);
		return Character.isLetterOrDigit(before) || before == '_' || before == ')' || before == ']' || before == '.' ? value : -value;
	}

	/** The source without its comments, strings and characters (each replaced by spaces, so positions and lines are kept). */
	public static String stripped(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int i = 0;
		int n = source.length();
		while( i < n ) {
			char c = source.charAt(i);
			int end;
			if( source.startsWith("//", i) ) {
				end = source.indexOf('\n', i);
				end = end < 0 ? n : end;
			} else if( source.startsWith("/*", i) ) {
				end = source.indexOf("*/", i + 2);
				end = end < 0 ? n : end + 2;
			} else if( c == '"' || c == '\'' ) {
				end = i + 1;
				while( end < n && source.charAt(end) != c ) {
					end += source.charAt(end) == '\\' ? 2 : 1;
				}
				end = Math.min(n, end + 1);
			} else {
				out.append(c);
				i++;
				continue;
			}
			for( int k = i; k < end; k++ ) {
				out.append(source.charAt(k) == '\n' ? '\n' : ' ');
			}
			i = end;
		}
		return out.toString();
	}

	public static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	/** The text from the heading that starts with {@code start} to the next heading that starts with {@code end} (or the end). */
	public static String between(String text, String start, String end) {
		int from = text.indexOf("\n" + start);
		if( from < 0 ) {
			throw new IllegalStateException("no heading " + start);
		}
		int to = end == null ? -1 : text.indexOf("\n" + end, from + 1);
		return text.substring(from, to < 0 ? text.length() : to);
	}

	public static String[] cells(String row) {
		String inner = row.trim();
		inner = inner.substring(1, inner.length() - 1);
		String[] cells = inner.split("\\|", -1);
		for( int i = 0; i < cells.length; i++ ) {
			cells[i] = cells[i].trim();
		}
		return cells;
	}

	private static boolean inside(List<int[]> spans, int at) {
		for( int[] span : spans ) {
			if( span[0] <= at && at < span[1] ) {
				return true;
			}
		}
		return false;
	}

	private static int closingBrace(String text, int open) {
		int depth = 0;
		for( int i = open; i < text.length(); i++ ) {
			char c = text.charAt(i);
			if( c == '{' ) {
				depth++;
			} else if( c == '}' && --depth == 0 ) {
				return i;
			}
		}
		return text.length();
	}

	private static int lineOf(String text, int at) {
		int lines = 0;
		for( int i = 0; i < at; i++ ) {
			lines += text.charAt(i) == '\n' ? 1 : 0;
		}
		return lines;
	}

	/** A tiny parser of + - * / and brackets, enough for a constant's initializer. */
	private static final class Arithmetic {
		private final String text;
		private int at;

		Arithmetic(String text) {
			this.text = text;
		}

		double parse() {
			double value = sum();
			if( this.at != this.text.length() ) {
				throw new IllegalArgumentException(this.text);
			}
			return value;
		}

		private double sum() {
			double value = product();
			while( this.at < this.text.length() && (peek() == '+' || peek() == '-') ) {
				char op = this.text.charAt(this.at++);
				double right = product();
				value = op == '+' ? value + right : value - right;
			}
			return value;
		}

		private double product() {
			double value = unary();
			while( this.at < this.text.length() && (peek() == '*' || peek() == '/') ) {
				char op = this.text.charAt(this.at++);
				double right = unary();
				value = op == '*' ? value * right : value / right;
			}
			return value;
		}

		private double unary() {
			if( peek() == '-' ) {
				this.at++;
				return -unary();
			}
			if( peek() == '(' ) {
				this.at++;
				double value = sum();
				this.at++;
				return value;
			}
			int start = this.at;
			while( this.at < this.text.length() && (Character.isDigit(peek()) || peek() == '.' || peek() == 'e' || peek() == 'E'
					|| ((peek() == '-' || peek() == '+') && this.at > start && (this.text.charAt(this.at - 1) == 'e' || this.text.charAt(this.at - 1) == 'E'))) ) {
				this.at++;
			}
			return Double.parseDouble(this.text.substring(start, this.at));
		}

		private char peek() {
			return this.at < this.text.length() ? this.text.charAt(this.at) : '\0';
		}
	}
}
