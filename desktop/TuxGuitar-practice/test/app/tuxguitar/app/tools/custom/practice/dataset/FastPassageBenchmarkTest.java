package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;

/**
 * Fast passages (spec 002 T051, SC-002 and SC-004): four single notes of a real guitar (IDMT-SMT-Guitar dataset 1, plain notes) struck
 * at 4, 6, 8, 10 and 12 notes per second, each ringing under the next, judged by Practice expecting them in order. Three kinds of
 * passage: four different pitches; the same pitch four times (identical repeats); two neighbouring pitches a semitone apart alternating.
 * Reported: notes recognised in their turn, passages followed in full, a wrong note expected in place of the last one accepted (false
 * accept), and the delay of each note.
 *
 * <p>Data rules as for the other dataset benchmarks ({@code -Dtuxguitar.datasets}, {@code -Dgroups=dataset -DexcludedGroups=}); only counts are written.</p>
 */
@Tag("dataset")
public class FastPassageBenchmarkTest {

	static final double[] RATES = { 4, 6, 8, 10, 12 };
	private static final int NOTES = 4;
	private static final int PASSAGES_PER_GUITAR = 20;
	private static final double ACCEPT_WINDOW_SECONDS = 0.6;

	private enum Kind {
		DIFFERENT("four different pitches"), REPEATED("the same pitch four times"), NEIGHBOURS("two pitches a semitone apart, alternating");

		final String text;

		Kind(String text) {
			this.text = text;
		}
	}

	private static final class Passage {
		final Kind kind;
		final DatasetCase[] clips;
		final String group;

		Passage(Kind kind, DatasetCase[] clips) {
			this.kind = kind;
			this.clips = clips;
			this.group = clips[0].group;
		}
	}

	private static final class Result {
		final Kind kind;
		final double rate;
		final int recognised;
		final boolean followedInFull;
		final boolean wrongTested;
		final boolean wrongAccepted;
		final double[] delays;

		Result(Kind kind, double rate, int recognised, boolean followedInFull, boolean wrongTested, boolean wrongAccepted, double[] delays) {
			this.kind = kind;
			this.rate = rate;
			this.recognised = recognised;
			this.followedInFull = followedInFull;
			this.wrongTested = wrongTested;
			this.wrongAccepted = wrongAccepted;
			this.delays = delays;
		}
	}

	private static List<Passage> passages(List<DatasetCase> singles) {
		Map<String, List<DatasetCase>> byGuitar = singles.stream().collect(Collectors.groupingBy(c -> c.group, TreeMap::new, Collectors.toList()));
		List<Passage> passages = new ArrayList<Passage>();
		for( List<DatasetCase> group : byGuitar.values() ) {
			List<DatasetCase> sorted = new ArrayList<DatasetCase>(group);
			Collections.sort(sorted, (x, y) -> Integer.compare(x.played[0], y.played[0]));
			int made = 0;
			for( int i = 0 ; i < sorted.size() && made < PASSAGES_PER_GUITAR ; i ++ ) {
				DatasetCase a = sorted.get(i);
				// the same pitch four times
				passages.add(new Passage(Kind.REPEATED, new DatasetCase[] { a, a, a, a }));
				// two pitches a semitone apart, alternating
				for( DatasetCase b : sorted ) {
					if( b.played[0] == a.played[0] + 1 ) {
						passages.add(new Passage(Kind.NEIGHBOURS, new DatasetCase[] { a, b, a, b }));
						break;
					}
				}
				// four different pitches, at least 3 semitones apart, ascending then descending alternately
				DatasetCase[] four = new DatasetCase[NOTES];
				four[0] = a;
				int found = 1;
				for( int j = i + 1 ; j < sorted.size() && found < NOTES ; j ++ ) {
					if( sorted.get(j).played[0] - four[found - 1].played[0] >= 3 ) {
						four[found ++] = sorted.get(j);
					}
				}
				if( found == NOTES ) {
					if( made % 2 == 1 ) {
						DatasetCase t = four[0];
						four[0] = four[3];
						four[3] = t;
						t = four[1];
						four[1] = four[2];
						four[2] = t;
					}
					passages.add(new Passage(Kind.DIFFERENT, four));
				}
				made ++;
			}
		}
		return passages;
	}

	private static double onsetOf(DatasetCase c) {
		try {
			Path annotation = c.audio.getParent().getParent().resolve("annotation").resolve(c.audio.getFileName().toString().replace(".wav", ".xml"));
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("<onsetSec>([0-9.]+)</onsetSec>").matcher(new String(Files.readAllBytes(annotation), StandardCharsets.UTF_8));
			if( m.find() ) {
				return Double.parseDouble(m.group(1));
			}
		} catch (IOException | RuntimeException exception) {
			// the typical value below
		}
		return 0.2;
	}

	private static Result run(Passage passage, double rate) {
		double spacing = 1.0 / rate;
		RecordedAudio[] clips = new RecordedAudio[NOTES];
		for( int i = 0 ; i < NOTES ; i ++ ) {
			clips[i] = RecordedAudio.load(passage.clips[i].audio);
			if( clips[i] == null || clips[i].sampleRate != clips[0].sampleRate ) {
				return null;
			}
		}
		int sampleRate = clips[0].sampleRate;
		double firstOnset = onsetOf(passage.clips[0]);
		double[] onset = new double[NOTES];
		int length = 0;
		for( int i = 0 ; i < NOTES ; i ++ ) {
			onset[i] = firstOnset + i * spacing;
			double start = Math.max(0.0, onset[i] - onsetOf(passage.clips[i]));
			length = Math.max(length, (int) Math.round(start * sampleRate) + clips[i].samples.length);
		}
		float[] mix = new float[length];
		for( int i = 0 ; i < NOTES ; i ++ ) {
			int offset = (int) Math.round(Math.max(0.0, onset[i] - onsetOf(passage.clips[i])) * sampleRate);
			for( int k = 0 ; k < clips[i].samples.length ; k ++ ) {
				mix[offset + k] += clips[i].samples[k];
			}
		}
		RecordedAudio audio = RecordedAudio.of(mix, sampleRate);
		int[][] steps = new int[NOTES][];
		for( int i = 0 ; i < NOTES ; i ++ ) {
			steps[i] = passage.clips[i].played;
		}
		double[] times = DatasetJudge.acceptanceTimes(audio, steps);
		double[] delays = new double[NOTES];
		int recognised = 0;
		boolean full = true;
		double previous = -1.0;
		for( int i = 0 ; i < NOTES ; i ++ ) {
			delays[i] = times[i] - onset[i];
			boolean ok = !Double.isNaN(times[i]) && times[i] >= onset[i] - 0.05 && times[i] <= onset[i] + ACCEPT_WINDOW_SECONDS && times[i] >= previous;
			if( ok ) {
				recognised ++;
			} else {
				full = false;
			}
			if( !Double.isNaN(times[i]) ) {
				previous = times[i];
			}
		}
		// a wrong last note expected in place of the real one: two semitones from it (never one of the pitches played)
		int real = steps[NOTES - 1][0];
		int wrong = real + 2;
		boolean tested = true;
		for( int[] step : steps ) {
			tested &= step[0] != wrong;
		}
		tested &= wrong >= 40 && wrong <= 88 && DatasetJudge.strings(new int[] { wrong }) != null;
		boolean wrongAccepted = false;
		if( tested ) {
			int[][] altered = steps.clone();
			altered[NOTES - 1] = new int[] { wrong };
			double[] wrongTimes = DatasetJudge.acceptanceTimes(audio, altered);
			wrongAccepted = !Double.isNaN(wrongTimes[NOTES - 1]);
		}
		return new Result(passage.kind, rate, recognised, full, tested, wrongAccepted, delays);
	}

	@Test
	public void fastPassagesOfRealNotes() throws IOException {
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
		List<DatasetCase> singles = DatasetLoaders.idmtGuitar(root, 60).stream().filter(c -> c.kind == DatasetCase.Kind.SINGLE_NOTE && c.played[0] >= 40 && c.played[0] <= 86).collect(Collectors.toList());
		assumeTrue(!singles.isEmpty(), "IDMT-SMT-Guitar dataset 1 not found under " + root.toAbsolutePath());
		List<Passage> passages = passages(singles);
		ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
		List<Runnable> jobs = new ArrayList<Runnable>();
		for( Passage passage : passages ) {
			for( double rate : RATES ) {
				jobs.add(() -> {
					Result r = run(passage, rate);
					if( r != null ) {
						results.add(r);
					}
				});
			}
		}
		jobs.parallelStream().forEach(Runnable::run);
		String report = report(passages, new ArrayList<Result>(results));
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("fast-passages-report.txt"), report.getBytes(StandardCharsets.UTF_8));
		assertTrue(!results.isEmpty(), report);
	}

	private static String revision() {
		try {
			String head = new String(new ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start().getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			boolean dirty = !new String(new ProcessBuilder("git", "status", "--porcelain", "--untracked-files=no").redirectErrorStream(true).start().getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().isEmpty();
			return head + (dirty ? " (+ uncommitted changes)" : "");
		} catch (IOException exception) {
			return "unknown";
		}
	}

	private static String report(List<Passage> passages, List<Result> results) {
		StringBuilder text = new StringBuilder();
		text.append("FAST PASSAGE BENCHMARK (spec 002 T051, SC-002, SC-004): four notes in order at 4 to 12 notes per second, each ringing under the next\n");
		text.append("Code revision: ").append(revision()).append("\n");
		text.append("Data: IDMT-SMT-Guitar V2 dataset 1, plain single notes (CC BY-NC-ND 4.0, evaluation only, kept outside the repository); ").append(passages.size())
				.append(" passages of ").append(passages.stream().map(p -> p.group).distinct().count()).append(" guitars. Settings: RecognitionSettings.defaults(), PolyphonicAcceptance.Parameters.defaults().\n");
		text.append("A note is recognised when accepted in its turn between 50 ms before and ").append(ACCEPT_WINDOW_SECONDS).append(" s after its onset (read from the annotation). False accept: the last note expected is a wrong one (two semitones from the real one) and is accepted.\n");
		text.append("Targets of the specification: SC-002 at least 95 % of the notes recognised at 8 notes per second and 90 % at 12, false accepts under 1 %; SC-004 identical repeats at 8 notes per second separated in at least 95 % of the passages.\n");
		for( Kind kind : Kind.values() ) {
			text.append("\n").append(kind.text).append(":\n");
			text.append(String.format("  %-8s %8s %-20s %-18s %-22s %s%n", "rate", "passages", "notes recognised", "followed in full", "wrong last note accepted", "delay median / p95 / max (ms)"));
			for( double rate : RATES ) {
				List<Result> mine = new ArrayList<Result>();
				for( Result r : results ) {
					if( r.kind == kind && r.rate == rate ) {
						mine.add(r);
					}
				}
				if( mine.isEmpty() ) {
					continue;
				}
				int notes = mine.stream().mapToInt(r -> r.recognised).sum();
				int full = (int) mine.stream().filter(r -> r.followedInFull).count();
				int tested = (int) mine.stream().filter(r -> r.wrongTested).count();
				int wrong = (int) mine.stream().filter(r -> r.wrongAccepted).count();
				List<Double> delays = new ArrayList<Double>();
				for( Result r : mine ) {
					for( double d : r.delays ) {
						if( !Double.isNaN(d) && d <= ACCEPT_WINDOW_SECONDS && d >= -0.05 ) {
							delays.add(d);
						}
					}
				}
				Collections.sort(delays);
				String delayText = delays.isEmpty() ? "-" : String.format(java.util.Locale.ROOT, "%.0f / %.0f / %.0f", 1000 * delays.get(delays.size() / 2),
						1000 * delays.get(Math.min(delays.size() - 1, (int) Math.ceil(0.95 * delays.size()) - 1)), 1000 * delays.get(delays.size() - 1));
				text.append(String.format(java.util.Locale.ROOT, "  %4.0f /s  %8d %4d / %-4d (%5.1f%%)  %3d / %-3d (%5.1f%%)  %3d / %-3d (%4.1f%%)        %s%n", rate, mine.size(), notes, NOTES * mine.size(),
						100.0 * notes / (NOTES * mine.size()), full, mine.size(), 100.0 * full / mine.size(), wrong, tested, 100.0 * wrong / Math.max(1, tested), delayText));
			}
		}
		return text.toString();
	}
}
