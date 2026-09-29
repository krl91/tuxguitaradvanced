package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PassageStatus.Decision;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.Outcome;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.PauseReason;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.State;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.util.TGContext;

/**
 * Passage retry policy approved by the owner on 2026-09-28 (FR-088–FR-090): 2 played measures;
 * a passage is repeated when more than 50 % of its judged events had a confirmed wrong verdict or
 * were skipped (each event once, a chord once); rests, silence, noise and uncertainty never count;
 * the decision is taken at the end of the attempt; at most 2 retries; memory-only summary.
 */
public class PassagePracticeTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final long HOLD_NANOS = STABILITY_NANOS + 2 * HOP_NANOS;
	private static final double E4 = 329.63;
	private static final double F4 = 349.23;
	private static final InputAssessment SUITABLE = new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);

	private long now;

	@Test
	public void aCleanPassageMovesOnWithoutRetry() {
		PracticeSession session = running(notesPerMeasure(4, 1));

		correct(session);
		PracticeSnapshot snapshot = correct(session);

		assertEquals(2, session.getCurrentIndex());
		PassageStatus status = snapshot.getPassageStatus();
		assertEquals(Decision.PASSED, status.getDecision());
		assertEquals(2, status.getPassageNumber());
		assertEquals(2, status.getPassageCount());
		assertEquals(1, status.getAttempt());
		assertEquals(3, status.getMaxAttempts());
		assertEquals(0, status.getErrors());
	}

	@Test
	public void exactlyHalfTheEventsWrongIsNotAboveTheLimit() {
		PracticeSession session = running(notesPerMeasure(2, 1));

		wrongThenCorrect(session);
		PracticeSnapshot snapshot = correct(session);

		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Decision.PASSED, snapshot.getPassageStatus().getDecision());
		assertEquals(1, snapshot.getPassageStatus().getDecisionErrors());
		assertEquals(2, snapshot.getPassageStatus().getDecisionJudged());
	}

	@Test
	public void moreThanHalfWrongOrSkippedRepeatsThePassageFromItsFirstEvent() {
		PracticeSession session = running(notesPerMeasure(4, 1));

		wrongThenCorrect(session);
		assertEquals(1, session.getCurrentIndex());
		// errors are counted but the decision waits for the end of the attempt
		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);

		assertEquals(0, session.getCurrentIndex());
		assertEquals(State.RUNNING, session.getState());
		assertNull(session.getOutcome(0));
		assertNull(session.getOutcome(1));
		PassageStatus status = snapshot.getPassageStatus();
		assertEquals(Decision.RETRY, status.getDecision());
		assertEquals(2, status.getDecisionErrors());
		assertEquals(1, status.getDecisionAttempt());
		assertEquals(1, status.getPassageNumber());
		assertEquals(2, status.getAttempt());
		assertEquals(0, status.getErrors());
	}

	@Test
	public void theDecisionIsTakenOnlyAtTheEndOfTheAttempt() {
		PracticeSession session = running(notesPerMeasure(4, 1));

		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);

		// 1 of 2 already > 50 % of the events played so far, but the passage is not over
		assertEquals(1, session.getCurrentIndex());
		assertEquals(Decision.NONE, snapshot.getPassageStatus().getDecision());
		assertEquals(1, snapshot.getPassageStatus().getErrors());
	}

	@Test
	public void anEventCountsOnceWhateverTheNumberOfWrongAttemptsOrASkip() {
		PracticeSession session = running(notesPerMeasure(2, 1));

		wrong(session);
		wrong(session);
		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);
		assertEquals(1, snapshot.getPassageStatus().getErrors());

		snapshot = correct(session);
		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Decision.PASSED, snapshot.getPassageStatus().getDecision());
		assertEquals(1, snapshot.getPassageStatus().getDecisionErrors());
	}

	@Test
	public void silenceNoiseUncertaintyAndUnarmedWrongNotesAreNotErrors() {
		PracticeSession session = running(notesPerMeasure(2, 1));

		// a confident wrong pitch without a fresh onset (e.g. a note still ringing)
		play(session, F4, 0.95, 500_000_000L, false, AnalysisFrame.LevelState.SUITABLE);
		play(session, Double.NaN, 0.0, 1_000_000_000L, false, AnalysisFrame.LevelState.TOO_LOW);
		// low-confidence noise with onsets
		play(session, F4, 0.3, 500_000_000L, true, AnalysisFrame.LevelState.SUITABLE);
		// a confident wrong pitch at an unusable level
		pluck(session, F4, 0.95, HOLD_NANOS, AnalysisFrame.LevelState.TOO_LOW);
		correct(session);
		PracticeSnapshot snapshot = correct(session);

		assertEquals(State.COMPLETE, session.getState());
		assertEquals(0, snapshot.getPassageStatus().getDecisionErrors());
	}

	@Test
	public void afterTwoRetriesPracticeMovesOnAndTheSummaryListsThePassage() {
		PracticeSession session = running(notesPerMeasure(3, 1));

		for( int attempt = 1 ; attempt <= 3 ; attempt ++ ) {
			assertEquals(0, session.getCurrentIndex());
			session.onCommand(PracticeCommand.SKIP);
			PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);
			assertEquals(attempt < 3 ? Decision.RETRY : Decision.MOVED_ON, snapshot.getPassageStatus().getDecision());
		}
		assertEquals(2, session.getCurrentIndex());
		assertEquals(Outcome.SKIPPED, session.getOutcome(0));

		PracticeSnapshot snapshot = correct(session);

		assertEquals(State.COMPLETE, session.getState());
		PassageStatus.Summary summary = snapshot.getPassageStatus().getSummary();
		assertEquals(2, summary.getPassages());
		assertEquals(1, summary.getPassedFirstAttempt());
		assertEquals(0, summary.getPassedAfterRetry());
		assertEquals(1, summary.getNotPassed().size());
		assertEquals(1, summary.getNotPassed().get(0).getFirst());
		assertEquals(2, summary.getNotPassed().get(0).getLast());
		assertEquals(2, summary.getRetries());
	}

	@Test
	public void aPassagePassedOnARetryIsCountedAsPassedAfterRetry() {
		PracticeSession session = running(notesPerMeasure(2, 1));

		session.onCommand(PracticeCommand.SKIP);
		session.onCommand(PracticeCommand.SKIP);
		correct(session);
		PracticeSnapshot snapshot = correct(session);

		PassageStatus.Summary summary = snapshot.getPassageStatus().getSummary();
		assertEquals(0, summary.getPassedFirstAttempt());
		assertEquals(1, summary.getPassedAfterRetry());
		assertEquals(1, summary.getRetries());
	}

	@Test
	public void theLastNoteStillRingingDoesNotAcceptTheFirstNoteAfterARetry() {
		PracticeSession session = running(notesPerMeasure(2, 1));

		session.onCommand(PracticeCommand.SKIP);
		wrongThenCorrect(session);
		assertEquals(0, session.getCurrentIndex());

		play(session, E4, 0.95, 1_000_000_000L, false, AnalysisFrame.LevelState.SUITABLE);
		assertEquals(0, session.getCurrentIndex());
		correct(session);
		assertEquals(1, session.getCurrentIndex());
	}

	@Test
	public void aRepeatedSectionIsRetriedOnlyInItsOwnOccurrence() {
		SongFixture fixture = new SongFixture(2);
		fixture.song.getMeasureHeader(0).setRepeatOpen(true);
		fixture.song.getMeasureHeader(1).setRepeatClose(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 0);
		PracticeSession session = running(fixture);

		// first occurrence: measures 1-2, played cleanly
		correct(session);
		PracticeSnapshot snapshot = correct(session);
		assertEquals(2, session.getCurrentIndex());
		assertEquals(2, snapshot.getPassageStatus().getPassageNumber());
		// second occurrence failed: it is repeated, not the first one
		session.onCommand(PracticeCommand.SKIP);
		snapshot = session.onCommand(PracticeCommand.SKIP);
		assertEquals(Decision.RETRY, snapshot.getPassageStatus().getDecision());
		assertEquals(2, session.getCurrentIndex());
		assertEquals(Outcome.ACCEPTED, session.getOutcome(0));
	}

	@Test
	public void aChordCountsOnceAndUnsupportedOrAutoPassedEventsDoNotCount() {
		SongFixture fixture = new SongFixture(2);
		// an expected chord (no spectrum here, so it can only be skipped) ...
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 1);
		// ... a dead note (auto-passed) and a single note
		fixture.quarter(0, 1, 3, 0).getEffect().setDeadNote(true);
		fixture.quarter(1, 0, 1, 0);
		PracticeSession session = running(fixture);

		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);
		assertEquals(2, snapshot.getPassageStatus().getJudged());
		assertEquals(1, snapshot.getPassageStatus().getErrors());
		snapshot = correct(session);

		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Decision.PASSED, snapshot.getPassageStatus().getDecision());
	}

	@Test
	public void aPauseKeepsTheAttemptAndItsErrors() {
		PracticeSession session = running(notesPerMeasure(2, 1));

		session.onCommand(PracticeCommand.SKIP);
		session.pause(PauseReason.TUNER_OPEN);
		session.resolve(PauseReason.TUNER_OPEN);
		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.RESUME);

		assertEquals(1, snapshot.getPassageStatus().getErrors());
		assertEquals(1, snapshot.getPassageStatus().getAttempt());
	}

	@Test
	public void restartClearsTheSummary() {
		PracticeSession session = running(notesPerMeasure(2, 1));
		session.onCommand(PracticeCommand.SKIP);
		session.onCommand(PracticeCommand.SKIP);
		correct(session);
		correct(session);
		assertEquals(State.COMPLETE, session.getState());

		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.RESTART);

		assertEquals(State.RUNNING, session.getState());
		assertNull(snapshot.getPassageStatus().getSummary());
		assertEquals(Decision.NONE, snapshot.getPassageStatus().getDecision());
		assertEquals(1, snapshot.getPassageStatus().getAttempt());
	}

	@Test
	public void theThresholdAndRetryLimitAreParameters() {
		PracticeSession session = running(notesPerMeasure(2, 1), new PassagePolicy(2, 0.0, 0));

		wrongThenCorrect(session);
		PracticeSnapshot snapshot = correct(session);

		// any error fails with a 0 % limit, but no retry is allowed: moved on
		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Decision.MOVED_ON, snapshot.getPassageStatus().getDecision());
		assertTrue(new PassagePolicy(2, 0.5, 2).isFailed(2, 3));
		assertTrue(!new PassagePolicy(2, 0.5, 2).isFailed(1, 2));
	}

	@Test
	public void theThresholdChosenInThePreparationWindowDecidesTheRetry() {
		// 4 judged events in one 2-measure passage
		assertEquals(Decision.PASSED, decisionAfterSkips(25, 1));
		assertEquals(Decision.RETRY, decisionAfterSkips(25, 2));
		assertEquals(Decision.PASSED, decisionAfterSkips(50, 2));
		assertEquals(Decision.RETRY, decisionAfterSkips(50, 3));
		assertEquals(Decision.PASSED, decisionAfterSkips(75, 3));
		assertEquals(Decision.RETRY, decisionAfterSkips(75, 4));
	}

	@Test
	public void thePassageLengthChosenInThePreparationWindowCutsThePassages() {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());
		settings.setMeasures(1);
		assertEquals(4, running(notesPerMeasure(4, 1), settings.toPolicy()).onCommand(PracticeCommand.SKIP)
				.getPassageStatus().getPassageCount());
		settings.setMeasures(4);
		assertEquals(1, running(notesPerMeasure(4, 1), settings.toPolicy()).onCommand(PracticeCommand.SKIP)
				.getPassageStatus().getPassageCount());
	}

	@Test
	public void offInThePreparationWindowPractisesStraightThroughAsBefore() {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());
		settings.setMeasures(PassageSettings.OFF);
		PracticeSession session = running(notesPerMeasure(2, 2), settings.toPolicy());

		wrongThenCorrect(session);
		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);
		assertNull(snapshot.getPassageStatus());
		session.onCommand(PracticeCommand.SKIP);
		snapshot = session.onCommand(PracticeCommand.SKIP);

		// every event wrong or skipped, and no retry: complete
		assertEquals(State.COMPLETE, session.getState());
		assertNull(snapshot.getPassageStatus());
		assertEquals(Outcome.ACCEPTED, session.getOutcome(0));
		assertEquals(Outcome.SKIPPED, session.getOutcome(3));
	}

	/** Runs one 4-event passage with {@code skips} skipped events, the rest correct. */
	private Decision decisionAfterSkips(int thresholdPercent, int skips) {
		PassageSettings settings = PassageSettings.getInstance(new TGContext());
		settings.setThresholdPercent(thresholdPercent);
		PracticeSession session = running(notesPerMeasure(2, 2), settings.toPolicy());
		PracticeSnapshot snapshot = null;
		for( int i = 0 ; i < 4 ; i ++ ) {
			snapshot = (i < skips ? session.onCommand(PracticeCommand.SKIP) : correct(session));
		}
		assertEquals(thresholdPercent, snapshot.getPassageStatus().getThresholdPercent());
		return snapshot.getPassageStatus().getDecision();
	}

	@Test
	public void anEventAcceptedAgainAfterARetryIsANewAcceptance() {
		PracticeSession session = running(notesPerMeasure(2, 1), new PassagePolicy(1, 0.5, 2));

		PracticeSnapshot first = wrongThenCorrect(session);
		assertEquals(0, session.getCurrentIndex());
		assertEquals(Decision.RETRY, first.getPassageStatus().getDecision());
		PracticeSnapshot again = correct(session);

		// same event object, but a new acceptance the score must flash green again
		assertSame(first.getLastAccepted(), again.getLastAccepted());
		assertEquals(first.getAcceptedCount() + 1, again.getAcceptedCount());
		assertEquals(1, session.getCurrentIndex());
	}

	@Test
	public void withoutAPolicyThereAreNoPassages() {
		SongFixture fixture = notesPerMeasure(2, 1);
		PracticeSession session = new PracticeSession(selection(fixture), fixture.sequence(), new NoteInterpreter(0.8),
				new TolerancePolicy(5.0, 25.0), STABILITY_NANOS, LATENCY_NANOS);
		session.setAssessment(SUITABLE);
		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.START);
		assertNull(snapshot.getPassageStatus());
		session.onCommand(PracticeCommand.SKIP);
		session.onCommand(PracticeCommand.SKIP);
		assertEquals(State.COMPLETE, session.getState());
	}

	// ------------------------------------------------------------------------------------------

	/** E4 (string 1, open) {@code perMeasure} times in each of {@code measures} measures. */
	private static SongFixture notesPerMeasure(int measures, int perMeasure) {
		SongFixture fixture = new SongFixture(measures);
		for( int m = 0 ; m < measures ; m ++ ) {
			for( int q = 0 ; q < perMeasure ; q ++ ) {
				fixture.quarter(m, q, 1, 0);
			}
		}
		return fixture;
	}

	private PracticeSession running(SongFixture fixture) {
		return running(fixture, PassagePolicy.defaults());
	}

	private PracticeSession running(SongFixture fixture, PassagePolicy policy) {
		PracticeSession session = new PracticeSession(selection(fixture), fixture.sequence(), new NoteInterpreter(0.8),
				new TolerancePolicy(5.0, 25.0), STABILITY_NANOS, LATENCY_NANOS,
				new ChordMatcher(ChordMatcher.Parameters.defaults()), policy);
		session.setAssessment(SUITABLE);
		session.onCommand(PracticeCommand.START);
		assertEquals(State.RUNNING, session.getState());
		return session;
	}

	private static PracticeTrackSelection selection(SongFixture fixture) {
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		return selection;
	}

	/** A fresh, correct E4 held until accepted; asserts it advanced. */
	private PracticeSnapshot correct(PracticeSession session) {
		int accepted = session.getAcceptedCount();
		PracticeSnapshot snapshot = pluck(session, E4, 0.95, HOLD_NANOS, AnalysisFrame.LevelState.SUITABLE);
		assertEquals(accepted + 1, session.getAcceptedCount(), "not accepted exactly once");
		return snapshot;
	}

	/** A fresh, reliable wrong note (F4) held for the stability window: a confirmed wrong verdict. */
	private PracticeSnapshot wrong(PracticeSession session) {
		PracticeSnapshot snapshot = pluck(session, F4, 0.95, HOLD_NANOS, AnalysisFrame.LevelState.SUITABLE);
		assertEquals(PracticeSession.Verdict.WRONG, snapshot.getVerdict());
		// let it stop, so the next pluck is a new attempt
		play(session, Double.NaN, 0.0, 50_000_000L, false, AnalysisFrame.LevelState.TOO_LOW);
		return snapshot;
	}

	private PracticeSnapshot wrongThenCorrect(PracticeSession session) {
		wrong(session);
		return correct(session);
	}

	private PracticeSnapshot pluck(PracticeSession session, double f0Hz, double confidence, long durationNanos, AnalysisFrame.LevelState level) {
		play(session, f0Hz, confidence, LATENCY_NANOS, false, level);
		play(session, f0Hz, confidence, HOP_NANOS, true, level);
		return play(session, f0Hz, confidence, durationNanos, false, level);
	}

	private PracticeSnapshot play(PracticeSession session, double f0Hz, double confidence, long durationNanos, boolean onset,
			AnalysisFrame.LevelState level) {
		PracticeSnapshot snapshot = null;
		long end = this.now + durationNanos;
		while( this.now < end ) {
			this.now += HOP_NANOS;
			snapshot = session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, level, f0Hz, confidence, onset));
		}
		return snapshot;
	}
}
