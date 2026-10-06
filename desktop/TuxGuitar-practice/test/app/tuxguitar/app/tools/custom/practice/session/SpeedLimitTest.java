package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.util.TGContext;

/** Spec 004 T008 / FR-008: a note struck faster than recognition follows is shown as a limit, after a while, and never as an error. */
public class SpeedLimitTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final double E4 = 329.63;
	private static final double F4 = 349.23;

	private long now;

	private PracticeSession session() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 1, 1, 1);
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0),
				STABILITY_NANOS, LATENCY_NANOS, new ChordMatcher(ChordMatcher.Parameters.defaults()), null);
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		return session;
	}

	private PracticeSnapshot frames(PracticeSession session, double f0Hz, long nanos, boolean onset) {
		PracticeSnapshot snapshot = null;
		long end = this.now + nanos;
		while( this.now < end ) {
			this.now += HOP_NANOS;
			snapshot = session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, f0Hz > 0 ? 0.95 : 0.0, onset));
		}
		return snapshot;
	}

	private PracticeSnapshot pluck(PracticeSession session, double f0Hz) {
		frames(session, f0Hz, LATENCY_NANOS, false);
		frames(session, f0Hz, HOP_NANOS, true);
		return frames(session, f0Hz, STABILITY_NANOS + 2 * HOP_NANOS, false);
	}

	@Test
	public void aNewAttackRightAfterAnAcceptanceIsShownAsALimitOnceItStaysUnrecognised() {
		PracticeSession session = session();
		assertEquals(1, pluck(session, E4).getAcceptedCount());
		// the next note arrives 0.1 s after the acceptance, and the audio gives no usable pitch
		frames(session, Double.NaN, 100_000_000L, false);
		PracticeSnapshot soon = frames(session, Double.NaN, HOP_NANOS, true);
		assertFalse(soon.isSpeedLimit(), "not shown at once: the note may still be recognised");
		PracticeSnapshot later = frames(session, Double.NaN, 1_000_000_000L, false);
		assertTrue(later.isSpeedLimit());
		assertEquals(1, later.getCurrentIndex(), "nothing advances and nothing is marked wrong");
		assertEquals(PracticeSession.Verdict.NONE, later.getVerdict());
	}

	@Test
	public void theLimitDisappearsWhenTheNoteIsRecognised() {
		PracticeSession session = session();
		pluck(session, E4);
		frames(session, Double.NaN, 100_000_000L, false);
		frames(session, Double.NaN, HOP_NANOS, true);
		assertTrue(frames(session, Double.NaN, 1_000_000_000L, false).isSpeedLimit());
		PracticeSnapshot after = pluck(session, F4);
		assertEquals(2, after.getAcceptedCount());
		assertFalse(after.isSpeedLimit());
	}

	@Test
	public void aNoteStruckAfterAPauseIsNotALimit() {
		PracticeSession session = session();
		pluck(session, E4);
		frames(session, Double.NaN, 500_000_000L, false);
		frames(session, Double.NaN, HOP_NANOS, true);
		assertFalse(frames(session, Double.NaN, 1_500_000_000L, false).isSpeedLimit());
	}

	@Test
	public void silenceAloneIsNotALimit() {
		PracticeSession session = session();
		pluck(session, E4);
		assertFalse(frames(session, Double.NaN, 2_000_000_000L, false).isSpeedLimit());
	}
}
