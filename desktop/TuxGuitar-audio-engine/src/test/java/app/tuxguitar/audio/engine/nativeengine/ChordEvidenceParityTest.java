package app.tuxguitar.audio.engine.nativeengine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;

/**
 * The native chord evidence gives that of {@code ChordEvidence.evaluate(spectrum, required, tolerated)} (spec 023 T023): for the owner's chords (their
 * labelled pitches, then a neighbour chord), GuitarSet and the other recordings (two fixed chords), every field within 1e-4 relative.
 */
public class ChordEvidenceParityTest {

	private static final int[] C_MAJOR = { 48, 52, 55, 60, 64 };
	private static final int[] E_MAJOR = { 40, 47, 52, 56, 59, 64 };

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void everyChordGivesTheSameEvidence() throws Exception {
		Map<String, int[]> labels = labels();
		for (ParityRecordings.Recording recording : ParityRecordings.all()) {
			int[] own = labels.get(recording.name);
			if (own != null) {
				compare(recording, own, new int[0]);
				int[] neighbour = own.clone();
				neighbour[neighbour.length - 1] += 2;
				compare(recording, neighbour, new int[] { own[0] - 5 });
			} else if (recording.name.startsWith("guitarset") || recording.name.startsWith("module")) {
				compare(recording, C_MAJOR, new int[0]);
				compare(recording, E_MAJOR, new int[] { 45 });
			}
		}
	}

	private static void compare(ParityRecordings.Recording recording, int[] required, int[] tolerated) {
		ParityEngines.Request request = new ParityEngines.Request();
		request.chordRequired = required;
		request.chordTolerated = tolerated;
		List<AnalysisFrame> java = ParityEngines.java(recording.mono, recording.rate, request);
		List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(recording.mono, recording.rate, request);
		Assertions.assertEquals(java.size(), nativeFrames.size(), recording.toString());
		for (int i = 0; i < java.size(); i++) {
			String label = recording + " frame " + i + " chord " + Arrays.toString(required);
			ChordEvidence a = java.get(i).getChordEvidence();
			ChordEvidence b = nativeFrames.get(i).getChordEvidence();
			Assertions.assertNotNull(b, label);
			Assertions.assertNull(nativeFrames.get(i).getSpectrum(), label + ": the spectrum is not asked");
			Assertions.assertEquals(a.getPitches().size(), b.getPitches().size(), label);
			double scale = 0.0;
			for (ChordEvidence.Pitch p : a.getPitches()) {
				scale = Math.max(scale, Math.max(p.getSalience(), p.getMeanAmplitude()));
			}
			for (int p = 0; p < a.getPitches().size(); p++) {
				ChordEvidence.Pitch x = a.getPitches().get(p);
				ChordEvidence.Pitch y = b.getPitches().get(p);
				String at = label + " pitch " + x.getMidi();
				Assertions.assertEquals(x.getMidi(), y.getMidi(), at);
				SpectrumParityTest.assertClose(at + " salience", x.getSalience(), y.getSalience(), scale);
				SpectrumParityTest.assertClose(at + " independent salience", x.getIndependentSalience(), y.getIndependentSalience(), scale);
				Assertions.assertEquals(x.isHarmonicOfLower(), y.isHarmonicOfLower(), at);
				SpectrumParityTest.assertClose(at + " mean amplitude", x.getMeanAmplitude(), y.getMeanAmplitude(), scale);
				SpectrumParityTest.assertClose(at + " unique amplitude", x.getUniqueAmplitude(), y.getUniqueAmplitude(), scale);
				Assertions.assertEquals(x.getUniquePartials(), y.getUniquePartials(), at + " unique partials");
				Assertions.assertEquals(x.getUniquePresentFraction(), y.getUniquePresentFraction(), 1e-9, at + " present fraction");
				Assertions.assertEquals(x.getUniqueClearPartials(), y.getUniqueClearPartials(), at + " clear partials");
			}
			SpectrumParityTest.assertSameCandidates(label + " residual", a.getResidual(), b.getResidual());
		}
	}

	private static Map<String, int[]> labels() throws IOException {
		Map<String, int[]> labels = new HashMap<String, int[]>();
		for (String folder : new String[] { "owner-chords", "owner-chords-2" }) {
			Path file = Paths.get("..", "..", "specs", "004-attack-difference-recognition", "benchmark", folder, "labels.csv");
			if (!Files.isRegularFile(file)) {
				continue;
			}
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				String[] parts = line.trim().split(",");
				if (parts.length < 2 || parts[0].equals("file")) {
					continue;
				}
				String[] pitches = parts[1].trim().split("\\s+");
				int[] midi = new int[pitches.length];
				for (int i = 0; i < pitches.length; i++) {
					midi[i] = Integer.parseInt(pitches[i]);
				}
				labels.put(folder + "/" + parts[0].trim(), midi);
			}
		}
		return labels;
	}
}
