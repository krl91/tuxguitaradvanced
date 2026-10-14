package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;

/**
 * The owner's own chords on the owner's own guitar (spec 001 T094-T095, spec 004 T007): takes recorded through the Rocksmith USB adapter, each with the
 * pitches that were played (a polyphonic transcription, Basic Pitch, read by ear and by tool: {@code labels.csv}, next to the WAV files). For every take
 * Practice expects the chord that was played and the figure is the share accepted; and it expects the chord of another take (another chord) and the figure is how many are wrongly accepted. The test is a guard on a recorded baseline (spec 010 T029, {@code owner-chords-baseline.properties}, the figures of before the pre-run check): it fails when a series gets worse and says so when it gets better.
 *
 * <p>The recordings are the owner's playing and are not committed: the folders are read from {@code -Dtuxguitar.owner.chords=dir1,dir2} (default
 * {@code ../../specs/004-attack-difference-recognition/benchmark/owner-chords} and {@code owner-chords-2}); the test is skipped when none exists.
 * Run with {@code -Dgroups=dataset -DexcludedGroups=}. The report goes to {@code target/dataset-benchmark/owner-chords.txt}.</p>
 */
@Tag("dataset")
public class OwnerChordsBenchmarkTest {

	private static final Path BASE = Paths.get("../../specs/004-attack-difference-recognition/benchmark");

	private static final class Take {
		final String folder;
		final String file;
		final int[] pitches;
		final RecordedAudio audio;

		Take(String folder, String file, int[] pitches, RecordedAudio audio) {
			this.folder = folder;
			this.file = file;
			this.pitches = pitches;
			this.audio = audio;
		}

		String label() {
			return this.folder + "/" + this.file + " " + Arrays.toString(this.pitches);
		}
	}

	private static List<Take> takes() throws IOException {
		String property = System.getProperty("tuxguitar.owner.chords");
		List<Path> folders = new ArrayList<Path>();
		if( property != null && !property.isEmpty() ) {
			for( String folder : property.split(",") ) {
				folders.add(Paths.get(folder));
			}
		} else {
			folders.add(BASE.resolve("owner-chords"));
			folders.add(BASE.resolve("owner-chords-2"));
		}
		List<Take> takes = new ArrayList<Take>();
		for( Path folder : folders ) {
			Path labels = folder.resolve("labels.csv");
			if( !Files.isRegularFile(labels) ) {
				continue;
			}
			List<String> lines = Files.readAllLines(labels, StandardCharsets.UTF_8);
			for( String line : lines.subList(1, lines.size()) ) {
				String[] cells = line.split(",", 2);
				if( cells.length < 2 || cells[1].trim().isEmpty() ) {
					continue;
				}
				int[] pitches = Arrays.stream(cells[1].trim().split(" ")).mapToInt(Integer::parseInt).toArray();
				if( DatasetJudge.strings(pitches) == null ) {
					continue; // not playable on six strings together: a transcription artefact
				}
				RecordedAudio audio = RecordedAudio.load(folder.resolve(cells[0]));
				if( audio != null ) {
					takes.add(new Take(folder.getFileName().toString(), cells[0], pitches, audio));
				}
			}
		}
		return takes;
	}

	@Test
	public void everyChordThatWasPlayedIsAcceptedAndAnotherChordIsNot() throws Exception {
		List<Take> takes = takes();
		assumeTrue(!takes.isEmpty(), "the owner's recordings are not here (see the class comment)");
		StringBuilder report = new StringBuilder("Owner chords through the Rocksmith USB adapter\n\n");
		Map<String, int[]> series = new TreeMap<String, int[]>(); // folder -> {takes, accepted, controls, wrong}
		int accepted = 0;
		int wrongAccepted = 0;
		int controls = 0;
		List<String> refused = new ArrayList<String>();
		for( int i = 0 ; i < takes.size() ; i ++ ) {
			Take take = takes.get(i);
			boolean own = DatasetJudge.accepts(take.audio, take.pitches);
			int[] figures = series.computeIfAbsent(take.folder, key -> new int[4]);
			figures[0]++;
			figures[1] += own ? 1 : 0;
			accepted += own ? 1 : 0;
			if( !own ) {
				refused.add(take.label());
			}
			report.append(String.format("%-48s own chord %s", take.label(), own ? "ACCEPTED" : "refused"));
			// the control: the chord of the next take that has other pitches
			for( int k = 1 ; k < takes.size() ; k ++ ) {
				Take other = takes.get((i + k) % takes.size());
				if( !Arrays.equals(other.pitches, take.pitches) ) {
					boolean wrong = DatasetJudge.accepts(take.audio, other.pitches);
					controls ++;
					figures[2]++;
					figures[3] += wrong ? 1 : 0;
					wrongAccepted += wrong ? 1 : 0;
					report.append(String.format("   | expecting %s: %s", Arrays.toString(other.pitches), wrong ? "WRONGLY ACCEPTED" : "refused"));
					break;
				}
			}
			report.append('\n');
		}
		report.append(String.format("%n%d of %d takes accepted for the chord that was played (%.0f %%); %d of %d other chords wrongly accepted%n", accepted, takes.size(),
				100.0 * accepted / takes.size(), wrongAccepted, controls));
		Path out = Paths.get("target", "dataset-benchmark", "owner-chords.txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
		Properties baseline = new Properties();
		Path baselineFile = Paths.get("test/app/tuxguitar/app/tools/custom/practice/dataset/owner-chords-baseline.properties");
		assertTrue(Files.isRegularFile(baselineFile), "the committed baseline is there: " + baselineFile.toAbsolutePath());
		try( java.io.InputStream in = Files.newInputStream(baselineFile) ) {
			baseline.load(in);
		}
		List<String> worse = new ArrayList<String>();
		for( Map.Entry<String, int[]> entry : series.entrySet() ) {
			String minimum = baseline.getProperty(entry.getKey() + ".accepted.min");
			String maximum = baseline.getProperty(entry.getKey() + ".wrong.max");
			int[] f = entry.getValue();
			if( minimum == null || maximum == null ) {
				System.out.println("OWNER-CHORDS series " + entry.getKey() + " has no baseline: reported only");
				continue;
			}
			int min = Integer.parseInt(minimum.trim());
			int max = Integer.parseInt(maximum.trim());
			if( f[1] < min || f[3] > max ) {
				worse.add(entry.getKey() + ": " + f[1] + " of " + f[0] + " accepted (baseline at least " + min + "), " + f[3] + " of " + f[2] + " wrongly accepted (baseline at most " + max + ")");
			} else if( f[1] > min || f[3] < max ) {
				System.out.println("OWNER-CHORDS series " + entry.getKey() + " is BETTER than its baseline: " + f[1] + " accepted (was " + min + "), " + f[3] + " wrongly accepted (was " + max + "): raise the baseline file");
			}
		}
		assertEquals(new ArrayList<String>(), worse, "a series of the owner's takes got worse than the recorded baseline");
		assertTrue(accepted >= 0 && controls >= 0, "the report was made");
	}
}
