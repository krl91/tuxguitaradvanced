package app.tuxguitar.audio.engine.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RealGuitarRegressionTest {

	@Test
	void acceptedRecordingsMatchManifestAndPitchBaseline() throws Exception {
		List<BenchmarkFixtures.Fixture> fixtures = BenchmarkFixtures.readManifest();
		assertEquals(22, fixtures.size());
		long totalBytes = 0L;

		for (BenchmarkFixtures.Fixture fixture : fixtures) {
			BenchmarkFixtures.Recording recording = BenchmarkFixtures.readRecording(fixture);
			totalBytes += recording.samples.length * 2L;
			assertEquals(fixture.sha256, recording.sha256, fixture.file + " SHA-256");
			BenchmarkFixtures.Result result = BenchmarkFixtures.analyze(recording);

			if ("silence".equals(fixture.role)) {
				assertFalse(result.stableMidi >= 40 && result.stableMidi <= 83,
						fixture.file + " must not produce a stable guitar-range note");
			} else if ("reference".equals(fixture.role)) {
				assertReference(fixture, result);
			} else {
				System.out.printf("benchmark informational: %s role=%s dominant=%d stable=%d latency=%.3f%n",
						fixture.file, fixture.role, result.dominantMidi, result.stableMidi,
						result.stableLatencySeconds());
			}
		}
		assertTrue(totalBytes <= 50L * 1024L * 1024L, "benchmark fixture set exceeds 50 MB");
	}

	private static void assertReference(BenchmarkFixtures.Fixture fixture,
			BenchmarkFixtures.Result result) {
		int expectedMidi = fixture.manifestMidi;
		assertFalse(fixture.playedNote.isEmpty(), fixture.file + " requires played_note ground truth");
		assertEquals(expectedMidi, result.dominantMidi, fixture.file + " dominant note");
		assertEquals(expectedMidi, result.stableMidi, fixture.file + " first stable note");
		assertTrue(result.octaveFrames(expectedMidi) <= BenchmarkFixtures.MAX_OCTAVE_FRAMES,
				fixture.file + " persistent octave error frames=" + result.octaveFrames(expectedMidi));
		assertTrue(result.longestOctaveRun(expectedMidi) < BenchmarkFixtures.STABLE_FRAMES,
				fixture.file + " stable octave-error run=" + result.longestOctaveRun(expectedMidi));
		assertTrue(result.stableLatencySeconds() <= BenchmarkFixtures.MAX_STABLE_LATENCY_SECONDS,
				fixture.file + " stable latency=" + result.stableLatencySeconds());
	}
}
