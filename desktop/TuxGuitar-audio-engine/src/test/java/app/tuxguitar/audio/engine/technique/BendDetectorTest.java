package app.tuxguitar.audio.engine.technique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.DoubleUnaryOperator;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.technique.TechniqueEvidence.Verdict;

/**
 * The bend judged against its written path (spec 002 T076, contract of 2026-10-06): the pitch the note
 * reaches is compared with the target of the path, the result is under / on pitch / over, the bend is
 * not called made before it has reached its target, and a bend that never happens is not detected.
 * The segments are built from a pitch trajectory (deterministic, no audio).
 */
public class BendDetectorTest {

	private static final double HOP = 256.0 / 48000.0;
	private static final double BASE = 64.0;

	private final BendDetector detector = new BendDetector(BendDetector.Parameters.defaults());

	private static NoteSegment segment(double seconds, DoubleUnaryOperator midiAt) {
		int n = (int) (seconds / HOP);
		double[] midi = new double[n];
		double[] level = new double[n];
		for( int i = 0 ; i < n ; i ++ ) {
			midi[i] = midiAt.applyAsDouble(i * HOP);
			level[i] = -20.0;
		}
		return new NoteSegment(midi, level, new boolean[n], HOP);
	}

	/** Holds the base pitch for 0.2 s, rises to {@code base + semitones} over 0.2 s and holds. */
	private static NoteSegment bendTo(double semitones, double seconds) {
		return segment(seconds, t -> BASE + semitones * Math.max(0.0, Math.min(1.0, (t - 0.2) / 0.2)));
	}

	private static int outcome(TechniqueEvidence evidence) {
		return (int) Math.round(evidence.getMeasurements().get("outcome"));
	}

	@Test
	public void aBendThatReachesItsTargetIsDetectedOnPitch() {
		BendPath path = BendPath.of(0.0, 0.0, 0.3, 1.0, 1.0, 1.0);
		TechniqueEvidence found = this.detector.evaluate(bendTo(1.0, 1.0), BASE, path);

		assertEquals(Verdict.DETECTED, found.getVerdict());
		assertEquals(0, outcome(found));
		assertEquals(100.0, found.getMeasurements().get("riseCents"), 15.0);
		assertEquals(100.0, found.getMeasurements().get("targetRiseCents"), 0.001);
	}

	@Test
	public void aBendThatStopsHalfWayReportsUnderAndIsNotDetected() {
		BendPath path = BendPath.of(0.0, 0.0, 0.3, 1.0, 1.0, 1.0);
		TechniqueEvidence found = this.detector.evaluate(bendTo(0.5, 1.0), BASE, path);

		assertEquals(-1, outcome(found));
		assertNotEquals(Verdict.DETECTED, found.getVerdict());
	}

	@Test
	public void aBendThatGoesTooFarReportsOverAndIsDetected() {
		BendPath path = BendPath.of(0.0, 0.0, 0.3, 1.0, 1.0, 1.0);
		TechniqueEvidence found = this.detector.evaluate(bendTo(1.5, 1.0), BASE, path);

		assertEquals(Verdict.DETECTED, found.getVerdict());
		assertEquals(1, outcome(found));
	}

	@Test
	public void aBendThatNeverHappensIsNotDetectedAndNothingWaitsForIt() {
		BendPath path = BendPath.of(0.0, 0.0, 0.3, 2.0, 1.0, 2.0);
		TechniqueEvidence found = this.detector.evaluate(segment(1.0, t -> BASE), BASE, path);

		assertEquals(Verdict.NOT_DETECTED, found.getVerdict());
		assertEquals(-1, outcome(found));
	}

	@Test
	public void theBendIsNotCalledMadeBeforeItHasReachedItsTarget() {
		BendPath path = BendPath.of(0.0, 0.0, 0.3, 1.0, 1.0, 1.0);
		NoteSegment whole = bendTo(1.0, 1.0);
		// the same trajectory seen only until the middle of the rise: the target is not reached yet
		NoteSegment early = segment(0.30, t -> BASE + Math.max(0.0, Math.min(1.0, (t - 0.2) / 0.2)));

		assertNotEquals(Verdict.DETECTED, this.detector.evaluate(early, BASE, path).getVerdict());
		assertEquals(Verdict.DETECTED, this.detector.evaluate(whole, BASE, path).getVerdict());
	}

	@Test
	public void aBendWithItsReleaseNeedsTheRiseAndTheFall() {
		BendPath path = BendPath.of(0.0, 0.0, 0.25, 2.0, 0.5, 2.0, 0.8, 0.0, 1.0, 0.0);
		NoteSegment risesAndFalls = segment(1.6, t -> BASE + 2.0 * Math.max(0.0, Math.min(1.0, (t - 0.2) / 0.25)) * (t < 0.9 ? 1.0 : Math.max(0.0, 1.0 - (t - 0.9) / 0.3)));
		NoteSegment risesOnly = segment(1.6, t -> BASE + 2.0 * Math.max(0.0, Math.min(1.0, (t - 0.2) / 0.25)));

		TechniqueEvidence made = this.detector.evaluate(risesAndFalls, BASE, path);
		assertEquals(Verdict.DETECTED, made.getVerdict());
		assertEquals(0, outcome(made));
		assertTrue(made.getMeasurements().get("fallCents") > 150.0);

		TechniqueEvidence heldUp = this.detector.evaluate(risesOnly, BASE, path);
		assertNotEquals(Verdict.DETECTED, heldUp.getVerdict(), "the release was written and not played");
	}

	@Test
	public void aPreBendIsDetectedWhenTheNoteStartsBentAndNotWhenItStartsAtTheFrettedPitch() {
		// the string is bent a whole tone before it is struck, then released
		BendPath path = BendPath.of(0.0, 2.0, 0.5, 2.0, 1.0, 0.0);
		NoteSegment bentThenReleased = segment(1.2, t -> BASE + 2.0 * (t < 0.5 ? 1.0 : Math.max(0.0, 1.0 - (t - 0.5) / 0.3)));
		NoteSegment notPreBent = segment(1.2, t -> BASE);

		TechniqueEvidence made = this.detector.evaluate(bentThenReleased, BASE, path);
		assertEquals(Verdict.DETECTED, made.getVerdict());
		assertTrue(Math.abs(made.getMeasurements().get("startErrorCents")) < 30.0);

		assertEquals(Verdict.NOT_DETECTED, this.detector.evaluate(notPreBent, BASE, path).getVerdict());
	}

	@Test
	public void aQuarterToneBendIsMeasuredInCents() {
		BendPath path = BendPath.of(0.0, 0.0, 0.3, 0.5, 1.0, 0.5);
		TechniqueEvidence found = this.detector.evaluate(bendTo(0.5, 1.0), BASE, path);

		assertEquals(50.0, found.getMeasurements().get("targetRiseCents"), 0.001);
		assertEquals(0, outcome(found));
	}

	@Test
	public void tooFewVoicedHopsAreUncertainAndABadPathIsRefused() {
		BendPath path = BendPath.of(0.0, 0.0, 1.0, 1.0);
		double[] midi = new double[40];
		java.util.Arrays.fill(midi, Double.NaN);
		NoteSegment mute = new NoteSegment(midi, new double[40], new boolean[40], HOP);
		assertEquals(Verdict.UNCERTAIN, this.detector.evaluate(mute, BASE, path).getVerdict());

		assertThrows(IllegalArgumentException.class, () -> BendPath.of());
		assertThrows(IllegalArgumentException.class, () -> BendPath.of(0.0, 0.0, 0.0, 1.0));
		assertThrows(IllegalArgumentException.class, () -> BendPath.of(0.5, 0.0, 1.5, 1.0));
	}
}
