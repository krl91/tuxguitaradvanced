package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The two engines on the datasets (spec 023 T037, SC-001, SC-002 offline, SC-004, FR-002, FR-007, FR-013): it reads the dataset reports of the Java engine
 * before the spec ({@code benchmark/before/}) and of both engines after it ({@code after-java/}, {@code after-native/}, each a copy of
 * {@code target/dataset-benchmark/} plus the bass report), and writes {@code benchmark/comparison.md}: every SC-001 line (before, Java after, native after,
 * pass or fail), the tests OK with Java and KO with the native engine, the offline delay lines of SC-002 and the SC-004 targets (reached or not).
 *
 * <p>It fails on any SC-001 line lower with the native engine than before, on any SC-002 offline line not met, on any test OK with Java and KO with the
 * native engine, and when a Java outcome file differs from {@code before/} (FR-007). Run after quickstart §3, with {@code -Dgroups=benchmark
 * -DexcludedGroups=}.</p>
 */
@Tag("benchmark")
public class EngineComparisonReportTest {

	private static final Path BENCHMARK = Paths.get("../../specs/023-native-recognition-engine/benchmark");
	private static final String BASS = "bass-recognition-2026-10-08.txt";
	private static final String[] OUTCOMES = { "outcomes.csv", "guitarset-chords-outcomes.csv" };

	private static final Pattern COUNTS = Pattern.compile("^\\s+(.+?)\\s+OK\\s+(\\d+)\\s+KO\\s+(\\d+)\\s+\\(");
	private static final Pattern GUITARSET = Pattern.compile("^\\s+(complete chord, [^\\n]+?)\\s+accepted (\\d+) / (\\d+) .*?delay median (\\d+) ms");
	private static final Pattern OWNER = Pattern.compile("^(\\d+) of (\\d+) takes accepted .*; (\\d+) of (\\d+) other chords wrongly accepted");
	private static final Pattern TECHNIQUE = Pattern.compile("^\\s+detected on (\\d+)% of (\\d+) (\\w+) notes .*wrongly on ([\\d.,]+)% of");
	private static final Pattern BASS_ROW = Pattern.compile("^\\| (\\S+) \\| (\\d+) \\| \\d+ \\| (\\d+)% \\|");

	/** One compared figure: higher is better unless {@code lowerIsBetter}. */
	private static final class Line {
		final String name;
		final double before;
		final double java;
		final double nativeAfter;
		final boolean lowerIsBetter;

		Line(String name, double before, double java, double nativeAfter, boolean lowerIsBetter) {
			this.name = name;
			this.before = before;
			this.java = java;
			this.nativeAfter = nativeAfter;
			this.lowerIsBetter = lowerIsBetter;
		}

		boolean pass() {
			return (this.lowerIsBetter ? this.nativeAfter <= this.before : this.nativeAfter >= this.before);
		}
	}

	@Test
	public void theNativeEngineIsAtLeastTheJavaEngineOnEveryLine() throws IOException {
		Path before = BENCHMARK.resolve("before");
		Path java = BENCHMARK.resolve("after-java");
		Path nativeAfter = BENCHMARK.resolve("after-native");
		for( Path folder : new Path[] { before, java, nativeAfter } ) {
			assertTrue(Files.isRegularFile(folder.resolve("report.txt")), "missing " + folder + "/report.txt: run quickstart §3 first");
		}
		List<String> failures = new ArrayList<String>();
		List<Line> lines = new ArrayList<Line>();

		// SC-001: every line of the dataset report (single notes, wrong notes, chords, incomplete chords, a tone higher, each effect and guitar)
		Map<String, double[]> b = counts(before.resolve("report.txt"));
		Map<String, double[]> j = counts(java.resolve("report.txt"));
		Map<String, double[]> n = counts(nativeAfter.resolve("report.txt"));
		for( Map.Entry<String, double[]> entry : b.entrySet() ) {
			lines.add(new Line(entry.getKey() + " (% OK)", share(entry.getValue()), share(j.get(entry.getKey())), share(n.get(entry.getKey())), false));
		}
		// SC-001: GuitarSet complete chords, and the owner's chords
		Map<String, double[]> gb = guitarSet(before.resolve("guitarset-chords-report.txt"));
		Map<String, double[]> gj = guitarSet(java.resolve("guitarset-chords-report.txt"));
		Map<String, double[]> gn = guitarSet(nativeAfter.resolve("guitarset-chords-report.txt"));
		for( String name : gb.keySet() ) {
			lines.add(new Line("GuitarSet " + name + " accepted", gb.get(name)[0], value(gj.get(name), 0), value(gn.get(name), 0), false));
		}
		double[] ob = owner(before.resolve("owner-chords.txt"));
		double[] oj = owner(java.resolve("owner-chords.txt"));
		double[] on = owner(nativeAfter.resolve("owner-chords.txt"));
		lines.add(new Line("Owner's chords accepted (of 42)", ob[0], oj[0], on[0], false));
		lines.add(new Line("Owner's neighbouring chords wrongly accepted (of 42)", ob[1], oj[1], on[1], true));
		// SC-001: the technique and bass benchmarks
		Map<String, double[]> tb = technique(before.resolve("technique-report.txt"));
		Map<String, double[]> tj = technique(java.resolve("technique-report.txt"));
		Map<String, double[]> tn = technique(nativeAfter.resolve("technique-report.txt"));
		for( String name : tb.keySet() ) {
			lines.add(new Line("Technique " + name + " detected (%)", tb.get(name)[0], value(tj.get(name), 0), value(tn.get(name), 0), false));
			lines.add(new Line("Technique " + name + " wrongly detected on plain notes (%)", tb.get(name)[1], value(tj.get(name), 1), value(tn.get(name), 1), true));
		}
		Map<String, double[]> bb = bass(before.resolve(BASS));
		Map<String, double[]> bj = bass(java.resolve(BASS));
		Map<String, double[]> bn = bass(nativeAfter.resolve(BASS));
		for( String name : bb.keySet() ) {
			lines.add(new Line("Bass " + name + " right (%)", bb.get(name)[0], value(bj.get(name), 0), value(bn.get(name), 0), false));
		}
		for( Line line : lines ) {
			if( !line.pass() ) {
				failures.add("SC-001 " + line.name + ": native " + format(line.nativeAfter) + ", before " + format(line.before));
			}
		}

		// SC-001: no test OK with Java and KO with the native engine
		// a recording can hold several tests of the same kind (one per pair of notes): the OK of each test line are counted, not the last status kept
		Map<String, int[]> javaOutcomes = outcomes(java.resolve("outcomes.csv"));
		Map<String, int[]> nativeOutcomes = outcomes(nativeAfter.resolve("outcomes.csv"));
		List<String> okToKo = new ArrayList<String>();
		for( Map.Entry<String, int[]> entry : javaOutcomes.entrySet() ) {
			int[] other = nativeOutcomes.get(entry.getKey());
			int lost = entry.getValue()[0] - (other != null ? other[0] : 0);
			for( int i = 0 ; i < lost ; i ++ ) {
				okToKo.add(entry.getKey());
			}
		}
		if( !okToKo.isEmpty() ) {
			failures.add("SC-001 " + okToKo.size() + " tests OK with Java are KO with the native engine");
		}

		// SC-002 offline: the GuitarSet chord delay
		List<String> delays = new ArrayList<String>();
		String[][] limits = { { "complete chord, six string channels summed", "103" }, { "complete chord, microphone", "149" } };
		for( String[] limit : limits ) {
			double javaDelay = value(gj.get(limit[0]), 2);
			double nativeDelay = value(gn.get(limit[0]), 2);
			double max = Double.parseDouble(limit[1]);
			boolean pass = nativeDelay <= max && nativeDelay <= javaDelay;
			delays.add("| " + limit[0] + " | " + format(value(gb.get(limit[0]), 2)) + " ms | " + format(javaDelay) + " ms | " + format(nativeDelay) + " ms | ≤ " + limit[1]
					+ " ms and ≤ Java | " + (pass ? "pass" : "FAIL") + " |");
			if( !pass ) {
				failures.add("SC-002 offline delay median, " + limit[0] + ": native " + format(nativeDelay) + " ms, Java " + format(javaDelay) + " ms, limit " + limit[1] + " ms");
			}
		}

		// FR-007: the Java engine's outcome files unchanged
		List<String> identity = new ArrayList<String>();
		for( String file : OUTCOMES ) {
			boolean same = sortedLines(before.resolve(file)).equals(sortedLines(java.resolve(file)));
			identity.add("| " + file + " | " + (same ? "identical" : "DIFFERENT") + " |");
			if( !same ) {
				failures.add("FR-007 the Java engine's " + file + " differs from before/");
			}
		}

		// SC-004: the chord targets, aimed at, not blocking
		List<String> targets = new ArrayList<String>();
		target(targets, "Owner's chords accepted ≥ 34/42", on[0], on[0] >= 34);
		target(targets, "Owner's neighbours wrongly accepted ≤ 6/42", on[1], on[1] <= 6);
		target(targets, "GuitarSet complete chords ≥ 40/56 summed", value(gn.get(limits[0][0]), 0), value(gn.get(limits[0][0]), 0) >= 40);
		target(targets, "GuitarSet complete chords ≥ 30/56 microphone", value(gn.get(limits[1][0]), 0), value(gn.get(limits[1][0]), 0) >= 30);
		String toneHigher = "IDMT-SMT-Audio-Effects poly | chord a whole tone higher refused";
		double toneHigherShare = share(n.get(toneHigher));
		target(targets, "Chord a whole tone higher refused ≥ 85 % (Audio-Effects)", toneHigherShare, toneHigherShare >= 85.0);

		write(lines, okToKo, delays, identity, targets, failures);
		assertTrue(failures.isEmpty(), String.join("\n", failures));
	}

	private static void target(List<String> targets, String name, double value, boolean reached) {
		targets.add("| " + name + " | " + format(value) + " | " + (reached ? "reached" : "not reached") + " |");
	}

	private static void write(List<Line> lines, List<String> okToKo, List<String> delays, List<String> identity, List<String> targets, List<String> failures)
			throws IOException {
		StringBuilder text = new StringBuilder();
		text.append("# Java and native engines on the datasets (spec 023)\n\n");
		text.append("Written by `EngineComparisonReportTest` from `before/` (the Java engine before the spec), `after-java/` and `after-native/`.\n");
		text.append("Result: ").append(failures.isEmpty() ? "every line passes." : failures.size() + " failing line(s).").append("\n\n");
		text.append("## SC-001: the native engine at least the baseline on each line\n\n");
		text.append("| Line | Before | Java after | Native after | Native |\n|---|---|---|---|---|\n");
		for( Line line : lines ) {
			text.append("| ").append(line.name.replace("|", "/")).append(" | ").append(format(line.before)).append(" | ").append(format(line.java)).append(" | ")
					.append(format(line.nativeAfter)).append(" | ").append(line.pass() ? "pass" : "FAIL").append(" |\n");
		}
		text.append("\n## SC-001: tests OK with Java and KO with the native engine\n\n");
		if( okToKo.isEmpty() ) {
			text.append("None.\n");
		}
		for( String test : okToKo ) {
			text.append("- ").append(test).append("\n");
		}
		text.append("\n## SC-002: offline delay, GuitarSet chords (median)\n\n");
		text.append("| Line | Before | Java after | Native after | Limit | Native |\n|---|---|---|---|---|---|\n");
		for( String delay : delays ) {
			text.append(delay).append("\n");
		}
		text.append("\n## FR-007: the Java engine's outcome files against before/\n\n| File | Java after |\n|---|---|\n");
		for( String line : identity ) {
			text.append(line).append("\n");
		}
		text.append("\n## SC-004: chord targets of the native engine (aimed at, not blocking)\n\n| Target | Native | Status |\n|---|---|---|\n");
		for( String line : targets ) {
			text.append(line).append("\n");
		}
		if( !failures.isEmpty() ) {
			text.append("\n## Failures\n\n");
			for( String failure : failures ) {
				text.append("- ").append(failure).append("\n");
			}
		}
		Files.write(BENCHMARK.resolve("comparison.md"), text.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String format(double value) {
		if( Double.isNaN(value) ) {
			return "missing";
		}
		return (value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value));
	}

	private static double share(double[] okKo) {
		if( okKo == null ) {
			return Double.NaN;
		}
		double total = okKo[0] + okKo[1];
		return (total > 0 ? Math.round(1000.0 * okKo[0] / total) / 10.0 : 100.0);
	}

	private static double value(double[] values, int index) {
		return (values != null ? values[index] : Double.NaN);
	}

	private static List<String> read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "missing " + file + ": run quickstart §3 first");
		return Files.readAllLines(file, StandardCharsets.UTF_8);
	}

	/** The lines "name OK n KO m" of the dataset report, the first of each name kept. */
	private static Map<String, double[]> counts(Path file) throws IOException {
		Map<String, double[]> counts = new LinkedHashMap<String, double[]>();
		for( String line : read(file) ) {
			Matcher m = COUNTS.matcher(line);
			if( m.find() && !counts.containsKey(m.group(1)) ) {
				counts.put(m.group(1), new double[] { Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)) });
			}
		}
		return counts;
	}

	/** The complete-chord lines of the GuitarSet report: accepted, chords, delay median in ms. */
	private static Map<String, double[]> guitarSet(Path file) throws IOException {
		Map<String, double[]> lines = new LinkedHashMap<String, double[]>();
		for( String line : read(file) ) {
			Matcher m = GUITARSET.matcher(line);
			if( m.find() && !lines.containsKey(m.group(1)) ) {
				lines.put(m.group(1), new double[] { Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) });
			}
		}
		return lines;
	}

	/** The owner's chords: accepted, wrongly accepted. */
	private static double[] owner(Path file) throws IOException {
		for( String line : read(file) ) {
			Matcher m = OWNER.matcher(line);
			if( m.find() ) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(3)) };
			}
		}
		return new double[] { Double.NaN, Double.NaN };
	}

	/** Each technique: detected %, wrongly detected % on plain notes. */
	private static Map<String, double[]> technique(Path file) throws IOException {
		Map<String, double[]> lines = new LinkedHashMap<String, double[]>();
		for( String line : read(file) ) {
			Matcher m = TECHNIQUE.matcher(line);
			if( m.find() ) {
				lines.put(m.group(3), new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(4).replace(',', '.')) });
			}
		}
		return lines;
	}

	/** Each bass note: right %. */
	private static Map<String, double[]> bass(Path file) throws IOException {
		Map<String, double[]> lines = new LinkedHashMap<String, double[]>();
		for( String line : read(file) ) {
			Matcher m = BASS_ROW.matcher(line);
			if( m.find() ) {
				lines.put(m.group(1) + " (midi " + m.group(2) + ")", new double[] { Double.parseDouble(m.group(3)) });
			}
		}
		return lines;
	}

	/** The outcomes file: for each test (every column before the status), how many of its lines are OK and how many KO. */
	private static Map<String, int[]> outcomes(Path file) throws IOException {
		Map<String, int[]> outcomes = new LinkedHashMap<String, int[]>();
		List<String> lines = read(file);
		Pattern status = Pattern.compile("^(.*),(OK|KO|NOT_RUN),");
		for( String line : lines.subList(1, lines.size()) ) {
			Matcher m = status.matcher(line);
			if( m.find() ) {
				int[] counts = outcomes.computeIfAbsent(m.group(1), key -> new int[2]);
				if( "OK".equals(m.group(2)) ) {
					counts[0]++;
				} else if( "KO".equals(m.group(2)) ) {
					counts[1]++;
				}
			}
		}
		return outcomes;
	}

	private static List<String> sortedLines(Path file) throws IOException {
		List<String> lines = new ArrayList<String>(read(file));
		Collections.sort(lines);
		return lines;
	}
}
