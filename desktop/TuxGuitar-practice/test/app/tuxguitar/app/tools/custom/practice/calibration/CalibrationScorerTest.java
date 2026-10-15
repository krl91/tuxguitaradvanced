package app.tuxguitar.app.tools.custom.practice.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.replay.RecordedAudio;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T020): the score of one candidate (contract scoring-search): the sounds recognised for their own event, minus two points for each sound
 * accepted for the NEXT event (the negative control), and the rate over the events (FR-006, FR-014).
 */
public class CalibrationScorerTest {

	private static final RecognitionSettings DEFAULTS = RecognitionSettings.defaults();

	@Test
	public void everySoundRightAndNoControlAcceptedScoresTheNumberOfEvents() {
		CalibrationScorer scorer = new CalibrationScorer((segment, event, settings) -> segment == event, new boolean[] { false, false, true });
		CalibrationScore score = scorer.score(DEFAULTS);
		assertEquals(3, score.getCorrect());
		assertEquals(0, score.getWrong());
		assertEquals(3.0, score.getObjective());
		assertEquals(100.0, score.getRate().totalPercent());
	}

	@Test
	public void aSettingThatAcceptsEverythingScoresBadlyBecauseOfTheControl() {
		CalibrationScorer scorer = new CalibrationScorer((segment, event, settings) -> true, new boolean[] { false, false, false, false });
		CalibrationScore score = scorer.score(DEFAULTS);
		assertEquals(4, score.getCorrect());
		assertEquals(4, score.getWrong());
		assertEquals(4 - CalibrationScorer.WRONG_WEIGHT * 4, score.getObjective());
		assertTrue(score.getObjective() < 0, "worse than a setting that recognises nothing");
		assertEquals(2.0, CalibrationScorer.WRONG_WEIGHT, "a starting value, declared");
	}

	@Test
	public void theControlJudgesEachSoundAgainstTheNextEventAndTheLastAgainstTheFirst() {
		List<int[]> asked = new ArrayList<int[]>();
		CalibrationScorer scorer = new CalibrationScorer((segment, event, settings) -> {
			asked.add(new int[] { segment, event });
			return false;
		}, new boolean[] { false, false, false });
		scorer.score(DEFAULTS);
		assertEquals(6, asked.size());
		for (int i = 0; i < 3; i++) {
			assertTrue(contains(asked, i, i), "sound " + i + " against its own event");
			assertTrue(contains(asked, i, (i + 1) % 3), "sound " + i + " against the next event");
		}
	}

	private static boolean contains(List<int[]> asked, int segment, int event) {
		for (int[] pair : asked) {
			if (pair[0] == segment && pair[1] == event) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void theRateIsCountedForTheNotesTheChordsAndTogether() {
		CalibrationScorer scorer = new CalibrationScorer((segment, event, settings) -> segment == event && segment != 1, new boolean[] { false, false, true });
		CalibrationScore score = scorer.score(DEFAULTS);
		assertEquals(1, score.getRate().getNotesAccepted());
		assertEquals(2, score.getRate().getNotesTotal());
		assertEquals(1, score.getRate().getChordsAccepted());
		assertEquals(1, score.getRate().getChordsTotal());
		assertEquals(100.0 * 2 / 3, score.getRate().totalPercent(), 1e-9);
	}

	@Test
	public void theRealJudgeScoresThreeCorpusNotesAndKeepsItsFramesBetweenCandidates() throws IOException {
		SongFixture fixture = new SongFixture(3);
		fixture.quarter(0, 0, 6, 0);
		fixture.quarter(1, 0, 5, 0);
		fixture.quarter(2, 0, 4, 0);
		EventJudge judge = new EventJudge(new TGContext(), fixture.song, fixture.track(), fixture.sequence());
		RecordedAudio e2 = CorpusFiles.load("02_E2_open_s6f0.wav");
		RecordedAudio a2 = CorpusFiles.load("03_A2_open_s5f0.wav");
		RecordedAudio d3 = CorpusFiles.load("04_D3_open_s4f0.wav");
		CalibrationScorer scorer = CalibrationScorer.forRecording(judge, Arrays.asList(e2.samples, a2.samples, d3.samples), e2.sampleRate);
		CalibrationScore score = scorer.score(DEFAULTS);
		assertEquals(3, score.getCorrect(), "the three notes are recognised with the default settings");
		assertEquals(0, score.getWrong());
		long before = scorer.getAnalysesDone();
		// a setting that does not reach the analysis (the acceptance in cents): the frames are kept, nothing is analysed again
		CalibrationScore again = scorer.score(DEFAULTS.with(app.tuxguitar.audio.engine.settings.RecognitionParameters.NOTE_ACCEPT,
				app.tuxguitar.audio.engine.settings.RecognitionParameters.NOTE_ACCEPT.getMinimum()));
		assertEquals(before, scorer.getAnalysesDone(), "no new analysis for a practice-only setting");
		assertTrue(again.getCorrect() <= 3);
		// a setting that reaches the analysis (the attack gate): the sounds are analysed again
		scorer.score(DEFAULTS.with(app.tuxguitar.audio.engine.settings.RecognitionParameters.ATTACK_MIN_LEVEL, -60.0));
		assertTrue(scorer.getAnalysesDone() > before, "a new analysis when the analysis settings change");
	}
}
