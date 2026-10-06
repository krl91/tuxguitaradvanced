package app.tuxguitar.app.tools.custom.practice.functional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.audio.engine.testsupport.TechniqueSignals;

/**
 * Bends through the whole practice chain on synthetic signals (spec 002 T081, contract of 2026-10-06): a written bend of a whole
 * tone on D4 followed by E4, the pitch the bend reaches. The base note starts the observation of the bend and the practice moves on;
 * the pitch moving along the written path is never a wrong note; the target pitch reached by bending is not the next note, which needs
 * its own attack; a bend that never happens does not block the practice; a pitch outside the path is still a real error.
 */
public class BendFunctionalTest {

	private static final int RATE = FunctionalRun.RATE;
	private static final int SAMPLES = 4 * RATE;
	private static final int D4 = 62;
	private static final int E4 = 64;
	/** The next note is picked here (seconds into the signal), well after the bend has reached its target. */
	private static final double NEXT_NOTE_SECOND = 2.2;
	private static final TechniqueCatalog BEND_VERIFIED = TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test");

	/** D4 bent a whole tone (string 3, fret 7), then E4 (string 3, fret 9). */
	private static SongFixture wholeToneBendThenE4() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 3, 9);
		return fixture;
	}

	private static float[] bendThenPickedNext(double... path) {
		float[] bend = TechniqueSignals.bend(D4, RATE, SAMPLES, path);
		float[] next = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(E4), RATE, SAMPLES - (int) (NEXT_NOTE_SECOND * RATE), 7L);
		return SyntheticSignals.mix(silenceAfter(bend, 1.9), next, (int) (NEXT_NOTE_SECOND * RATE));
	}

	/** The bent note stops sounding at the given second (a plucked note fading to silence). */
	private static float[] silenceAfter(float[] signal, double seconds) {
		float[] result = signal.clone();
		for( int i = (int) (seconds * RATE) ; i < result.length ; i ++ ) {
			result[i] = 0.0f;
		}
		return result;
	}

	private static TechniqueResult resultFor(FunctionalRun run, PlayingTechnique technique) {
		for( TechniqueResult result : run.techniqueResults ) {
			if( result.getTechnique() == technique ) {
				return result;
			}
		}
		return null;
	}

	@Test
	public void theBendAlongItsPathIsNeverAWrongNoteAndTheTargetIsNotTakenForTheNextNote() {
		// D4 held 0.15 s, bent to E4 over 0.25 s, held until 1.9 s; the next note E4 is picked at 2.2 s
		FunctionalRun run = FunctionalRun.of(wholeToneBendThenE4(), bendThenPickedNext(0.0, 0.0, 0.15, 0.0, 0.40, 2.0));

		assertFalse(run.wrongSeen, "a pitch on the written path was shown as a wrong note");
		assertEquals(2, run.accepted);
		assertTrue(run.acceptedAt.get(0) < 0.8, "the base note starts the bend and is accepted: " + run.acceptedAt);
		assertTrue(run.acceptedAt.get(1) >= NEXT_NOTE_SECOND, "the pitch the bend reaches was accepted as the next note at " + run.acceptedAt.get(1));
	}

	@Test
	public void theBentPitchAloneDoesNotAcceptTheNextNoteEvenWhenNothingIsPickedAfterwards() {
		// the same bend and no next note: only the bent note is accepted
		FunctionalRun run = FunctionalRun.of(wholeToneBendThenE4(), TechniqueSignals.bend(D4, RATE, SAMPLES, 0.0, 0.0, 0.15, 0.0, 0.40, 2.0));

		assertFalse(run.wrongSeen);
		assertEquals(1, run.accepted, "the target pitch of the bend, held, was accepted as the next note");
	}

	@Test
	public void aBendThatNeverHappensDoesNotBlockThePracticeAndIsNotDetected() {
		FunctionalRun run = FunctionalRun.of(wholeToneBendThenE4(), bendThenPickedNext(0.0, 0.0, 3.0, 0.0), BEND_VERIFIED);

		assertEquals(2, run.accepted, "the practice waited for a bend that was not played");
		assertFalse(run.wrongSeen);
		TechniqueResult bend = resultFor(run, PlayingTechnique.BEND);
		assertEquals(TechniqueVerdict.NOT_DETECTED, bend.getVerdict());
	}

	@Test
	public void aBendThatReachesItsTargetIsDetectedOnPitchAndNotBeforeItIsReached() {
		FunctionalRun run = FunctionalRun.of(wholeToneBendThenE4(), bendThenPickedNext(0.0, 0.0, 0.15, 0.0, 0.40, 2.0), BEND_VERIFIED);

		TechniqueResult bend = resultFor(run, PlayingTechnique.BEND);
		assertEquals(TechniqueVerdict.DETECTED, bend.getVerdict());
		assertEquals(0.0, bend.getMeasurements().get("outcome"), 0.001);
		// nothing was said while the pitch was still moving
		assertTrue(run.firstResultAt >= 0.40, "the technique was reported at " + run.firstResultAt + " before the target was reached");
	}

	@Test
	public void aBendThatStopsHalfWayIsReportedUnderAndNotDetected() {
		FunctionalRun run = FunctionalRun.of(wholeToneBendThenE4(), bendThenPickedNext(0.0, 0.0, 0.15, 0.0, 0.40, 1.0), BEND_VERIFIED);

		assertEquals(2, run.accepted);
		TechniqueResult bend = resultFor(run, PlayingTechnique.BEND);
		assertNotEquals(TechniqueVerdict.DETECTED, bend.getVerdict());
		assertEquals(-1.0, bend.getMeasurements().get("outcome"), 0.001);
	}

	@Test
	public void aBendAndItsReleaseFollowTheirPathsWithoutAWrongNote() {
		SongFixture fixture = new SongFixture(1);
		fixture.bendPoints(0, 0, 3, 7, 0, 0, 3, 4, 6, 4, 10, 0, 12, 0);
		fixture.quarter(0, 1, 3, 9);
		// rises to E4 by 0.4 s, holds, falls back to D4 between 0.7 s and 0.95 s (inside the observation window), then the next note
		FunctionalRun run = FunctionalRun.of(fixture, bendThenPickedNext(0.0, 0.0, 0.15, 0.0, 0.40, 2.0, 0.7, 2.0, 0.95, 0.0), BEND_VERIFIED);

		assertFalse(run.wrongSeen);
		assertEquals(2, run.accepted);
		assertEquals(TechniqueVerdict.DETECTED, resultFor(run, PlayingTechnique.BEND).getVerdict());
		assertEquals(TechniqueVerdict.DETECTED, resultFor(run, PlayingTechnique.RELEASE).getVerdict());
	}

	@Test
	public void aPreBendIsAcceptedAtTheBentPitch() {
		SongFixture fixture = new SongFixture(1);
		fixture.bendPoints(0, 0, 3, 7, 0, 4, 12, 4);
		fixture.quarter(0, 1, 3, 11);

		// bent a whole tone before the note is struck: E4 is what is heard (a pitch outside the path being a wrong note is in BendSessionTest,
		// which scripts the frames: this chain reports a wrong note only for a note whose attack the spectrum confirms)
		FunctionalRun bent = FunctionalRun.of(fixture, SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(E4), RATE, SAMPLES, 4L));
		assertTrue(bent.accepted >= 1);
		assertFalse(bent.wrongSeen);
	}
}
