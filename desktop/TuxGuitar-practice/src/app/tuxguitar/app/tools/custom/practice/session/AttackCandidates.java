package app.tuxguitar.app.tools.custom.practice.session;

import java.util.SortedSet;
import java.util.TreeSet;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;

/**
 * The pitches the attack evidence is computed for (spec 004): every pitch of the practice track,
 * and each one's semitone, whole-tone and octave neighbours, so that a wrong close pitch can compete
 * with the right one instead of being absent.
 */
public final class AttackCandidates {

	private static final int[] NEIGHBOURS = { -12, -2, -1, 1, 2, 12 };

	private AttackCandidates() {
	}

	/** With the recommended candidate range. */
	public static int[] of(PracticeSequence sequence) {
		return of(sequence, RecognitionSettings.defaults());
	}

	/** @return the candidate pitches in ascending order, neighbours within the range of the settings; empty when the sequence has no note or chord */
	public static int[] of(PracticeSequence sequence, RecognitionSettings settings) {
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
		return around(own, settings);
	}

	/** The pitches and their neighbours within the candidate range of the settings (spec 026, {@code pitch.attack-candidates-low/high}). */
	public static int[] around(SortedSet<Integer> own, RecognitionSettings settings) {
		return around(own, (int) settings.get(RecognitionParameters.PITCH_ATTACK_CANDIDATES_LOW),
				(int) settings.get(RecognitionParameters.PITCH_ATTACK_CANDIDATES_HIGH));
	}

	/** The pitches and their neighbours from {@code lowest} to {@code highest}; the pitches themselves are always kept. */
	public static int[] around(SortedSet<Integer> own, int lowest, int highest) {
		TreeSet<Integer> all = new TreeSet<Integer>(own);
		for( int pitch : own ) {
			for( int delta : NEIGHBOURS ) {
				int neighbour = pitch + delta;
				if( neighbour >= lowest && neighbour <= highest ) {
					all.add(neighbour);
				}
			}
		}
		return all.stream().mapToInt(Integer::intValue).toArray();
	}
}
