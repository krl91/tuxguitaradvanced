package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevel;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeLevelFilter;
import app.tuxguitar.app.tools.custom.practice.calibration.CalibrationPiece;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.effects.TGEffectBend;

/**
 * Spec 014 (T018, FR-006 to FR-009, SC-004): the level filter. The first sound of each kind is always kept and the others are spread by rank
 * ({@code floor(i x n / k)}, k = max(1, round(share x n))), the same ones at every run; a chord is whole; at least one sound is kept whenever the piece has one;
 * the events that are not judged are kept as they are; the removed sounds are given back in order. Contract arcade-levels and research R4. (In the model test
 * package because the song fixture's helpers are package-private.)
 */
public class ArcadeLevelFilterTest {

	/** The event indices kept by a level on a sequence. */
	private static List<Integer> keptIndices(PracticeSequence full, ArcadeLevelFilter.Result result) {
		List<Integer> indices = new ArrayList<Integer>();
		for (PracticeEvent kept : result.kept().getEvents()) {
			indices.add(full.getEvents().indexOf(kept));
		}
		return indices;
	}

	private static List<Integer> list(int... values) {
		List<Integer> list = new ArrayList<Integer>();
		for (int value : values) {
			list.add(value);
		}
		return list;
	}

	private static PracticeSequence notes(int count) {
		SongFixture fixture = new SongFixture((count + 3) / 4);
		for (int i = 0; i < count; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 12);
		}
		return PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), null, 0L);
	}

	private static PracticeSequence chords(int count) {
		SongFixture fixture = new SongFixture((count + 3) / 4);
		for (int i = 0; i < count; i++) {
			fixture.chord(i / 4, i % 4, new int[] { 5, 4 }, new int[] { 0, 2 });
		}
		return PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), null, 0L);
	}

	@Test
	public void onTheCalibrationPieceEachLevelKeepsTheStatedSounds() {
		PracticeSequence piece = CalibrationPiece.load().getSequence();
		assertEquals(13, piece.getEvents().size());
		for (int i = 0; i < 10; i++) {
			assertEquals(PracticeEvent.Kind.EXPECTED_NOTE, piece.getEvents().get(i).getKind(), "event " + i);
		}
		for (int i = 10; i < 13; i++) {
			assertEquals(PracticeEvent.Kind.EXPECTED_CHORD, piece.getEvents().get(i).getKind(), "event " + i);
		}
		assertEquals(list(0, 3, 6), keptIndices(piece, ArcadeLevelFilter.apply(piece, ArcadeLevel.of(1))), "level 1: notes 1, 4, 7, no chord");
		assertEquals(list(0, 3, 6, 10), keptIndices(piece, ArcadeLevelFilter.apply(piece, ArcadeLevel.of(2))), "level 2: the same notes and chord 1");
		assertEquals(list(0, 2, 4, 6, 8, 10, 11), keptIndices(piece, ArcadeLevelFilter.apply(piece, ArcadeLevel.of(3))), "level 3: notes 1, 3, 5, 7, 9 and chords 1, 2 (1.5 gives 2)");
		assertEquals(list(0, 1, 2, 3, 5, 6, 7, 8, 10, 11), keptIndices(piece, ArcadeLevelFilter.apply(piece, ArcadeLevel.of(4))), "level 4: notes 1-4 and 6-9, chords 1, 2");
		assertEquals(list(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), keptIndices(piece, ArcadeLevelFilter.apply(piece, ArcadeLevel.of(5))), "level 5: everything");
	}

	@Test
	public void theCountsAreTheRoundedShareAndAtLeastOneForEveryLengthOfPiece() {
		for (int n = 1; n <= 40; n++) {
			PracticeSequence piece = notes(n);
			for (ArcadeLevel level : ArcadeLevel.values()) {
				int kept = ArcadeLevelFilter.apply(piece, level).kept().getEvents().size();
				int expected = Math.max(1, (level.notePercent() * n + 50) / 100);
				assertEquals(expected, kept, n + " notes at level " + level.number());
			}
		}
		for (int n = 1; n <= 12; n++) {
			PracticeSequence piece = chords(n);
			for (ArcadeLevel level : ArcadeLevel.values()) {
				int kept = ArcadeLevelFilter.apply(piece, level).kept().getEvents().size();
				int expected = level.chordPercent() == 0 ? 1 : Math.max(1, (level.chordPercent() * n + 50) / 100);
				assertEquals(expected, kept, n + " chords at level " + level.number() + " (a piece of chords only is never empty)");
			}
		}
	}

	@Test
	public void theFirstSoundIsAlwaysKeptAndTheOthersAreSpreadEvenly() {
		for (int n = 2; n <= 40; n++) {
			PracticeSequence piece = notes(n);
			for (ArcadeLevel level : ArcadeLevel.values()) {
				List<Integer> kept = keptIndices(piece, ArcadeLevelFilter.apply(piece, level));
				assertEquals(0, (int) kept.get(0), "the first sound of " + n + " notes is kept at level " + level.number());
				if (kept.size() >= 3) {
					int smallest = Integer.MAX_VALUE;
					int largest = 0;
					for (int i = 1; i < kept.size(); i++) {
						int gap = kept.get(i) - kept.get(i - 1);
						smallest = Math.min(smallest, gap);
						largest = Math.max(largest, gap);
					}
					assertTrue(largest - smallest <= 1, n + " notes at level " + level.number() + ": gaps differ by at most one, " + kept);
				}
			}
		}
	}

	@Test
	public void theSameInputGivesTheSameSoundsAtEveryRun() {
		PracticeSequence piece = CalibrationPiece.load().getSequence();
		for (ArcadeLevel level : ArcadeLevel.values()) {
			assertEquals(keptIndices(piece, ArcadeLevelFilter.apply(piece, level)), keptIndices(piece, ArcadeLevelFilter.apply(piece, level)));
		}
	}

	@Test
	public void aChordIsKeptOrRemovedWholeAndTheKeptEventsAreTheOriginalObjects() {
		PracticeSequence piece = CalibrationPiece.load().getSequence();
		ArcadeLevelFilter.Result result = ArcadeLevelFilter.apply(piece, ArcadeLevel.of(3));
		for (PracticeEvent kept : result.kept().getEvents()) {
			assertSame(kept, piece.getEvents().get(piece.getEvents().indexOf(kept)));
			if (kept.getKind() == PracticeEvent.Kind.EXPECTED_CHORD) {
				int original = piece.getEvents().indexOf(kept);
				assertEquals(piece.getEvents().get(original).getRequiredPitches().length, kept.getRequiredPitches().length, "no note of a kept chord is lost");
			}
		}
	}

	@Test
	public void theRemovedSoundsAreGivenBackInOrderAndTogetherWithTheKeptOnesTheyAreTheWholePiece() {
		PracticeSequence piece = CalibrationPiece.load().getSequence();
		for (ArcadeLevel level : ArcadeLevel.values()) {
			ArcadeLevelFilter.Result result = ArcadeLevelFilter.apply(piece, level);
			List<PracticeEvent> all = new ArrayList<PracticeEvent>(result.kept().getEvents());
			all.addAll(result.removed());
			assertEquals(piece.getEvents().size(), all.size(), "level " + level.number());
			assertTrue(all.containsAll(piece.getEvents()));
			List<Integer> removedOrder = new ArrayList<Integer>();
			for (PracticeEvent removed : result.removed()) {
				removedOrder.add(piece.getEvents().indexOf(removed));
			}
			List<Integer> sorted = new ArrayList<Integer>(removedOrder);
			java.util.Collections.sort(sorted);
			assertEquals(sorted, removedOrder, "in the order of the piece");
		}
		assertTrue(ArcadeLevelFilter.apply(piece, ArcadeLevel.of(5)).removed().isEmpty(), "nothing is removed at level 5");
	}

	@Test
	public void levelFiveReturnsTheSequenceEventForEvent() {
		PracticeSequence piece = CalibrationPiece.load().getSequence();
		ArcadeLevelFilter.Result result = ArcadeLevelFilter.apply(piece, ArcadeLevel.of(5));
		assertEquals(piece.getEvents(), result.kept().getEvents());
		assertEquals(piece.getStartIndex(), result.kept().getStartIndex());
	}

	@Test
	public void aPieceWithOnlyChordsKeepsOneChordAtLevelOneAndAPieceWithoutChordsIgnoresTheirShare() {
		PracticeSequence onlyChords = chords(2);
		assertEquals(1, ArcadeLevelFilter.apply(onlyChords, ArcadeLevel.of(1)).kept().getEvents().size(), "level 1 would be empty: one chord is kept");
		assertEquals(1, ArcadeLevelFilter.apply(onlyChords, ArcadeLevel.of(2)).kept().getEvents().size());
		PracticeSequence onlyNotes = notes(10);
		assertEquals(3, ArcadeLevelFilter.apply(onlyNotes, ArcadeLevel.of(1)).kept().getEvents().size());
		assertEquals(3, ArcadeLevelFilter.apply(onlyNotes, ArcadeLevel.of(2)).kept().getEvents().size(), "the chord share changes nothing without chords");
		assertEquals(1, ArcadeLevelFilter.apply(notes(1), ArcadeLevel.of(1)).kept().getEvents().size(), "a piece of one note keeps it");
	}

	@Test
	public void theEventsThatAreNotJudgedAreKeptAndAMultiNoteEventCountsAsAChord() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 2);
		fixture.note(0, 1, 0, 1, 0, TGDuration.QUARTER).getEffect().setDeadNote(true);
		fixture.quarter(0, 2, 2, 3);
		TGEffectBend bend = fixture.factory.newEffectBend();
		bend.addPoint(0, 0);
		bend.addPoint(6, 4);
		fixture.quarter(0, 3, 3, 5).getEffect().setBend(bend);
		fixture.quarter(0, 3, 4, 5);
		PracticeSequence piece = PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), null, 0L);
		List<PracticeEvent.Kind> kinds = new ArrayList<PracticeEvent.Kind>();
		for (PracticeEvent event : piece.getEvents()) {
			kinds.add(event.getKind());
		}
		assertEquals(Arrays.asList(PracticeEvent.Kind.EXPECTED_NOTE, PracticeEvent.Kind.AUTO_PASS, PracticeEvent.Kind.EXPECTED_NOTE, PracticeEvent.Kind.MULTI_NOTE), kinds);
		ArcadeLevelFilter.Result result = ArcadeLevelFilter.apply(piece, ArcadeLevel.of(1));
		List<PracticeEvent.Kind> keptKinds = new ArrayList<PracticeEvent.Kind>();
		for (PracticeEvent event : result.kept().getEvents()) {
			keptKinds.add(event.getKind());
		}
		assertEquals(Arrays.asList(PracticeEvent.Kind.EXPECTED_NOTE, PracticeEvent.Kind.AUTO_PASS), keptKinds, "the first note and the dead note that is not judged; the second note and the multi-note chord are removed");
		assertFalse(result.removed().isEmpty());
		assertEquals(2, result.removed().size());
	}

	@Test
	public void theStartIndexFollowsTheKeptEventsAtOrAfterTheOriginalStart() {
		SongFixture fixture = new SongFixture(3);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.quarter(1, 2, 1, 2);
		fixture.quarter(2, 0, 1, 3);
		PracticeSequence fromCaret = PracticeSequenceBuilder.build(fixture.song, fixture.track(), fixture.tuning(), fixture.track().getMeasure(1),
				fixture.track().getMeasure(1).getStart() + 2 * SongFixture.QUARTER);
		assertEquals(2, fromCaret.getStartIndex());
		PracticeSequence kept = ArcadeLevelFilter.apply(fromCaret, ArcadeLevel.of(3)).kept();
		// 4 notes at 50 %: ranks 0 and 2 are kept; the third event (the start) is kept, at its new rank 1
		assertEquals(2, kept.getEvents().size());
		assertEquals(1, kept.getStartIndex());
		assertSame(fromCaret.getEvents().get(2), kept.getEvents().get(kept.getStartIndex()));
	}
}
