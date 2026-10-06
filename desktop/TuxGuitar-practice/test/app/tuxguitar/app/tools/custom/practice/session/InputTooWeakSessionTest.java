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

/** Spec 002 FR-015 in the session: the snapshot says the input is too weak for fast recognition, without changing what is accepted. */
public class InputTooWeakSessionTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private long now;

	private PracticeSession session() {
		// enough notes for the session to still be running after the plucks of the tests: 3 measures of 4 notes
		SongFixture fixture = new SongFixture(3);
		for( int m = 0 ; m < 3 ; m ++ ) {
			for( int q = 0 ; q < 4 ; q ++ ) {
				fixture.quarter(m, q, 1, 0);
			}
		}
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0),
				100_000_000L, 13 * HOP_NANOS, new ChordMatcher(ChordMatcher.Parameters.defaults()), null);
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.onCommand(PracticeCommand.START);
		return session;
	}

	/** A pluck of E4 at {@code rms} dBFS: an onset, then 400 ms of the note, then 300 ms of quiet. */
	private PracticeSnapshot pluck(PracticeSession session, double rms) {
		PracticeSnapshot snapshot = null;
		for( int phase = 0 ; phase < 3 ; phase ++ ) {
			long end = this.now + (phase == 0 ? HOP_NANOS * 14 : phase == 1 ? 400_000_000L : 300_000_000L);
			boolean first = (phase == 0);
			while( this.now < end ) {
				this.now += HOP_NANOS;
				boolean sounding = phase < 2;
				snapshot = session.onFrame(new AnalysisFrame(this.now, sounding ? rms : -75.0, (sounding ? rms : -75.0) + 6.0, 0.0,
						sounding ? AnalysisFrame.LevelState.SUITABLE : AnalysisFrame.LevelState.TOO_LOW, sounding ? 329.63 : Double.NaN, sounding ? 0.95 : 0.0, first));
				first = false;
			}
		}
		return snapshot;
	}

	@Test
	public void weakNotesRaiseTheFlagAndStrongNotesDoNot() {
		PracticeSession weak = session();
		PracticeSnapshot snapshot = null;
		for( int i = 0 ; i < 4 ; i ++ ) {
			snapshot = pluck(weak, -34.0);
		}
		assertTrue(snapshot.isInputTooWeak());
		assertEquals(-34.0, snapshot.getPlayedLevelDbfs(), 1.5);

		this.now = 0;
		PracticeSession strong = session();
		for( int i = 0 ; i < 4 ; i ++ ) {
			snapshot = pluck(strong, -18.0);
		}
		assertFalse(snapshot.isInputTooWeak());
	}
}
