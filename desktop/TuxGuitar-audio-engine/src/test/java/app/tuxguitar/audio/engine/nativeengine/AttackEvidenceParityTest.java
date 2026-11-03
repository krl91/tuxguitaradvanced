package app.tuxguitar.audio.engine.nativeengine;

import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;

/**
 * The native attack evidence gives the Java one (spec 023 T021, {@code AttackAnalyzer} and {@code NnlsSolver}): every field within 1e-4 relative, for
 * candidate sets built as the practice's {@code AttackCandidates} builds them (the expected pitches with their neighbours -12, -2, -1, +1, +2, +12).
 */
public class AttackEvidenceParityTest {

	@BeforeAll
	public static void library() {
		ParityRecordings.requireNativeLibrary();
	}

	@Test
	public void aNoteAndAChordGiveTheSameEvidence() throws Exception {
		int[][] sets = { candidates(45), candidates(40, 47, 52, 56, 59, 64), candidates(48, 52, 55, 60, 64) };
		for (int[] set : sets) {
			ParityEngines.Request request = new ParityEngines.Request();
			request.attackCandidates = set;
			for (ParityRecordings.Recording recording : ParityRecordings.first(SpectrumParityTest.RECORDINGS)) {
				List<AnalysisFrame> java = ParityEngines.java(recording.mono, recording.rate, request);
				List<AnalysisFrame> nativeFrames = ParityEngines.nativeFrames(recording.mono, recording.rate, request);
				Assertions.assertEquals(java.size(), nativeFrames.size(), recording.toString());
				int evidence = 0;
				for (int i = 0; i < java.size(); i++) {
					assertSame(recording + " frame " + i, java.get(i).getAttackEvidence(), nativeFrames.get(i).getAttackEvidence());
					evidence += java.get(i).getAttackEvidence() != null ? 1 : 0;
				}
				Assertions.assertTrue(java.size() <= 15 || evidence > 0, recording + ": the evidence starts after the first hops");
			}
		}
	}

	private static void assertSame(String label, AttackEvidence a, AttackEvidence b) {
		Assertions.assertEquals(a == null, b == null, label + ": evidence in one engine only");
		if (a == null) {
			return;
		}
		Assertions.assertArrayEquals(a.getPitches(), b.getPitches(), label);
		for (int i = 0; i < a.getPitches().length; i++) {
			SpectrumParityTest.assertClose(label + " amplitude " + a.getPitches()[i], a.getAmplitude(i), b.getAmplitude(i), a.getMaximum());
		}
		SpectrumParityTest.assertClose(label + " total", a.getTotal(), b.getTotal(), a.getTotal());
		SpectrumParityTest.assertClose(label + " maximum", a.getMaximum(), b.getMaximum(), a.getMaximum());
		SpectrumParityTest.assertClose(label + " strength", a.getAttackStrength(), b.getAttackStrength(), 1.0);
		Assertions.assertEquals(a.getBinHz(), b.getBinHz(), 1e-12, label);
		Assertions.assertEquals(a.getGrowth() == null, b.getGrowth() == null, label + " growth");
		if (a.getGrowth() != null) {
			Assertions.assertEquals(a.getGrowth().length, b.getGrowth().length, label);
			double max = 0.0;
			for (double g : a.getGrowth()) {
				max = Math.max(max, g);
			}
			for (int k = 0; k < a.getGrowth().length; k++) {
				SpectrumParityTest.assertClose(label + " growth " + k, a.getGrowth()[k], b.getGrowth()[k], max);
			}
		}
	}

	static int[] candidates(int... own) {
		TreeSet<Integer> all = new TreeSet<Integer>();
		for (int pitch : own) {
			all.add(pitch);
			for (int delta : new int[] { -12, -2, -1, 1, 2, 12 }) {
				if (pitch + delta >= 28 && pitch + delta <= 100) {
					all.add(pitch + delta);
				}
			}
		}
		return all.stream().mapToInt(Integer::intValue).toArray();
	}
}
