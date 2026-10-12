package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PitchPath;

/** What the picture needs to know of a written bend (spec 003 FR-077): how high, whether it starts bent, whether it comes back down. */
public class BendMarkTest {

	@Test
	public void aBendThatRisesAndHoldsHasItsPeakAndNothingElse() {
		BendMark mark = BendMark.of(PitchPath.of(0.0, 0.0, 0.5, 2.0, 1.0, 2.0));
		assertNotNull(mark);
		assertEquals(2.0, mark.peakSemitones(), 1e-9);
		assertFalse(mark.startsBent());
		assertFalse(mark.releases());
	}

	@Test
	public void aPreBendStartsBent() {
		BendMark mark = BendMark.of(PitchPath.of(0.0, 2.0, 1.0, 2.0));
		assertEquals(2.0, mark.peakSemitones(), 1e-9);
		assertTrue(mark.startsBent());
		assertFalse(mark.releases());
	}

	@Test
	public void aBendThatComesBackDownIsAReleaseAndABendReleaseKeepsItsPeak() {
		BendMark release = BendMark.of(PitchPath.of(0.0, 0.0, 0.5, 2.0, 1.0, 0.0));
		assertEquals(2.0, release.peakSemitones(), 1e-9);
		assertFalse(release.startsBent());
		assertTrue(release.releases());
		BendMark preRelease = BendMark.of(PitchPath.of(0.0, 2.0, 1.0, 0.0));
		assertTrue(preRelease.startsBent() && preRelease.releases());
	}

	@Test
	public void aPathThatNeverRisesAboveItsFrettedPitchShowsNothing() {
		assertNull(BendMark.of(null));
		assertNull(BendMark.of(PitchPath.of(0.0, 0.0, 1.0, 0.0)));
		assertNull(BendMark.of(PitchPath.of(0.0, 0.0, 1.0, -1.0)));
	}
}
