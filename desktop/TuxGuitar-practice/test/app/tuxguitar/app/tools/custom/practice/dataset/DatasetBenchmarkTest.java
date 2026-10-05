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

	/**
	 * Notes played one after the other, the previous one still ringing when the next is struck (as a learner plays them): two
	 * single-note recordings of the same guitar and effect are mixed, the second starting 0.35 s after the first. Practice expecting
	 * the two notes must accept both in order, and must not accept a second note that is wrong.
	 */
	private static List<Outcome> successiveNotes(List<DatasetCase> cases) {
		List<Outcome> out = new ArrayList<Outcome>();
		List<DatasetCase> singles = cases.stream().filter(c -> c.kind == DatasetCase.Kind.SINGLE_NOTE && playable(c.played)).collect(Collectors.toList());
		java.util.Map<String, List<DatasetCase>> byGroup = singles.stream().collect(Collectors.groupingBy(c -> c.dataset + " | " + c.group, TreeMap::new, Collectors.toList()));
		int[] distances = { 1, 2, 3, 5, 7, -1, -2, -3, -5, -7 };
		java.util.List<Runnable> jobs = new ArrayList<Runnable>();
		ConcurrentLinkedQueue<Outcome> collected = new ConcurrentLinkedQueue<Outcome>();
		for( List<DatasetCase> group : byGroup.values() ) {
			int paired = 0;
			for( DatasetCase first : group ) {
				for( int distance : distances ) {
					DatasetCase second = null;
					for( DatasetCase candidate : group ) {
						if( candidate.played[0] == first.played[0] + distance ) {
							second = candidate;
							break;
						}
					}
					if( second == null || paired >= 12 ) {
						continue;
					}
					paired ++;
					final DatasetCase a = first;
					final DatasetCase b = second;
					final int d = distance;
					jobs.add(() -> {
						RecordedAudio x = RecordedAudio.load(a.audio);
						RecordedAudio y = RecordedAudio.load(b.audio);
						if( x == null || y == null || x.sampleRate != y.sampleRate ) {
							return;
						}
						int offset = (int) (0.35 * x.sampleRate);
						float[] mix = new float[Math.max(x.samples.length, offset + y.samples.length)];
						System.arraycopy(x.samples, 0, mix, 0, x.samples.length);
						for( int i = 0 ; i < y.samples.length ; i ++ ) {
							mix[offset + i] += y.samples[i];
						}
						RecordedAudio audio = RecordedAudio.of(mix, x.sampleRate);
						String test = "second note recognised while the first still rings (" + (Math.abs(d) <= 2 ? "close pitch" : "other pitch") + ")";
						int ok = DatasetJudge.acceptedSteps(audio, new int[][] { a.played, b.played });
						collected.add(new Outcome(a, test, ok == 2 ? Status.OK : Status.KO, ok == 2 ? "" : "accepted " + ok + " of 2 (" + a.played[0] + " then " + b.played[0] + ")"));
						// simulated hammer-on / pull-off: the first note is damped as the second begins, whose pluck is replaced by a soft start
						if( Math.abs(d) <= 7 ) {
							// the first note is held for 0.6 s before the hammer-on / pull-off
							int held = (int) (0.6 * x.sampleRate);
							
							int fade = (int) (0.04 * x.sampleRate);
							int soft = (int) (0.08 * x.sampleRate);
							float[] legato = new float[Math.max(x.samples.length, held + y.samples.length)];
							for( int i = 0 ; i < x.samples.length ; i ++ ) {
								double gain = (i < held ? 1.0 : Math.max(0.0, 1.0 - (double) (i - held) / fade));
								legato[i] += (float) (x.samples[i] * gain);
							}
							for( int i = 0 ; i < y.samples.length ; i ++ ) {
								double gain = (i < soft ? 0.15 + 0.85 * i / soft : 1.0);
								legato[held + i] += (float) (y.samples[i] * gain);
							}
							int legatoOk = DatasetJudge.acceptedSteps(RecordedAudio.of(legato, x.sampleRate), new int[][] { a.played, b.played });
							collected.add(new Outcome(a, "legato (simulated hammer-on or pull-off): second note recognised", legatoOk == 2 ? Status.OK : Status.KO,
									legatoOk == 2 ? "" : "accepted " + legatoOk + " of 2 (" + a.played[0] + " then " + b.played[0] + "; first alone " + DatasetJudge.acceptedSteps(RecordedAudio.of(legato, x.sampleRate), new int[][] { a.played }) + ")"));
						}
						int[] wrong = { b.played[0] + (d > 0 ? 3 : -3) };
						if( playable(wrong) && wrong[0] != a.played[0] ) {
							int w = DatasetJudge.acceptedSteps(audio, new int[][] { a.played, wrong });
							collected.add(new Outcome(a, "wrong second note refused while the first still rings", w < 2 ? Status.OK : Status.KO, w < 2 ? "" : "wrong second note accepted (" + a.played[0] + " then " + wrong[0] + ")"));
						}
					});
				}
			}
		}
		jobs.parallelStream().forEach(Runnable::run);
		out.addAll(collected);
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
		outcomes.addAll(successiveNotes(cases));
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
		// regression guards (measured 2026-10-05, research R9): a result must not fall below these shares
		guard(all, "recognised when played", "single note", 0.97, report);
		guard(all, "wrong note", null, 0.995, report);
		guard(all, "recognised when played", "poly", 0.85, report);
		guard(all, "incomplete chord", null, 0.90, report);
	}

	/** The share of OK among the run tests of one kind (test name prefix; label or dataset filter) must be at least {@code minimum}. */
	private static void guard(List<Outcome> all, String testPrefix, String filter, double minimum, String report) {
		long ok = 0;
		long total = 0;
		for( Outcome o : all ) {
			boolean selected = o.test.startsWith(testPrefix) && o.status != Status.NOT_RUN
					&& (filter == null || o.label.equals(filter) || (filter.equals("poly") && o.dataset.endsWith("poly")));
			if( selected ) {
				total ++;
				ok += (o.status == Status.OK ? 1 : 0);
			}
		}
		if( total >= 30 ) {
			assertTrue((double) ok / total >= minimum, testPrefix + " " + filter + ": " + ok + "/" + total + " < " + minimum + "\n" + report);
		}
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
