package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.Outcome;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.PauseReason;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.State;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;

/**
 * Scripted analysis frames against the session rules (FR-007, FR-022, FR-032/033, FR-044), and
 * the owner requirement of 2026-09-28: never advance on silence or low-confidence noise; every
 * note needs a fresh onset plus a stable, confident pitch.
 */
public class PracticeSessionTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final double E4 = 329.63;
	private static final double F4 = 349.23;
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);

	private long now;

	@Test
	public void startNeedsAnAcceptedTrackAndACheckedUsableInput() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		PracticeTrackSelection selection = selection(fixture);
		PracticeSession session = session(fixture, selection);

		session.onCommand(PracticeCommand.START);
		assertEquals(State.PREPARING, session.getState());

		session.setAssessment(SUITABLE);
		session.onCommand(PracticeCommand.START);
		assertEquals(State.PREPARING, session.getState());

		selection.accept();
		session.setAssessment(InputAssessment.UNCHECKED);
		session.onCommand(PracticeCommand.START);
		assertEquals(State.PREPARING, session.getState());

		session.setAssessment(new InputAssessment(InputAssessment.Status.SUBOPTIMAL, InputAssessment.Reason.TOO_LOW));
		session.onCommand(PracticeCommand.START);
		assertEquals(State.RUNNING, session.getState());
	}

	@Test
	public void silenceUncertainWrongNoteAndWrongOctaveNeverAdvance() {
		PracticeSession session = running(new int[] { 0 });

		play(session, Double.NaN, 0.0, 1_000_000_000L, false);
		play(session, E4, 0.3, 1_000_000_000L, false);
		play(session, F4, 0.95, 1_000_000_000L, false);
		play(session, 2 * E4, 0.95, 1_000_000_000L, false);
		play(session, E4 / 2, 0.95, 1_000_000_000L, false);

		assertEquals(0, session.getCurrentIndex());
		assertEquals(State.RUNNING, session.getState());
	}

	@Test
	public void correctNoteIsAcceptedOnlyAfterAnUninterruptedStabilityWindow() {
		PracticeSession session = running(new int[] { 0, 1 });

		pluck(session, E4, 0.95, 0L);
		play(session, F4, 0.95, HOP_NANOS, false);
		play(session, E4, 0.95, 60_000_000L, false);
		assertEquals(0, session.getCurrentIndex());

		play(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
		assertEquals(1, session.getCurrentIndex());
		assertEquals(Outcome.ACCEPTED, session.getOutcome(0));
	}

	@Test
	public void snapshotsCarryFeedbackAndTheAcceptedNote() {
		PracticeSession session = running(new int[] { 0, 1 });

		pluck(session, F4, 0.95, 0L);
		this.now += HOP_NANOS;
		PracticeSnapshot early = session.onFrame(analysis(F4, 0.95, false));
		assertEquals(Feedback.Kind.WAITING, early.getFeedback().getKind(), "a wrong note is confirmed only after the stability window");
		assertEquals(PracticeSession.Verdict.NONE, early.getVerdict());
		play(session, F4, 0.95, STABILITY_NANOS, false);
		this.now += HOP_NANOS;
		PracticeSnapshot wrong = session.onFrame(analysis(F4, 0.95, false));
		assertEquals(Feedback.Kind.INCORRECT, wrong.getFeedback().getKind());
		assertEquals(65, wrong.getFeedback().getDetectedMidi());
		assertEquals(PracticeSession.Verdict.WRONG, wrong.getVerdict());

		pluck(session, E4, 0.95, 0L);
		PracticeSnapshot last = null;
		long end = this.now + STABILITY_NANOS + HOP_NANOS;
		while( session.getCurrentIndex() == 0 && this.now < end ) {
			this.now += HOP_NANOS;
			last = session.onFrame(analysis(E4, 0.95, false));
		}
		assertEquals(Feedback.Kind.CORRECT, last.getFeedback().getKind());
		assertEquals(PracticeSession.Verdict.CORRECT, last.getVerdict());
		assertEquals(0, last.getLastAccepted().getSequenceIndex());
		assertEquals(1, last.getCurrentIndex());
	}

	@Test
	public void samePitchNeedsAnArticulationAfterThePreviousAcceptance() {
		PracticeSession session = running(new int[] { 0, 0 });

		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(1, session.getCurrentIndex());

		play(session, E4, 0.95, 1_000_000_000L, false);
		assertEquals(1, session.getCurrentIndex());

		frame(session, E4, 0.95, true);
		play(session, E4, 0.95, HOP_NANOS, false);
		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Outcome.ACCEPTED, session.getOutcome(1));
	}

	@Test
	public void onsetReportedAfterAnAcceptanceButCausedBeforeItDoesNotArm() {
		PracticeSession session = running(new int[] { 0, 0 });

		pluck(session, E4, 0.95, 0L);
		while( session.getCurrentIndex() == 0 ) {
			frame(session, E4, 0.95, false);
		}
		// reported right after the acceptance, but its transient lies before it
		frame(session, E4, 0.95, true);
		play(session, E4, 0.95, 500_000_000L, false);

		assertEquals(1, session.getCurrentIndex());
	}

	@Test
	public void aDifferentPitchAfterAnAcceptedNoteIsAcceptedFromTheStablePitchAlone() {
		PracticeSession session = running(new int[] { 0, 1 });

		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(1, session.getCurrentIndex());

		// the next note: no attack reported (legato, or the attack still being confirmed)
		play(session, F4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Outcome.ACCEPTED, session.getOutcome(1));
	}

	@Test
	public void aWrongNeighbourOrTheStillRingingPreviousNoteIsNeverAcceptedWithoutAnAttack() {
		PracticeSession session = running(new int[] { 0, 2 });

		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(1, session.getCurrentIndex());

		play(session, E4, 0.95, 1_000_000_000L, false);
		play(session, F4, 0.95, 1_000_000_000L, false);
		assertEquals(1, session.getCurrentIndex());
		assertEquals(State.RUNNING, session.getState());
	}

	private static final long EARLY_NANOS = 12 * HOP_NANOS;

	/** A frame carrying evidence: the expected pitch with the given score, its neighbour with 0.1. */
	private AnalysisFrame withEvidence(double f0Hz, boolean onset, int expectedMidi, double expectedScore) {
		TargetEvidence evidence = new TargetEvidence(java.util.Arrays.asList(new TargetEvidence.Hypothesis(expectedMidi, expectedScore),
				new TargetEvidence.Hypothesis(expectedMidi + 1, 0.1)), 40.0);
		return new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, 0.95, onset, null, evidence, null);
	}

	/** The attack is reported 13 hops after its transient and the pitch is heard only from the transient: the stability run starts late. */
	private void silentThenAttack(PracticeSession session) {
		play(session, Double.NaN, 0.0, LATENCY_NANOS, false);
		frame(session, E4, 0.95, true);
	}

	private void playWithEvidence(PracticeSession session, long durationNanos, double expectedScore) {
		long end = this.now + durationNanos;
		while( this.now < end ) {
			this.now += HOP_NANOS;
			session.onFrame(withEvidence(E4, false, 64, expectedScore));
		}
	}

	@Test
	public void aLargeEvidenceMarginAcceptsAfterTheShortStabilityWindow() {
		PracticeSession session = running(new int[] { 0, 1 });
		session.setEarlyStability(0.5, EARLY_NANOS);

		silentThenAttack(session);
		playWithEvidence(session, EARLY_NANOS + 2 * HOP_NANOS, 0.9);
		assertEquals(1, session.getCurrentIndex(), "accepted well before the standard " + STABILITY_NANOS / 1_000_000 + " ms window");
	}

	@Test
	public void aSmallOrMissingEvidenceMarginKeepsTheStandardStabilityWindow() {
		PracticeSession small = running(new int[] { 0, 1 });
		small.setEarlyStability(0.5, EARLY_NANOS);
		silentThenAttack(small);
		playWithEvidence(small, EARLY_NANOS + 2 * HOP_NANOS, 0.4);
		assertEquals(0, small.getCurrentIndex());
		playWithEvidence(small, STABILITY_NANOS, 0.4);
		assertEquals(1, small.getCurrentIndex(), "the conservative window still accepts");

		PracticeSession none = running(new int[] { 0, 1 });
		none.setEarlyStability(0.5, EARLY_NANOS);
		silentThenAttack(none);
		play(none, E4, 0.95, EARLY_NANOS + 2 * HOP_NANOS, false);
		assertEquals(0, none.getCurrentIndex());
	}

	@Test
	public void evidenceNeverAcceptsAWrongPitchOrSkipsTheAttackRule() {
		PracticeSession session = running(new int[] { 0, 0 });
		session.setEarlyStability(0.5, EARLY_NANOS);
		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(1, session.getCurrentIndex());
		// identical pitch, strong evidence, but no new attack: still waits
		playWithEvidence(session, 1_000_000_000L, 0.95);
		assertEquals(1, session.getCurrentIndex());
		// strong evidence for the expected pitch while a wrong pitch is heard: nothing is accepted
		PracticeSession wrong = running(new int[] { 0 });
		wrong.setEarlyStability(0.5, EARLY_NANOS);
		pluck(wrong, F4, 0.95, 0L);
		for( int i = 0 ; i < 40 ; i ++ ) {
			this.now += HOP_NANOS;
			wrong.onFrame(withEvidence(F4, false, 64, 0.95));
		}
		assertEquals(0, wrong.getCurrentIndex());
	}

	@Test
	public void theExpectedPitchesAreHandedOverOnEveryEventChangeAndClearedAtTheEnd() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		PracticeTrackSelection selection = selection(fixture);
		selection.accept();
		PracticeSession session = session(fixture, selection);
		session.setAssessment(SUITABLE);
		java.util.List<int[]> seen = new java.util.ArrayList<int[]>();
		session.setExpectedPitchSink(seen::add);
		session.onCommand(PracticeCommand.START);
		assertEquals(64, seen.get(seen.size() - 1)[0]);
		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(65, seen.get(seen.size() - 1)[0]);
		play(session, F4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
		assertEquals(State.COMPLETE, session.getState());
		assertNull(seen.get(seen.size() - 1));
	}

	@Test
	public void aSharpFrettedNoteIsAcceptedWithTheFretAllowanceButNotOnAnOpenString() {
		for( int fret : new int[] { 0, 12 } ) {
			SongFixture fixture = new SongFixture(1);
			fixture.quarter(0, 0, 1, fret);
			PracticeTrackSelection selection = selection(fixture);
			selection.accept();
			PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8),
					new TolerancePolicy(5.0, 10.0, 0.5), STABILITY_NANOS, LATENCY_NANOS);
			session.setAssessment(SUITABLE);
			session.onCommand(PracticeCommand.START);
			double played = (fret == 0 ? E4 : 2.0 * E4) * Math.pow(2.0, 12.0 / 1200.0);
			pluck(session, played, 0.95, STABILITY_NANOS + HOP_NANOS);
			assertEquals(fret == 12 ? State.COMPLETE : State.RUNNING, session.getState(), "fret " + fret + " at +12 cents (limit 10 + 0.5 per fret)");
		}
	}

	@Test
	public void aStableCorrectPitchWithoutAFreshOnsetNeverAdvances() {
		PracticeSession session = running(new int[] { 0, 1 });

		PracticeSnapshot snapshot = null;
		for( int i = 0 ; i < 200 ; i ++ ) {
			this.now += HOP_NANOS;
			snapshot = session.onFrame(analysis(E4, 0.95, false));
		}
		assertEquals(0, session.getCurrentIndex());
		assertTrue(snapshot.getFeedback().needsArticulation());

		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(1, session.getCurrentIndex());
	}

	@Test
	public void anOnsetHeardBeforeStartOrSkipDoesNotCount() {
		PracticeSession session = session(new int[] { 0, 1, 3 });
		frame(session, E4, 0.95, true);
		session.onCommand(PracticeCommand.START);
		play(session, E4, 0.95, 500_000_000L, false);
		assertEquals(0, session.getCurrentIndex());

		session.onCommand(PracticeCommand.SKIP);
		assertEquals(1, session.getCurrentIndex());
		play(session, F4, 0.95, 500_000_000L, false);
		assertEquals(1, session.getCurrentIndex());
		pluck(session, F4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(2, session.getCurrentIndex());
	}

	@Test
	public void silenceNoiseAndGlitchesShowNeitherCheckNorCross() {
		PracticeSession session = running(new int[] { 0 });
		java.util.Random random = new java.util.Random(7L);

		for( int round = 0 ; round < 6 ; round ++ ) {
			// silence
			for( int i = 0 ; i < 100 ; i ++ ) {
				assertNoVerdict(session, Double.NaN, 0.0, false);
			}
			// low-confidence noise with occasional onsets
			for( int i = 0 ; i < 300 ; i ++ ) {
				assertNoVerdict(session, 60.0 + random.nextDouble() * 900.0, 0.3 + 0.45 * random.nextDouble(), random.nextInt(50) == 0);
			}
			// a confident wrong reading after an onset, shorter than the stability window
			assertNoVerdict(session, F4, 0.95, true);
			for( int i = 0 ; i < 10 ; i ++ ) {
				assertNoVerdict(session, F4, 0.95, false);
			}
		}
		assertEquals(0, session.getCurrentIndex());
	}

	private void assertNoVerdict(PracticeSession session, double f0Hz, double confidence, boolean onset) {
		this.now += HOP_NANOS;
		PracticeSnapshot snapshot = session.onFrame(analysis(f0Hz, confidence, onset));
		assertEquals(PracticeSession.Verdict.NONE, snapshot.getVerdict());
	}

	@Test
	public void aWrongNoteStillRingingFromBeforeTheAttemptShowsNoCross() {
		PracticeSession session = running(new int[] { 0, 1 });
		pluck(session, E4, 0.95, 0L);
		while( session.getCurrentIndex() == 0 ) {
			frame(session, E4, 0.95, false);
		}
		// E4 keeps ringing while F4 is expected: not a new wrong note
		PracticeSnapshot snapshot = null;
		for( int i = 0 ; i < 200 ; i ++ ) {
			this.now += HOP_NANOS;
			snapshot = session.onFrame(analysis(E4, 0.95, false));
			assertTrue(snapshot.getVerdict() != PracticeSession.Verdict.WRONG);
		}
		assertEquals(Feedback.Kind.WAITING, snapshot.getFeedback().getKind());
	}

	@Test
	public void silenceWithOnsetsNeverAdvances() {
		PracticeSession session = running(new int[] { 0 });

		for( int i = 0 ; i < 2000 ; i ++ ) {
			frame(session, Double.NaN, 0.0, i % 25 == 0);
		}
		assertEquals(0, session.getCurrentIndex());
		assertEquals(State.RUNNING, session.getState());
	}

	@Test
	public void ambientNoiseNeverAdvances() {
		PracticeSession session = running(new int[] { 0 });
		java.util.Random random = new java.util.Random(42L);

		// low-confidence readings wandering around the expected note, with occasional onsets
		for( int i = 0 ; i < 5000 ; i ++ ) {
			double f0 = (random.nextInt(4) == 0 ? E4 * Math.pow(2.0, (random.nextDouble() - 0.5) / 12.0) : 60.0 + random.nextDouble() * 900.0);
			frame(session, f0, 0.2 + 0.55 * random.nextDouble(), random.nextInt(40) == 0);
		}
		assertEquals(0, session.getCurrentIndex());

		// a confident expected pitch that flickers (never stable for the window) also waits
		for( int i = 0 ; i < 2000 ; i ++ ) {
			frame(session, (i % 6 < 3 ? E4 : F4), 0.95, i % 60 == 0);
		}
		assertEquals(0, session.getCurrentIndex());
	}

	@Test
	public void aTransientWithoutAStablePitchNeverAdvances() {
		PracticeSession session = running(new int[] { 0 });

		for( int knock = 0 ; knock < 20 ; knock ++ ) {
			play(session, Double.NaN, 0.0, 200_000_000L, false);
			frame(session, E4, 0.95, true);
			play(session, E4, 0.95, STABILITY_NANOS / 2, false);
		}
		assertEquals(0, session.getCurrentIndex());
	}

	@Test
	public void aConfidentMatchAtATooLowOrClippingLevelDoesNotAdvance() {
		PracticeSession session = running(new int[] { 0 });

		for( AnalysisFrame.LevelState level : new AnalysisFrame.LevelState[] { AnalysisFrame.LevelState.TOO_LOW, AnalysisFrame.LevelState.CLIPPING } ) {
			play(session, E4, 0.95, LATENCY_NANOS, false, level);
			play(session, E4, 0.95, HOP_NANOS, true, level);
			play(session, E4, 0.95, 1_000_000_000L, false, level);
		}
		assertEquals(0, session.getCurrentIndex());
	}

	@Test
	public void lowConfidenceExpectedPitchWithAnOnsetDoesNotAdvance() {
		PracticeSession session = running(new int[] { 0 });

		pluck(session, E4, 0.6, 1_000_000_000L);
		assertEquals(0, session.getCurrentIndex());
	}

	@Test
	public void completionAndRestartReturnToTheOriginalStart() {
		PracticeSession session = running(new int[] { 0, 1 });
		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		pluck(session, F4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(State.COMPLETE, session.getState());
		assertNull(session.onFrame(analysis(E4, 0.95, false)).getCurrentEvent());

		session.onCommand(PracticeCommand.RESTART);
		assertEquals(State.RUNNING, session.getState());
		assertEquals(0, session.getCurrentIndex());
		assertNull(session.getOutcome(0));
	}

	@Test
	public void pausedPracticeWaitsForAllCausesAndAnExplicitResume() {
		PracticeSession session = running(new int[] { 0 });

		session.pause(PauseReason.PLAYBACK);
		session.pause(PauseReason.TUNER_OPEN);
		session.resolve(PauseReason.PLAYBACK);
		assertEquals(State.PAUSED, session.getState());
		session.resolve(PauseReason.TUNER_OPEN);
		assertEquals(State.READY_TO_RESUME, session.getState());

		play(session, E4, 0.95, 1_000_000_000L, false);
		assertEquals(0, session.getCurrentIndex());

		session.onCommand(PracticeCommand.RESUME);
		assertEquals(State.RUNNING, session.getState());
		play(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
		assertEquals(0, session.getCurrentIndex(), "a note ringing since before Resume is not a fresh attack");
		pluck(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS);
		assertEquals(State.COMPLETE, session.getState());

		session.onCommand(PracticeCommand.STOP);
		assertEquals(State.STOPPED, session.getState());
	}

	/** A running session over notes on string 1 (E4) at the given frets. */
	private PracticeSession running(int[] frets) {
		PracticeSession session = session(frets);
		session.onCommand(PracticeCommand.START);
		assertEquals(State.RUNNING, session.getState());
		return session;
	}

	/** A session ready to start (accepted track, suitable input). */
	private PracticeSession session(int[] frets) {
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < frets.length ; i ++ ) {
			fixture.quarter(0, i, 1, frets[i]);
		}
		PracticeTrackSelection selection = selection(fixture);
		selection.accept();
		PracticeSession session = session(fixture, selection);
		session.setAssessment(SUITABLE);
		return session;
	}

	/**
	 * A new pluck: the pitch sounds, its onset is reported {@link #LATENCY_NANOS} after the
	 * transient (as the detector does), then the pitch continues for {@code durationNanos}.
	 */
	private void pluck(PracticeSession session, double f0Hz, double confidence, long durationNanos) {
		play(session, f0Hz, confidence, LATENCY_NANOS, false);
		frame(session, f0Hz, confidence, true);
		play(session, f0Hz, confidence, durationNanos, false);
	}

	private static PracticeTrackSelection selection(SongFixture fixture) {
		return new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
	}

	private static PracticeSession session(SongFixture fixture, PracticeTrackSelection selection) {
		return new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8),
				new TolerancePolicy(5.0, 25.0), STABILITY_NANOS, LATENCY_NANOS);
	}

	private void play(PracticeSession session, double f0Hz, double confidence, long durationNanos, boolean onset) {
		long end = this.now + durationNanos;
		while( this.now < end ) {
			frame(session, f0Hz, confidence, onset);
		}
	}

	private void play(PracticeSession session, double f0Hz, double confidence, long durationNanos, boolean onset, AnalysisFrame.LevelState level) {
		long end = this.now + durationNanos;
		while( this.now < end ) {
			this.now += HOP_NANOS;
			session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, level, f0Hz, confidence, onset));
		}
	}

	private void frame(PracticeSession session, double f0Hz, double confidence, boolean onset) {
		this.now += HOP_NANOS;
		session.onFrame(analysis(f0Hz, confidence, onset));
	}

	private AnalysisFrame analysis(double f0Hz, double confidence, boolean onset) {
		return new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, confidence, onset);
	}
}
