package app.tuxguitar.app.tools.custom.practice.dataset;

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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The default chord rule on the owner's reference series (spec 024, task T016; FR-001, FR-002, SC-001, SC-002): with
 * {@code RecognitionSettings.defaults()}, judged as {@link DatasetJudge} does (the Wait session), every take is accepted for its own chord and no take
 * is accepted for a chord that misses two or more of its names. Voicings and chords one name off are reported, not judged.
 *
 * <p>The takes are not committed: the folder is read from {@code -Dtuxguitar.owner.chords} (default
 * {@code ../../specs/004-attack-difference-recognition/benchmark/owner-chords-3}); the test is skipped when it is absent. Run with
 * {@code -Dgroups=dataset -DexcludedGroups=}. The report goes to {@code target/dataset-benchmark/owner-chords-profile-<engine>.txt}.</p>
 */
@Tag("dataset")
public class OwnerChordsProfileTest {

	private static final Path REFERENCE = Paths.get("../../specs/004-attack-difference-recognition/benchmark/owner-chords-3");

	private static final class Take {
		final String file;
		final int[] pitches;
		final RecordedAudio audio;

		Take(String file, int[] pitches, RecordedAudio audio) {
			this.file = file;
			this.pitches = pitches;
			this.audio = audio;
		}
	}

	private static List<Take> takes() throws IOException {
		String property = System.getProperty("tuxguitar.owner.chords");
		Path folder = (property != null && !property.isEmpty() ? Paths.get(property.split(",")[0]) : REFERENCE);
		List<Take> takes = new ArrayList<Take>();
		Path labels = folder.resolve("labels.csv");
		if( !Files.isRegularFile(labels) ) {
			return takes;
		}
		List<String> lines = Files.readAllLines(labels, StandardCharsets.UTF_8);
		for( String line : lines.subList(1, lines.size()) ) {
			String[] cells = line.split(",", 2);
			if( cells.length < 2 || cells[1].trim().isEmpty() ) {
				continue;
			}
			int[] pitches = Arrays.stream(cells[1].trim().split(" ")).mapToInt(Integer::parseInt).toArray();
			if( DatasetJudge.strings(pitches) == null ) {
				continue;
			}
			RecordedAudio audio = RecordedAudio.load(folder.resolve(cells[0]));
			if( audio != null ) {
				takes.add(new Take(cells[0], pitches, audio));
			}
		}
		return takes;
	}

	@Test
	public void everyRightChordIsAcceptedAndNoClearlyWrongOne() throws Exception {
		List<Take> takes = takes();
		assumeTrue(!takes.isEmpty(), "the owner's reference series is not here (see the class comment)");
		String engine = PitchEngineCatalog.resolveKind(RecognitionSettings.defaults()).name().toLowerCase();
		StringBuilder report = new StringBuilder("Owner chords, reference series, default chord rule (spec 024, T016), engine " + engine + "\n\n");
		int right = 0;
		int[] accepted = new int[3];
		int[] judged = new int[3];
		List<String> refused = new ArrayList<String>();
		List<String> clearlyWrong = new ArrayList<String>();
		for( Take take : takes ) {
			boolean own = DatasetJudge.accepts(take.audio, take.pitches);
			right += own ? 1 : 0;
			if( !own ) {
				refused.add(take.file + " " + Arrays.toString(take.pitches));
			}
			report.append(String.format("%-28s %-28s own chord %s%n", take.file, Arrays.toString(take.pitches), own ? "ACCEPTED" : "refused"));
			for( Take other : takes ) {
				if( other == take || Arrays.equals(other.pitches, take.pitches) ) {
					continue;
				}
				int kind = OwnerChordsStrumHoldProbeTest.distance(take.pitches, other.pitches);
				boolean wrong = DatasetJudge.accepts(take.audio, other.pitches);
				judged[kind]++;
				accepted[kind] += wrong ? 1 : 0;
				if( wrong ) {
					String line = take.file + " accepted for " + Arrays.toString(other.pitches);
					report.append("    ").append(kind == 0 ? "voicing" : kind == 1 ? "one name off" : "CLEARLY WRONG").append(": ").append(line).append('\n');
					if( kind == 2 ) {
						clearlyWrong.add(line);
					}
				}
			}
		}
		report.append(String.format("%n%d of %d right accepted; voicings %d of %d, one name off %d of %d, clearly wrong %d of %d accepted%n", right, takes.size(),
				accepted[0], judged[0], accepted[1], judged[1], accepted[2], judged[2]));
		Path out = Paths.get("target", "dataset-benchmark", "owner-chords-profile-" + engine + ".txt");
		Files.createDirectories(out.getParent());
		Files.write(out, report.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(report);
		assertEquals(new ArrayList<String>(), refused, "every right chord of the reference series is accepted");
		assertEquals(new ArrayList<String>(), clearlyWrong, "no clearly wrong chord is accepted");
	}
}
