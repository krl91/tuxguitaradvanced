package app.tuxguitar.audio.engine.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class AttackAnalyzerTest {

	private static final int RATE = 44100;
	private static final int HOP = 256;
	private static final int[] CANDIDATES = { 45, 52, 57, 59, 60, 64 };

	/** A harmonic tone with decaying partials, from {@code from} seconds. */
	private static void addTone(float[] signal, int midi, double from, double gain) {
		double f0 = SyntheticSignals.midiFrequency(midi);
		int start = (int) (from * RATE);
		for( int i = start ; i < signal.length ; i ++ ) {
			double t = (i - start) / (double) RATE;
			double envelope = gain * Math.exp(-1.2 * t) * Math.min(1.0, t * 400.0);
			double s = 0.0;
			for( int h = 1 ; h <= 8 ; h ++ ) {
				s += Math.sin(2.0 * Math.PI * f0 * h * (i / (double) RATE)) / h;
			}
			signal[i] += (float) (envelope * s);
		}
	}

	/** Pushes the signal hop by hop and returns the evidence of the hop at {@code seconds}. */
	private static AttackEvidence evidenceAt(float[] signal, double seconds) {
		AttackAnalyzer analyzer = new AttackAnalyzer(AttackAnalyzer.Parameters.defaults(), RATE, HOP, CANDIDATES);
		AttackEvidence last = null;
		int target = (int) (seconds * RATE) / HOP;
		for( int h = 0 ; h <= target ; h ++ ) {
			analyzer.push(signal, h * HOP, HOP);
			last = analyzer.analyze();
		}
		return last;
	}

	@Test
	public void nothingIsReportedBeforeEnoughHistory() {
		AttackAnalyzer analyzer = new AttackAnalyzer(AttackAnalyzer.Parameters.defaults(), RATE, HOP, CANDIDATES);
		analyzer.push(new float[HOP], 0, HOP);
		assertNull(analyzer.analyze());
	}

	@Test
	public void aNewNoteStandsOutOverARingingBass() {
		float[] signal = new float[RATE * 3];
		addTone(signal, 45, 0.2, 0.5);
		addTone(signal, 60, 1.5, 0.5);
		AttackEvidence evidence = evidenceAt(signal, 1.5 + 0.18);
		assertNotNull(evidence);
		assertTrue(evidence.amplitudeOf(60) > 3.0 * evidence.amplitudeOf(45), "C4 " + evidence.amplitudeOf(60) + " vs ringing A2 " + evidence.amplitudeOf(45));
		assertEquals(60, topPitch(evidence));
		assertTrue(evidence.shareOf(60) > 0.5);
	}

	@Test
	public void aSteadyRingingSoundAddsNothing() {
		float[] signal = new float[RATE * 3];
		addTone(signal, 45, 0.2, 0.5);
		AttackEvidence evidence = evidenceAt(signal, 2.4);
		assertTrue(evidence.getAttackStrength() < 0.15, "strength " + evidence.getAttackStrength());
	}

	@Test
	public void theOctaveAboveIsNotTakenForTheNote() {
		float[] signal = new float[RATE * 3];
		addTone(signal, 45, 1.0, 0.5);
		AttackEvidence evidence = evidenceAt(signal, 1.0 + 0.18);
		assertEquals(45, topPitch(evidence));
		assertTrue(evidence.amplitudeOf(57) < 0.5 * evidence.amplitudeOf(45));
	}

	@Test
	public void aSemitoneNeighbourIsNotTakenForTheNote() {
		float[] signal = new float[RATE * 3];
		addTone(signal, 59, 1.0, 0.5);
		AttackEvidence evidence = evidenceAt(signal, 1.0 + 0.18);
		assertEquals(59, topPitch(evidence));
		assertTrue(evidence.amplitudeOf(60) < 0.5 * evidence.amplitudeOf(59));
	}

	@Test
	public void twoStrikesAtOnceAreBothFound() {
		float[] signal = new float[RATE * 3];
		addTone(signal, 45, 1.0, 0.4);
		addTone(signal, 60, 1.0, 0.4);
		AttackEvidence evidence = evidenceAt(signal, 1.0 + 0.18);
		assertTrue(evidence.shareOf(45, 60) > 0.7, "share " + evidence.shareOf(45, 60));
		assertTrue(evidence.amplitudeOf(45) > 0.2 * evidence.getMaximum());
		assertTrue(evidence.amplitudeOf(60) > 0.2 * evidence.getMaximum());
	}

	@Test
	public void theResultIsDeterministic() {
		float[] signal = new float[RATE * 2];
		addTone(signal, 52, 0.5, 0.5);
		AttackEvidence first = evidenceAt(signal, 0.7);
		AttackEvidence second = evidenceAt(signal, 0.7);
		for( int i = 0 ; i < CANDIDATES.length ; i ++ ) {
			assertEquals(first.getAmplitude(i), second.getAmplitude(i), 0.0);
		}
	}

	private static int topPitch(AttackEvidence evidence) {
		int best = 0;
		for( int i = 1 ; i < evidence.getPitches().length ; i ++ ) {
			if( evidence.getAmplitude(i) > evidence.getAmplitude(best) ) {
				best = i;
			}
		}
		return evidence.getPitches()[best];
	}
}
