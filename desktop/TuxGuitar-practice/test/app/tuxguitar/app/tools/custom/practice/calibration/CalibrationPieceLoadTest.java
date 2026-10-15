package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.util.TGContext;

/** Spec 013 (T020): the shipped piece read as the events the listening and the scoring work on (FR-016). */
public class CalibrationPieceLoadTest {

	@Test
	public void theShippedPieceHasThirteenEventsTheLastThreeBeingChords() {
		CalibrationPiece piece = CalibrationPiece.load();
		assertEquals(13, piece.eventCount());
		for (int i = 0; i < 10; i++) {
			assertFalse(piece.isChord(i), "event " + i + " is a single note");
		}
		for (int i = 10; i < 13; i++) {
			assertTrue(piece.isChord(i), "event " + i + " is a chord");
		}
		boolean[] chords = piece.chordFlags();
		assertEquals(13, chords.length);
		assertTrue(chords[12] && !chords[0]);
	}

	@Test
	public void thePieceBuildsItsJudge() {
		CalibrationPiece piece = CalibrationPiece.load();
		EventJudge judge = piece.judge(new TGContext());
		assertEquals(13, judge.eventCount());
		assertTrue(judge.isChord(11));
	}
}
