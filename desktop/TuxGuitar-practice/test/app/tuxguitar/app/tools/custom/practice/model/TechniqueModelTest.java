package app.tuxguitar.app.tools.custom.practice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.song.models.TGNote;

public class TechniqueModelTest {

	@Test
	public void onlyThreeVerdictsComeFromADetector() {
		assertTrue(TechniqueVerdict.DETECTED.isDetectorVerdict());
		assertTrue(TechniqueVerdict.NOT_DETECTED.isDetectorVerdict());
		assertTrue(TechniqueVerdict.UNCERTAIN.isDetectorVerdict());
		assertFalse(TechniqueVerdict.NOT_VERIFIABLE.isDetectorVerdict());
	}

	@Test
	public void displayOnlyTechniquesAreNeverJudged() {
		assertFalse(PlayingTechnique.ACCENT.isJudged());
		assertFalse(PlayingTechnique.LET_RING.isJudged());
		assertFalse(PlayingTechnique.STACCATO.isJudged());
		assertTrue(PlayingTechnique.BEND.isJudged());
		assertTrue(PlayingTechnique.PINCH_HARMONIC.isJudged());
	}

	@Test
	public void aPitchPathInterpolatesAndReportsItsTarget() {
		PitchPath path = PitchPath.of(0.0, 0.0, 0.5, 1.0, 1.0, 1.0);
		assertEquals(1.0, path.getTargetSemitones(), 1e-9);
		assertEquals(1.0, path.getPeakSemitones(), 1e-9);
		assertEquals(0.5, path.semitonesAt(0.25), 1e-9);
		assertEquals(1.0, path.semitonesAt(0.75), 1e-9);
		assertEquals(0.0, path.semitonesAt(-1.0), 1e-9);
		assertEquals(1.0, path.semitonesAt(2.0), 1e-9);
	}

	@Test
	public void aReleasePathPeaksBeforeItsEnd() {
		PitchPath path = PitchPath.of(0.0, 0.0, 0.4, 2.0, 1.0, 0.0);
		assertEquals(2.0, path.getPeakSemitones(), 1e-9);
		assertEquals(0.0, path.getTargetSemitones(), 1e-9);
	}

	@Test
	public void invalidPathsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> PitchPath.of());
		assertThrows(IllegalArgumentException.class, () -> PitchPath.of(0.0));
		assertThrows(IllegalArgumentException.class, () -> PitchPath.of(0.5, 0.0, 0.5, 1.0));
		assertThrows(IllegalArgumentException.class, () -> PitchPath.of(0.0, 0.0, 1.5, 1.0));
	}

	@Test
	public void targetsCarryOnlyTheirOwnParts() {
		TechniqueTarget plain = TechniqueTarget.of(PlayingTechnique.VIBRATO);
		assertEquals(TechniqueTarget.NONE, plain.getTargetMidi());
		assertNull(plain.getPitchPath());

		TechniqueTarget legato = TechniqueTarget.withPartner(PlayingTechnique.HAMMER_ON, 52, 7);
		assertEquals(7, legato.getPartnerEventIndex());
		assertEquals(52, legato.getTargetMidi());

		TechniqueTarget bend = TechniqueTarget.withPath(PlayingTechnique.BEND, 57, PitchPath.of(0.0, 0.0, 1.0, 1.0));
		assertEquals(1.0, bend.getPitchPath().getTargetSemitones(), 1e-9);

		TechniqueTarget harmonic = TechniqueTarget.withSoundingPitch(PlayingTechnique.NATURAL_HARMONIC, 64);
		assertEquals(64, harmonic.getSoundingMidi());

		assertThrows(IllegalArgumentException.class, () -> TechniqueTarget.of(null));
		assertThrows(IllegalArgumentException.class, () -> TechniqueTarget.withPath(PlayingTechnique.BEND, 57, null));
	}

	@Test
	public void theBuilderDerivesTargetsFromTheWrittenEffectsWithoutChangingThemOtherwise() {
		SongFixture fixture = new SongFixture(1);
		TGNote vibrato = fixture.quarter(0, 0, 6, 0);
		vibrato.getEffect().setVibrato(true);
		TGNote palm = fixture.quarter(0, 1, 6, 0);
		palm.getEffect().setPalmMute(true);
		fixture.quarter(0, 2, 6, 0);

		List<PracticeEvent> events = fixture.sequence().getEvents();

		assertEquals(List.of(PlayingTechnique.VIBRATO), techniques(events.get(0)));
		assertEquals(List.of(PlayingTechnique.PALM_MUTE), techniques(events.get(1)));
		assertTrue(events.get(2).getTechniqueTargets().isEmpty());
		// the existing display-only set is unchanged
		assertEquals(java.util.Set.of(PracticeEvent.Technique.VIBRATO), events.get(0).getTechniques());
	}

	private static List<PlayingTechnique> techniques(PracticeEvent event) {
		java.util.ArrayList<PlayingTechnique> result = new java.util.ArrayList<PlayingTechnique>();
		for( TechniqueTarget target : event.getTechniqueTargets() ) {
			result.add(target.getTechnique());
		}
		return result;
	}
}
