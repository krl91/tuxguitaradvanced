package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevelFilter;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;

/**
 * Spec 014 (T024, FR-015): the sounds a level removed, as a sequence of their own, in the order of the piece, for the timeline of the backing that plays them;
 * none at level 5.
 */
public class ArcadeLevelRemovedSequenceTest {

	@Test
	public void theRemovedSequenceHoldsTheRemovedEventsInOrderAndNothingElse() {
		PracticeSequence piece = CalibrationPiece.load().getSequence();
		for (ArcadeLevel level : ArcadeLevel.values()) {
			ArcadeLevelFilter.Result result = ArcadeLevelFilter.apply(piece, level);
			if (level.keepsEverything()) {
				assertNull(result.removedSequence(), "nothing removed at level 5: nothing to play");
				continue;
			}
			assertEquals(result.removed().size(), result.removedSequence().getEvents().size(), "level " + level.number());
			for (int i = 0; i < result.removed().size(); i++) {
				assertSame(result.removed().get(i), result.removedSequence().getEvents().get(i));
			}
			assertEquals(0, result.removedSequence().getStartIndex());
		}
	}
}
