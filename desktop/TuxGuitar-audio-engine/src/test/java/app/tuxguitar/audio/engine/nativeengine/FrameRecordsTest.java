package app.tuxguitar.audio.engine.nativeengine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.ChordEvidence;
import app.tuxguitar.audio.engine.analysis.PitchCandidate;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;

/**
 * The frame record (spec 023, data-model "Frame record"): an {@link AnalysisFrame} written into a {@code float[]} and read back keeps every field, each
 * optional part present or absent; the offsets are those of the crate's {@code frame_layout.txt}; the largest record fits the capacity, and a record that
 * does not fit its buffer is refused, never cut.
 */
public class FrameRecordsTest {

	private static final Path LAYOUT = Paths.get("..", "build-scripts", "native-modules", "tuxguitar-monitor-native", "src", "analysis", "frame_layout.txt");

	@Test
	public void theConstantsAreThoseOfTheSharedLayout() throws Exception {
		Map<String, Integer> layout = readLayout();
		Assertions.assertFalse(layout.isEmpty(), "the layout is read from " + LAYOUT.toAbsolutePath());
		for (Map.Entry<String, Integer> entry : layout.entrySet()) {
			int value = FrameRecords.class.getField(entry.getKey()).getInt(null);
			Assertions.assertEquals(entry.getValue().intValue(), value, entry.getKey());
		}
	}

	@Test
	public void aFrameWithoutOptionalPartsComesBackWhole() {
		AnalysisFrame frame = new AnalysisFrame(123_456_789_012L, -23.5, -6.25, 0.0125, AnalysisFrame.LevelState.SUITABLE, Double.NaN, 0.0, true, null, null, null, null,
				987_654_321L);
		AnalysisFrame back = roundTrip(frame, 42_000_000_123L);
		assertSameHeader(frame, back);
		Assertions.assertTrue(Double.isNaN(back.getF0Hz()), "unpitched stays NaN");
		Assertions.assertNull(back.getSpectrum());
		Assertions.assertNull(back.getTargetEvidence());
		Assertions.assertNull(back.getAttackEvidence());
		Assertions.assertNull(back.getChordEvidence());
		Assertions.assertNull(back.getTechniqueFeatures());
	}

	@Test
	public void theArrivalAndEveryLevelStateComeBack() {
		for (AnalysisFrame.LevelState state : AnalysisFrame.LevelState.values()) {
			AnalysisFrame frame = new AnalysisFrame(5L, -1.0, -0.5, 0.5, state, 196.123456789, 0.987654321, false);
			float[] record = new float[FrameRecords.CAPACITY];
			FrameRecords.write(frame, -7_000_000_001L, record, 0);
			Assertions.assertEquals(-7_000_000_001L, FrameRecords.arrivalNanos(record, 0));
			AnalysisFrame back = FrameRecords.read(record, 0);
			assertSameHeader(frame, back);
			Assertions.assertEquals(AnalysisFrame.NO_CAPTURE_SAMPLE, back.getCaptureSampleEnd());
		}
	}

	@Test
	public void everyOptionalPartComesBackWhole() {
		SpectrumFrame spectrum = chordSpectrum(new int[] { 48, 52, 55 });
		ChordEvidence chord = ChordEvidence.evaluate(spectrum, new int[] { 48, 52, 55, 59 }, new int[] { 40 });
		List<TargetEvidence.Hypothesis> hypotheses = new ArrayList<TargetEvidence.Hypothesis>();
		hypotheses.add(new TargetEvidence.Hypothesis(57, 0.91234));
		hypotheses.add(new TargetEvidence.Hypothesis(56, 0.1));
		TargetEvidence target = new TargetEvidence(hypotheses, 21.333333333);
		AttackEvidence attack = new AttackEvidence(new int[] { 40, 45, 50 }, new double[] { 0.1, 0.0, 0.333333333333 }, 1.75, new double[] { 0.0, 0.5, 0.25, 1e-9 },
				5.859375);
		AnalysisFrame frame = new AnalysisFrame(1L, -12.0, -3.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 220.5, 0.5, false, spectrum, target, null, attack, 4096L,
				chord);
		AnalysisFrame back = roundTrip(frame, 3L);
		assertSameHeader(frame, back);

		SpectrumFrame s = back.getSpectrum();
		Assertions.assertNotNull(s);
		Assertions.assertEquals(spectrum.getBinHz(), s.getBinHz());
		Assertions.assertEquals(spectrum.getMinBandMidi(), s.getMinBandMidi());
		Assertions.assertEquals(spectrum.getBandCount(), s.getBandCount());
		for (int midi = 0; midi < 128; midi++) {
			Assertions.assertEquals(spectrum.getBandDb(midi), s.getBandDb(midi), "band " + midi);
			Assertions.assertEquals(spectrum.getSalience(midi), s.getSalience(midi), "salience " + midi);
		}
		assertSameCandidates(spectrum.getCandidates(), s.getCandidates());
		Assertions.assertEquals(spectrum.getPeakLength(), s.getPeakLength());
		for (int k = 0; k < spectrum.getPeakLength(); k++) {
			Assertions.assertEquals(spectrum.getPeak(k), s.getPeak(k), "peak " + k);
		}
		Assertions.assertEquals(spectrum.getParameters().fftSize, s.getParameters().fftSize);

		TargetEvidence t = back.getTargetEvidence();
		Assertions.assertEquals(target.getWindowMillis(), t.getWindowMillis());
		Assertions.assertEquals(target.getHypotheses().size(), t.getHypotheses().size());
		for (int i = 0; i < target.getHypotheses().size(); i++) {
			Assertions.assertEquals(target.getHypotheses().get(i).getMidi(), t.getHypotheses().get(i).getMidi());
			Assertions.assertEquals(target.getHypotheses().get(i).getScore(), t.getHypotheses().get(i).getScore());
		}

		AttackEvidence a = back.getAttackEvidence();
		Assertions.assertArrayEquals(attack.getPitches(), a.getPitches());
		for (int i = 0; i < attack.getPitches().length; i++) {
			Assertions.assertEquals(attack.getAmplitude(i), a.getAmplitude(i));
		}
		Assertions.assertEquals(attack.getTotal(), a.getTotal());
		Assertions.assertEquals(attack.getMaximum(), a.getMaximum());
		Assertions.assertEquals(attack.getAttackStrength(), a.getAttackStrength());
		Assertions.assertEquals(attack.getBinHz(), a.getBinHz());
		Assertions.assertArrayEquals(attack.getGrowth(), a.getGrowth());
		Assertions.assertEquals(attack.offsetCents(45), a.offsetCents(45));

		ChordEvidence c = back.getChordEvidence();
		Assertions.assertNotNull(c);
		Assertions.assertEquals(chord.getPitches().size(), c.getPitches().size());
		for (int i = 0; i < chord.getPitches().size(); i++) {
			ChordEvidence.Pitch expected = chord.getPitches().get(i);
			ChordEvidence.Pitch actual = c.getPitches().get(i);
			Assertions.assertEquals(expected.getMidi(), actual.getMidi());
			Assertions.assertEquals(expected.getSalience(), actual.getSalience());
			Assertions.assertEquals(expected.getIndependentSalience(), actual.getIndependentSalience());
			Assertions.assertEquals(expected.isHarmonicOfLower(), actual.isHarmonicOfLower());
			Assertions.assertEquals(expected.getMeanAmplitude(), actual.getMeanAmplitude());
			Assertions.assertEquals(expected.getUniqueAmplitude(), actual.getUniqueAmplitude());
			Assertions.assertEquals(expected.getUniquePartials(), actual.getUniquePartials());
			Assertions.assertEquals(expected.getUniquePresentFraction(), actual.getUniquePresentFraction());
			Assertions.assertEquals(expected.getUniqueClearPartials(), actual.getUniqueClearPartials());
		}
		assertSameCandidates(chord.getResidual(), c.getResidual());
	}

	@Test
	public void anAttackWithoutGrowthKeepsNoGrowth() {
		AttackEvidence attack = new AttackEvidence(new int[] { 40 }, new double[] { 0.2 }, 0.5);
		AnalysisFrame frame = new AnalysisFrame(1L, -12.0, -3.0, 0.0, AnalysisFrame.LevelState.TOO_LOW, Double.NaN, 0.0, false, null, null, null, attack, 1L);
		AttackEvidence a = roundTrip(frame, 0L).getAttackEvidence();
		Assertions.assertNull(a.getGrowth());
		Assertions.assertTrue(Double.isNaN(a.offsetCents(40)) == Double.isNaN(attack.offsetCents(40)));
	}

	@Test
	public void theLargestRecordFitsTheCapacityAndATooSmallBufferIsRefused() {
		SpectrumFrame spectrum = chordSpectrum(new int[] { 40, 45, 50, 55, 59, 64 });
		int[] required = new int[16];
		for (int i = 0; i < required.length; i++) {
			required[i] = 40 + 3 * i;
		}
		ChordEvidence chord = ChordEvidence.evaluate(spectrum, required, new int[0]);
		List<TargetEvidence.Hypothesis> hypotheses = new ArrayList<TargetEvidence.Hypothesis>();
		for (int i = 0; i < 64; i++) {
			hypotheses.add(new TargetEvidence.Hypothesis(30 + i, 0.5));
		}
		int[] pitches = new int[49];
		double[] amplitudes = new double[49];
		for (int i = 0; i < pitches.length; i++) {
			pitches[i] = 40 + i;
			amplitudes[i] = 0.01 * i;
		}
		AttackEvidence attack = new AttackEvidence(pitches, amplitudes, 1.0, new double[700], 11.71875);
		AnalysisFrame frame = new AnalysisFrame(1L, -12.0, -3.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 220.0, 0.5, true, spectrum, new TargetEvidence(hypotheses, 10.0),
				null, attack, 1L, chord);
		float[] record = new float[FrameRecords.CAPACITY];
		int length = FrameRecords.write(frame, 0L, record, 0);
		Assertions.assertTrue(length <= FrameRecords.CAPACITY, "length " + length);
		Assertions.assertEquals(length, FrameRecords.length(record, 0));

		float[] small = new float[length - 1];
		Arrays.fill(small, 7f);
		Assertions.assertThrows(IllegalArgumentException.class, () -> FrameRecords.write(frame, 0L, small, 0));
		Assertions.assertThrows(IllegalArgumentException.class, () -> FrameRecords.write(frame, 0L, record, FrameRecords.CAPACITY - length + 1));
		float[] cut = Arrays.copyOf(record, length - 1);
		Assertions.assertThrows(IllegalArgumentException.class, () -> FrameRecords.read(cut, 0));
	}

	@Test
	public void recordsFollowOneAnother() {
		AnalysisFrame first = new AnalysisFrame(1L, -10.0, -5.0, 0.0, AnalysisFrame.LevelState.SUITABLE, 110.0, 0.9, true);
		AnalysisFrame second = new AnalysisFrame(2L, -11.0, -6.0, 0.1, AnalysisFrame.LevelState.TOO_HIGH, Double.NaN, 0.1, false);
		float[] records = new float[2 * FrameRecords.CAPACITY];
		int a = FrameRecords.write(first, 10L, records, 0);
		int b = FrameRecords.write(second, 20L, records, a);
		assertSameHeader(first, FrameRecords.read(records, 0));
		assertSameHeader(second, FrameRecords.read(records, a));
		Assertions.assertEquals(b, FrameRecords.length(records, a));
		Assertions.assertEquals(20L, FrameRecords.arrivalNanos(records, a));
	}

	private static AnalysisFrame roundTrip(AnalysisFrame frame, long arrivalNanos) {
		float[] record = new float[FrameRecords.CAPACITY];
		int length = FrameRecords.write(frame, arrivalNanos, record, 0);
		Assertions.assertEquals(length, FrameRecords.length(record, 0));
		Assertions.assertEquals(arrivalNanos, FrameRecords.arrivalNanos(record, 0));
		return FrameRecords.read(record, 0);
	}

	private static void assertSameHeader(AnalysisFrame expected, AnalysisFrame actual) {
		Assertions.assertEquals(expected.getTimestampNanos(), actual.getTimestampNanos());
		Assertions.assertEquals(expected.getCaptureSampleEnd(), actual.getCaptureSampleEnd());
		Assertions.assertEquals(expected.getRmsDbfs(), actual.getRmsDbfs());
		Assertions.assertEquals(expected.getPeakDbfs(), actual.getPeakDbfs());
		Assertions.assertEquals(expected.getClippedRatio(), actual.getClippedRatio());
		Assertions.assertEquals(expected.getLevelState(), actual.getLevelState());
		Assertions.assertEquals(Double.doubleToLongBits(expected.getF0Hz()), Double.doubleToLongBits(actual.getF0Hz()));
		Assertions.assertEquals(expected.getConfidence(), actual.getConfidence());
		Assertions.assertEquals(expected.isOnset(), actual.isOnset());
	}

	private static void assertSameCandidates(List<PitchCandidate> expected, List<PitchCandidate> actual) {
		Assertions.assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			Assertions.assertEquals(expected.get(i).getMidi(), actual.get(i).getMidi());
			Assertions.assertEquals(expected.get(i).getSalience(), actual.get(i).getSalience());
			Assertions.assertEquals(expected.get(i).getRelativeSalience(), actual.get(i).getRelativeSalience());
			Assertions.assertEquals(expected.get(i).getLevelDb(), actual.get(i).getLevelDb());
		}
	}

	private static SpectrumFrame chordSpectrum(int[] pitches) {
		int rate = 48000;
		PolyphonicAnalyzer analyzer = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), rate);
		float[] samples = new float[8192];
		for (int i = 0; i < samples.length; i++) {
			double value = 0.0;
			for (int midi : pitches) {
				double f = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
				for (int h = 1; h <= 6; h++) {
					value += 0.05 / h * Math.sin(2 * Math.PI * f * h * i / rate);
				}
			}
			samples[i] = (float) value;
		}
		analyzer.push(samples, 0, samples.length);
		return analyzer.analyze();
	}

	static Map<String, Integer> readLayout() throws IOException {
		Map<String, Integer> layout = new LinkedHashMap<String, Integer>();
		for (String line : Files.readAllLines(LAYOUT, StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			String[] parts = trimmed.split("\\s+");
			layout.put(parts[0], Integer.valueOf(parts[1]));
		}
		return layout;
	}
}
