package app.tuxguitar.audio.engine.benchmark;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.frontend.FrontEnd;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

/**
 * Spike S-B1 (spec 002, task T042): does the input front end help or hurt recognition on the real
 * built-in-microphone clips? Every clip is preceded by 2 s of the same microphone's own silence clip
 * (the background noise the gate learns from), then run through four conditions: no front end, the
 * noise gate (rumble filter + gate), gate + spectral subtraction, and the same on a clip made worse
 * with mains hum and extra broadband noise. Reported, not asserted: the decision (default of the
 * subtraction) is the owner's, from the table in the report.
 */
@Tag("benchmark")
class FrontEndBenchmarkTest {

	private static final int HOP = BenchmarkFixtures.HOP_SIZE;
	private static final int RATE = BenchmarkFixtures.SAMPLE_RATE;

	private enum Condition {
		NONE, GATE, GATE_AND_SUBTRACTION
	}

	private static float[] through(float[] samples, Condition condition) {
		FrontEnd frontEnd = new FrontEnd();
		if( condition != Condition.NONE ) {
			frontEnd.setNoiseReduction(true);
			frontEnd.setSubtraction(condition == Condition.GATE_AND_SUBTRACTION);
		}
		float[] out = samples.clone();
		float[] hop = new float[HOP];
		for( int start = 0 ; start + HOP <= out.length ; start += HOP ) {
			System.arraycopy(out, start, hop, 0, HOP);
			frontEnd.process(hop, HOP);
			System.arraycopy(hop, 0, out, start, HOP);
		}
		return out;
	}

	private static float[] lead(float[] silence, int samples) {
		float[] lead = new float[samples];
		for( int i = 0 ; i < samples ; i ++ ) {
			lead[i] = silence[i % silence.length];
		}
		return lead;
	}

	private static float[] concat(float[] a, float[] b) {
		float[] result = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	private static float[] degrade(float[] samples) {
		float[] worse = samples.clone();
		TechniqueSignals.hum(RATE, samples.length, 50.0, 0.006);
		float[] hum = TechniqueSignals.hum(RATE, samples.length, 50.0, 0.006);
		float[] noise = app.tuxguitar.audio.engine.testsupport.SyntheticSignals.noise(samples.length, 0.006, 21L);
		for( int i = 0 ; i < worse.length ; i ++ ) {
			worse[i] += hum[i] + noise[i];
		}
		return worse;
	}

	@Test
	void writeWithAndWithoutReport() throws Exception {
		BenchmarkFixtures.Recording silenceRecording = null;
		List<BenchmarkFixtures.Fixture> clips = new ArrayList<>();
		for( BenchmarkFixtures.Fixture fixture : BenchmarkFixtures.readManifest() ) {
			if( !"builtin-mic".equals(fixture.inputType) ) {
				continue;
			}
			if( "silence".equals(fixture.role) ) {
				silenceRecording = BenchmarkFixtures.readRecording(fixture);
			} else if( fixture.manifestMidi != null ) {
				clips.add(fixture);
			}
		}
		assertTrue(silenceRecording != null && !clips.isEmpty(), "the built-in microphone clips are needed");
		float[] leadIn = lead(silenceRecording.samples, 2 * RATE);

		List<String> rows = new ArrayList<>();
		rows.add("condition,input,clips,correct_stable_note,stable_note_clips,median_stable_latency_ms,median_from_clip_start_ms,added_latency_ms");
		StringBuilder table = new StringBuilder();
		for( boolean worse : new boolean[] { false, true } ) {
			for( Condition condition : Condition.values() ) {
				int correct = 0;
				int stable = 0;
				List<Double> latencies = new ArrayList<>();
				List<Double> fromClipStart = new ArrayList<>();
				for( BenchmarkFixtures.Fixture fixture : clips ) {
					BenchmarkFixtures.Recording clip = BenchmarkFixtures.readRecording(fixture);
					float[] signal = concat(leadIn, clip.samples);
					if( worse ) {
						signal = degrade(signal);
					}
					float[] processed = through(signal, condition);
					BenchmarkFixtures.Result result = BenchmarkFixtures.analyze(new BenchmarkFixtures.Recording(processed, clip.sha256));
					if( result.stableMidi >= 0 ) {
						stable ++;
						if( result.stableMidi == fixture.manifestMidi ) {
							correct ++;
						}
						double latency = result.stableLatencySeconds();
						if( !Double.isNaN(latency) ) {
							latencies.add(latency * 1000.0);
						}
						// independent of where the level-based onset detection fires: the end of the window
						// of the first stable frame, counted from the real start of the clip (after the lead-in)
						fromClipStart.add(((result.stableFrame * (double) HOP + BenchmarkFixtures.WINDOW_SIZE) - leadIn.length) * 1000.0 / RATE);
					}
				}
				java.util.Collections.sort(latencies);
				java.util.Collections.sort(fromClipStart);
				double medianFromStart = fromClipStart.isEmpty() ? Double.NaN : fromClipStart.get(fromClipStart.size() / 2);
				double median = latencies.isEmpty() ? Double.NaN : latencies.get(latencies.size() / 2);
				double added = (condition == Condition.GATE_AND_SUBTRACTION ? HOP * 1000.0 / RATE : 0.0);
				String row = String.format(java.util.Locale.ROOT, "%s,%s,%d,%d,%d,%.0f,%.0f,%.1f",
						condition, worse ? "builtin-mic+hum+noise" : "builtin-mic", clips.size(), correct, stable, median, medianFromStart, added);
				rows.add(row);
				table.append(row).append('\n');
			}
		}
		System.out.println("FRONT-END BENCHMARK (S-B1)\n" + table);

		Path repository = null;
		for( Path path = Paths.get(System.getProperty("user.dir")).toAbsolutePath() ; path != null ; path = path.getParent() ) {
			if( Files.isDirectory(path.resolve("specs/002-practice-industrialization")) ) {
				repository = path;
				break;
			}
		}
		assertFalse(repository == null, "could not locate the specs directory for the report");
		Path output = repository.resolve("specs/002-practice-industrialization/benchmark/frontend-with-without.csv");
		Files.createDirectories(output.getParent());
		Files.write(output, rows, StandardCharsets.UTF_8);
	}
}
