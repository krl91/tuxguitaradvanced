package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueVerdict;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.technique.DetectableTechnique;
import app.tuxguitar.audio.engine.technique.NoteSegment;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.technique.TechniqueDetector;
import app.tuxguitar.audio.engine.technique.TechniqueEvidence;
import app.tuxguitar.util.TGContext;

/** Spec 002 FR-030: technique results sit beside the pitch result and never change progression. */
public class TechniqueSessionTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long STABILITY_NANOS = 100_000_000L;
	private static final long LATENCY_NANOS = 13 * HOP_NANOS;
	private static final double E4 = 329.63;
	private static final double F4 = 349.23;

	private long now;

	/** E4 with a hammer-on mark, then F4: the destination note is a hammer-on. */
	private static SongFixture hammerScore() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 1, 0).getEffect().setHammer(true);
		fixture.quarter(0, 1, 1, 1);
		fixture.quarter(0, 2, 1, 1);
		return fixture;
	}

	private PracticeSession running(SongFixture fixture, TechniqueJudge judge) {
		PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
				PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
		selection.accept();
		PracticeSession session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0),
				STABILITY_NANOS, LATENCY_NANOS, new ChordMatcher(ChordMatcher.Parameters.defaults()), null);
		session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		session.setTechniqueJudge(judge);
		session.onCommand(PracticeCommand.START);
		return session;
	}

	private PracticeSnapshot frames(PracticeSession session, double f0Hz, long nanos, boolean onset) {
		PracticeSnapshot snapshot = null;
		long end = this.now + nanos;
		while( this.now < end ) {
			this.now += HOP_NANOS;
			snapshot = session.onFrame(new AnalysisFrame(this.now, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0Hz, 0.95, onset));
		}
		return snapshot;
	}

	private PracticeSnapshot pluck(PracticeSession session, double f0Hz) {
		frames(session, f0Hz, LATENCY_NANOS, false);
		frames(session, f0Hz, HOP_NANOS, true);
		return frames(session, f0Hz, STABILITY_NANOS + 2 * HOP_NANOS, false);
	}

	@Test
	public void theDestinationOfAHammerOnIsReportedNotVerifiableAndProgressionIsUnchanged() {
		PracticeSession session = running(hammerScore(), new TechniqueJudge(TechniqueCatalog.defaults(), true));

		PracticeSnapshot first = pluck(session, E4);
		assertEquals(1, session.getCurrentIndex());
		assertTrue(first.getTechniqueResults().isEmpty());

		PracticeSnapshot second = pluck(session, F4);
		assertEquals(2, session.getCurrentIndex());
		assertEquals(1, second.getTechniqueResults().size());
		assertEquals(PlayingTechnique.HAMMER_ON, second.getTechniqueResults().get(0).getTechnique());
		assertEquals(TechniqueVerdict.NOT_VERIFIABLE, second.getTechniqueResults().get(0).getVerdict());
		assertEquals(1, second.getTechniqueEvent().getSequenceIndex());
	}

	@Test
	public void withoutAJudgeThereAreNoResultsAndNothingElseChanges() {
		PracticeSession session = running(hammerScore(), null);

		pluck(session, E4);
		PracticeSnapshot second = pluck(session, F4);

		assertEquals(2, session.getCurrentIndex());
		assertTrue(second.getTechniqueResults().isEmpty());
	}

	@Test
	public void aVerifiedDetectorJudgesTheNoteOnceItIsOver() {
		TechniqueDetector detector = new TechniqueDetector() {
			@Override
			public DetectableTechnique technique() {
				return DetectableTechnique.LEGATO;
			}

			@Override
			public TechniqueEvidence evaluate(NoteSegment segment) {
				return new TechniqueEvidence(DetectableTechnique.LEGATO, TechniqueEvidence.Verdict.NOT_DETECTED, 0.8,
						Collections.singletonMap("frames", (double) segment.size()));
			}
		};
		PracticeSession session = running(hammerScore(), new TechniqueJudge(TechniqueCatalog.of(detector, true, "test"), true));

		pluck(session, E4);
		PracticeSnapshot afterDestination = pluck(session, F4);
		// the note is still sounding: nothing is said about it yet, and it was accepted as usual
		assertEquals(2, session.getCurrentIndex());
		assertTrue(afterDestination.getTechniqueResults().isEmpty());

		// the next note ends it: the destination is judged (the stub says "not detected") and the learner sees it
		PracticeSnapshot afterNext = pluck(session, F4);
		assertEquals(PracticeSession.State.COMPLETE, session.getState());
		assertEquals(1, afterNext.getTechniqueEvent().getSequenceIndex());
		assertEquals(TechniqueVerdict.NOT_DETECTED, afterNext.getTechniqueResults().get(0).getVerdict());
		assertTrue(afterNext.getTechniqueResults().get(0).getMeasurements().get("frames") > 2.0);
	}
}
