package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent.Kind;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGStroke;
import app.tuxguitar.song.models.effects.TGEffectBend;

/**
 * The events of the view come from the practice events and the timeline, nothing else (spec 003 T034, visual-design.md): the onset is the one of the
 * following mode across tempo changes and speeds, the positions are the tablature's, and what cannot be shown as a recognised event is shown as what it is.
 */
public class ArcadeRenderEventTest {

	private static List<ArcadeRenderEvent> eventsOf(SongFixture fixture, int percent) {
		FollowTimeline timeline = FollowTimeline.build(fixture.song, fixture.sequence(), percent);
		List<PracticeEvent> events = fixture.sequence().getEvents();
		java.util.ArrayList<ArcadeRenderEvent> result = new java.util.ArrayList<ArcadeRenderEvent>();
		for (int i = 0; i < events.size(); i++) {
			result.add(ArcadeRenderEvent.of(i, events.get(i), timeline));
		}
		return result;
	}

	@Test
	public void aNoteIsOnePositionOfTheTablatureAtTheOnsetOfTheTimeline() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 3, 7);
		fixture.quarter(0, 1, 6, 0);
		fixture.quarter(0, 2, 1, 12);

		List<ArcadeRenderEvent> events = eventsOf(fixture, 100);
		FollowTimeline timeline = FollowTimeline.build(fixture.song, fixture.sequence(), 100);

		assertEquals(3, events.size());
		assertEquals(Kind.NOTE, events.get(0).kind());
		assertEquals(1, events.get(0).positions().size());
		assertEquals(3, events.get(0).positions().get(0).tgString());
		assertEquals(7, events.get(0).positions().get(0).fret());
		assertEquals(7, events.get(0).minFret());
		assertEquals(7, events.get(0).maxFret());
		for (int i = 0; i < 3; i++) {
			assertEquals(timeline.targetNanos(i), events.get(i).onsetNanos(), "event " + i);
			assertEquals(i, events.get(i).index());
		}
		assertEquals(0, events.get(1).positions().get(0).fret(), "an open string is a position too");
	}

	@Test
	public void theOnsetFollowsTheSpeedTheTempoChangesAndTheRepeatsOfTheTimeline() {
		SongFixture fixture = new SongFixture(3);
		for (int measure = 0; measure < 3; measure++) {
			fixture.quarter(measure, 0, 1, measure);
		}
		fixture.tempoChange(1, 60);
		fixture.repeat(0, 0, 2);

		for (int percent : new int[] { 50, 100, 130 }) {
			FollowTimeline timeline = FollowTimeline.build(fixture.song, fixture.sequence(), percent);
			List<ArcadeRenderEvent> events = eventsOf(fixture, percent);
			assertEquals(4, events.size(), "measure 0 is played twice");
			for (int i = 0; i < events.size(); i++) {
				assertEquals(timeline.targetNanos(i), events.get(i).onsetNanos(), percent + " %, event " + i);
			}
			assertTrue(events.get(1).onsetNanos() > events.get(0).onsetNanos());
		}
	}

	@Test
	public void aChordIsOneEventWithAPositionPerNewNoteAndTheNameOnlyFromTheScore() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		TGBeat beat = fixture.track().getMeasure(0).getBeat(0);
		TGChord named = fixture.factory().newChord(6);
		named.setName("A5");
		beat.setChord(named);
		fixture.chord(0, 1, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		fixture.chord(0, 2, new int[] { 6, 5, 4 }, new int[] { 5, 7, 7 });
		TGChord blank = fixture.factory().newChord(6);
		blank.setName("   ");
		fixture.track().getMeasure(0).getBeat(2).setChord(blank);

		List<ArcadeRenderEvent> events = eventsOf(fixture, 100);

		assertEquals(Kind.CHORD, events.get(0).kind());
		assertEquals(3, events.get(0).positions().size());
		assertEquals(5, events.get(0).minFret());
		assertEquals(7, events.get(0).maxFret());
		assertEquals("A5", events.get(0).chordName());
		assertNull(events.get(1).chordName(), "no name written, none invented from the pitches");
		assertNull(events.get(2).chordName(), "a blank name is no name");
		assertEquals(3, events.get(0).positions().stream().map(ArcadeRenderEvent.Position::tgString).distinct().count());
	}

	@Test
	public void theWrittenStrokeIsKeptAndNoneStaysNone() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 5, 4 }, new int[] { 0, 2 });
		fixture.chord(0, 1, new int[] { 5, 4 }, new int[] { 0, 2 });
		fixture.chord(0, 2, new int[] { 5, 4 }, new int[] { 0, 2 });
		fixture.track().getMeasure(0).getBeat(0).getStroke().setDirection(TGStroke.STROKE_DOWN);
		fixture.track().getMeasure(0).getBeat(1).getStroke().setDirection(TGStroke.STROKE_UP);

		List<ArcadeRenderEvent> events = eventsOf(fixture, 100);

		assertEquals(ArcadeRenderEvent.Stroke.DOWN, events.get(0).stroke());
		assertEquals(ArcadeRenderEvent.Stroke.UP, events.get(1).stroke());
		assertEquals(ArcadeRenderEvent.Stroke.NONE, events.get(2).stroke(), "no direction is invented when none is written");
	}

	@Test
	public void deadStringsOfAChordAreExplicitAndOmittedStringsAreNot() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 6, 0).getEffect().setDeadNote(true);
		fixture.quarter(0, 0, 5, 0);
		fixture.quarter(0, 0, 4, 2);

		ArcadeRenderEvent chord = eventsOf(fixture, 100).get(0);

		assertEquals(Kind.CHORD, chord.kind());
		assertEquals(List.of(6), chord.deadStrings(), "the muted string is written");
		assertEquals(2, chord.positions().size(), "a dead string is not a position to play");
	}

	@Test
	public void anEventThatIsNotARecognisedNoteOrChordIsShownAsWhatItIs() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 2, 3);
		TGEffectBend bend = fixture.factory().newEffectBend();
		bend.addPoint(0, 0);
		bend.addPoint(6, 4);
		TGNote bent = fixture.quarter(0, 0, 3, 5);
		bent.getEffect().setBend(bend);
		fixture.quarter(0, 1, 1, 0).getEffect().setDeadNote(true);

		List<ArcadeRenderEvent> events = eventsOf(fixture, 100);

		assertEquals(Kind.UNSUPPORTED, events.get(0).kind(), "several notes with a bend: not judged, not disguised as a chord");
		assertEquals(Kind.AUTOMATIC, events.get(1).kind(), "a dead note passes by itself");
	}

	@Test
	public void duplicatedPitchesAreFlaggedSoThatNothingIsClaimedForEachOfThem() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 5);
		fixture.quarter(0, 0, 3, 2);

		assertTrue(eventsOf(fixture, 100).get(0).hasDuplicatePitches());
	}
}
