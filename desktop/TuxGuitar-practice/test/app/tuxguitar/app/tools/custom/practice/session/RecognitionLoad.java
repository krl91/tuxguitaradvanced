package app.tuxguitar.app.tools.custom.practice.session;

/**
 * A stand-in for the CPU load of the recognition while something else is measured (spec 003 T042): one whole replay of synthetic guitar audio through
 * the real recognition chain and the tempo-following judging, as the benchmarks of specification 002 do. It is a load for the processor, not a
 * real-time capture: it runs as fast as it can, so it is heavier than the real thing.
 */
public final class RecognitionLoad {

	private RecognitionLoad() {
	}

	/** Runs one replay of a scale and one of chords; returns the number of events that were matched, only so that the work cannot be optimised away. */
	public static int runOnce(long seed) {
		return FollowBenchmarkTest.run(FollowBenchmarkTest.Scenario.CLEAN, 4.0, seed).matched
				+ FollowBenchmarkTest.run(FollowBenchmarkTest.Scenario.CHORDS, 1.0, seed).matched;
	}
}
