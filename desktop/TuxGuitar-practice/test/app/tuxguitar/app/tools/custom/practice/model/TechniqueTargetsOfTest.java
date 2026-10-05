package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Spec 002 T068/T069: the destination of a hammer-on or pull-off is told apart by its pitch. */
public class TechniqueTargetsOfTest {

	@Test
	public void aHigherDestinationIsAHammerOnWithItsOriginAsPartner() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0).getEffect().setHammer(true);
		fixture.quarter(0, 1, 1, 2);

		PracticeSequence sequence = fixture.sequence();

		assertTrue(sequence.targetsOf(0).isEmpty());
		List<TechniqueTarget> destination = sequence.targetsOf(1);
		assertEquals(1, destination.size());
		assertEquals(PlayingTechnique.HAMMER_ON, destination.get(0).getTechnique());
		assertEquals(0, destination.get(0).getPartnerEventIndex());
		assertEquals(66, destination.get(0).getTargetMidi());
	}

	@Test
	public void aLowerDestinationIsAPullOff() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 3).getEffect().setHammer(true);
		fixture.quarter(0, 1, 1, 0);

		assertEquals(PlayingTechnique.PULL_OFF, fixture.sequence().targetsOf(1).get(0).getTechnique());
	}

	@Test
	public void theSamePitchOrAPlainNoteHasNoLegatoTarget() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 3).getEffect().setHammer(true);
		fixture.quarter(0, 1, 1, 3);
		fixture.quarter(0, 2, 1, 5);

		PracticeSequence sequence = fixture.sequence();

		assertTrue(sequence.targetsOf(1).isEmpty());
		assertTrue(sequence.targetsOf(2).isEmpty());
	}

	@Test
	public void aWrittenSlideAndVibratoStayOnTheirOwnEvent() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 3).getEffect().setVibrato(true);

		List<TechniqueTarget> targets = fixture.sequence().targetsOf(0);

		assertEquals(1, targets.size());
		assertEquals(PlayingTechnique.VIBRATO, targets.get(0).getTechnique());
	}
}
