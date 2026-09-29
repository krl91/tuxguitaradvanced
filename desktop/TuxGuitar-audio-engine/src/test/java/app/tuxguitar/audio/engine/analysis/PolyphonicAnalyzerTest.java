package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * Pins the polyphonic analysis on deterministic synthetic signals (plan stage D). These are
 * properties of the method, not accuracy claims: real strummed chords are measured separately
 * (SC-010, tasks T094–T095).
 */
public class PolyphonicAnalyzerTest {

	private static final int RATE = 48000;
	private static final int STRUM_SPACING = 480; // 10 ms between strings
	private static final int ANALYSED_SAMPLES = 12000; // window ending 250 ms after the first string

	@Test
	public void aSinglePluckedNoteIsTheFirstCandidate() {
		for (int midi : new int[] { 40, 45, 50, 55, 59, 64, 76 }) {
			SpectrumFrame frame = analyze(new int[] { midi }, 7L, 16384);
			assertEquals(midi, frame.getCandidates().get(0).getMidi(), "pluck " + midi);
			assertEquals(1.0, frame.getCandidates().get(0).getRelativeSalience());
		}
	}

	@Test
	public void bandLevelsAreCalibratedInDbfs() {
		PolyphonicAnalyzer analyzer = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), RATE);
		float[] sine = SyntheticSignals.sine(440.0, RATE, 8192, 0.5);
		analyzer.push(sine, 0, sine.length);

		SpectrumFrame frame = analyzer.analyze();

		assertEquals(-6.02, frame.getBandDb(69), 0.5);
		assertTrue(frame.getBandDb(71) < frame.getBandDb(69) - 30.0);
		assertEquals(69, frame.getCandidates().get(0).getMidi());
	}

	@Test
	public void silenceHasNoCandidates() {
		PolyphonicAnalyzer analyzer = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), RATE);
		float[] silence = SyntheticSignals.silence(8192);
		analyzer.push(silence, 0, silence.length);

		SpectrumFrame frame = analyzer.analyze();

		assertTrue(frame.getCandidates().isEmpty());
		assertEquals(Double.NEGATIVE_INFINITY, frame.getBandDb(40));
		assertTrue(ChordEvidence.evaluate(frame, new int[] { 40, 47 }, new int[0]).getResidual().isEmpty());
	}

	@Test
	public void identicalInputGivesIdenticalResults() {
		int[] chord = { 40, 47, 52, 56, 59, 64 };
		SpectrumFrame first = analyze(chord, 11L, ANALYSED_SAMPLES);
		SpectrumFrame second = analyze(chord, 11L, ANALYSED_SAMPLES);

		assertEquals(first.getCandidates().toString(), second.getCandidates().toString());
		for (int midi = 33; midi <= 88; midi++) {
			assertEquals(first.getSalience(midi), second.getSalience(midi));
		}
	}

	@Test
	public void pitchesOnALowerPitchsHarmonicAreFlagged() {
		ChordEvidence evidence = ChordEvidence.evaluate(analyze(new int[] { 40, 47, 52, 56, 59, 64 }, 11L, ANALYSED_SAMPLES),
				new int[] { 40, 47, 52, 56, 59, 64, 64 }, new int[0]);

		List<ChordEvidence.Pitch> pitches = evidence.getPitches();
		assertEquals(6, pitches.size());
		boolean[] expected = { false, false, true, false, true, true }; // E3 = 2×E2, B3 = 3×E2, E4 = 4×E2
		for (int i = 0; i < pitches.size(); i++) {
			assertEquals(expected[i], pitches.get(i).isHarmonicOfLower(), "pitch " + pitches.get(i).getMidi());
			assertTrue(pitches.get(i).getSalience() > 0.0);
		}
	}

	@Test
	public void aMissingIndependentChordToneLowersItsEvidence() {
		// E major without G#3, C major without G3, E minor without G3: none lies on a lower tone's harmonic
		assertEvidenceDrops(new int[] { 40, 47, 52, 56, 59, 64 }, 56);
		assertEvidenceDrops(new int[] { 48, 52, 55, 60, 64 }, 55);
		assertEvidenceDrops(new int[] { 40, 47, 52, 55, 59, 64 }, 55);
	}

	/**
	 * Requirements B (2026-09-28): on seven open chords, with every string dropped in turn, a
	 * sounding verifiable pitch has at least two clearly sounding unique partials and a missing one
	 * at most one; pitches on a lower pitch's harmonic have none of their own.
	 */
	@Test
	public void uniquePartialsSeparateSoundingFromMissingChordTones() {
		int[][] chords = { { 40, 47, 52, 56, 59, 64 }, { 45, 52, 57, 60, 64 }, { 48, 52, 55, 60, 64 }, { 43, 47, 50, 55, 59, 67 },
				{ 50, 57, 62, 66 }, { 45, 52, 57, 61, 64 }, { 40, 47, 52, 55, 59, 64 } };
		for (int[] chord : chords) {
			for (int drop = -1; drop < chord.length; drop++) {
				int missing = (drop >= 0 ? chord[drop] : -1);
				ChordEvidence evidence = ChordEvidence.evaluate(analyze(without(chord, missing), 11L, ANALYSED_SAMPLES), chord, new int[0]);
				for (ChordEvidence.Pitch pitch : evidence.getPitches()) {
					String what = java.util.Arrays.toString(chord) + " without " + missing + ", pitch " + pitch.getMidi();
					if (pitch.getUniquePartials() < 2) {
						continue;
					}
					if (pitch.getMidi() == missing) {
						assertTrue(pitch.getUniqueClearPartials() <= 1, what + ": " + pitch.getUniqueClearPartials());
					} else {
						assertTrue(pitch.getUniqueClearPartials() >= 2 && pitch.getUniquePresentFraction() >= 1.0 / 3.0,
								what + ": " + pitch.getUniqueClearPartials() + " / " + pitch.getUniquePresentFraction());
					}
				}
			}
		}
		ChordEvidence eMajor = ChordEvidence.evaluate(analyze(chords[0], 11L, ANALYSED_SAMPLES), chords[0], new int[0]);
		for (ChordEvidence.Pitch pitch : eMajor.getPitches()) {
			boolean onLowerHarmonic = (pitch.getMidi() == 52 || pitch.getMidi() == 59 || pitch.getMidi() == 64);
			assertEquals(onLowerHarmonic, pitch.getUniquePartials() == 0, "E major pitch " + pitch.getMidi());
		}
	}

	@Test
	public void aForeignNoteIsNamedFirstInTheResidual() {
		// A minor and A major with an added F3, which belongs to neither chord
		assertForeignFirst(new int[] { 45, 52, 57, 60, 64 }, 53);
		assertForeignFirst(new int[] { 45, 52, 57, 61, 64 }, 53);
	}

	@Test
	public void toleratedPitchesAreNotResidual() {
		int[] chord = { 45, 52, 57, 60, 64 };
		SpectrumFrame frame = analyze(with(chord, 53), 11L, ANALYSED_SAMPLES);

		ChordEvidence evidence = ChordEvidence.evaluate(frame, chord, new int[] { 53 });

		for (PitchCandidate candidate : evidence.getResidual()) {
			assertFalse(candidate.getMidi() == 53);
		}
	}

	private static void assertEvidenceDrops(int[] chord, int missing) {
		double full = independent(ChordEvidence.evaluate(analyze(chord, 11L, ANALYSED_SAMPLES), chord, new int[0]), missing);
		double without = independent(ChordEvidence.evaluate(analyze(without(chord, missing), 11L, ANALYSED_SAMPLES), chord, new int[0]), missing);
		assertTrue(full > 1.5 * without, "pitch " + missing + ": " + full + " vs " + without);
	}

	private static void assertForeignFirst(int[] chord, int foreign) {
		ChordEvidence evidence = ChordEvidence.evaluate(analyze(with(chord, foreign), 11L, ANALYSED_SAMPLES), chord, new int[0]);
		assertEquals(foreign, evidence.getResidual().get(0).getMidi());
	}

	private static double independent(ChordEvidence evidence, int midi) {
		for (ChordEvidence.Pitch pitch : evidence.getPitches()) {
			if (pitch.getMidi() == midi) {
				return pitch.getIndependentSalience();
			}
		}
		throw new AssertionError("no evidence for " + midi);
	}

	private static SpectrumFrame analyze(int[] pitches, long seed, int samples) {
		float[] signal = SyntheticSignals.strum(pitches, RATE, RATE, STRUM_SPACING, 0.2, seed);
		PolyphonicAnalyzer analyzer = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), RATE);
		analyzer.push(signal, 0, samples);
		return analyzer.analyze();
	}

	private static int[] with(int[] chord, int extra) {
		int[] result = java.util.Arrays.copyOf(chord, chord.length + 1);
		result[chord.length] = extra;
		return result;
	}

	private static int[] without(int[] chord, int missing) {
		return java.util.Arrays.stream(chord).filter(midi -> midi != missing).toArray();
	}
}
