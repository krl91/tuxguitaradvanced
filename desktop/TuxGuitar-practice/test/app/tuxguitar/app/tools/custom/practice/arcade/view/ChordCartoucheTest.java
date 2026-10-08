package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.ChordCartouche.Mark;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGStroke;

/**
 * The chord diagram (spec 003 T036, visual-design.md): the A5 fixture, an open chord, muted strings only when written, metadata used only when it fits,
 * no name or finger invented, the stroke as written.
 */
public class ChordCartoucheTest {

	private static final StringLayout SIX = StringLayout.of(6);

	private static ChordCartouche cartoucheOf(SongFixture fixture, int eventIndex) {
		FollowTimeline timeline = FollowTimeline.build(fixture.song, fixture.sequence(), 100);
		List<PracticeEvent> events = fixture.sequence().getEvents();
		return ChordCartouche.of(ArcadeRenderEvent.of(eventIndex, events.get(eventIndex), timeline), SIX);
	}

	private static List<Integer> columns(ChordCartouche cartouche) {
		List<Integer> columns = new ArrayList<Integer>();
		for (ChordCartouche.Dot dot : cartouche.dots()) {
			columns.add(dot.column());
		}
		return columns;
	}

	@Test
	public void theA5ChordIsAFifthFretWindowWithThreeDotsInTheirOwnColours() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });

		ChordCartouche cartouche = cartoucheOf(fixture, 0);

		assertEquals(6, cartouche.strings(), "the six vertical lines of the track");
		assertEquals(5, cartouche.firstFret());
		assertEquals("5fr", cartouche.fretLabel());
		assertEquals(ChordCartouche.MIN_ROWS, cartouche.rows());
		assertEquals(3, cartouche.dots().size());
		assertEquals(List.of(0, 1, 2), columns(cartouche), "the low strings are on the left");
		assertEquals(0, cartouche.dots().get(0).row());
		assertEquals(2, cartouche.dots().get(1).row());
		assertEquals(2, cartouche.dots().get(2).row());
		StringColours colours = StringColours.forStrings(6);
		assertEquals(StringColours.COBALT, colours.of(cartouche.dots().get(0).tgString()), "low E: cobalt");
		assertEquals(StringColours.CYAN, colours.of(cartouche.dots().get(1).tgString()), "A: cyan");
		assertEquals(StringColours.TEAL, colours.of(cartouche.dots().get(2).tgString()), "D: teal");
		assertNull(cartouche.name(), "no name written, none invented");
	}

	@Test
	public void anOpenChordStartsAtTheFirstFretAndMarksItsOpenStrings() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 1, 0, 0 });

		ChordCartouche cartouche = cartoucheOf(fixture, 0);

		assertEquals(1, cartouche.firstFret());
		assertNull(cartouche.fretLabel(), "no label for the first position");
		assertEquals(3, cartouche.dots().size());
		assertEquals(Mark.OPEN, cartouche.markAt(0), "low E open");
		assertEquals(Mark.NONE, cartouche.markAt(1));
		assertEquals(Mark.NONE, cartouche.markAt(3));
		assertEquals(Mark.OPEN, cartouche.markAt(4), "B open");
		assertEquals(Mark.OPEN, cartouche.markAt(5), "high e open");
	}

	@Test
	public void aMutedStringIsMarkedOnlyWhenTheScoreWritesItAndAnOmittedStringIsBlank() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 6, 0).getEffect().setDeadNote(true);
		fixture.quarter(0, 0, 5, 0);
		fixture.quarter(0, 0, 4, 2);
		fixture.chord(0, 1, new int[] { 5, 4 }, new int[] { 0, 2 });

		ChordCartouche muted = cartoucheOf(fixture, 0);
		ChordCartouche omitted = cartoucheOf(fixture, 1);

		assertEquals(Mark.DEAD, muted.markAt(0), "written dead note");
		assertEquals(Mark.OPEN, muted.markAt(1));
		assertEquals(Mark.NONE, omitted.markAt(0), "a string that is not written is blank, not muted");
		assertEquals(Mark.NONE, omitted.markAt(3));
		assertEquals(Mark.NONE, omitted.markAt(5));
	}

	@Test
	public void theHintOfTheWrittenDiagramIsUsedWhenItFitsAndIgnoredWhenItDoesNot() {
		SongFixture fixture = new SongFixture(1);
		for (int beat = 0; beat < 4; beat++) {
			fixture.chord(0, beat, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		}
		int[] hints = { 4, 9, 3, 5 };
		int[] strings = { 6, 6, 5, 6 };
		for (int beat = 0; beat < 4; beat++) {
			TGChord chord = fixture.factory().newChord(strings[beat]);
			chord.setFirstFret(hints[beat]);
			fixture.track().getMeasure(0).getBeat(beat).setChord(chord);
		}

		assertEquals(4, cartoucheOf(fixture, 0).firstFret(), "a compatible hint: the window starts at 4");
		assertEquals(5, cartoucheOf(fixture, 1).firstFret(), "a hint above the lowest written fret is inconsistent: ignored");
		assertEquals(5, cartoucheOf(fixture, 2).firstFret(), "a hint of a diagram with another number of strings: ignored");
		assertEquals(5, cartoucheOf(fixture, 3).firstFret());
		assertEquals("4fr", cartoucheOf(fixture, 0).fretLabel());
		assertEquals(1, cartoucheOf(fixture, 0).dots().get(0).row(), "the dots follow the window");
	}

	@Test
	public void aWideChordGetsMoreRowsAndTheNameAndStrokeAreOnlyWhatIsWritten() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 6, 1 }, new int[] { 3, 9 });
		fixture.chord(0, 1, new int[] { 5, 4 }, new int[] { 0, 2 });
		TGChord named = fixture.factory().newChord(6);
		named.setName("G7");
		fixture.track().getMeasure(0).getBeat(0).setChord(named);
		fixture.track().getMeasure(0).getBeat(0).getStroke().setDirection(TGStroke.STROKE_UP);

		ChordCartouche wide = cartoucheOf(fixture, 0);
		ChordCartouche plain = cartoucheOf(fixture, 1);

		assertEquals(3, wide.firstFret());
		assertEquals(7, wide.rows(), "frets 3 to 9");
		assertEquals("G7", wide.name());
		assertEquals(ArcadeRenderEvent.Stroke.UP, wide.stroke());
		assertEquals(ArcadeRenderEvent.Stroke.NONE, plain.stroke(), "no stroke written, no arrow");
		assertNull(plain.name());
	}

	@Test
	public void duplicatedPitchesAreFlaggedSoThatTheDiagramNeverClaimsEachNoteWasHeard() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 5);
		fixture.quarter(0, 0, 3, 2);

		assertTrue(cartoucheOf(fixture, 0).notCheckedSeparately());
		SongFixture other = new SongFixture(1);
		other.chord(0, 0, new int[] { 6, 5 }, new int[] { 5, 7 });
		assertFalse(cartoucheOf(other, 0).notCheckedSeparately());
	}

	@Test
	public void theDiagramHasAMinimumSizeOnTheScreen() {
		assertTrue(ChordCartouche.MIN_WIDTH_PX >= 48.0 && ChordCartouche.MIN_HEIGHT_PX >= 60.0, "readable even in the distance");
	}
}
