package app.tuxguitar.app.tools.custom.practice.session;

import java.util.TreeSet;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;

/**
 * The pitches the attack evidence is computed for (spec 004): every pitch of the practice track,
 * and each one's semitone, whole-tone and octave neighbours, so that a wrong close pitch can compete
 * with the right one instead of being absent.
 */
public final class AttackCandidates {

	private static final int[] NEIGHBOURS = { -12, -2, -1, 1, 2, 12 };
	private static final int LOWEST = 28;
	private static final int HIGHEST = 100;

	private AttackCandidates() {
	}

	/** @return the candidate pitches in ascending order; empty when the sequence has no note or chord */
	public static int[] of(PracticeSequence sequence) {
		TreeSet<Integer> own = new TreeSet<Integer>();
		for( PracticeEvent event : sequence.getEvents() ) {
			if( event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
				own.add(event.getExpectedMidi());
			} else if( event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
				for( int pitch : event.getRequiredPitches() ) {
					own.add(pitch);
				}
			}
		}
		TreeSet<Integer> all = new TreeSet<Integer>(own);
		for( int pitch : own ) {
			for( int delta : NEIGHBOURS ) {
				int neighbour = pitch + delta;
				if( neighbour >= LOWEST && neighbour <= HIGHEST ) {
					all.add(neighbour);
				}
			}
		}
		return all.stream().mapToInt(Integer::intValue).toArray();
	}
}
