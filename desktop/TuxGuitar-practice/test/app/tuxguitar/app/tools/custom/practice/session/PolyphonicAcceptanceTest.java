package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.analysis.AttackEvidence;

/** The decision rule on scripted evidence: no audio (spec 004, constitution VII). */
public class PolyphonicAcceptanceTest {

	private static final long HOP = 5_800_000L;
	private static final int[] PITCHES = { 43, 45, 52, 55, 57, 59, 60 };

	/** Evidence in which {@code pitch} carries {@code amplitude} and every other candidate a tenth of it. */
	private static AttackEvidence evidence(double strength, int pitch, double amplitude) {
		double[] amplitudes = new double[PITCHES.length];
		for( int i = 0 ; i < PITCHES.length ; i ++ ) {
			amplitudes[i] = (PITCHES[i] == pitch ? amplitude : 0.1 * amplitude);
		}
		return new AttackEvidence(PITCHES, amplitudes, strength);
	}

	/** The rule ignores a hop whose total is below the room-noise floor: tests use real-looking totals. */
	private static final double LOUD = 0.2;

	private PolyphonicAcceptance fresh() {
		PolyphonicAcceptance acceptance = new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults());
		acceptance.rearm(0L);
		return acceptance;
	}

	@Test
	public void theExpectedNoteIsAcceptedOnlyAfterSeveralHopsInARow() {
		PolyphonicAcceptance acceptance = fresh();
		long t = 400_000_000L;
		int accepted = -1;
		for( int hop = 0 ; hop < 20 ; hop ++ ) {
			t += HOP;
			if( acceptance.offer(evidence(0.3, 57, LOUD), t, new int[] { 57 }) && accepted < 0 ) {
				accepted = hop;
			}
		}
		assertTrue(accepted >= PolyphonicAcceptance.Parameters.defaults().hops - 1, "accepted at hop " + accepted);
		assertTrue(accepted < 12, "accepted at hop " + accepted);
	}

	@Test
	public void aDifferentNoteIsNeverAccepted() {
		PolyphonicAcceptance acceptance = fresh();
		long t = 400_000_000L;
		for( int hop = 0 ; hop < 40 ; hop ++ ) {
			t += HOP;
			assertFalse(acceptance.offer(evidence(0.3, 59, LOUD), t, new int[] { 57 }));
		}
	}

	@Test
	public void roomNoiseAndSteadySoundsAreNotAnAttack() {
		PolyphonicAcceptance acceptance = fresh();
		long t = 400_000_000L;
		for( int hop = 0 ; hop < 40 ; hop ++ ) {
			t += HOP;
			assertFalse(acceptance.offer(evidence(0.3, 57, 0.001), t, new int[] { 57 }), "noise");
		}
	}

	@Test
	public void evidenceCarryingTheAttackOfAStartOrAnAcceptanceIsIgnoredFor220Ms() {
		PolyphonicAcceptance acceptance = fresh();
		acceptance.rearm(1_000_000_000L);
		long t = 1_000_000_000L;
		while( t < 1_219_000_000L ) {
			t += HOP;
			assertFalse(acceptance.offer(evidence(0.3, 57, LOUD), t, new int[] { 57 }), "within the re-arm interval");
		}
		boolean accepted = false;
		for( int hop = 0 ; hop < 20 ; hop ++ ) {
			t += HOP;
			accepted |= acceptance.offer(evidence(0.3, 57, LOUD), t, new int[] { 57 });
		}
		assertTrue(accepted);
	}

	@Test
	public void aChordIsAcceptedWhenAllItsNotesGrowTogether() {
		PolyphonicAcceptance acceptance = fresh();
		long t = 400_000_000L;
		boolean accepted = false;
		double[] both = new double[PITCHES.length];
		for( int i = 0 ; i < PITCHES.length ; i ++ ) {
			both[i] = (PITCHES[i] == 45 || PITCHES[i] == 59 ? LOUD : 0.02);
		}
		for( int hop = 0 ; hop < 30 ; hop ++ ) {
			t += HOP;
			accepted |= acceptance.offer(new AttackEvidence(PITCHES, both, 0.3), t, new int[] { 45, 59 });
		}
		assertTrue(accepted);
	}

	@Test
	public void aChordWhoseNotesAreNotThePlayedOnesIsNotAccepted() {
		PolyphonicAcceptance acceptance = fresh();
		long t = 400_000_000L;
		for( int hop = 0 ; hop < 30 ; hop ++ ) {
			t += HOP;
			assertFalse(acceptance.offer(evidence(0.3, 60, LOUD), t, new int[] { 45, 59 }), "another note played");
		}
	}

	@Test
	public void aCloseNeighbourIsNotTakenForTheNote() {
		PolyphonicAcceptance acceptance = new PolyphonicAcceptance(new PolyphonicAcceptance.Parameters(0.30, 0.0, 0.7, 6, 1.5, 0.0, 0L));
		acceptance.rearm(0L);
		int[] pitches = { 57, 58, 59 };
		long t = 400_000_000L;
		for( int hop = 0 ; hop < 30 ; hop ++ ) {
			t += HOP;
			// the whole tone above (59) is four times as strong as the expected 57
			assertFalse(acceptance.offer(new AttackEvidence(pitches, new double[] { 0.05, 0.02, 0.2 }, 0.3), t, new int[] { 57 }));
		}
	}

	@Test
	public void invalidParametersAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> new PolyphonicAcceptance.Parameters(1.5, 0.0, 0.7, 6, 0.0, 0L));
		assertThrows(IllegalArgumentException.class, () -> new PolyphonicAcceptance.Parameters(0.3, 0.0, 0.7, 0, 0.0, 0L));
	}
}
