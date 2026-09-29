package app.tuxguitar.audio.engine.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("benchmark")
class BenchmarkRunnerTest {

	@Test
	void writePerInputReportForT076() throws Exception {
		List<String> rows = new ArrayList<>();
		rows.add("file,input_type,fixture_role,played_note,dominant_midi,first_stable_midi,stable_latency_s,octave_frames,longest_octave_run");
		for (BenchmarkFixtures.Fixture fixture : BenchmarkFixtures.readManifest()) {
			BenchmarkFixtures.Result result = BenchmarkFixtures.analyze(
					BenchmarkFixtures.readRecording(fixture));
			long octaveFrames = fixture.playedNote.isEmpty() || fixture.manifestMidi == null
					? 0L : result.octaveFrames(fixture.manifestMidi);
			int longestOctaveRun = fixture.playedNote.isEmpty() || fixture.manifestMidi == null
					? 0 : result.longestOctaveRun(fixture.manifestMidi);
			rows.add(String.join(",",
					fixture.file,
					fixture.inputType,
					fixture.role,
					fixture.playedNote,
					Integer.toString(result.dominantMidi),
					Integer.toString(result.stableMidi),
					Double.toString(result.stableLatencySeconds()),
					Long.toString(octaveFrames),
					Integer.toString(longestOctaveRun)));
		}

		Path repository = findRepositoryRoot(Paths.get(System.getProperty("user.dir")));
		assertFalse(repository == null, "could not locate repository root for benchmark report");
		Path output = repository.resolve("specs/001-interactive-practice-mvp/benchmark/builtin-mic.csv");
		Files.createDirectories(output.getParent());
		Files.write(output, rows, StandardCharsets.UTF_8);
	}

	private static Path findRepositoryRoot(Path start) {
		for (Path path = start.toAbsolutePath(); path != null; path = path.getParent()) {
			if (Files.isDirectory(path.resolve("specs/001-interactive-practice-mvp"))) {
				return path;
			}
		}
		return null;
	}
}
