package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The song builders of the functional tests (spec 002 T129): what each builds is what the practice sequence makes of it. */
public class SongFixtureBuildersTest {

	@Test
	public void aLegatoPairMakesTheDestinationAHammerOnOrAPullOff() {
		SongFixture up = new SongFixture(1);
		up.legatoPair(0, 0, 2, 5, 7);
		List<PracticeEvent> events = up.sequence().getEvents();
		assertEquals(2, events.size());
		assertTrue(events.get(0).getTechniques().contains(PracticeEvent.Technique.HAMMER_ON_PULL_OFF));
		assertEquals(PlayingTechnique.HAMMER_ON, up.sequence().targetsOf(1).get(0).getTechnique());

		SongFixture down = new SongFixture(1);
		down.legatoPair(0, 0, 2, 7, 5);
		assertEquals(PlayingTechnique.PULL_OFF, down.sequence().targetsOf(1).get(0).getTechnique());
	}

	@Test
	public void aChordStrumIsOneExpectedChordWithItsSoundingPitches() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 6, 5, 4 }, new int[] { 0, 2, 2 });
		List<PracticeEvent> events = fixture.sequence().getEvents();
		assertEquals(1, events.size());
		assertEquals(PracticeEvent.Kind.EXPECTED_CHORD, events.get(0).getKind());
		assertArrayEquals(new int[] { 40, 47, 52 }, events.get(0).getRequiredPitches());
	}

	@Test
	public void aBendIsAnExpectedNoteWithAPathAndANaturalHarmonicIsStillPassed() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 2, 7, 1);
		fixture.naturalHarmonic(0, 1, 3, 12);
		List<PracticeEvent> events = fixture.sequence().getEvents();
		assertEquals(2, events.size());
		assertEquals(PracticeEvent.Kind.EXPECTED_NOTE, events.get(0).getKind());
		assertEquals(1.0, events.get(0).getBendPath().getTargetSemitones(), 1e-9);
		assertEquals(PracticeEvent.Kind.AUTO_PASS, events.get(1).getKind());
		assertEquals(PracticeEvent.AutoPassReason.HARMONIC, events.get(1).getAutoPassReason());
	}

	@Test
	public void aTempoChangeAppliesFromItsMeasureOn() {
		SongFixture fixture = new SongFixture(4);
		fixture.tempoChange(2, 140);
		assertEquals(120, fixture.header(0).getTempo().getQuarterValue());
		assertEquals(120, fixture.header(1).getTempo().getQuarterValue());
		assertEquals(140, fixture.header(2).getTempo().getQuarterValue());
		assertEquals(140, fixture.header(3).getTempo().getQuarterValue());
	}

	@Test
	public void aRepeatPlaysItsSectionTheGivenNumberOfTimes() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.repeat(0, 1, 2);
		List<PracticeEvent> events = fixture.sequence().getEvents();
		assertEquals(4, events.size(), "two notes, the section played twice");
		assertEquals(64, events.get(0).getExpectedMidi());
		assertEquals(65, events.get(1).getExpectedMidi());
		assertEquals(64, events.get(2).getExpectedMidi());
		assertEquals(65, events.get(3).getExpectedMidi());
	}
}
