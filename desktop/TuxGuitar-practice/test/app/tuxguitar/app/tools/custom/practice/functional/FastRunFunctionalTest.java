package app.tuxguitar.app.tools.custom.practice.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.dataset.DatasetJudge;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

/**
 * Fast runs through the practice session on synthetic plucks (spec 002 T133, SC-002, SC-004): Karplus-Strong notes at 3 to 12 notes per
 * second, each ringing under the next, in the default test suite (no recording, no dataset). Reported for every rate and kind; asserted:
 * a wrong note is never accepted, and the recognition does not fall below what is measured today. The figures are not the targets of the
 * specification (95 % at 8 notes per second, 90 % at 12; research R3): they are the floor that work on the re-arm, on identical repeats and
 * on close notes must not go under, and the table printed by the test is where that work shows its progress. Real notes are measured by the
 * dataset benchmarks.
 */
public class FastRunFunctionalTest {

	private static final int SAMPLE_RATE = 48000;
	private static final double[] RATES = { 3, 4, 6, 8, 10, 12 };
	/** Notes in a passage. */
	private static final int NOTES = 4;
	private static final double WINDOW_SECONDS = 0.6;

	private enum Kind {
		DIFFERENT(new int[] { 52, 57, 62, 55 }), REPEATED(new int[] { 57, 57, 57, 57 }), NEIGHBOURS(new int[] { 57, 58, 57, 58 });

		final int[] notes;

		Kind(int[] notes) {
			this.notes = notes;
		}
	}

	/** @return {notes recognised in their turn, passage followed in full (0/1), wrong last note accepted (0/1)} */
	private static int[] run(Kind kind, double rate, long seed) {
		TechniqueSignals.Passage passage = TechniqueSignals.fastPassage(kind.notes, SAMPLE_RATE, rate, SAMPLE_RATE / 2, SAMPLE_RATE, 0.5, seed);
		RecordedAudio audio = RecordedAudio.of(passage.getSignal(), SAMPLE_RATE);
		int[][] steps = new int[NOTES][];
		for( int i = 0 ; i < NOTES ; i ++ ) {
			steps[i] = new int[] { kind.notes[i] };
		}
		double[] times = DatasetJudge.acceptanceTimes(audio, steps);
		int recognised = 0;
		boolean full = true;
		double previous = -1.0;
		for( int i = 0 ; i < NOTES ; i ++ ) {
			double onset = passage.getOnsetSamples()[i] / (double) SAMPLE_RATE;
			boolean ok = !Double.isNaN(times[i]) && times[i] >= onset - 0.05 && times[i] <= onset + WINDOW_SECONDS && times[i] >= previous;
			if( ok ) {
				recognised ++;
			} else {
				full = false;
			}
			if( !Double.isNaN(times[i]) ) {
				previous = times[i];
			}
		}
		// the last note replaced by a close one that was not played in the passage (a pitch played earlier still rings): it must not be accepted
		int wrong = kind.notes[NOTES - 1];
		for( int delta : new int[] { 2, 3, -2, -3, 4 } ) {
			boolean played = false;
			for( int note : kind.notes ) {
				played |= (note == kind.notes[NOTES - 1] + delta);
			}
			if( !played ) {
				wrong = kind.notes[NOTES - 1] + delta;
				break;
			}
		}
		int[][] altered = steps.clone();
		altered[NOTES - 1] = new int[] { wrong };
		boolean wrongAccepted = !Double.isNaN(DatasetJudge.acceptanceTimes(audio, altered)[NOTES - 1]);
		return new int[] { recognised, full ? 1 : 0, wrongAccepted ? 1 : 0 };
	}

	@Test
	public void fastRunsOfSyntheticPlucksAreReportedAndNeverAcceptAWrongNote() {
		StringBuilder report = new StringBuilder("fast runs, synthetic plucks, notes recognised in their turn / passages followed in full (3 passages per cell):\n");
		report.append(String.format("%-12s", "rate"));
		for( Kind kind : Kind.values() ) {
			report.append(String.format("%-16s", kind.name().toLowerCase()));
		}
		report.append("\n");
		List<String> failures = new ArrayList<String>();
		for( double rate : RATES ) {
			report.append(String.format("%4.0f /s      ", rate));
			for( Kind kind : Kind.values() ) {
				int notes = 0;
				int full = 0;
				int wrong = 0;
				int passages = 3;
				for( int seed = 1 ; seed <= passages ; seed ++ ) {
					int[] r = run(kind, rate, 100 * seed + (int) rate);
					notes += r[0];
					full += r[1];
					wrong += r[2];
				}
				report.append(String.format("%2d/%2d, %d/%d     ", notes, NOTES * passages, full, passages));
				// the floor: what is measured today at 3 and 4 notes per second (85 to 100 % of the notes on synthetic plucks) must not be lost
				if( rate <= 4 && notes < 0.75 * NOTES * passages ) {
					failures.add(kind + " at " + rate + " per second: only " + notes + " of " + NOTES * passages + " notes recognised");
				}
				if( wrong > 0 ) {
					failures.add(kind + " at " + rate + " per second: a wrong last note was accepted in " + wrong + " passages");
				}
			}
			report.append("\n");
		}
		System.out.println(report);
		assertEquals(new ArrayList<String>(), failures, report.toString());
		assertTrue(report.length() > 0);
	}
}
