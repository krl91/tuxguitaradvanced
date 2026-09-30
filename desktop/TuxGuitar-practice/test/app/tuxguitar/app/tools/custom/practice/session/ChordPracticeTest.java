package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackCandidates;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.State;
import app.tuxguitar.app.tools.custom.practice.session.PracticeSession.Verdict;
import app.tuxguitar.audio.engine.AudioEngine;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.LevelAnalyzer;
import app.tuxguitar.audio.engine.analysis.LevelMeasurement;
import app.tuxguitar.audio.engine.analysis.PolyphonicAnalyzer;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.util.TGContext;

/**
 * Blocking defect of 2026-09-28 (chords never recognised in the real build): the whole chord path
 * — synthetic strum → level, articulation and polyphonic analysis as in the engine → chord
 * evidence → matcher → session — on deterministic Karplus–Strong strums. Pins what a correct,
 * incomplete, wrong, stale or silent chord does; thresholds stay the provisional defaults.
 */
public class ChordPracticeTest {

	private static final int RATE = 48000;
	private static final int WINDOW = 2048;
	private static final int HOP = 256;
	private static final long HOP_NANOS = HOP * 1_000_000_000L / RATE;
	/** Open E major, string 6 to 1: E2 B2 E3 G#3 B3 E4. */
	private static final int[][] E_MAJOR = { { 6, 0 }, { 5, 2 }, { 4, 2 }, { 3, 1 }, { 2, 0 }, { 1, 0 } };
	private static final int[] E_MAJOR_PITCHES = { 40, 47, 52, 56, 59, 64 };

	@Test
	public void aCompleteStrumIsAcceptedWithACheck() {
		Run run = run(E_MAJOR, audio(0.3, E_MAJOR_PITCHES, 1.2));

		assertEquals(State.COMPLETE, run.session.getState());
		assertEquals(PracticeSession.Outcome.ACCEPTED, run.session.getOutcome(0));
		assertTrue(run.sawVerdict(Verdict.CORRECT));
		assertTrue(!run.sawVerdict(Verdict.WRONG));
	}

	@Test
	public void harmonicCoincidentPitchesAreReportedAsNotSeparatelyVerifiable() {
		Run run = run(E_MAJOR, audio(0.3, E_MAJOR_PITCHES, 0.45));

		// E3 = 2×E2, B3 = 3×E2 = 2×B2, E4 = 4×E2: no partial of their own (spec Q1, answer A)
		assertArrayEquals(new int[] { 52, 59, 64 }, run.lastChordFeedback.getUnverifiable());
	}

	@Test
	public void aStrumMissingAVerifiableStringIsAConfirmedWrongChord() {
		Run run = run(E_MAJOR, audio(0.3, new int[] { 40, 47, 52, 59, 64 }, 1.5));

		assertEquals(0, run.session.getCurrentIndex());
		assertEquals(ChordFeedback.Kind.INCOMPLETE, run.lastChordFeedback.getKind());
		assertArrayEquals(new int[] { 56 }, run.lastChordFeedback.getMissing());
		assertTrue(run.sawVerdict(Verdict.WRONG));
		assertTrue(!run.sawVerdict(Verdict.CORRECT));
	}

	@Test
	public void aDifferentChordIsNeverAccepted() {
		// A minor (A2 E3 A3 C4 E4) strummed for E major
		Run run = run(E_MAJOR, audio(0.3, new int[] { 45, 52, 57, 60, 64 }, 1.5));

		assertEquals(0, run.session.getCurrentIndex());
		assertEquals(ChordFeedback.Kind.INCOMPLETE, run.lastChordFeedback.getKind());
		assertTrue(run.sawVerdict(Verdict.WRONG));
	}

	@Test
	public void silenceAndRoomNoiseShowNeitherCheckNorCross() {
		float[] quiet = new float[2 * RATE];
		Random random = new Random(5L);
		for( int i = RATE; i < quiet.length; i++ ) {
			quiet[i] = (float) ((random.nextDouble() * 2.0 - 1.0) * 0.004);
		}
		Run run = run(E_MAJOR, quiet);

		assertEquals(0, run.session.getCurrentIndex());
		assertTrue(!run.sawVerdict(Verdict.CORRECT) && !run.sawVerdict(Verdict.WRONG));
		assertEquals(ChordFeedback.Kind.WAITING, run.lastChordFeedback.getKind());
	}

	@Test
	public void aChordRingingSinceBeforeStartNeedsANewStrum() {
		Run run = new Run(E_MAJOR);
		float[] ringing = audio(0.05, E_MAJOR_PITCHES, 0.9);
		// the strum happens before Start: its onset must not count
		run.feed(ringing, 0, (int) (0.35 * RATE));
		run.session.onCommand(PracticeCommand.START);
		run.feed(ringing, (int) (0.35 * RATE), ringing.length);

		assertEquals(0, run.session.getCurrentIndex());
		assertNotEquals(ChordFeedback.Kind.INCOMPLETE, run.lastChordFeedback.getKind());
		assertTrue(!run.sawVerdict(Verdict.CORRECT));
	}

	// ---------------------------------------------------------------- harness

	private static Run run(int[][] shape, float[] samples) {
		Run run = new Run(shape);
		run.session.onCommand(PracticeCommand.START);
		run.feed(samples, 0, samples.length);
		return run;
	}

	/** Silence, then a strum (10 ms between strings) at {@code strumSeconds}, lasting to {@code endSeconds} after it. */
	private static float[] audio(double strumSeconds, int[] pitches, double seconds) {
		int start = (int) (strumSeconds * RATE);
		float[] signal = new float[start + (int) (seconds * RATE)];
		for( int i = 0; i < pitches.length; i++ ) {
			int onset = start + i * RATE / 100;
			float[] pluck = karplusStrong(440.0 * Math.pow(2.0, (pitches[i] - 69) / 12.0), signal.length - onset, 11L + i);
			for( int j = 0; j < pluck.length; j++ ) {
				signal[onset + j] += 0.2f * pluck[j];
			}
		}
		return signal;
	}

	private static float[] karplusStrong(double frequency, int count, long seed) {
		int period = Math.max(2, (int) Math.round(RATE / frequency));
		Random random = new Random(seed);
		float[] delay = new float[period];
		for( int i = 0; i < delay.length; i++ ) {
			delay[i] = (float) (random.nextDouble() * 2.0 - 1.0);
		}
		float[] signal = new float[count];
		for( int i = 0; i < count; i++ ) {
			int current = i % period;
			signal[i] = delay[current];
			delay[current] = 0.996f * 0.5f * (delay[current] + delay[(current + 1) % period]);
		}
		return signal;
	}

	private static final class Run {
		final PracticeSession session;
		final LevelAnalyzer levels = new LevelAnalyzer(AudioEngine.DEFAULT_TOO_LOW_RMS_DBFS, AudioEngine.DEFAULT_TOO_HIGH_RMS_DBFS,
				AudioEngine.DEFAULT_CLIPPED_SAMPLE_LEVEL, AudioEngine.DEFAULT_CLIPPING_RATIO);
		final ArticulationDetector articulation = new ArticulationDetector();
		final PolyphonicAnalyzer polyphonic = new PolyphonicAnalyzer(PolyphonicAnalyzer.Parameters.defaults(), RATE);
		final float[] window = new float[WINDOW];
		int filled;
		long now;
		ChordFeedback lastChordFeedback;
		boolean sawCorrect;
		boolean sawWrong;

		Run(int[][] shape) {
			SongFixture fixture = new SongFixture(1);
			for( int[] note : shape ) {
				fixture.quarter(0, 0, note[0], note[1]);
			}
			PracticeTrackSelection selection = new PracticeTrackSelection(fixture.song,
					PracticeTrackCandidates.evaluate(fixture.song, fixture.track()).getPreselected(),
					PracticeTunerContext.getInstance(new TGContext()));
			selection.accept();
			assertEquals(PracticeEvent.Kind.EXPECTED_CHORD, fixture.sequence().getEvents().get(0).getKind());
			this.session = new PracticeSession(selection, fixture.sequence(), new NoteInterpreter(0.8),
					new TolerancePolicy(5.0, 35.0), 20 * HOP_NANOS, ArticulationDetector.DECISION_DELAY_HOPS * HOP_NANOS);
			this.session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		}

		/** Analyses samples [from, to) hop by hop, as the engine's analysis thread does, into the session. */
		void feed(float[] samples, int from, int to) {
			for( int offset = from; offset + HOP <= to; offset += HOP ) {
				System.arraycopy(this.window, HOP, this.window, 0, WINDOW - HOP);
				System.arraycopy(samples, offset, this.window, WINDOW - HOP, HOP);
				this.polyphonic.push(samples, offset, HOP);
				this.filled = Math.min(WINDOW, this.filled + HOP);
				this.now += HOP_NANOS;
				if( this.filled < WINDOW ) {
					continue;
				}
				LevelMeasurement level = this.levels.analyze(this.window);
				boolean onset = this.articulation.process(this.window);
				AnalysisFrame frame = new AnalysisFrame(this.now, level.getRmsDbfs(), level.getPeakDbfs(), level.getClippedRatio(),
						level.getLevelState(), Double.NaN, 0.0, onset, this.polyphonic.analyze());
				PracticeSnapshot snapshot = this.session.onFrame(frame);
				if( snapshot.getChordFeedback() != null ) {
					this.lastChordFeedback = snapshot.getChordFeedback();
				}
				this.sawCorrect |= (snapshot.getVerdict() == Verdict.CORRECT);
				this.sawWrong |= (snapshot.getVerdict() == Verdict.WRONG);
			}
		}

		boolean sawVerdict(Verdict verdict) {
			return (verdict == Verdict.CORRECT ? this.sawCorrect : this.sawWrong);
		}
	}
}
