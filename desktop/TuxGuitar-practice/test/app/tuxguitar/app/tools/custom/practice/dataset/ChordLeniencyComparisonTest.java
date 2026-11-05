package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The guards of spec 024 after the chord rule by profile (research R8, task T027; FR-003, FR-004, FR-011, SC-003): it reads the reports measured
 * before (spec 023's code) and after, with each engine, in {@code specs/024-lenient-chords/benchmark/{before,after}-{java,native}/}, writes
 * {@code benchmark/comparison.md} (each guard line, before and after, pass or fail; the other lines reported) and fails on any guard line lower.
 *
 * <p>Two lines are allowed lower, with a floor (spec Clarifications, the owner's option B of 2026-11-05): the incomplete chords refused (at least 20
 * of 44) and the IDMT-SMT-Audio-Effects chords recognised (at least 85 %). The reports are made by the five committed dataset benchmarks (see the
 * quickstart); the test is skipped when they are absent. Run with {@code -Dgroups=benchmark -DexcludedGroups=}.</p>
 */
@Tag("benchmark")
public class ChordLeniencyComparisonTest {

	private static final Path BENCHMARK = Paths.get("../../specs/024-lenient-chords/benchmark");
	private static final Pattern LINE = Pattern.compile("^\\s+(.+?)\\s+OK\\s+(\\d+)\\s+KO\\s+(\\d+)\\s+\\(.*$");
	private static final Pattern GUITARSET = Pattern.compile("^\\s+(complete chord, [^\\n]+?)\\s+accepted (\\d+) / (\\d+) \\(.*$");

	/** OK and total of a line. */
	private static final class Count {
		final int ok;
		final int total;

		Count(int ok, int total) {
			this.ok = ok;
			this.total = total;
		}

		double share() {
			return (this.total == 0 ? 1.0 : (double) this.ok / this.total);
		}

		@Override
		public String toString() {
			return this.ok + " / " + this.total;
		}
	}

	/** The OK/KO lines of the dataset report: the run tests and, prefixed with "played:", what was played. */
	private static Map<String, Count> dataset(Path folder) throws IOException {
		Map<String, Count> lines = new LinkedHashMap<String, Count>();
		String section = "";
		for( String line : Files.readAllLines(folder.resolve("report.txt"), StandardCharsets.UTF_8) ) {
			if( !line.startsWith(" ") ) {
				section = line;
				continue;
			}
			Matcher m = LINE.matcher(line);
			if( m.matches() && (section.startsWith("Run tests") || section.startsWith("Recognised when played")) ) {
				int ok = Integer.parseInt(m.group(2));
				lines.put((section.startsWith("Run tests") ? "" : "played: ") + m.group(1).trim(), new Count(ok, ok + Integer.parseInt(m.group(3))));
			}
		}
		return lines;
	}

	private static Map<String, Count> guitarSet(Path folder) throws IOException {
		Map<String, Count> lines = new LinkedHashMap<String, Count>();
		for( String line : Files.readAllLines(folder.resolve("guitarset-chords-report.txt"), StandardCharsets.UTF_8) ) {
			Matcher m = GUITARSET.matcher(line);
			if( m.matches() && !lines.containsKey(m.group(1).trim()) ) {
				lines.put(m.group(1).trim(), new Count(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
			}
		}
		return lines;
	}

	/** The takes of each series of the owner accepted for their own chord. */
	private static Map<String, Count> owner(Path folder) throws IOException {
		Map<String, Count> lines = new LinkedHashMap<String, Count>();
		for( String line : Files.readAllLines(folder.resolve("owner-chords.txt"), StandardCharsets.UTF_8) ) {
			int slash = line.indexOf('/');
			if( slash > 0 && line.contains("own chord") ) {
				String series = line.substring(0, slash);
				Count before = lines.getOrDefault(series, new Count(0, 0));
				lines.put(series, new Count(before.ok + (line.contains("own chord ACCEPTED") ? 1 : 0), before.total + 1));
			}
		}
		return lines;
	}

	/** The text of a report without the line naming the code revision. */
	private static String text(Path file) throws IOException {
		StringBuilder text = new StringBuilder();
		for( String line : Files.readAllLines(file, StandardCharsets.UTF_8) ) {
			if( !line.startsWith("Code revision") ) {
				text.append(line).append('\n');
			}
		}
		return text.toString();
	}

	private static boolean singleNoteLine(String key) {
		return key.contains("| wrong") || key.contains("| second note") || key.contains("| legato") || key.endsWith("| single note");
	}

	@Test
	public void noGuardLineIsLowerThanBefore() throws IOException {
		assumeTrue(Files.isDirectory(BENCHMARK.resolve("after-java")) && Files.isDirectory(BENCHMARK.resolve("after-native")),
				"the reports before and after are not here (see the quickstart)");
		StringBuilder md = new StringBuilder("# Spec 024: the guards before and after\n\n");
		md.append("Written by `ChordLeniencyComparisonTest` (task T027, research R8) from `before-{java,native}/` (spec 023's code) and ")
				.append("`after-{java,native}/` (the chord rule by profile, *Normal*). A guard line fails when it is lower than before; the two lines ")
				.append("the spec allows lower (the owner's option B, 2026-11-05) fail under their floor only.\n");
		List<String> failures = new ArrayList<String>();
		for( String engine : new String[] { "java", "native" } ) {
			Path before = BENCHMARK.resolve("before-" + engine);
			Path after = BENCHMARK.resolve("after-" + engine);
			md.append("\n## Engine ").append(engine).append("\n\n| Line | Before | After | Guard | Verdict |\n|---|---|---|---|---|\n");
			Map<String, Count> b = dataset(before);
			Map<String, Count> a = dataset(after);
			int incompleteOk = 0;
			int incompleteTotal = 0;
			int incompleteBefore = 0;
			List<String> reported = new ArrayList<String>();
			for( Map.Entry<String, Count> entry : a.entrySet() ) {
				String key = entry.getKey();
				Count now = entry.getValue();
				Count then = b.getOrDefault(key, new Count(0, 0));
				String guard;
				boolean pass;
				if( key.contains("| incomplete chord") ) {
					incompleteOk += now.ok;
					incompleteTotal += now.total;
					incompleteBefore += then.ok;
					reported.add(row(key, then, now, "summed below", "reported"));
					continue;
				} else if( key.contains("| chord a whole tone higher") ) {
					guard = "at least 85 %";
					pass = now.share() >= 0.85;
				} else if( key.equals("IDMT-SMT-Audio-Effects poly | recognised when played") ) {
					guard = "at least 85 % (allowed lower, SC-003)";
					pass = now.share() >= 0.85;
				} else if( singleNoteLine(key) ) {
					guard = "not lower";
					pass = now.share() >= then.share();
				} else {
					reported.add(row(key, then, now, "reported", "-"));
					continue;
				}
				md.append(row(key, then, now, guard, pass ? "pass" : "**FAIL**"));
				if( !pass ) {
					failures.add(engine + ": " + key + " " + then + " -> " + now);
				}
			}
			boolean incomplete = incompleteOk >= 20;
			md.append(row("incomplete chords refused, both IDMT datasets", new Count(incompleteBefore, incompleteTotal), new Count(incompleteOk, incompleteTotal),
					"at least 20 of 44 (allowed lower, SC-003)", incomplete ? "pass" : "**FAIL**"));
			if( !incomplete ) {
				failures.add(engine + ": incomplete chords refused " + incompleteOk + " / " + incompleteTotal);
			}
			Map<String, Count> gb = guitarSet(before);
			for( Map.Entry<String, Count> entry : guitarSet(after).entrySet() ) {
				Count then = gb.getOrDefault(entry.getKey(), new Count(0, 0));
				boolean pass = entry.getValue().ok >= then.ok;
				md.append(row("GuitarSet " + entry.getKey(), then, entry.getValue(), "not lower", pass ? "pass" : "**FAIL**"));
				if( !pass ) {
					failures.add(engine + ": GuitarSet " + entry.getKey());
				}
			}
			Map<String, Count> ob = owner(before);
			for( Map.Entry<String, Count> entry : owner(after).entrySet() ) {
				int minimum = (entry.getKey().equals("owner-chords") ? 8 : 18);
				boolean pass = entry.getValue().ok >= minimum;
				md.append(row(entry.getKey() + ", own chord accepted", ob.getOrDefault(entry.getKey(), new Count(0, 0)), entry.getValue(), "at least " + minimum,
						pass ? "pass" : "**FAIL**"));
				if( !pass ) {
					failures.add(engine + ": " + entry.getKey());
				}
			}
			for( String report : new String[] { "technique-report.txt", "bass-recognition-2026-10-08.txt" } ) {
				boolean same = text(before.resolve(report)).equals(text(after.resolve(report)));
				md.append("| ").append(report).append(" | | | unchanged | ").append(same ? "pass" : "**FAIL**").append(" |\n");
				if( !same ) {
					failures.add(engine + ": " + report + " changed");
				}
			}
			md.append("\nReported, not guarded:\n\n| Line | Before | After | | |\n|---|---|---|---|---|\n");
			for( String row : reported ) {
				md.append(row);
			}
		}
		md.append("\nKnown limit (research R8): IDMT-SMT-Audio-Effects \"minor triad\" is one recording, G2 B♭2 D3, whose low semitone bands leak into ")
				.append("their neighbours; the other 29 triads of the NoFX folder are accepted (`stop-t027/`).\n");
		md.append(failures.isEmpty() ? "\nVerdict: every guard holds with both engines.\n" : "\nVerdict: " + failures.size() + " guard line(s) lower.\n");
		Files.write(BENCHMARK.resolve("comparison.md"), md.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(md);
		assertEquals(new ArrayList<String>(), failures, "no guard line is lower than before");
	}

	private static String row(String line, Count before, Count after, String guard, String verdict) {
		return "| " + line + " | " + before + " | " + after + " | " + guard + " | " + verdict + " |\n";
	}
}
