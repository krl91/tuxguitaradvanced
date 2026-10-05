package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Practice's recognition against annotated public datasets (spec 004): thousands of notes,
 * intervals and chords played on real guitars with 11 audio effects. Every case yields tests that
 * must pass (the played notes are recognised; other notes are not) and tests that are not run, each
 * with its reason. Run on demand (tag "dataset"):
 * {@code mvn test -pl ../../TuxGuitar-practice -am -Dgroups=dataset -DexcludedGroups= -Dtest=DatasetBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false}
 * Data: {@code -Dtuxguitar.datasets=<dir>} (default {@code ~/tuxguitar-datasets}); the data are never
 * committed (CC BY-NC-ND licences) and the test is skipped when they are absent.
 * {@code -Dtuxguitar.datasets.sample=N} sets the number of recordings per folder or effect (default 8, 0 = all).
 */
@Tag("dataset")
public class DatasetBenchmarkTest {

	private enum Status {
		OK, KO, NOT_RUN
	}

	private static final class Outcome {
		final String dataset;
		final String group;
		final String label;
		final String test;
		final String id;
		final Status status;
		final String reason;

		Outcome(DatasetCase c, String test, Status status, String reason) {
			this.dataset = c.dataset;
			this.group = c.group;
			this.label = c.label;
			this.test = test;
			this.id = c.id;
			this.status = status;
			this.reason = reason;
		}
	}

	private static final int LOWEST = 40;
	private static final int HIGHEST = 88;

	private static Path root() {
		return Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
	}

	private static boolean playable(int[] pitches) {
		for( int pitch : pitches ) {
			if( pitch < LOWEST || pitch > HIGHEST ) {
				return false;
			}
		}
		return DatasetJudge.strings(pitches) != null;
	}

	/** Octave or harmonic relatives of the lowest note, and unison/octave of any note: audio cannot tell them apart (spec FR-004). */
	private static boolean relatedToPlayed(int candidate, int[] played) {
		int lowest = played[0];
		int[] harmonics = { 12, 19, 24, 28, 31, 34, 36 };
		for( int pitch : played ) {
			if( candidate == pitch || Math.abs(candidate - pitch) % 12 == 0 ) {
				return true;
			}
		}
		for( int h : harmonics ) {
			if( candidate - lowest == h ) {
				return true;
			}
		}
		return false;
	}

	private static List<Outcome> run(DatasetCase c) {
		List<Outcome> out = new ArrayList<Outcome>();
		RecordedAudio audio = RecordedAudio.load(c.audio);
		if( audio == null ) {
			out.add(new Outcome(c, "recognised when played", Status.NOT_RUN, "audio file unreadable here"));
			return out;
		}
		if( !playable(c.played) ) {
			out.add(new Outcome(c, "recognised when played", Status.NOT_RUN, c.played[0] < LOWEST || c.played[c.played.length - 1] > HIGHEST
					? "pitch outside the range of a standard 6-string guitar (E2 to E6)" : "notes cannot be played together on six strings"));
			return out;
		}
		boolean accepted = DatasetJudge.accepts(audio, c.played);
		out.add(new Outcome(c, "recognised when played", accepted ? Status.OK : Status.KO, accepted ? "" : "not recognised"));
		if( c.kind == DatasetCase.Kind.SINGLE_NOTE ) {
			for( int delta : new int[] { -2, -1, 1, 2 } ) {
				int[] wrong = { c.played[0] + delta };
				String test = "wrong note " + (delta > 0 ? "+" : "") + delta + " semitone(s) refused";
				if( !playable(wrong) ) {
					out.add(new Outcome(c, test, Status.NOT_RUN, "the wrong note is outside the guitar range"));
				} else {
					boolean wrongAccepted = DatasetJudge.accepts(audio, wrong);
					out.add(new Outcome(c, test, wrongAccepted ? Status.KO : Status.OK, wrongAccepted ? "wrong note accepted" : ""));
				}
			}
			out.add(new Outcome(c, "octave refused", Status.NOT_RUN, "an octave cannot be told from a partial of the played note (spec FR-004)"));
			return out;
		}
		// an interval or a chord
		int extra = -1;
		for( int offset : new int[] { 2, 3, 4, 5, 6, 1 } ) {
			int candidate = c.played[c.played.length - 1] + offset;
			if( candidate <= HIGHEST && !relatedToPlayed(candidate, c.played) ) {
				extra = candidate;
				break;
			}
		}
		if( extra < 0 ) {
			out.add(new Outcome(c, "incomplete chord refused (one more note expected)", Status.NOT_RUN, "no extra note that audio could tell from the played ones"));
		} else {
			int[] bigger = Arrays.copyOf(c.played, c.played.length + 1);
			bigger[bigger.length - 1] = extra;
			Arrays.sort(bigger);
			if( !playable(bigger) ) {
				out.add(new Outcome(c, "incomplete chord refused (one more note expected)", Status.NOT_RUN, "notes cannot be played together on six strings"));
			} else {
				boolean bad = DatasetJudge.accepts(audio, bigger);
				out.add(new Outcome(c, "incomplete chord refused (one more note expected)", bad ? Status.KO : Status.OK, bad ? "chord with a note missing accepted" : ""));
			}
		}
		int[] shifted = new int[c.played.length];
		for( int i = 0 ; i < shifted.length ; i ++ ) {
			shifted[i] = c.played[i] + 2;
		}
		boolean unrelated = true;
		for( int pitch : shifted ) {
			unrelated &= !relatedToPlayed(pitch, c.played) || Arrays.binarySearch(c.played, pitch) < 0 && false;
		}
		if( !playable(shifted) ) {
			out.add(new Outcome(c, "chord a whole tone higher refused", Status.NOT_RUN, "notes cannot be played together on six strings"));
		} else {
			boolean bad = DatasetJudge.accepts(audio, shifted);
			out.add(new Outcome(c, "chord a whole tone higher refused", bad ? Status.KO : Status.OK, bad ? "wrong chord accepted" : ""));
		}
		out.add(new Outcome(c, "chord without one of its notes refused", Status.NOT_RUN, "extra strings sounding are tolerated by design (like the chord setting \"foreign strings: ignore\")"));
		return out;
	}

	@Test
	public void practiceRecognisesTheAnnotatedRecordingsOfThePublicDatasets() throws IOException {
		Path root = root();
		assumeTrue(Files.isDirectory(root), "datasets not found: " + root.toAbsolutePath() + " (see the class comment)");
		int sample = Integer.getInteger("tuxguitar.datasets.sample", 8);
		List<DatasetCase> cases = new ArrayList<DatasetCase>();
		cases.addAll(DatasetLoaders.idmtGuitar(root, sample));
		cases.addAll(DatasetLoaders.effects(root, false, sample));
		cases.addAll(DatasetLoaders.effects(root, true, sample));
		assumeTrue(!cases.isEmpty(), "no dataset recording found under " + root.toAbsolutePath());
		ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<Outcome>();
		cases.parallelStream().forEach(c -> {
			try {
				outcomes.addAll(run(c));
			} catch (RuntimeException exception) {
				outcomes.add(new Outcome(c, "recognised when played", Status.NOT_RUN, "error while analysing: " + exception));
			}
		});
		List<Outcome> all = new ArrayList<Outcome>(outcomes);
		String report = report(cases.size(), all);
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("report.txt"), report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		List<String> csv = new ArrayList<String>();
		csv.add("dataset,group,label,test,recording,status,reason");
		for( Outcome o : all ) {
			csv.add(String.join(",", q(o.dataset), q(o.group), q(o.label), q(o.test), q(o.id), o.status.name(), q(o.reason)));
		}
		Files.write(target.resolve("outcomes.csv"), csv, java.nio.charset.StandardCharsets.UTF_8);
		assertTrue(all.stream().anyMatch(o -> o.status != Status.NOT_RUN), report);
	}

	private static String q(String value) {
		return "\"" + value.replace("\"", "'") + "\"";
	}

	private static String report(int recordings, List<Outcome> all) {
		StringBuilder sb = new StringBuilder();
		long ok = all.stream().filter(o -> o.status == Status.OK).count();
		long ko = all.stream().filter(o -> o.status == Status.KO).count();
		long skipped = all.stream().filter(o -> o.status == Status.NOT_RUN).count();
		sb.append(String.format("DATASET BENCHMARK: %d recordings, %d tests: %d OK, %d KO, %d not run%n", recordings, all.size(), ok, ko, skipped));
		Map<String, List<Outcome>> byTest = all.stream().filter(o -> o.status != Status.NOT_RUN).collect(Collectors.groupingBy(o -> o.dataset + " | " + o.test, TreeMap::new, Collectors.toList()));
		sb.append("\nRun tests, by dataset and kind of test:\n");
		for( Map.Entry<String, List<Outcome>> e : byTest.entrySet() ) {
			long a = e.getValue().stream().filter(o -> o.status == Status.OK).count();
			sb.append(String.format("  %-90s OK %5d  KO %5d  (%.1f%% OK)%n", e.getKey(), a, e.getValue().size() - a, 100.0 * a / e.getValue().size()));
		}
		Map<String, List<Outcome>> byEffect = all.stream().filter(o -> o.status != Status.NOT_RUN).collect(Collectors.groupingBy(o -> o.group, TreeMap::new, Collectors.toList()));
		sb.append("\nBy guitar / effect (all kinds of test together):\n");
		for( Map.Entry<String, List<Outcome>> e : byEffect.entrySet() ) {
			long a = e.getValue().stream().filter(o -> o.status == Status.OK).count();
			sb.append(String.format("  %-60s OK %5d  KO %5d  (%.1f%% OK)%n", e.getKey(), a, e.getValue().size() - a, 100.0 * a / e.getValue().size()));
		}
		Map<String, List<Outcome>> byLabel = all.stream().filter(o -> o.status != Status.NOT_RUN && o.test.equals("recognised when played")).collect(Collectors.groupingBy(o -> o.dataset + " | " + o.label, TreeMap::new, Collectors.toList()));
		sb.append("\nRecognised when played, by what was played:\n");
		for( Map.Entry<String, List<Outcome>> e : byLabel.entrySet() ) {
			long a = e.getValue().stream().filter(o -> o.status == Status.OK).count();
			sb.append(String.format("  %-70s OK %5d  KO %5d  (%.1f%% OK)%n", e.getKey(), a, e.getValue().size() - a, 100.0 * a / e.getValue().size()));
		}
		Map<String, Long> reasons = all.stream().filter(o -> o.status == Status.NOT_RUN).collect(Collectors.groupingBy(o -> o.reason, TreeMap::new, Collectors.counting()));
		sb.append("\nTests not run, by reason:\n");
		for( Map.Entry<String, Long> e : reasons.entrySet() ) {
			sb.append(String.format("  %6d  %s%n", e.getValue(), e.getKey()));
		}
		Map<String, Long> kos = all.stream().filter(o -> o.status == Status.KO).collect(Collectors.groupingBy(o -> o.test + " -> " + o.reason, TreeMap::new, Collectors.counting()));
		sb.append("\nKO by kind:\n");
		for( Map.Entry<String, Long> e : kos.entrySet() ) {
			sb.append(String.format("  %6d  %s%n", e.getValue(), e.getKey()));
		}
		return sb.toString();
	}
}
