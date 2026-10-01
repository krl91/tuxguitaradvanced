package app.tuxguitar.audio.engine.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

public class ExpectedPitchVerifierTest {

	private static final int RATE = 44100;
	private static final int WINDOW = 2048;
	private final ExpectedPitchVerifier verifier = new ExpectedPitchVerifier(RATE);

	private static float[] pluck(int midi) {
		// skip the first samples: Karplus-Strong starts as noise and settles into the pitch
		float[] signal = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(midi), RATE, WINDOW + 4096, 7L);
		float[] window = new float[WINDOW];
		System.arraycopy(signal, 4096, window, 0, WINDOW);
		return window;
	}

	/**
	 * Measured limit, reported honestly: up to E4 (MIDI 64) the expected pitch leads its neighbours
	 * by a wide margin; on the highest strings a 20-30 ms window barely resolves a semitone, so the
	 * margin is small (but never negative) and the session must keep its conservative fallback.
	 */
	@Test
	public void theExpectedPitchBeatsItsNeighboursAndOctaveAcrossTheGuitarRange() {
		for( int midi = 40 ; midi <= 76 ; midi += 3 ) {
			TargetEvidence evidence = this.verifier.verify(pluck(midi), midi);
			double required = (midi <= 64 ? 0.4 : 0.0);
			assertTrue(evidence.marginOver(midi) > required, "pitch " + midi + " margin " + evidence.marginOver(midi));
			assertTrue(evidence.of(midi).getScore() > 0.6, "pitch " + midi + " score " + evidence.of(midi).getScore());
		}
	}

	@Test
	public void aWrongNeighbourNeverScoresAboveTheExpectedPitch() {
		for( int played = 41 ; played <= 75 ; played += 2 ) {
			TargetEvidence evidence = this.verifier.verify(pluck(played), played + 1);
			assertTrue(evidence.marginOver(played + 1) < 0.0, "played " + played + " expected " + (played + 1));
			evidence = this.verifier.verify(pluck(played), played - 1);
			assertTrue(evidence.marginOver(played - 1) < 0.0, "played " + played + " expected " + (played - 1));
		}
	}

	@Test
	public void theOctaveBelowIsNotAcceptedAsTheExpectedPitch() {
		TargetEvidence evidence = this.verifier.verify(pluck(64), 52);
		assertTrue(evidence.of(52).getScore() < 0.3, "score " + evidence.of(52).getScore());
	}

	@Test
	public void silenceAndNoiseScoreLow() {
		for( float[] audio : new float[][] { SyntheticSignals.silence(WINDOW), SyntheticSignals.noise(WINDOW, 0.3, 3L) } ) {
			TargetEvidence evidence = this.verifier.verify(audio, 52);
			for( TargetEvidence.Hypothesis hypothesis : evidence.getHypotheses() ) {
				assertTrue(hypothesis.getScore() < 0.4, "pitch " + hypothesis.getMidi() + " score " + hypothesis.getScore());
			}
		}
	}

	@Test
	public void theEvidenceWindowNeverExceedsTheAudioAndIsShorterForHighStrings() {
		TargetEvidence low = this.verifier.verify(pluck(40), 40);
		TargetEvidence high = this.verifier.verify(pluck(76), 76);
		double windowMillis = 1000.0 * WINDOW / RATE;
		assertTrue(low.getWindowMillis() <= windowMillis + 1e-9);
		assertTrue(high.getWindowMillis() < low.getWindowMillis());
		assertEquals(windowMillis, low.getWindowMillis(), 1e-9);
	}

	@Test
	public void aChordGivesEvidenceForEveryExpectedPitchWithoutDuplicates() {
		TargetEvidence evidence = this.verifier.verify(pluck(52), 52, 53);
		// 52,53 + neighbours 51,54 + octaves 64,65 = 6 distinct pitches (53 and 52 neighbours overlap)
		assertEquals(6, evidence.getHypotheses().size());
		assertTrue(evidence.of(52) != null && evidence.of(53) != null && evidence.of(64) != null && evidence.of(65) != null);
	}

	@Test
	public void refusesInvalidArguments() {
		assertThrows(IllegalArgumentException.class, () -> new ExpectedPitchVerifier(0));
		assertThrows(IllegalArgumentException.class, () -> this.verifier.verify(pluck(52)));
	}
}
