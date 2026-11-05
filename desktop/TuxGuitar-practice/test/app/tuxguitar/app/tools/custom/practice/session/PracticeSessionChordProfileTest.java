package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.Verdict;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.AttackEvidence;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.util.TGContext;

/**
 * Wait (PracticeSession) with the chord rule by profile (spec 024, contracts/chord-profile-rule.md, task T011), on synthetic frames: the profile is
 * reset at each arming and summed on each usable frame; the decision, the feedback, the tolerated pitches and the guarded attack path.
 */
public class PracticeSessionChordProfileTest {

	private static final long HOP_NANOS = 256L * 1_000_000_000L / 48000L;
	private static final long LATENCY = ArticulationDetector.DECISION_DELAY_HOPS * HOP_NANOS;
	/** A minor, one pitch per name: A2 (string 5 open), E3 (string 4 fret 2), C4 (string 2 fret 1). */
	private static final int[] STRINGS = { 5, 4, 2 };
	private static final int[] FRETS = { 0, 2, 1 };
	private static final int[] A_MINOR = { 45, 52, 60 };

	private static ChordMatcher.Parameters rule(boolean attack) {
		return new ChordMatcher.Parameters(2, 1.0 / 3.0, 8, 600_000_000L, RecognitionParameters.COMPLETENESS_PROFILE, 0.79, 0.40, 84, 0L, attack);
	}

	private static final class Run {
		final PracticeSession session;
		long now;
		PracticeSnapshot last;

		Run(SongFixture fixture, ChordMatcher.Parameters chord, boolean withAttack) {
			PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
					PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(), PracticeTunerContext.getInstance(new TGContext()));
			selection.accept();
			this.session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8), new TolerancePolicy(5.0, 35.0), 20 * HOP_NANOS, LATENCY,
					new ChordMatcher(chord), null);
			if( withAttack ) {
				this.session.setPolyphonicAcceptance(new PolyphonicAcceptance(new PolyphonicAcceptance.Parameters(0.0, 0.0, 0.0, 1, 0.0, 0L)));
			}
			this.session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
			this.session.onCommand(PracticeCommand.START);
		}

		PracticeSnapshot frame(boolean onset, SpectrumFrame spectrum) {
			return frame(onset, spectrum, AnalysisFrame.LevelState.SUITABLE, null, Double.NaN);
		}

		PracticeSnapshot frame(boolean onset, SpectrumFrame spectrum, AnalysisFrame.LevelState level, AttackEvidence evidence, double f0) {
			this.now += HOP_NANOS;
			AnalysisFrame frame = new AnalysisFrame(this.now, -20.0, -10.0, level == AnalysisFrame.LevelState.CLIPPING ? 0.5 : 0.0, level, f0,
					Double.isNaN(f0) ? 0.0 : 0.95, onset, spectrum, null, null, evidence);
			this.last = this.session.onFrame(frame);
			return this.last;
		}
	}

	private static SongFixture aMinor() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, STRINGS, FRETS);
		return fixture;
	}

	/** Attack evidence where the given pitches grew, their partials exactly in tune. */
	static AttackEvidence evidence(int... pitches) {
		double binHz = 0.5;
		double[] growth = new double[24000];
		for( int pitch : pitches ) {
			double f0 = 440.0 * Math.pow(2.0, (pitch - 69) / 12.0);
			for( int h = 1 ; h <= 8 ; h ++ ) {
				int bin = (int) Math.round(f0 * h / binHz);
				if( bin + 1 < growth.length ) {
					growth[bin] += 1.0;
					growth[bin - 1] += 0.5;
					growth[bin + 1] += 0.5;
				}
			}
		}
		int[] sorted = pitches.clone();
		java.util.Arrays.sort(sorted);
		double[] amplitudes = new double[sorted.length];
		java.util.Arrays.fill(amplitudes, 1.0);
		return new AttackEvidence(sorted, amplitudes, 0.1, growth, binHz);
	}

	@Test
	public void aChordIsAcceptedAfterStableHopsCompleteFramesInsideTheWindow() {
		Run run = new Run(aMinor(), rule(true), false);
		for( int i = 0 ; i < 7 ; i ++ ) {
			assertEquals(0, run.frame(i == 0, ChordProfileTest.frame(-20, A_MINOR)).getAcceptedCount(), "frame " + i);
		}
		assertEquals(1, run.frame(false, ChordProfileTest.frame(-20, A_MINOR)).getAcceptedCount());
		assertEquals(PracticeSession.Outcome.ACCEPTED, run.session.getOutcome(0));
	}

	@Test
	public void theProfileIsResetAtEachArming() {
		Run run = new Run(aMinor(), rule(true), false);
		// a loud G major for the whole window: a wrong chord
		boolean wrong = false;
		for( int i = 0 ; i < 130 ; i ++ ) {
			wrong |= run.frame(i == 0, ChordProfileTest.frame(-5, 43, 47, 50, 55, 59, 67)).getVerdict() == Verdict.WRONG;
		}
		assertTrue(wrong);
		// a new, quiet strum of the right chord: the loud G major summed before must not count
		for( int i = 0 ; i < 20 ; i ++ ) {
			run.frame(i == 0, ChordProfileTest.frame(-40, A_MINOR));
		}
		assertEquals(PracticeSession.Outcome.ACCEPTED, run.session.getOutcome(0));
	}

	@Test
	public void aChordWithAToleratedPitchIsAcceptedWhetherItRingsOrHasFaded() {
		for( boolean rings : new boolean[] { true, false } ) {
			SongFixture fixture = new SongFixture(1);
			// F2 held for a whole note from beat 1, then A minor on beat 2: F2 is still sounding, tolerated
			fixture.note(fixture.track(), 0, 0, 1, 6, 1, TGDuration.WHOLE);
			fixture.chord(0, 1, STRINGS, FRETS);
			Run run = new Run(fixture, rule(true), false);
			run.session.onCommand(PracticeCommand.SKIP);
			PracticeEvent chord = fixture.sequence().getEvents().get(1);
			assertEquals(PracticeEvent.Kind.EXPECTED_CHORD, chord.getKind());
			assertArrayEquals(new int[] { 41 }, chord.getToleratedPitches());
			for( int i = 0 ; i < 20 ; i ++ ) {
				SpectrumFrame spectrum = (rings ? ChordProfileTest.frame(new double[] { -20, 45, 52, 60 }, new double[] { -8, 41 })
						: ChordProfileTest.frame(-20, A_MINOR));
				run.frame(i == 0, spectrum);
			}
			assertEquals(PracticeSession.Outcome.ACCEPTED, run.session.getOutcome(1), rings ? "ringing" : "faded");
		}
	}

	@Test
	public void whenTheWindowEndsFirstTheFeedbackNamesTheWeakestNames() {
		Run run = new Run(aMinor(), rule(true), false);
		boolean wrong = false;
		for( int i = 0 ; i < 130 && !wrong ; i ++ ) {
			// A and B only: E and C are missing
			wrong = run.frame(i == 0, ChordProfileTest.frame(-20, 45, 47)).getVerdict() == Verdict.WRONG;
		}
		assertTrue(wrong);
		ChordFeedback feedback = run.last.getChordFeedback();
		assertEquals(ChordFeedback.Kind.INCOMPLETE, feedback.getKind());
		assertArrayEquals(new int[] { 52, 60 }, feedback.getMissing());
		assertArrayEquals(new int[0], feedback.getUnverifiable());
	}

	@Test
	public void anUnusableFrameResetsTheRunButNotTheSums() {
		Run run = new Run(aMinor(), rule(true), false);
		for( int i = 0 ; i < 5 ; i ++ ) {
			run.frame(i == 0, ChordProfileTest.frame(-10, A_MINOR));
		}
		assertEquals(0, run.frame(false, null, AnalysisFrame.LevelState.TOO_LOW, null, Double.NaN).getAcceptedCount());
		// only A from now on, quiet: alone it would match 0.58; with the sums kept the chord stays complete
		for( int i = 0 ; i < 7 ; i ++ ) {
			assertEquals(0, run.frame(false, ChordProfileTest.frame(-60, 45)).getAcceptedCount(), "the run started again: frame " + i);
		}
		assertEquals(1, run.frame(false, ChordProfileTest.frame(-60, 45)).getAcceptedCount());
	}

	@Test
	public void singleNotesAreUnchanged() {
		SongFixture fixture = new SongFixture(1);
		fixture.quarter(0, 0, 5, 0);
		Run run = new Run(fixture, rule(false), false);
		for( int i = 0 ; i < 40 ; i ++ ) {
			run.frame(i == 0, null, AnalysisFrame.LevelState.SUITABLE, null, 110.0);
		}
		assertEquals(PracticeSession.Outcome.ACCEPTED, run.session.getOutcome(0));
	}

	@Test
	public void theAttackPathFollowsItsFlagAndItsGuard() {
		// on, the right spectrum: accepted on the attack itself, before the profile's eight frames
		Run on = new Run(aMinor(), rule(true), true);
		assertEquals(1, on.frame(true, ChordProfileTest.frame(-20, A_MINOR), AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
		// on, a profile with two weak names (A only): the attack path refuses it
		Run guarded = new Run(aMinor(), rule(true), true);
		for( int i = 0 ; i < 30 ; i ++ ) {
			assertEquals(0, guarded.frame(i == 0, ChordProfileTest.frame(-20, 45), AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
		}
		// no onset at all: the guard reads the attack's own frame
		Run unarmed = new Run(aMinor(), rule(true), true);
		assertEquals(1, unarmed.frame(false, ChordProfileTest.frame(-20, A_MINOR), AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
		Run unarmedWrong = new Run(aMinor(), rule(true), true);
		for( int i = 0 ; i < 30 ; i ++ ) {
			assertEquals(0, unarmedWrong.frame(false, ChordProfileTest.frame(-20, 45), AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
		}
		// off: the chord waits for the profile's eight frames
		Run off = new Run(aMinor(), rule(false), true);
		for( int i = 0 ; i < 7 ; i ++ ) {
			assertEquals(0, off.frame(i == 0, ChordProfileTest.frame(-20, A_MINOR), AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
		}
		assertEquals(1, off.frame(false, ChordProfileTest.frame(-20, A_MINOR), AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
		// off for chords only: a single note is still accepted on its attack
		SongFixture note = new SongFixture(1);
		note.quarter(0, 0, 5, 0);
		Run single = new Run(note, rule(false), true);
		assertEquals(1, single.frame(true, null, AnalysisFrame.LevelState.SUITABLE, evidence(45), Double.NaN).getAcceptedCount());
	}

	@Test
	public void anArmedStrumWithNoSpectrumAtAllIsJudgedAsANullSpectrum() {
		// the gate of phase 4 (RecordedPracticeReplayTest analyses without a spectrum): a strum armed whose profile summed nothing gives the attack
		// path no evidence of a missing name, as a frame with no spectrum does: the attack is allowed
		Run run = new Run(aMinor(), rule(true), true);
		assertEquals(1, run.frame(true, null, AnalysisFrame.LevelState.SUITABLE, evidence(A_MINOR), Double.NaN).getAcceptedCount());
	}

	@Test
	public void anInputProblemStillShowsAsToday() {
		Run run = new Run(aMinor(), rule(true), false);
		run.frame(true, ChordProfileTest.frame(-20, A_MINOR));
		assertEquals(ChordFeedback.Kind.UNCERTAIN, run.frame(false, null, AnalysisFrame.LevelState.CLIPPING, null, Double.NaN).getChordFeedback().getKind());
		Run quiet = new Run(aMinor(), rule(true), false);
		assertEquals(ChordFeedback.Kind.WAITING, quiet.frame(false, null, AnalysisFrame.LevelState.TOO_LOW, null, Double.NaN).getChordFeedback().getKind());
	}
}
