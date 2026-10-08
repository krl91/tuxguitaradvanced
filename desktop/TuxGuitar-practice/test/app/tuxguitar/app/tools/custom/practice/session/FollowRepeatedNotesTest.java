package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Identical notes repeated at 4 a second (spec 002 T101, SC-004, found by the benchmark): an attack must keep the time of its own transient. The
 * polyphonic acceptance sees the attack of a note over the identical note that still rings only late, and the event used to be matched with that
 * late time, reported LATE and 60 to 100 ms off the note that was played (56 false matches in 12 runs). Synthetic plucks, seeded, whole runs of the
 * session on the fake player.
 */
public class FollowRepeatedNotesTest {

	private static final long[] SEEDS = { 1000, 1001, 1002, 1003, 1004, 1005 };

	private static int[] falseAndRight(double rate, boolean waits) {
		int falseMatches = 0;
		int right = 0;
		for (long seed : SEEDS) {
			FollowBenchmarkTest.Outcome outcome = FollowBenchmarkTest.run(FollowBenchmarkTest.Scenario.REPEATED, rate, seed, waits);
			falseMatches += outcome.falseMatches;
			right += outcome.rightMatches;
		}
		return new int[] { falseMatches, right };
	}

	/**
	 * The default: the attack the detector reported gives the time. Before the change the same 6 runs gave 28 false matches and 40 right ones (the
	 * time of the late evidence was always taken); the protection must keep at least that many right and clearly fewer false.
	 */
	@Test
	public void byDefaultTheReportedAttackGivesTheTimeAndFewerRepeatedNotesAreMatchedAtTheWrongTime() {
		int[] result = falseAndRight(4, false);
		assertTrue(result[0] <= 20, "false matches over the 6 runs: " + result[0]);
		assertTrue(result[1] >= 40, "right matches over the 6 runs of 16 notes: " + result[1]);
	}

	/** The variant that waits for the report: no false match at 4 notes a second, at the price measured in research R15 on the notes that are not repeated. */
	@Test
	public void whenTheEventWaitsForTheReportNoRepeatedNoteIsMatchedAtTheWrongTimeAtFourNotesASecond() {
		int[] result = falseAndRight(4, true);
		assertEquals(0, result[0], "no repeated note is matched at a time that is not its own");
		assertTrue(result[1] >= 40, "right matches over the 6 runs of 16 notes: " + result[1]);
	}

	@Test
	public void anAttackIsCountedOnceNotAsAnEventAndAgainAsAnExtraNote() {
		int extras = 0;
		for (long seed : SEEDS) {
			extras += FollowBenchmarkTest.run(FollowBenchmarkTest.Scenario.REPEATED, 4, seed).extras;
		}
		assertTrue(extras <= 3, "extra notes over the 6 runs: " + extras);
	}

	@Test
	public void theSlowRepeatedNotesStayAllRight() {
		int right = 0;
		for (long seed : SEEDS) {
			FollowBenchmarkTest.Outcome outcome = FollowBenchmarkTest.run(FollowBenchmarkTest.Scenario.REPEATED, 2, seed);
			right += outcome.rightMatches;
			assertEquals(0, outcome.falseMatches);
		}
		assertTrue(right >= 90, "right matches over 6 runs of 16 notes: " + right);
	}
}
