package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The recording of the learner must not depend on the wall clock (spec 003 T047). The player's clock is read when the run is aligned, and its reading varies from
 * one run to the next by a few milliseconds: if that moved the samples of the recording by a fraction of a block, the analysis would see another signal in each
 * run (the frames would not fall on the same part of the attacks) and the judgements of the marginal events would differ for a reason that is not the subject
 * of the test. Only the alignment of the whole recording on the timeline may differ, and by whole blocks.
 */
public class StrikePlacementTest {

	private static final long MS = 1_000_000L;

	@Test
	public void aDifferentReadingOfTheClockMovesTheRecordingByWholeBlocksOnly() {
		long start = 5_000_000_000L;
		long[] strikes = { 1_000 * MS + 123_456, 1_333 * MS + 777_001, 2_000 * MS, 9_999 * MS + 333_333 };
		for (long jitter = -12 * MS; jitter <= 12 * MS; jitter += 137_000L) {
			for (long strike : strikes) {
				long reference = StrikePlacement.startSample(start, 20_000 * MS, strike);
				long moved = StrikePlacement.startSample(start, 20_000 * MS + jitter, strike);
				assertEquals(0L, Math.floorMod(moved - reference, (long) StrikePlacement.BLOCK),
						"a reading of the clock " + jitter / 1000 + " us later moved the strike by a part of a block: " + (moved - reference) + " samples");
			}
		}
	}

	@Test
	public void theAlignmentIsTheNearestWholeBlockSoTheLearnerStaysWithinHalfABlockOfTheTimeline() {
		long start = 0L;
		long block = StrikePlacement.BLOCK * 1_000_000_000L / 48_000L;
		for (long jitter = -30 * MS; jitter <= 30 * MS; jitter += 311_000L) {
			long wanted = 7_000 * MS + jitter;
			long sample = StrikePlacement.startSample(start, wanted, 0L);
			long placedNanos = sample * 1_000_000_000L / 48_000L;
			assertTrue(Math.abs(placedNanos - wanted) <= block / 2 + 1, "off by more than half a block: " + (placedNanos - wanted) / 1000 + " us");
		}
	}
}
