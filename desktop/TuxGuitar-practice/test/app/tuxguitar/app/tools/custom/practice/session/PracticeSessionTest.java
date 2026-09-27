package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import app.tuxguitar.util.TGContext;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;

/** Scripted analysis frames against the session rules (FR-007, FR-022, FR-032/033, FR-044). */
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

		play(session, E4, 0.95, 60_000_000L, false);
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

		this.now += HOP_NANOS;
		PracticeSnapshot wrong = session.onFrame(analysis(F4, 0.95, false));
		assertEquals(Feedback.Kind.INCORRECT, wrong.getFeedback().getKind());
		assertEquals(65, wrong.getFeedback().getDetectedMidi());

		PracticeSnapshot last = null;
		long end = this.now + STABILITY_NANOS + HOP_NANOS;
		while( session.getCurrentIndex() == 0 && this.now < end ) {
			this.now += HOP_NANOS;
			last = session.onFrame(analysis(E4, 0.95, false));
		}
		assertEquals(Feedback.Kind.CORRECT, last.getFeedback().getKind());
		assertEquals(0, last.getLastAccepted().getSequenceIndex());
		assertEquals(1, last.getCurrentIndex());
	}

	@Test
	public void samePitchNeedsAnArticulationAfterThePreviousAcceptance() {
		PracticeSession session = running(new int[] { 0, 0 });

		play(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
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

		play(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
		assertEquals(1, session.getCurrentIndex());
		frame(session, E4, 0.95, true);
		play(session, E4, 0.95, 500_000_000L, false);

		assertEquals(1, session.getCurrentIndex());
	}

	@Test
	public void completionAndRestartReturnToTheOriginalStart() {
		PracticeSession session = running(new int[] { 0, 1 });
		play(session, E4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
		play(session, F4, 0.95, STABILITY_NANOS + HOP_NANOS, false);
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
		assertEquals(State.COMPLETE, session.getState());

		session.onCommand(PracticeCommand.STOP);
		assertEquals(State.STOPPED, session.getState());
	}

	/** A running session over notes on string 1 (E4) at the given frets. */
	private PracticeSession running(int[] frets) {
		SongFixture fixture = new SongFixture(1);
		for( int i = 0 ; i < frets.length ; i ++ ) {
			fixture.quarter(0, i, 1, frets[i]);
		}
		PracticeTrackSelection selection = selection(fixture);
		selection.accept();
		PracticeSession session = session(fixture, selection);
		session.setAssessment(SUITABLE);
		session.onCommand(PracticeCommand.START);
		assertEquals(State.RUNNING, session.getState());
		return session;
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

	private void frame(PracticeSession session, double f0Hz, double confidence, boolean onset) {
		this.now += HOP_NANOS;
		session.onFrame(analysis(f0Hz, confidence, onset));
	}

	private AnalysisFrame analysis(double f0Hz, double confidence, boolean onset) {
		return new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, confidence, onset);
	}
}
