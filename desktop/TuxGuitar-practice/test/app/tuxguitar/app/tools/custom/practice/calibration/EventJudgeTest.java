package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T018): does a recorded sound make a Wait-mode session accept ONE event of the piece? The procedure of {@code DatasetJudge} (a test class),
 * copied into production, with the settings of the candidate used for both the offline analysis and the practice parameters (FR-006).
 */
public class EventJudgeTest {

	private static final int CHORD_RATE = 44100;
	private static final int[] A_MAJOR = { 45, 52, 57, 61, 64 };

	/** E2, A2, D3 as single notes in the measures 0 to 2, then an A major chord (strings 5 to 1: 0 2 2 2 0) */
	private static EventJudge judge() {
		SongFixture fixture = new SongFixture(4);
		fixture.quarter(0, 0, 6, 0);
		fixture.quarter(1, 0, 5, 0);
		fixture.quarter(2, 0, 4, 0);
		fixture.chord(3, 0, new int[] { 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 2, 0 });
		return new EventJudge(new TGContext(), fixture.song, fixture.track(), fixture.sequence());
	}

	@Test
	public void thePieceHasFourEventsTheLastOneAChord() {
		EventJudge judge = judge();
		assertEquals(4, judge.eventCount());
		assertFalse(judge.isChord(0));
		assertTrue(judge.isChord(3));
	}

	@Test
	public void aRightNoteIsAcceptedAndAWrongOneIsRefused() throws IOException {
		EventJudge judge = judge();
		RecordedAudio e2 = CorpusFiles.load("02_E2_open_s6f0.wav");
		RecognitionSettings settings = RecognitionSettings.defaults();
		assertTrue(judge.accepts(e2.samples, e2.sampleRate, 0, settings), "E2 played, E2 expected");
		assertFalse(judge.accepts(e2.samples, e2.sampleRate, 1, settings), "E2 played, A2 expected");
		assertFalse(judge.accepts(e2.samples, e2.sampleRate, 2, settings), "E2 played, D3 expected");
		RecordedAudio a2 = CorpusFiles.load("03_A2_open_s5f0.wav");
		assertTrue(judge.accepts(a2.samples, a2.sampleRate, 1, settings), "A2 played, A2 expected");
		assertFalse(judge.accepts(a2.samples, a2.sampleRate, 0, settings), "A2 played, E2 expected");
	}

	@Test
	public void aChordWithAllItsNotesIsAcceptedAndAnotherChordOrTheBassAloneIsRefused() {
		EventJudge judge = judge();
		RecognitionSettings settings = RecognitionSettings.defaults();
		for (long seed : new long[] { 11L, 23L, 37L }) {
			float[] whole = SyntheticSignals.strum(A_MAJOR, CHORD_RATE, CHORD_RATE * 5 / 2, 600, 1.0, seed);
			assertTrue(judge.accepts(whole, CHORD_RATE, 3, settings), "the five notes of A major, A major expected (seed " + seed + ")");
		}
		float[] cMajor = SyntheticSignals.strum(new int[] { 48, 52, 55, 60, 64 }, CHORD_RATE, CHORD_RATE * 5 / 2, 600, 1.0, 11L);
		assertFalse(judge.accepts(cMajor, CHORD_RATE, 3, settings), "C major played, A major expected");
		float[] bass = SyntheticSignals.strum(new int[] { 45 }, CHORD_RATE, CHORD_RATE * 5 / 2, 600, 1.0, 11L);
		assertFalse(judge.accepts(bass, CHORD_RATE, 3, settings), "the bass note alone");
		// Measured on 2026-10-15 (synthetic strums, default settings): a chord with one note missing is sometimes accepted by the recognition as it is
		// (for example 4 of the 5 notes in 3 of 3 seeds for some subsets). That is the recognition's own leniency, out of scope here: not asserted.
	}

	@Test
	public void theAnalysisAndTheJudgmentCanBeDoneSeparately() throws IOException {
		EventJudge judge = judge();
		RecordedAudio e2 = CorpusFiles.load("02_E2_open_s6f0.wav");
		RecognitionSettings settings = RecognitionSettings.defaults();
		java.util.List<app.tuxguitar.audio.engine.analysis.AnalysisFrame> frames = judge.analyse(e2.samples, e2.sampleRate, 0, settings);
		assertFalse(frames.isEmpty());
		assertEquals(judge.accepts(e2.samples, e2.sampleRate, 0, settings), judge.accepts(frames, 0, settings), "the same verdict from the frames");
	}
}
