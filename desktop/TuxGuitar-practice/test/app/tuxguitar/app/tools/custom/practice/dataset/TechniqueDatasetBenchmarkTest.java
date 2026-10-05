package app.tuxguitar.app.tools.custom.practice.dataset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.DetectableTechnique;
import app.tuxguitar.audio.engine.technique.NoteSegment;
import app.tuxguitar.audio.engine.technique.SlideDetector;
import app.tuxguitar.audio.engine.technique.TechniqueDetector;
import app.tuxguitar.audio.engine.technique.TechniqueEvidence;
import app.tuxguitar.audio.engine.technique.VibratoDetector;

/**
 * The technique detectors against the annotated notes of IDMT-SMT-Guitar dataset 2 (spec 002, research
 * R4): for each labelled note (vibrato VI, bend BE, slide SL, plain NO) the pitch track of the note, from
 * 120 ms after its onset to its offset, goes to the detector, and the verdicts are counted by label. The
 * numbers justify the {@code verified} flags of {@code TechniqueCatalog}: a technique is verified only
 * when at least 85 % of its notes are detected and under 10 % of the plain notes are wrongly detected.
 *
 * <p>Data: {@code -Dtuxguitar.datasets=<dir>} (default {@code ~/tuxguitar-datasets}); never committed,
 * never copied. Only the verdict counts are written (to {@code target/dataset-benchmark}).</p>
 */
@Tag("dataset")
public class TechniqueDatasetBenchmarkTest {

	private static final Pattern EVENT = Pattern.compile("<event>(.*?)</event>", Pattern.DOTALL);
	private static final double ATTACK_SKIP_SECONDS = 0.12;
	/** The analysis window is centred 23 ms before the hop's end. */
	private static final double WINDOW_DELAY_SECONDS = 0.0232;
	private static final double MIN_CONFIDENCE = 0.8;

	private static String tag(String xml, String name) {
		Matcher m = Pattern.compile("<" + name + ">([^<]*)</" + name + ">").matcher(xml);
		return m.find() ? m.group(1) : null;
	}

	private static float[] readMono(File file, int[] rate) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
			AudioFormat format = in.getFormat();
			rate[0] = (int) format.getSampleRate();
			int bytes = format.getSampleSizeInBits() / 8;
			byte[] data = in.readAllBytes();
			float[] samples = new float[data.length / bytes];
			for( int i = 0 ; i < samples.length ; i ++ ) {
				int value = 0;
				for( int k = bytes - 1 ; k >= 0 ; k -- ) {
					value = (value << 8) | (k == bytes - 1 ? data[i * bytes + k] : (data[i * bytes + k] & 0xff));
				}
				samples[i] = (float) (value / Math.pow(2, 8 * bytes - 1));
			}
			return samples;
		}
	}

	@Test
	public void theDetectorsAgainstTheAnnotatedExpressionStylesOfIdmtDataset2() throws Exception {
		Path root = Paths.get(System.getProperty("tuxguitar.datasets", System.getProperty("user.home") + "/tuxguitar-datasets"),
				"guitar2", "IDMT-SMT-GUITAR_V2", "dataset2");
		assumeTrue(Files.isDirectory(root.resolve("annotation")), "IDMT-SMT-Guitar dataset 2 not found under " + root.toAbsolutePath());
		int sample = Integer.getInteger("tuxguitar.datasets.sample", 0);
		List<Path> annotations = new ArrayList<Path>();
		try (java.util.stream.Stream<Path> stream = Files.list(root.resolve("annotation"))) {
			stream.filter(p -> p.toString().endsWith(".xml")).sorted().forEach(annotations::add);
		}
		if( sample > 0 && annotations.size() > sample ) {
			annotations = annotations.subList(0, sample);
		}
		TechniqueDetector[] detectors = { new VibratoDetector(VibratoDetector.Parameters.defaults()), new BendDetector(BendDetector.Parameters.defaults()),
				new SlideDetector(SlideDetector.Parameters.defaults()) };
		// technique -> label -> [detected, not detected, uncertain]
		Map<DetectableTechnique, Map<String, int[]>> counts = new EnumMap<DetectableTechnique, Map<String, int[]>>(DetectableTechnique.class);
		int recordings = 0;
		for( Path annotation : annotations ) {
			String name = annotation.getFileName().toString().replace(".xml", "");
			File wav = root.resolve("audio").resolve(name + ".wav").toFile();
			if( !wav.isFile() ) {
				continue;
			}
			int[] rate = new int[1];
			float[] audio = readMono(wav, rate);
			List<AnalysisFrame> frames = OfflineAnalysis.analyze(audio, rate[0], RecognitionSettings.defaults(), false, null);
			double hop = (double) OfflineAnalysis.HOP / rate[0];
			recordings ++;
			Matcher events = EVENT.matcher(new String(Files.readAllBytes(annotation), StandardCharsets.UTF_8));
			while( events.find() ) {
				String event = events.group(1);
				String label = tag(event, "expressionStyle");
				double onset = Double.parseDouble(tag(event, "onsetSec"));
				double offset = Double.parseDouble(tag(event, "offsetSec"));
				List<double[]> points = new ArrayList<double[]>();
				for( AnalysisFrame frame : frames ) {
					double t = frame.getTimestampNanos() / 1e9 - WINDOW_DELAY_SECONDS;
					if( t < onset + ATTACK_SKIP_SECONDS || t > offset ) {
						continue;
					}
					boolean voiced = Double.isFinite(frame.getF0Hz()) && frame.getConfidence() > MIN_CONFIDENCE;
					points.add(new double[] { voiced ? 69.0 + 12.0 * Math.log(frame.getF0Hz() / 440.0) / Math.log(2.0) : Double.NaN, frame.getRmsDbfs() });
				}
				if( points.size() < 4 ) {
					continue;
				}
				double[] midi = new double[points.size()];
				double[] level = new double[points.size()];
				for( int i = 0 ; i < midi.length ; i ++ ) {
					midi[i] = points.get(i)[0];
					level[i] = points.get(i)[1];
				}
				NoteSegment segment = new NoteSegment(midi, level, new boolean[midi.length], hop);
				for( TechniqueDetector detector : detectors ) {
					TechniqueEvidence evidence = detector.evaluate(segment);
					counts.computeIfAbsent(detector.technique(), k -> new TreeMap<String, int[]>()).computeIfAbsent(label, k -> new int[3])[evidence.getVerdict().ordinal()] ++;
				}
			}
		}
		assumeTrue(recordings > 0, "no recording of dataset 2 could be read");
		String report = report(recordings, counts);
		System.out.println(report);
		Path target = Paths.get("target/dataset-benchmark");
		Files.createDirectories(target);
		Files.write(target.resolve("technique-report.txt"), Collections.singletonList(report), StandardCharsets.UTF_8);
		// regression guards (measured 2026-10-06): plain notes are rarely taken for a technique
		for( DetectableTechnique technique : counts.keySet() ) {
			int[] plain = counts.get(technique).get("NO");
			if( plain != null ) {
				int total = plain[0] + plain[1] + plain[2];
				assertTrue(plain[0] <= 0.06 * total, technique + " wrongly detected on " + plain[0] + " of " + total + " plain notes");
			}
		}
	}

	private static String report(int recordings, Map<DetectableTechnique, Map<String, int[]>> counts) {
		StringBuilder text = new StringBuilder();
		text.append("TECHNIQUE BENCHMARK (IDMT-SMT-Guitar dataset 2, ").append(recordings).append(" recordings)\n");
		String[][] wanted = { { "VIBRATO", "VI" }, { "BEND", "BE" }, { "SLIDE", "SL" } };
		for( String[] pair : wanted ) {
			Map<String, int[]> byLabel = counts.get(DetectableTechnique.valueOf(pair[0]));
			if( byLabel == null ) {
				continue;
			}
			text.append('\n').append(pair[0]).append(": verdicts by annotated style (detected / not detected / uncertain)\n");
			for( Map.Entry<String, int[]> entry : byLabel.entrySet() ) {
				int[] c = entry.getValue();
				text.append(String.format("  %-3s %5d %5d %5d%n", entry.getKey(), c[0], c[1], c[2]));
			}
			int[] technique = byLabel.get(pair[1]);
			int[] plain = byLabel.get("NO");
			if( technique != null && plain != null ) {
				int techniqueTotal = technique[0] + technique[1] + technique[2];
				int plainTotal = plain[0] + plain[1] + plain[2];
				boolean verified = technique[0] >= 0.85 * techniqueTotal && plain[0] < 0.10 * plainTotal;
				text.append(String.format("  detected on %.0f%% of %d %s notes (target 85%%), wrongly on %.1f%% of %d plain notes (limit 10%%) -> %s%n",
						100.0 * technique[0] / techniqueTotal, techniqueTotal, pair[1], 100.0 * plain[0] / plainTotal, plainTotal,
						verified ? "may be verified" : "stays not verifiable"));
			}
		}
		return text.toString();
	}
}
