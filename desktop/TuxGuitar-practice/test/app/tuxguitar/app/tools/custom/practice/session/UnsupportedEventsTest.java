package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.Outcome;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.State;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;

/** FR-011–FR-013, FR-016, FR-025, FR-030, FR-031: unsupported events and Skip. */
public class UnsupportedEventsTest {

	private static final long HOP_NANOS = 5_333_333L;

	@Test
	public void unsupportedEventsPassAutomaticallyAndAreNeverCorrect() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 2, 0).getEffect().setDeadNote(true);
		fixture.quarter(0, 2, 2, 12).getEffect().setHarmonic(fixture.factory().newEffectHarmonic());
		fixture.quarter(0, 3, 1, 3);
		PracticeSession session = running(fixture);

		PracticeSnapshot snapshot = accept(session, 329.63);

		assertEquals(3, session.getCurrentIndex());
		assertEquals(Outcome.AUTO_PASSED, session.getOutcome(1));
		assertEquals(Outcome.AUTO_PASSED, session.getOutcome(2));
		assertEquals(2, snapshot.getLastPassed().getSequenceIndex());
		assertEquals(Outcome.AUTO_PASSED, snapshot.getLastPassedOutcome());
		assertEquals(PracticeEvent.AutoPassReason.HARMONIC, snapshot.getLastPassed().getAutoPassReason());
	}

	@Test
	public void aMultiNoteEventGivesNoJudgementAndAdvancesOnlyOnSkip() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 0, 2, 1);
		fixture.quarter(0, 1, 1, 3);
		PracticeSession session = running(fixture);

		PracticeSnapshot snapshot = null;
		for( int i = 0 ; i < 200 ; i ++ ) {
			snapshot = session.onFrame(frame(i, 329.63));
		}
		assertEquals(0, session.getCurrentIndex());
		assertEquals(PracticeEvent.Kind.MULTI_NOTE, snapshot.getCurrentEvent().getKind());
		assertNull(snapshot.getFeedback());

		snapshot = session.onCommand(PracticeCommand.SKIP);
		assertEquals(1, session.getCurrentIndex());
		assertEquals(Outcome.SKIPPED, session.getOutcome(0));
		assertEquals(Outcome.SKIPPED, snapshot.getLastPassedOutcome());
	}

	@Test
	public void skipMarksAnyEventSkippedNeverCorrect() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		PracticeSession session = running(fixture);

		PracticeSnapshot snapshot = session.onCommand(PracticeCommand.SKIP);

		assertEquals(Outcome.SKIPPED, session.getOutcome(0));
		assertNotEquals(Outcome.ACCEPTED, session.getOutcome(0));
		assertNull(snapshot.getLastAccepted());
		assertEquals(State.COMPLETE, session.getState());
	}

	@Test
	public void trailingUnsupportedEventsCompleteThePractice() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 2, 0).getEffect().setDeadNote(true);
		PracticeSession session = running(fixture);

		accept(session, 329.63);

		assertEquals(State.COMPLETE, session.getState());
		assertEquals(Outcome.AUTO_PASSED, session.getOutcome(1));
	}

	private static PracticeSession running(SongFixture fixture) {
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
				PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8),
				new TolerancePolicy(5.0, 30.0), 100_000_000L, 0L);
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		return session;
	}

	private static PracticeSnapshot accept(PracticeSession session, double f0Hz) {
		PracticeSnapshot snapshot = null;
		int start = session.getCurrentIndex();
		for( int i = 0 ; i < 100 && session.getCurrentIndex() == start ; i ++ ) {
			snapshot = session.onFrame(frame(i, f0Hz));
		}
		return snapshot;
	}

	private static AnalysisFrame frame(int hop, double f0Hz) {
		return new AnalysisFrame(hop * HOP_NANOS, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, 0.95, false);
	}
}
