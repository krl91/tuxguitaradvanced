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
import app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance;

/**
 * The closest notes Practice can follow (spec 004 T010): three single notes of a real guitar (IDMT-SMT-Guitar dataset 1, plain
 * notes) are mixed one after the other with a controlled spacing, the second and the third struck while the previous ones ring,
 * and Practice expecting the three notes in order is judged on: all three recognised in order, each one recognised, a wrong third note
 * accepted (false acceptance), and the delay between each note's onset and its recognition.
 *
 * <p>The onset of each note in its clip is read from the annotation of the dataset (about 0.2 s after the start of the clip); the clips are
 * placed so that the onsets are one spacing apart, and the delay is measured from each onset. Same data rules as the other dataset benchmarks
 * ({@code -Dtuxguitar.datasets}, {@code -Dgroups=dataset -DexcludedGroups=}); only counts are written.</p>
 */
@Tag("dataset")
public class NoteSpacingBenchmarkTest {

	static final double[] SPACINGS = { 0.05, 0.075, 0.10, 0.125, 0.15, 0.20, 0.25, 0.35 };
	private static final int TRIPLES_PER_GUITAR = 24;
	/** Not accepted later than this after its onset counts as not recognised in its turn (a learner would have played the next note). */
	private static final double ACCEPT_WINDOW_SECONDS = 0.6;

	static final class Triple {
		final DatasetCase[] notes;
		final String group;

		Triple(DatasetCase a, DatasetCase b, DatasetCase c) {
			this.notes = new DatasetCase[] { a, b, c };
			this.group = a.group;
		}
	}

	static final class Result {
		final double spacing;
		final boolean allInOrder;
		final int recognised;
		final boolean wrongThirdAccepted;
		final boolean wrongThirdTested;
		final double[] delays;

		Result(double spacing, boolean allInOrder, int recognised, boolean wrongThirdTested, boolean wrongThirdAccepted, double[] delays) {
			this.spacing = spacing;
			this.allInOrder = allInOrder;
			this.recognised = recognised;
			this.wrongThirdTested = wrongThirdTested;
			this.wrongThirdAccepted = wrongThirdAccepted;
			this.delays = delays;
		}
	}

	static List<Triple> triples(List<DatasetCase> singles) {
		Map<String, List<DatasetCase>> byGuitar = singles.stream().collect(Collectors.groupingBy(c -> c.group, TreeMap::new, Collectors.toList()));
		List<Triple> triples = new ArrayList<Triple>();
		for( List<DatasetCase> group : byGuitar.values() ) {
			List<DatasetCase> sorted = new ArrayList<DatasetCase>(group);
			Collections.sort(sorted, (x, y) -> Integer.compare(x.played[0], y.played[0]));
			int made = 0;
			for( int i = 0 ; i < sorted.size() && made < TRIPLES_PER_GUITAR ; i += 2 ) {
				DatasetCase a = sorted.get(i);
				DatasetCase b = null;
				DatasetCase c = null;
				for( int j = i + 1 ; j < sorted.size() ; j ++ ) {
					if( b == null && sorted.get(j).played[0] - a.played[0] >= 3 ) {
						b = sorted.get(j);
					} else if( b != null && sorted.get(j).played[0] - b.played[0] >= 3 ) {
						c = sorted.get(j);
						break;
					}
				}
				if( b == null || c == null ) {
					continue;
				}
				// ascending, then descending, alternately
				triples.add(made % 2 == 0 ? new Triple(a, b, c) : new Triple(c, b, a));
				made ++;
			}
		}
		return triples;
	}

	/** The onset of the note in its clip, seconds, from the dataset's annotation (the clip begins about 0.2 s before the note). */
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

	static Result run(Triple triple, double spacing) {
		return run(triple, spacing, PolyphonicAcceptance.Parameters.defaults());
	}

	static Result run(Triple triple, double spacing, PolyphonicAcceptance.Parameters acceptance) {
		RecordedAudio[] clips = new RecordedAudio[3];
		for( int i = 0 ; i < 3 ; i ++ ) {
			clips[i] = RecordedAudio.load(triple.notes[i].audio);
			if( clips[i] == null || clips[i].sampleRate != clips[0].sampleRate ) {
				return null;
			}
		}
		int rate = clips[0].sampleRate;
		// clip i is placed so that its note begins at i * spacing + the onset of the first clip
		double[] onsetInClip = new double[3];
		double[] start = new double[3];
		double[] onset = new double[3];
		for( int i = 0 ; i < 3 ; i ++ ) {
			onsetInClip[i] = onsetOf(triple.notes[i]);
		}
		for( int i = 0 ; i < 3 ; i ++ ) {
			onset[i] = onsetInClip[0] + i * spacing;
			start[i] = onset[i] - onsetInClip[i];
		}
		int length = 0;
		for( int i = 0 ; i < 3 ; i ++ ) {
			length = Math.max(length, (int) Math.round(Math.max(0.0, start[i]) * rate) + clips[i].samples.length);
		}
		float[] mix = new float[length];
		for( int i = 0 ; i < 3 ; i ++ ) {
			int offset = (int) Math.round(Math.max(0.0, start[i]) * rate);
			for( int k = 0 ; k < clips[i].samples.length ; k ++ ) {
				mix[offset + k] += clips[i].samples[k];
			}
		}
		RecordedAudio audio = RecordedAudio.of(mix, rate);
		int[][] steps = { triple.notes[0].played, triple.notes[1].played, triple.notes[2].played };
		double[] times = DatasetJudge.acceptanceTimes(audio, steps, acceptance);
		double[] delays = new double[3];
		int recognised = 0;
		boolean inOrder = true;
		double previous = -1.0;
		for( int i = 0 ; i < 3 ; i ++ ) {
			delays[i] = times[i] - onset[i];
			if( !Double.isNaN(times[i]) && times[i] >= onset[i] - 0.05 && times[i] <= onset[i] + ACCEPT_WINDOW_SECONDS ) {
				recognised ++;
			} else {
				inOrder = false;
			}
			if( Double.isNaN(times[i]) || times[i] < previous ) {
				inOrder = false;
			}
			previous = times[i];
		}
		// a wrong third note: three semitones from the real one, expected in its place
		int wrong = triple.notes[2].played[0] + (triple.notes[2].played[0] > triple.notes[1].played[0] ? 3 : -3);
		boolean tested = wrong >= 40 && wrong <= 88 && wrong != triple.notes[0].played[0] && wrong != triple.notes[1].played[0] && DatasetJudge.strings(new int[] { wrong }) != null;
		boolean wrongAccepted = false;
		if( tested ) {
			double[] wrongTimes = DatasetJudge.acceptanceTimes(audio, new int[][] { steps[0], steps[1], { wrong } }, acceptance);
			wrongAccepted = !Double.isNaN(wrongTimes[2]);
		}
		return new Result(spacing, inOrder, recognised, tested, wrongAccepted, delays);
	}

	@Test
	public void theClosestNotesPracticeCanFollow() throws IOException {
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
		List<DatasetCase> singles = DatasetLoaders.idmtGuitar(root, 60).stream().filter(c -> c.kind == DatasetCase.Kind.SINGLE_NOTE && c.played[0] >= 40 && c.played[0] <= 88).collect(Collectors.toList());
		assumeTrue(!singles.isEmpty(), "IDMT-SMT-Guitar dataset 1 not found under " + root.toAbsolutePath());
		List<Triple> triples = triples(singles);
		assumeTrue(!triples.isEmpty(), "no triple of notes could be built");
		ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<Result>();
		List<Runnable> jobs = new ArrayList<Runnable>();
		for( Triple triple : triples ) {
			for( double spacing : SPACINGS ) {
				jobs.add(() -> {
					Result result = run(triple, spacing);
					if( result != null ) {
						results.add(result);
					}
				});
			}
		}
		jobs.parallelStream().forEach(Runnable::run);
		String report = report(triples, new ArrayList<Result>(results));
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("note-spacing-report.txt"), report.getBytes(StandardCharsets.UTF_8));
		assertTrue(!results.isEmpty(), report);
	}

	private static String report(List<Triple> triples, List<Result> results) {
		StringBuilder text = new StringBuilder();
		text.append("NOTE SPACING BENCHMARK (spec 004 T010): three notes in order, each struck while the previous ones ring\n");
		text.append("Data: IDMT-SMT-Guitar V2 dataset 1, plain single notes (CC BY-NC-ND 4.0, evaluation only, kept outside the repository); ").append(triples.size()).append(" triples of notes of ")
				.append(triples.stream().map(t -> t.group).distinct().count()).append(" guitars, ascending and descending, notes at least 3 semitones apart.\n");
		text.append("Settings: RecognitionSettings.defaults(), PolyphonicAcceptance.Parameters.defaults(). A note counts as recognised when accepted in its turn between 50 ms before and ")
				.append(ACCEPT_WINDOW_SECONDS).append(" s after its onset. Delay = acceptance time minus the onset of the note.\n\n");
		text.append(String.format("%-9s %6s %-18s %-18s %-24s %s%n", "spacing", "triples", "all 3 in order", "notes recognised", "wrong 3rd note accepted", "delay of each note: median / p90 / max (ms)"));
		for( double spacing : SPACINGS ) {
			List<Result> mine = new ArrayList<Result>();
			for( Result r : results ) {
				if( r.spacing == spacing ) {
					mine.add(r);
				}
			}
			int all = (int) mine.stream().filter(r -> r.allInOrder).count();
			int notes = mine.stream().mapToInt(r -> r.recognised).sum();
			int tested = (int) mine.stream().filter(r -> r.wrongThirdTested).count();
			int falseAccepts = (int) mine.stream().filter(r -> r.wrongThirdAccepted).count();
			List<Double> delays = new ArrayList<Double>();
			for( Result r : mine ) {
				for( int i = 0 ; i < 3 ; i ++ ) {
					if( !Double.isNaN(r.delays[i]) && r.delays[i] <= ACCEPT_WINDOW_SECONDS ) {
						delays.add(r.delays[i]);
					}
				}
			}
			Collections.sort(delays);
			String delayText = delays.isEmpty() ? "-" : String.format("%.0f / %.0f / %.0f", 1000 * delays.get(delays.size() / 2), 1000 * delays.get((int) (delays.size() * 0.9)), 1000 * delays.get(delays.size() - 1));
			text.append(String.format(java.util.Locale.ROOT, "%5.0f ms %6d %3d / %-3d (%4.1f%%)   %4d / %-4d (%4.1f%%)   %3d / %-3d (%4.1f%%)       %s%n", 1000 * spacing, mine.size(), all, mine.size(),
					100.0 * all / Math.max(1, mine.size()), notes, 3 * mine.size(), 100.0 * notes / Math.max(1, 3 * mine.size()), falseAccepts, tested, 100.0 * falseAccepts / Math.max(1, tested), delayText));
		}
		return text.toString();
	}
}
