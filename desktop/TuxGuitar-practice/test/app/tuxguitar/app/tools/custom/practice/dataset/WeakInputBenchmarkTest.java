package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;

/**
 * What a weak or noisy input does to recognition (spec 002 T050, FR-015): real single notes (IDMT-SMT-Guitar dataset 1, plain notes) are
 * scaled so that the strongest 2048-sample window reaches a chosen level, and room noise of a chosen level is added; Practice expecting
 * the note must accept it. The table gives the recognition rate by level and noise, and so the signal level and the signal-to-noise ratio
 * below which Practice should tell the learner that the input is too poor. Same data rules as the other dataset benchmarks.
 */
@Tag("dataset")
public class WeakInputBenchmarkTest {

	static final double[] LEVELS = { -15, -25, -30, -35, -40, -45, -50 };
	/** Noise RMS in dBFS; null = none. */
	static final Double[] NOISE = { null, -65.0, -55.0, -50.0, -45.0, -40.0 };

	static double peakWindowRmsDb(float[] samples) {
		double best = 0.0;
		for( int start = 0 ; start + 2048 <= samples.length ; start += 1024 ) {
			double sum = 0.0;
			for( int i = start ; i < start + 2048 ; i ++ ) {
				sum += samples[i] * (double) samples[i];
			}
			best = Math.max(best, sum / 2048.0);
		}
		return 10.0 * Math.log10(Math.max(best, 1e-20));
	}

	static RecordedAudio condition(RecordedAudio clip, double levelDb, Double noiseDb, long seed) {
		double gain = Math.pow(10.0, (levelDb - peakWindowRmsDb(clip.samples)) / 20.0);
		float[] out = new float[clip.samples.length];
		Random random = new Random(seed);
		double noise = (noiseDb == null ? 0.0 : Math.pow(10.0, noiseDb / 20.0));
		for( int i = 0 ; i < out.length ; i ++ ) {
			out[i] = (float) (clip.samples[i] * gain + (noiseDb == null ? 0.0 : noise * random.nextGaussian()));
		}
		return RecordedAudio.of(out, clip.sampleRate);
	}

	@Test
	public void recognitionOfRealNotesAtWeakLevelsAndWithNoise() throws IOException {
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
		List<DatasetCase> singles = DatasetLoaders.idmtGuitar(root, 12).stream().filter(c -> c.kind == DatasetCase.Kind.SINGLE_NOTE && c.played[0] >= 40 && c.played[0] <= 86).collect(Collectors.toList());
		assumeTrue(!singles.isEmpty(), "IDMT-SMT-Guitar dataset 1 not found under " + root.toAbsolutePath());
		StringBuilder text = new StringBuilder();
		text.append("WEAK INPUT BENCHMARK (spec 002 T050, FR-015): recognition of real single notes by signal level and added room noise\n");
		text.append("Data: IDMT-SMT-Guitar V2 dataset 1, plain single notes (CC BY-NC-ND 4.0, kept outside the repository), ").append(singles.size()).append(" notes of ")
				.append(singles.stream().map(c -> c.group).distinct().count()).append(" guitars. Level = RMS of the strongest 2048-sample window of the clip (dBFS); noise = white Gaussian, RMS (dBFS).\n");
		text.append("Recognised = a session expecting the note accepts it (default settings). Rows: signal level; columns: noise level; each cell the share of notes recognised.\n\n");
		text.append(String.format("%-14s", "level \\ noise"));
		for( Double n : NOISE ) {
			text.append(String.format("%10s", n == null ? "none" : String.format("%.0f dB", n)));
		}
		text.append("\n");
		AtomicInteger seed = new AtomicInteger(1);
		for( double level : LEVELS ) {
			text.append(String.format("%-14s", String.format("%.0f dBFS", level)));
			for( Double n : NOISE ) {
				AtomicInteger ok = new AtomicInteger();
				AtomicInteger total = new AtomicInteger();
				singles.parallelStream().forEach(c -> {
					RecordedAudio clip = RecordedAudio.load(c.audio);
					if( clip == null ) {
						return;
					}
					total.incrementAndGet();
					if( DatasetJudge.accepts(condition(clip, level, n, 1000 + seed.get()), c.played) ) {
						ok.incrementAndGet();
					}
				});
				text.append(String.format(java.util.Locale.ROOT, "%9.0f%%", 100.0 * ok.get() / Math.max(1, total.get())));
				seed.incrementAndGet();
			}
			text.append("\n");
		}
		// the same, for three notes in a row 0.35 s apart (the closest spacing that is followed): the margin is smaller than for one note
		text.append("\nThree different notes in a row, 0.35 s apart (share of the triples followed in full, in order), same levels and noises:\n\n");
		List<NoteSpacingBenchmarkTest.Triple> triples = NoteSpacingBenchmarkTest.triples(singles);
		text.append(String.format("%-14s", "level \\ noise"));
		for( Double n : NOISE ) {
			text.append(String.format("%10s", n == null ? "none" : String.format("%.0f dB", n)));
		}
		text.append("\n");
		for( double level : LEVELS ) {
			text.append(String.format("%-14s", String.format("%.0f dBFS", level)));
			for( Double n : NOISE ) {
				AtomicInteger ok = new AtomicInteger();
				AtomicInteger total = new AtomicInteger();
				triples.parallelStream().forEach(t -> {
					NoteSpacingBenchmarkTest.Result r = NoteSpacingBenchmarkTest.run(t, 0.35, app.tuxguitar.app.tools.custom.practice.session.PolyphonicAcceptance.Parameters.defaults(),
							audio -> condition(audio, level, n, 77));
					if( r != null ) {
						total.incrementAndGet();
						if( r.allInOrder ) {
							ok.incrementAndGet();
						}
					}
				});
				text.append(String.format(java.util.Locale.ROOT, "%9.0f%%", 100.0 * ok.get() / Math.max(1, total.get())));
			}
			text.append("\n");
		}
		String report = text.toString();
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("weak-input-report.txt"), report.getBytes(StandardCharsets.UTF_8));
		assertTrue(report.length() > 0);
	}

	/** The same weak notes through the input front end the application applies before the analysis (gain, then optionally the noise gate and subtraction). */
	static RecordedAudio throughFrontEnd(RecordedAudio audio, double gainDb, boolean noiseReduction, boolean subtraction, double noiseDb, long seed) {
		int rate = audio.sampleRate;
		// the gate learns the background noise from the start of the input: 1.5 s of the same noise first (the learner presses Start, then plays)
		int lead = (int) (1.5 * rate);
		float[] samples = new float[lead + audio.samples.length];
		Random random = new Random(seed + 5);
		double noise = Math.pow(10.0, noiseDb / 20.0);
		for( int i = 0 ; i < lead ; i ++ ) {
			samples[i] = (float) (noise * random.nextGaussian());
		}
		System.arraycopy(audio.samples, 0, samples, lead, audio.samples.length);
		app.tuxguitar.audio.engine.frontend.FrontEnd frontEnd = new app.tuxguitar.audio.engine.frontend.FrontEnd();
		frontEnd.configure(rate, 256);
		frontEnd.getGain().setMultiplier(Math.pow(10.0, gainDb / 20.0));
		frontEnd.setNoiseReduction(noiseReduction);
		frontEnd.setSubtraction(subtraction);
		float[] hop = new float[256];
		for( int start = 0 ; start + 256 <= samples.length ; start += 256 ) {
			System.arraycopy(samples, start, hop, 0, 256);
			frontEnd.process(hop, 256);
			System.arraycopy(hop, 0, samples, start, 256);
		}
		return RecordedAudio.of(samples, rate);
	}

	/** Spec 002 T135: what raising the input gain, and the noise gate and subtraction, do for weak and noisy notes (the remedy the warning proposes). */
	@Test
	public void weakAndNoisyNotesThroughTheFrontEnd() throws IOException {
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"));
		List<DatasetCase> singles = DatasetLoaders.idmtGuitar(root, 12).stream().filter(c -> c.kind == DatasetCase.Kind.SINGLE_NOTE && c.played[0] >= 40 && c.played[0] <= 86).collect(Collectors.toList());
		assumeTrue(!singles.isEmpty(), "IDMT-SMT-Guitar dataset 1 not found under " + root.toAbsolutePath());
		StringBuilder text = new StringBuilder();
		text.append("WEAK AND NOISY NOTES THROUGH THE INPUT FRONT END (spec 002 T135): share of ").append(singles.size()).append(" real notes recognised\n");
		text.append("Data: IDMT-SMT-Guitar V2 dataset 1, plain single notes (CC BY-NC-ND 4.0, kept outside the repository); level = RMS of the strongest 2048-sample window before the front end, white noise added before it; the gain is the application's input gain (-6 to +18 dB).\n\n");
		text.append(String.format("%-34s %12s %12s %12s%n", "condition", "-45 dBFS", "-40 dBFS", "-35 dBFS"));
		double[] levels = { -45, -40, -35 };
		Object[][] conditions = {
				{ "no front end, no noise", 0.0, false, false, null },
				{ "gain +0 dB, noise -50 dBFS", 0.0, false, false, -50.0 },
				{ "gain +6 dB, noise -50 dBFS", 6.0, false, false, -50.0 },
				{ "gain +12 dB, noise -50 dBFS", 12.0, false, false, -50.0 },
				{ "gain +18 dB, noise -50 dBFS", 18.0, false, false, -50.0 },
				{ "gate, noise -50 dBFS", 0.0, true, false, -50.0 },
				{ "gate + subtraction, noise -50 dBFS", 0.0, true, true, -50.0 },
				{ "gain +12 dB + gate + subtraction", 12.0, true, true, -50.0 },
				{ "gain +12 dB, noise -40 dBFS", 12.0, false, false, -40.0 },
				{ "gain +12 dB + gate + subtraction, noise -40 dBFS", 12.0, true, true, -40.0 } };
		for( Object[] condition : conditions ) {
			text.append(String.format("%-34s", condition[0]));
			for( double level : levels ) {
				AtomicInteger ok = new AtomicInteger();
				AtomicInteger total = new AtomicInteger();
				Double noise = (Double) condition[4];
				singles.parallelStream().forEach(c -> {
					RecordedAudio clip = RecordedAudio.load(c.audio);
					if( clip == null ) {
						return;
					}
					total.incrementAndGet();
					RecordedAudio weak = condition(clip, level, noise, 31);
					RecordedAudio processed = (noise == null && (Double) condition[1] == 0.0 && !(Boolean) condition[2]) ? weak
							: throughFrontEnd(weak, (Double) condition[1], (Boolean) condition[2], (Boolean) condition[3], noise == null ? -90.0 : noise, 31);
					if( DatasetJudge.accepts(processed, c.played) ) {
						ok.incrementAndGet();
					}
				});
				text.append(String.format(java.util.Locale.ROOT, "%11.0f%%", 100.0 * ok.get() / Math.max(1, total.get())));
			}
			text.append("\n");
		}
		String report = text.toString();
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("weak-input-frontend-report.txt"), report.getBytes(StandardCharsets.UTF_8));
		assertTrue(report.length() > 0);
	}
}
