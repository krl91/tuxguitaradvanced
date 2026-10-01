package app.tuxguitar.audio.engine.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.InputAssessor;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.analysis.PitchEstimate;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;
import app.tuxguitar.audio.engine.input.InputAssessment;

/**
 * T076: evidence for the provisional parameters on the committed real-guitar clips. Writes
 * {@code benchmark/builtin-mic-sweep.csv} (pitch gate × stability × level floor),
 * {@code builtin-mic-articulation.csv} and {@code builtin-mic-level-check.csv}. Reports only;
 * the regression assertions stay in {@link RealGuitarRegressionTest}.
 */
@Tag("benchmark")
class BenchmarkSweepTest {

	private static final double[] GATES = { 0.70, 0.80, 0.90 };
	private static final int[] STABLE_FRAMES = { 10, 20, 30 };
	private static final double[] FLOORS = { Double.NEGATIVE_INFINITY, -40.0, -35.0 };

	@Test
	void writeParameterEvidence() throws Exception {
		List<BenchmarkFixtures.Fixture> fixtures = BenchmarkFixtures.readManifest();
		List<float[]> samples = new ArrayList<>();
		for (BenchmarkFixtures.Fixture fixture : fixtures) {
			samples.add(BenchmarkFixtures.readRecording(fixture).samples);
		}
		Path directory = reportDirectory();
		Files.write(directory.resolve("builtin-mic-sweep.csv"), sweep(fixtures, samples), StandardCharsets.UTF_8);
		Files.write(directory.resolve("builtin-mic-articulation.csv"), articulation(fixtures, samples), StandardCharsets.UTF_8);
		Files.write(directory.resolve("builtin-mic-level-check.csv"), levelCheck(fixtures, samples), StandardCharsets.UTF_8);
		Files.write(directory.resolve("builtin-mic-articulation-sweep.csv"), articulationSweep(fixtures, samples), StandardCharsets.UTF_8);
		Files.write(directory.resolve("builtin-mic-articulation-fast-sweep.csv"), fastArticulationSweep(fixtures, samples), StandardCharsets.UTF_8);
		Files.write(directory.resolve("builtin-mic-cents.csv"), cents(fixtures, samples), StandardCharsets.UTF_8);
		Files.write(directory.resolve("builtin-mic-silence.csv"), silence(fixtures, samples), StandardCharsets.UTF_8);
	}

	private static List<String> sweep(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("confidence_gate,stable_frames,pitch_floor_dbfs,references,first_stable_correct,wrong_octave_stable,other_wrong_stable,no_stable,median_latency_s,max_latency_s,silence_stable_midi");
		for (double gate : GATES) {
			for (int stable : STABLE_FRAMES) {
				for (double floor : FLOORS) {
					int references = 0;
					int correct = 0;
					int octave = 0;
					int other = 0;
					int none = 0;
					List<Double> latencies = new ArrayList<>();
					int silenceStable = -1;
					for (int i = 0; i < fixtures.size(); i++) {
						BenchmarkFixtures.Fixture fixture = fixtures.get(i);
						BenchmarkFixtures.Result result = BenchmarkFixtures.analyze(new BenchmarkFixtures.Recording(samples.get(i), fixture.sha256), gate, stable, floor);
						if ("silence".equals(fixture.role)) {
							silenceStable = result.stableMidi;
						}
						if (!"reference".equals(fixture.role)) {
							continue;
						}
						references++;
						if (result.stableMidi < 0) {
							none++;
						} else if (result.stableMidi == fixture.manifestMidi) {
							correct++;
							latencies.add(result.stableLatencySeconds());
						} else if (Math.abs(result.stableMidi - fixture.manifestMidi) % 12 == 0) {
							octave++;
						} else {
							other++;
						}
					}
					latencies.sort(null);
					rows.add(String.join(",", Double.toString(gate), Integer.toString(stable),
							Double.isInfinite(floor) ? "none" : Double.toString(floor), Integer.toString(references),
							Integer.toString(correct), Integer.toString(octave), Integer.toString(other), Integer.toString(none),
							latencies.isEmpty() ? "NaN" : String.format(Locale.ROOT, "%.3f", latencies.get(latencies.size() / 2)),
							latencies.isEmpty() ? "NaN" : String.format(Locale.ROOT, "%.3f", latencies.get(latencies.size() - 1)),
							Integer.toString(silenceStable)));
				}
			}
		}
		return rows;
	}

	/**
	 * Articulation thresholds on the clips with a known onset count (reference and known-issue):
	 * extra = reported onsets beyond the expected count (false articulations), missed = expected
	 * onsets not reported. FR-022 prefers missed to extra.
	 */
	private static List<String> articulationSweep(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("flux_median_factor,gate_db,clips,expected_onsets,reported_onsets,extra,missed,clips_with_extra");
		for (double factor : new double[] { 1.5, 2.0, 3.0 }) {
			for (double gateDb : new double[] { 1.5, 3.0, 4.5, 6.0 }) {
				int clips = 0;
				int expected = 0;
				int reported = 0;
				int extra = 0;
				int missed = 0;
				int clipsWithExtra = 0;
				for (int i = 0; i < fixtures.size(); i++) {
					BenchmarkFixtures.Fixture fixture = fixtures.get(i);
					if (fixture.expectedOnsets == null || !("reference".equals(fixture.role) || "known-issue".equals(fixture.role))) {
						continue;
					}
					ArticulationDetector detector = new ArticulationDetector(factor, ArticulationDetector.DEFAULT_FLUX_OFFSET,
							ArticulationDetector.DEFAULT_MIN_LEVEL_DB, ArticulationDetector.DEFAULT_REFRACTORY_HOPS, gateDb);
					int count = onsetTimes(detector, samples.get(i)).size();
					clips++;
					expected += fixture.expectedOnsets;
					reported += count;
					extra += Math.max(0, count - fixture.expectedOnsets);
					missed += Math.max(0, fixture.expectedOnsets - count);
					clipsWithExtra += (count > fixture.expectedOnsets ? 1 : 0);
				}
				rows.add(factor + "," + gateDb + "," + clips + "," + expected + "," + reported + "," + extra + "," + missed + "," + clipsWithExtra);
			}
		}
		return rows;
	}

	/** Median deviation, in cents from the played note, of the frames on that note (tolerance evidence). */
	private static List<String> cents(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("file,played_note,frames_on_note,median_cents,p10_cents,p90_cents");
		app.tuxguitar.audio.engine.pitch.NoteInterpreter interpreter = new app.tuxguitar.audio.engine.pitch.NoteInterpreter(BenchmarkFixtures.CONFIDENCE_GATE);
		InputAssessment suitable = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		for (int i = 0; i < fixtures.size(); i++) {
			BenchmarkFixtures.Fixture fixture = fixtures.get(i);
			if (!"reference".equals(fixture.role)) {
				continue;
			}
			TarsosFastYinEstimator estimator = new TarsosFastYinEstimator();
			float[] window = new float[BenchmarkFixtures.WINDOW_SIZE];
			float[] clip = samples.get(i);
			List<Double> values = new ArrayList<>();
			for (int offset = 0; offset + window.length <= clip.length; offset += BenchmarkFixtures.HOP_SIZE) {
				System.arraycopy(clip, offset, window, 0, window.length);
				app.tuxguitar.audio.engine.pitch.PitchReading reading = interpreter.interpret(estimator.estimate(window), suitable);
				if (reading.hasPitch() && reading.isReliable() && reading.getMidi() == fixture.manifestMidi) {
					values.add(reading.getCents());
				}
			}
			values.sort(null);
			rows.add(String.join(",", fixture.file, fixture.playedNote, Integer.toString(values.size()),
					percentile(values, 0.5), percentile(values, 0.1), percentile(values, 0.9)));
		}
		return rows;
	}

	/** Reliable readings in the silence clip with no level floor: false low readings (T077). */
	private static List<String> silence(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("confidence_gate,frames,reliable_frames,reliable_notes");
		InputAssessment suitable = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		for (int i = 0; i < fixtures.size(); i++) {
			if (!"silence".equals(fixtures.get(i).role)) {
				continue;
			}
			for (double gate : GATES) {
				app.tuxguitar.audio.engine.pitch.NoteInterpreter interpreter = new app.tuxguitar.audio.engine.pitch.NoteInterpreter(gate);
				TarsosFastYinEstimator estimator = new TarsosFastYinEstimator();
				float[] window = new float[BenchmarkFixtures.WINDOW_SIZE];
				float[] clip = samples.get(i);
				int frames = 0;
				java.util.TreeMap<String, Integer> notes = new java.util.TreeMap<>();
				for (int offset = 0; offset + window.length <= clip.length; offset += BenchmarkFixtures.HOP_SIZE) {
					System.arraycopy(clip, offset, window, 0, window.length);
					app.tuxguitar.audio.engine.pitch.PitchReading reading = interpreter.interpret(estimator.estimate(window), suitable);
					frames++;
					if (reading.hasPitch() && reading.isReliable()) {
						notes.merge(reading.getNoteName() + reading.getOctave(), 1, Integer::sum);
					}
				}
				int reliable = notes.values().stream().mapToInt(Integer::intValue).sum();
				rows.add(gate + "," + frames + "," + reliable + "," + notes.toString().replace(",", ";"));
			}
		}
		return rows;
	}

	private static String percentile(List<Double> sorted, double fraction) {
		return sorted.isEmpty() ? "NaN" : String.format(Locale.ROOT, "%.1f", sorted.get((int) Math.min(sorted.size() - 1, Math.floor(fraction * sorted.size()))));
	}

	/** The fast-run detector (T048) against the MVP one on the same real clips: false articulations and misses per gate. */
	private static List<String> fastArticulationSweep(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("detector,gate_db,clips,expected_onsets,reported_onsets,extra,missed,clips_with_extra");
		for (boolean fast : new boolean[] { false, true }) {
			for (double gateDb : new double[] { 1.5, 3.0, 4.5, 6.0 }) {
				int clips = 0;
				int expected = 0;
				int reported = 0;
				int extra = 0;
				int missed = 0;
				int clipsWithExtra = 0;
				for (int i = 0; i < fixtures.size(); i++) {
					BenchmarkFixtures.Fixture fixture = fixtures.get(i);
					if (fixture.expectedOnsets == null || !("reference".equals(fixture.role) || "known-issue".equals(fixture.role))) {
						continue;
					}
					ArticulationDetector detector = (fast ? ArticulationDetector.fast(ArticulationDetector.DEFAULT_MIN_LEVEL_DB, gateDb)
							: new ArticulationDetector(ArticulationDetector.DEFAULT_FLUX_MEDIAN_FACTOR, ArticulationDetector.DEFAULT_FLUX_OFFSET,
									ArticulationDetector.DEFAULT_MIN_LEVEL_DB, ArticulationDetector.DEFAULT_REFRACTORY_HOPS, gateDb));
					int count = onsetTimes(detector, samples.get(i)).size();
					clips++;
					expected += fixture.expectedOnsets;
					reported += count;
					extra += Math.max(0, count - fixture.expectedOnsets);
					missed += Math.max(0, fixture.expectedOnsets - count);
					clipsWithExtra += (count > fixture.expectedOnsets ? 1 : 0);
				}
				rows.add((fast ? "fast" : "mvp") + "," + gateDb + "," + clips + "," + expected + "," + reported + "," + extra + "," + missed + "," + clipsWithExtra);
			}
		}
		return rows;
	}

	private static List<Double> onsetTimes(ArticulationDetector detector, float[] clip) {
		List<Double> times = new ArrayList<>();
		float[] window = new float[BenchmarkFixtures.WINDOW_SIZE];
		for (int hop = 0; (hop + 1) * BenchmarkFixtures.HOP_SIZE <= clip.length; hop++) {
			System.arraycopy(window, BenchmarkFixtures.HOP_SIZE, window, 0, window.length - BenchmarkFixtures.HOP_SIZE);
			System.arraycopy(clip, hop * BenchmarkFixtures.HOP_SIZE, window, window.length - BenchmarkFixtures.HOP_SIZE, BenchmarkFixtures.HOP_SIZE);
			if (detector.process(window)) {
				int transientHop = hop - detector.getDecisionDelayHops();
				times.add(((transientHop + 1) * BenchmarkFixtures.HOP_SIZE - 512) / (double) BenchmarkFixtures.SAMPLE_RATE);
			}
		}
		return times;
	}

	/** Articulations reported by the provisional detector on each clip (FR-022 evidence). */
	private static List<String> articulation(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("file,fixture_role,expected_onsets,reported_onsets,onset_times_s");
		for (int i = 0; i < fixtures.size(); i++) {
			BenchmarkFixtures.Fixture fixture = fixtures.get(i);
			ArticulationDetector detector = new ArticulationDetector();
			float[] window = new float[BenchmarkFixtures.WINDOW_SIZE];
			float[] clip = samples.get(i);
			List<String> times = new ArrayList<>();
			for (int hop = 0; (hop + 1) * BenchmarkFixtures.HOP_SIZE <= clip.length; hop++) {
				System.arraycopy(window, BenchmarkFixtures.HOP_SIZE, window, 0, window.length - BenchmarkFixtures.HOP_SIZE);
				System.arraycopy(clip, hop * BenchmarkFixtures.HOP_SIZE, window, window.length - BenchmarkFixtures.HOP_SIZE, BenchmarkFixtures.HOP_SIZE);
				if (detector.process(window)) {
					int transientHop = hop - ArticulationDetector.DECISION_DELAY_HOPS;
					times.add(String.format(Locale.ROOT, "%.3f", ((transientHop + 1) * BenchmarkFixtures.HOP_SIZE - 512) / (double) BenchmarkFixtures.SAMPLE_RATE));
				}
			}
			rows.add(String.join(",", fixture.file, fixture.role,
					fixture.expectedOnsets == null ? "" : Integer.toString(fixture.expectedOnsets),
					Integer.toString(times.size()), String.join(" ", times)));
		}
		return rows;
	}

	/** How the provisional level check classifies each clip, frames fed as the engine does. */
	private static List<String> levelCheck(List<BenchmarkFixtures.Fixture> fixtures, List<float[]> samples) {
		List<String> rows = new ArrayList<>();
		rows.add("file,fixture_role,assessment_status,assessment_reason");
		LevelAnalyzer levels = new LevelAnalyzer(-40.0, -6.0, 0.99, 0.10);
		for (int i = 0; i < fixtures.size(); i++) {
			BenchmarkFixtures.Fixture fixture = fixtures.get(i);
			List<InputAssessment> results = new ArrayList<>();
			InputAssessor assessor = new InputAssessor(new InputAssessor.Parameters(), results::add);
			assessor.runLevelCheck();
			TarsosFastYinEstimator estimator = new TarsosFastYinEstimator();
			float[] window = new float[BenchmarkFixtures.WINDOW_SIZE];
			float[] clip = samples.get(i);
			for (int offset = 0, frame = 0; offset + window.length <= clip.length && results.isEmpty(); offset += BenchmarkFixtures.HOP_SIZE, frame++) {
				System.arraycopy(clip, offset, window, 0, window.length);
				LevelMeasurement level = levels.analyze(window);
				PitchEstimate pitch = estimator.estimate(window);
				assessor.analysisFrame(new AnalysisFrame(frame * (long) BenchmarkFixtures.HOP_SIZE * 1_000_000_000L / BenchmarkFixtures.SAMPLE_RATE,
						level.getRmsDbfs(), level.getPeakDbfs(), level.getClippedRatio(), level.getLevelState(),
						pitch.isPitched() ? pitch.getF0Hz() : Double.NaN, pitch.getProbability(), false));
			}
			InputAssessment result = results.isEmpty() ? null : results.get(0);
			rows.add(String.join(",", fixture.file, fixture.role,
					result == null ? "not concluded" : result.getStatus().name(),
					result == null ? "" : result.getReason().name()));
		}
		return rows;
	}

	private static Path reportDirectory() throws Exception {
		for (Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); path != null; path = path.getParent()) {
			if (Files.isDirectory(path.resolve("specs/001-interactive-practice-mvp"))) {
				Path directory = path.resolve("specs/001-interactive-practice-mvp/benchmark");
				Files.createDirectories(directory);
				return directory;
			}
		}
		assertFalse(true, "could not locate repository root for benchmark reports");
		return null;
	}
}
