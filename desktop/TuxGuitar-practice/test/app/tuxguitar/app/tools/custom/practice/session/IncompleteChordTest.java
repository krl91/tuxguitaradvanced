package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AttackEvidence;

/**
 * A chord with a note missing (spec 004 FR-003), on scripted evidence: no audio. These tests pin the rule; they do not replace the
 * measurements on real recordings (the GuitarSet benchmark, which shows where the rule is not good enough).
 */
public class IncompleteChordTest {

	private static final long HOP = 5_800_000L;
	/** A2, E3, A3 (an octave of the bass), B3, E4 (a twelfth above... the fifth of A3's octave), G3. */
	private static final int[] PITCHES = { 43, 45, 52, 55, 57, 59, 64 };
	private static final double LOUD = 0.2;

	/** Evidence in which each given pitch carries its amplitude and every other candidate a hundredth of the loudest. */
	private static AttackEvidence evidence(int[] pitches, double[] amplitudes) {
		double[] all = new double[PITCHES.length];
		for( int i = 0 ; i < PITCHES.length ; i ++ ) {
			all[i] = 0.01 * LOUD;
			for( int j = 0 ; j < pitches.length ; j ++ ) {
				if( PITCHES[i] == pitches[j] ) {
					all[i] = amplitudes[j];
				}
			}
		}
		return new AttackEvidence(PITCHES, all, 0.3);
	}

	private static boolean accepted(int[] expected, AttackEvidence evidence) {
		PolyphonicAcceptance acceptance = new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults());
		acceptance.rearm(0L);
		long t = 400_000_000L;
		boolean accepted = false;
		for( int hop = 0 ; hop < 30 ; hop ++ ) {
			t += HOP;
			accepted |= acceptance.offer(evidence, t, expected);
		}
		return accepted;
	}

	/** A2 + E3 + A3: A3 is an octave of the bass, so audio can tell only A2 and E3 apart. */
	private static final int[] CHORD = { 45, 52, 57 };

	@Test
	public void onlyTheNotesThatAreNotHarmonicsOfALowerNoteAreSeparatelyVerifiable() {
		assertArrayEquals(new int[] { 45, 52 }, PolyphonicAcceptance.separable(CHORD));
		assertArrayEquals(new int[] { 40, 47 }, PolyphonicAcceptance.separable(new int[] { 40, 47, 52, 64 }), "the E3 an octave and the E5 two octaves above the bass are harmonics of it");
		assertArrayEquals(new int[] { 45 }, PolyphonicAcceptance.separable(new int[] { 45, 57 }));
	}

	@Test
	public void theNotesNotCheckedSeparatelyAreTheOnesTheAcceptanceRuleSkips() {
		assertArrayEquals(new int[] { 57 }, PolyphonicAcceptance.notSeparable(CHORD));
		assertArrayEquals(new int[] { 52, 64 }, PolyphonicAcceptance.notSeparable(new int[] { 40, 47, 52, 64 }));
		assertArrayEquals(new int[0], PolyphonicAcceptance.notSeparable(new int[] { 40, 47, 55 }));
		assertArrayEquals(new int[0], PolyphonicAcceptance.notSeparable(new int[] { 45, 45, 52 }), "the same pitch twice is one pitch, which is checked");
	}

	@Test
	public void theCompleteChordIsAccepted() {
		assertTrue(accepted(CHORD, evidence(CHORD, new double[] { LOUD, LOUD, LOUD })));
	}

	@Test
	public void aChordWithItsBassMissingIsRefused() {
		// the bass and the octave above it (which the bass explains) are absent: only E3 grows
		assertFalse(accepted(CHORD, evidence(new int[] { 52 }, new double[] { LOUD })));
	}

	@Test
	public void aChordWithItsUpperSeparableNoteMissingIsRefused() {
		assertFalse(accepted(CHORD, evidence(new int[] { 45, 57 }, new double[] { LOUD, LOUD })));
	}

	@Test
	public void aNoteThatIsAnOctaveOfTheBassIsNotVerifiedSeparately() {
		// A3 alone missing: audio cannot tell it from a partial of A2 (spec FR-004); the chord is not refused for it
		assertTrue(accepted(CHORD, evidence(new int[] { 45, 52 }, new double[] { LOUD, LOUD })));
	}

	@Test
	public void theWeakestSeparableNoteMustReachItsShareOfTheStrongest() {
		// minBalance 10 % shared by the 2 separable notes: 5 % of the strongest
		double threshold = PolyphonicAcceptance.Parameters.defaults().minBalance / 2.0;
		assertTrue(accepted(CHORD, evidence(CHORD, new double[] { LOUD, 1.2 * threshold * LOUD, LOUD })), "just above the share");
		assertFalse(accepted(CHORD, evidence(CHORD, new double[] { LOUD, 0.8 * threshold * LOUD, LOUD })), "just below the share");
	}

	@Test
	public void aNoteHeardAloneIsNotAChord() {
		assertFalse(accepted(CHORD, evidence(new int[] { 57 }, new double[] { LOUD })));
	}
}
