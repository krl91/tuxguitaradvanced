package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.util.TGContext;

/**
 * Bends against the session rules with scripted analysis frames (spec 002 T081, contract of 2026-10-06): a written bend of a whole
 * tone on D4 followed by E4, the pitch the bend reaches. The base note starts the observation and the practice moves on; the pitch
 * moving along the written path is never a wrong note; the pitch the bend reaches is not the next note, which needs its own attack
 * (also after the observation window); a bend that never happens does not block; a pitch outside the path is a real error; the
 * technique is not reported before the target is reached.
 */
public class BendSessionTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final int D4 = 62;
	private static final int E4 = 64;
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
	private static final TechniqueCatalog BEND_VERIFIED = TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test");

	private long now;
	private PracticeSession session;
	private boolean wrongSeen;
	private PracticeSnapshot last;

	private static double hz(double midi) {
		return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
	}

	/** D4 bent a whole tone (string 3, fret 7), then E4 (string 3, fret 9). */
	private PracticeSession start(SongFixture fixture, boolean judge) {
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		this.session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0), STABILITY_NANOS, LATENCY_NANOS);
		if( judge ) {
			this.session.setTechniqueJudge(new TechniqueJudge(BEND_VERIFIED, true));
		}
		this.session.setAssessment(SUITABLE);
		this.session.onCommand(PracticeCommand.START);
		return this.session;
	}

	private static SongFixture wholeToneBendThenE4() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 3, 9);
		return fixture;
	}

	private void frame(double midi, boolean onset) {
		this.now += HOP_NANOS;
		this.last = this.session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, hz(midi), 0.95, onset));
		this.wrongSeen |= (this.last.getVerdict() == PracticeSession.Verdict.WRONG);
	}

	private void hold(double midi, long nanos) {
		long end = this.now + nanos;
		while( this.now < end ) {
			frame(midi, false);
		}
	}

	/** A new pluck: the pitch sounds, the onset is reported 13 hops after the transient (as the detector does), then the pitch continues. */
	private void pluck(double midi, long holdNanos) {
		hold(midi, LATENCY_NANOS);
		frame(midi, true);
		hold(midi, holdNanos);
	}

	/** The pitch glides linearly (in semitones) from one pitch to another, no onset. */
	private void glide(double from, double to, long nanos) {
		long steps = Math.max(1, nanos / HOP_NANOS);
		for( long i = 1 ; i <= steps ; i ++ ) {
			frame(from + (to - from) * i / steps, false);
		}
	}

	private static long millis(long ms) {
		return ms * 1_000_000L;
	}

	@Test
	public void theBaseNoteIsAcceptedAndTheBendAlongItsPathIsNeverAWrongNote() {
		start(wholeToneBendThenE4(), false);

		pluck(D4, millis(150));
		assertEquals(1, this.session.getCurrentIndex(), "the base note starts the bend and the practice moves on");
		glide(D4, E4, millis(250));
		hold(E4, millis(600));

		assertFalse(this.wrongSeen, "a pitch on the written path was shown as a wrong note");
		assertEquals(1, this.session.getCurrentIndex(), "the pitch the bend reaches was taken for the next note");
	}

	@Test
	public void thePitchTheBendReachesIsNotTheNextNoteEvenAfterTheObservationWindow() {
		start(wholeToneBendThenE4(), false);

		pluck(D4, millis(150));
		glide(D4, E4, millis(250));
		// longer than the longest observation window (2.5 s)
		hold(E4, millis(3200));
		assertEquals(1, this.session.getCurrentIndex());
		assertFalse(this.wrongSeen);

		// the next note needs its own attack
		pluck(E4, STABILITY_NANOS + millis(60));
		assertEquals(2, this.session.getCurrentIndex());
		assertFalse(this.wrongSeen);
	}

	@Test
	public void aBendThatNeverHappensDoesNotBlockThePractice() {
		start(wholeToneBendThenE4(), true);

		pluck(D4, millis(150));
		hold(D4, millis(1500));
		assertEquals(1, this.session.getCurrentIndex());

		pluck(E4, STABILITY_NANOS + millis(60));
		assertEquals(2, this.session.getCurrentIndex(), "the practice waited for a bend that was not played");
		assertFalse(this.wrongSeen);
		TechniqueResult bend = this.last.getTechniqueResults().isEmpty() ? null : this.last.getTechniqueResults().get(0);
		assertEquals(PlayingTechnique.BEND, bend.getTechnique());
		assertEquals(TechniqueVerdict.NOT_DETECTED, bend.getVerdict());
	}

	@Test
	public void aPitchOutsideThePathIsStillAWrongNoteLikeForAPlainNote() {
		// a semitone under the fretted pitch: C#4
		SongFixture plain = new SongFixture(1);
		plain.quarter(0, 0, 3, 7);
		start(plain, false);
		pluck(D4 - 1, STABILITY_NANOS + millis(100));
		assertTrue(this.wrongSeen, "reference: a plain D4 expected, C#4 played");

		this.wrongSeen = false;
		this.now = 0;
		start(wholeToneBendThenE4(), false);
		pluck(D4 - 1, STABILITY_NANOS + millis(100));
		assertTrue(this.wrongSeen, "a pitch outside the written path and its margin must be reported");
		assertEquals(0, this.session.getCurrentIndex());
	}

	@Test
	public void aPitchAboveThePathIsAlsoWrong() {
		start(wholeToneBendThenE4(), false);
		pluck(E4 + 2, STABILITY_NANOS + millis(100));
		assertTrue(this.wrongSeen);
		assertEquals(0, this.session.getCurrentIndex());
	}

	@Test
	public void aPreBendIsExpectedAtTheBentPitchAndThePlainFrettedPitchIsNot() {
		SongFixture fixture = new SongFixture(1);
		fixture.bendPoints(0, 0, 3, 7, 0, 4, 12, 4);
		fixture.quarter(0, 1, 3, 11);
		start(fixture, false);
		pluck(D4, STABILITY_NANOS + millis(100));
		assertTrue(this.wrongSeen, "the string was not bent before it was struck");
		assertEquals(0, this.session.getCurrentIndex());

		this.wrongSeen = false;
		this.now = 0;
		start(fixture, false);
		pluck(E4, STABILITY_NANOS + millis(100));
		assertEquals(1, this.session.getCurrentIndex());
		assertFalse(this.wrongSeen);
	}

	@Test
	public void aReleaseFallsAlongItsPathWithoutAWrongNoteOrAnEarlyNextNote() {
		SongFixture fixture = new SongFixture(1);
		// pre-bent a whole tone, held, then released to the fretted pitch; the next note is the fretted pitch D4
		fixture.bendPoints(0, 0, 3, 7, 0, 4, 6, 4, 12, 0);
		fixture.quarter(0, 1, 3, 7);
		start(fixture, false);

		pluck(E4, STABILITY_NANOS + millis(60));
		assertEquals(1, this.session.getCurrentIndex());
		glide(E4, D4, millis(250));
		hold(D4, millis(800));
		assertFalse(this.wrongSeen);
		assertEquals(1, this.session.getCurrentIndex(), "the pitch the release falls to was taken for the next note");

		pluck(D4, STABILITY_NANOS + millis(60));
		assertEquals(2, this.session.getCurrentIndex());
	}

	@Test
	public void theTechniqueIsNotReportedBeforeTheTargetIsReachedThenDetectedOnPitch() {
		start(wholeToneBendThenE4(), true);

		pluck(D4, millis(150));
		assertEquals(1, this.session.getCurrentIndex());
		// the bend is under way, halfway to its target: nothing is said yet
		glide(D4, D4 + 1, millis(120));
		assertTrue(this.last.getTechniqueResults().isEmpty(), "reported while the pitch was still moving");
		glide(D4 + 1, E4, millis(120));
		hold(E4, millis(300));
		assertTrue(this.last.getTechniqueResults().isEmpty(), "reported before the end of the observation window");

		// the observation window of a note written at 0.5 s lasts 1.0 s; the next note ends it earlier
		pluck(E4, STABILITY_NANOS + millis(60));
		assertEquals(2, this.session.getCurrentIndex());
		List<TechniqueResult> results = this.last.getTechniqueResults();
		assertEquals(1, results.size());
		assertEquals(TechniqueVerdict.DETECTED, results.get(0).getVerdict());
		assertEquals(0.0, results.get(0).getMeasurements().get("outcome"), 0.001);
	}

	@Test
	public void aBendThatStopsHalfWayIsUnderAndNotDetected() {
		start(wholeToneBendThenE4(), true);

		pluck(D4, millis(150));
		glide(D4, D4 + 1, millis(200));
		hold(D4 + 1, millis(800));
		pluck(E4, STABILITY_NANOS + millis(60));

		assertEquals(2, this.session.getCurrentIndex());
		TechniqueResult bend = this.last.getTechniqueResults().get(0);
		assertNotEquals(TechniqueVerdict.DETECTED, bend.getVerdict());
		assertEquals(-1.0, bend.getMeasurements().get("outcome"), 0.001);
	}

	@Test
	public void theResultOfAnUnfinishedBendComesWhenTheObservationWindowEnds() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		start(fixture, true);

		pluck(D4, millis(150));
		glide(D4, E4, millis(250));
		hold(E4, millis(500));
		assertTrue(this.last.getTechniqueResults().isEmpty());
		// the note written at 0.5 s is observed for 1.0 s after its acceptance, whatever comes next
		hold(E4, millis(900));
		assertEquals(1, this.last.getTechniqueResults().size());
		assertEquals(TechniqueVerdict.DETECTED, this.last.getTechniqueResults().get(0).getVerdict());
	}

	@Test
	public void anOnsetHeardWhileTheBendIsMovingDoesNotMakeThePathAWrongNote() {
		start(wholeToneBendThenE4(), false);

		pluck(D4, millis(150));
		assertEquals(1, this.session.getCurrentIndex());
		// the level change of the bend is taken for an attack in the middle of the glide, and the pitch stays there for a while
		glide(D4, D4 + 1, millis(120));
		frame(D4 + 1, true);
		hold(D4 + 1, STABILITY_NANOS + millis(200));
		glide(D4 + 1, E4, millis(120));

		assertFalse(this.wrongSeen, "a pitch on the written path was shown as a wrong note");
		assertEquals(1, this.session.getCurrentIndex());
	}

	@Test
	public void afterTheWindowAWrongNoteIsReportedAgain() {
		start(wholeToneBendThenE4(), false);

		pluck(D4, millis(150));
		glide(D4, E4, millis(250));
		hold(E4, millis(2800));
		// well after the observation window of the bend: a struck note far from E4 and from the path is a real error
		pluck(E4 + 3, STABILITY_NANOS + millis(100));
		assertTrue(this.wrongSeen);
		assertEquals(1, this.session.getCurrentIndex());
	}
}
