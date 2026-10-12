package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.TechniqueMark.Kind;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.song.models.TGNote;

/** What the picture shows of the techniques written on a note (spec 003 FR-078): the kind, and for a hammer-on, a pull-off or a slide the frets to the next note of the string. */
public class TechniqueMarkTest {

	private static List<TechniqueMark> marks(SongFixture song, int index) {
		List<PracticeEvent> events = song.sequence().getEvents();
		return TechniqueMark.of(events.get(index), index + 1 < events.size() ? events.get(index + 1) : null);
	}

	@Test
	public void aHammerOnGoesUpToTheNextNoteOfTheStringAndAPullOffGoesDown() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5).getEffect().setHammer(true);
		song.quarter(1, 1, 3, 8);
		song.quarter(1, 2, 3, 7).getEffect().setHammer(true);
		song.quarter(1, 3, 3, 5);
		assertEquals(Kind.HAMMER_ON, marks(song, 0).get(0).kind());
		assertEquals(3, marks(song, 0).get(0).fretDelta());
		assertEquals(Kind.PULL_OFF, marks(song, 2).get(0).kind());
		assertEquals(-2, marks(song, 2).get(0).fretDelta());
		assertTrue(marks(song, 1).isEmpty() && marks(song, 3).isEmpty(), "the destination notes carry nothing");
	}

	@Test
	public void aHammerOrPullOffWithoutANextNoteOnItsStringIsLegatoOfUnknownDirection() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5).getEffect().setHammer(true);
		song.quarter(1, 1, 2, 7);
		assertEquals(Kind.LEGATO, marks(song, 0).get(0).kind());
		assertEquals(0, marks(song, 0).get(0).fretDelta());
	}

	@Test
	public void aSlideHasItsDirectionAndWithoutANextNoteOnTheStringNoDestination() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5).getEffect().setSlide(true);
		song.quarter(1, 1, 3, 9);
		song.quarter(1, 2, 3, 9).getEffect().setSlide(true);
		song.quarter(1, 3, 2, 3);
		assertEquals(Kind.SLIDE, marks(song, 0).get(0).kind());
		assertEquals(4, marks(song, 0).get(0).fretDelta());
		assertEquals(Kind.SLIDE, marks(song, 2).get(0).kind());
		assertEquals(0, marks(song, 2).get(0).fretDelta(), "the next note is on another string: no destination");
	}

	@Test
	public void theOtherTechniquesAreMarksWithoutDestination() {
		SongFixture song = new SongFixture(2);
		TGNote vibrato = song.quarter(1, 0, 3, 5);
		vibrato.getEffect().setVibrato(true);
		TGNote palm = song.quarter(1, 1, 3, 5);
		palm.getEffect().setPalmMute(true);
		TGNote tap = song.quarter(1, 2, 3, 5);
		tap.getEffect().setTapping(true);
		TGNote tremolo = song.quarter(1, 3, 3, 5);
		tremolo.getEffect().setTremoloPicking(song.factory().newEffectTremoloPicking());
		assertEquals(Kind.VIBRATO, marks(song, 0).get(0).kind());
		assertEquals(Kind.PALM_MUTE, marks(song, 1).get(0).kind());
		assertEquals(Kind.TAPPING, marks(song, 2).get(0).kind());
		assertEquals(Kind.TREMOLO_PICKING, marks(song, 3).get(0).kind());
		for (int i = 0; i < 4; i++) {
			assertEquals(0, marks(song, i).get(0).fretDelta());
		}
	}

	@Test
	public void aPlainNoteAndAnEventThatIsNotASingleNoteHaveNoMark() {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 3, 5);
		song.chord(1, 1, new int[] { 6, 5 }, new int[] { 3, 3 });
		song.quarter(1, 2, 3, 5).getEffect().setDeadNote(true);
		assertTrue(marks(song, 0).isEmpty());
		assertTrue(marks(song, 1).isEmpty());
		assertTrue(marks(song, 2).isEmpty());
		assertNull(TechniqueMark.letterOf(Kind.VIBRATO));
		assertEquals("H/P", TechniqueMark.letterOf(Kind.LEGATO));
		assertEquals("H", TechniqueMark.letterOf(Kind.HAMMER_ON));
		assertEquals("P", TechniqueMark.letterOf(Kind.PULL_OFF));
		assertEquals("S", TechniqueMark.letterOf(Kind.SLIDE));
		assertEquals("P.M.", TechniqueMark.letterOf(Kind.PALM_MUTE));
		assertEquals("T", TechniqueMark.letterOf(Kind.TAPPING));
	}
}
