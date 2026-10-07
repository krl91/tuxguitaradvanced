package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.PitchResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.audio.engine.OfflineAnalysis;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.TechniqueCatalog;
import app.tuxguitar.audio.engine.testsupport.SyntheticSignals;

/**
 * The functional test of the tempo-following mode (spec 002 T137): a whole run on the fake player and its fake clock, the way the application runs it
 * (the run starts the player, the count-down runs out, the clock gives the position, the frames come in), with notes whose times are known. The
 * session stays synchronised after a mistake because every event keeps its own window on the player's clock.
 */
public class FollowFunctionalTest {

	private static final int RATE = 48000;

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static EventResult result(FakePlayerRun run, int index) {
		return run.last.results().get(index);
	}

	@Test
	public void everyVerdictOfTheTimingComesOutOfTheRunOnTheFakePlayer() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		assertEquals(FollowPlayback.Result.STARTED, run.start());
		run.playEvent(0, -60);      // on time
		run.playEvent(1, -150);     // early
		run.playEvent(2, 150);      // late
		// event 3 is not played
		run.playEvent(4, 0);
		run.play(run.target(0) + 250 * MS, 71, 120 * MS);          // an extra note between two windows
		run.runAll();

		assertEquals(State.COMPLETE, run.last.state());
		assertEquals(TimingVerdict.ON_TIME, result(run, 0).timing());
		assertEquals(-60 * MS, result(run, 0).offsetNanos());
		assertEquals(TimingVerdict.EARLY, result(run, 1).timing());
		assertEquals(TimingVerdict.LATE, result(run, 2).timing());
		assertEquals(Status.MISSED, result(run, 3).status());
		assertEquals(Status.MATCHED, result(run, 4).status());
		assertEquals(1, run.last.counts().extra());
		assertEquals(1, run.last.counts().missed());
		assertTrue(run.player.fakeClock().isReady());
	}

	@Test
	public void noFrameIsJudgedDuringTheCountInAndTheFirstNotesAreOnTimeAtTheEnd() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.runUntil(T0 + LEAD - 5 * MS);
		assertEquals(State.COUNT_IN, run.last.state());
		assertFalse(run.clock.isReady(), "the player's clock says nothing during the count-down");
		assertEquals(0, run.last.counts().onTime() + run.last.counts().early() + run.last.counts().late() + run.last.counts().missed() + run.last.counts().extra());

		run.runAll();
		assertEquals(TimingVerdict.ON_TIME, result(run, 0).timing());
	}

	@Test
	public void aMistakeNeverMovesTheWindowOfTheEventsAfterIt() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.play(run.target(1), 70, 600 * MS);          // event 1: a wrong note
		// event 2 is not played at all
		run.playEvent(3, 0);
		run.playEvent(4, 40);
		run.runAll();

		assertEquals(Status.MATCHED, result(run, 0).status());
		assertEquals(Status.WRONG_PITCH, result(run, 1).status());
		assertEquals(PitchResult.WRONG, result(run, 1).pitch());
		assertEquals(Status.MISSED, result(run, 2).status());
		assertEquals(Status.MATCHED, result(run, 3).status());
		assertEquals(TimingVerdict.ON_TIME, result(run, 3).timing());
		assertEquals(0L, result(run, 3).offsetNanos());
		assertEquals(Status.MATCHED, result(run, 4).status());
		assertEquals(40 * MS, result(run, 4).offsetNanos());
	}

	@Test
	public void theSameNotesAreOnTimeAtSeventyAndAtOneHundredPercent() {
		for (int percent : new int[] { 70, 100 }) {
			FakePlayerRun run = new FakePlayerRun(fiveNotes(), percent);
			run.start();
			for (int i = 0; i < 5; i++) {
				run.playEvent(i, 0);
			}
			run.runAll();
			assertEquals(5, run.last.counts().onTime(), percent + " %");
			assertEquals(State.COMPLETE, run.last.state());
		}
		// the score at 70 % lasts longer than at 100 %: the events are further apart
		assertTrue(new FakePlayerRun(fiveNotes(), 70).timeline.durationNanos() > new FakePlayerRun(fiveNotes(), 100).timeline.durationNanos());
	}

	@Test
	public void aTempoChangeARepeatAndAnAlternativeEndingAreFollowedByTheClock() {
		SongFixture fixture = new SongFixture(4);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(1, 0, 1, 1);
		fixture.quarter(2, 0, 1, 2);
		fixture.quarter(3, 0, 1, 3);
		fixture.tempoChange(1, 60);
		fixture.song.getMeasureHeader(0).setRepeatOpen(true);
		fixture.song.getMeasureHeader(1).setRepeatAlternative(1);
		fixture.song.getMeasureHeader(1).setRepeatClose(1);
		fixture.song.getMeasureHeader(2).setRepeatAlternative(2);
		FakePlayerRun run = new FakePlayerRun(fixture, 100);
		int events = run.sequence.getEvents().size();
		assertTrue(events > 4, "the first ending is played, then the repeat, then the second ending: " + events + " events");
		run.start();
		for (int i = 0; i < events; i++) {
			run.playEvent(i, 0);
		}
		run.runAll();

		assertEquals(events, run.last.counts().onTime(), "every event is on time at the tempo of its own measure, in the order the player plays");
		assertEquals(State.COMPLETE, run.last.state());
	}

	@Test
	public void aChordAndANoteAreJudgedOnTheirOwnInsideTheRun() {
		SongFixture fixture = new SongFixture(1);
		fixture.chord(0, 0, new int[] { 6, 5, 4, 3, 2, 1 }, new int[] { 0, 2, 2, 1, 0, 0 });
		fixture.quarter(0, 1, 1, 1);
		FakePlayerRun run = new FakePlayerRun(fixture, 100);
		run.start();
		float[] signal = new float[(int) ((LEAD + run.timeline.durationNanos() + 3_000 * MS) * RATE / 1_000_000_000L)];
		int[] pitches = { 40, 47, 52, 56, 59, 64 };
		for (int i = 0; i < pitches.length; i++) {
			float[] note = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(pitches[i]), RATE, (int) (1.3 * RATE), 11L + i);
			int offset = (int) ((run.target(0) - T0 + i * 10 * MS) * RATE / 1_000_000_000L);
			for (int k = 0; k < note.length && offset + k < signal.length; k++) {
				signal[offset + k] += 0.2f * note[k];
			}
		}
		float[] pick = SyntheticSignals.karplusStrong(SyntheticSignals.midiFrequency(65), RATE, (int) (0.9 * RATE), 40L);
		int pickAt = (int) ((run.target(1) - T0) * RATE / 1_000_000_000L);
		for (int k = 0; k < pick.length && pickAt + k < signal.length; k++) {
			signal[pickAt + k] += 0.3f * pick[k];
		}
		List<AnalysisFrame> frames = OfflineAnalysis.analyze(signal, RATE, RecognitionSettings.defaults(), true, AttackCandidates.of(run.sequence));
		run.session.setPolyphonicAcceptance(() -> new PolyphonicAcceptance(PolyphonicAcceptance.Parameters.defaults()));
		run.feed(frames);

		assertEquals(Status.MATCHED, result(run, 0).status());
		assertEquals(PitchResult.CORRECT, result(run, 0).pitch());
		assertEquals(TimingVerdict.ON_TIME, result(run, 0).timing());
		assertEquals(Status.MATCHED, result(run, 1).status());
		assertEquals(TimingVerdict.ON_TIME, result(run, 1).timing());
	}

	@Test
	public void aTechniqueEventKeepsItsTimingWhateverTheTechniqueDid() {
		SongFixture fixture = new SongFixture(1);
		fixture.bend(0, 0, 3, 7, 2);
		fixture.quarter(0, 1, 1, 1);
		TechniqueJudge judge = new TechniqueJudge(TechniqueCatalog.of(new BendDetector(BendDetector.Parameters.defaults()), true, "test"), true);
		String[] verdicts = new String[2];
		TechniqueResult[] results = new TechniqueResult[2];
		for (int made = 0; made < 2; made++) {
			FakePlayerRun run = new FakePlayerRun(fixture, 100);
			run.session.setTechniqueJudge(judge);
			run.start();
			final boolean bent = (made == 1);
			run.playMoving(run.target(0), 450 * MS, seconds -> 62.0 + (bent ? 2.0 * Math.min(1.0, Math.max(0.0, (seconds - 0.1) / 0.2)) : 0.0));
			run.playEvent(1, 0);
			run.runAll();
			EventResult bend = result(run, 0);
			verdicts[made] = bend.status() + "/" + bend.pitch() + "/" + bend.timing() + "/" + bend.offsetNanos() + " " + result(run, 1).status() + "/" + result(run, 1).timing();
			for (TechniqueResult technique : bend.techniques()) {
				if (technique.getTechnique() == PlayingTechnique.BEND) {
					results[made] = technique;
				}
			}
		}
		assertEquals(verdicts[0], verdicts[1], "the bend made or not, the pitch and timing verdicts are the same");
		assertTrue(verdicts[0].startsWith("MATCHED/CORRECT/ON_TIME"), verdicts[0]);
		assertTrue(results[0] != null && results[1] != null, "the bend is judged apart in both runs");
		assertTrue(results[0].getVerdict() != results[1].getVerdict(), "…and the technique result is the only thing that differs: " + results[0].getVerdict() + " / " + results[1].getVerdict());
	}

	@Test
	public void aPauseJudgesNothingAndTheEventsOfThePauseAreNotJudgedThenTheRunGoesOn() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.playEvent(1, 0);
		run.playEvent(2, 0);          // played while the session is paused
		run.playEvent(3, 0);
		run.playEvent(4, 0);
		run.runUntil(run.target(1) + 250 * MS);
		assertEquals(Status.MATCHED, result(run, 1).status());
		run.session.pause(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runUntil(run.target(2) + 250 * MS);
		assertEquals(State.PAUSED, run.last.state());
		assertEquals(0, run.last.counts().extra());
		run.session.resume(PracticeSession.PauseReason.INPUT_UNUSABLE);
		run.runAll();

		assertEquals(Status.NOT_JUDGED, result(run, 2).status(), "the event of the pause is neither matched nor missed");
		assertEquals(Status.MATCHED, result(run, 3).status());
		assertEquals(TimingVerdict.ON_TIME, result(run, 3).timing());
		assertEquals(Status.MATCHED, result(run, 4).status());
		assertEquals(0, run.last.counts().missed());
		assertEquals(State.COMPLETE, run.last.state());
	}

	@Test
	public void theEndOfTheSessionThenTheEndOfTheSongGiveTheStateBack() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 70);
		run.start();
		for (int i = 0; i < 5; i++) {
			run.playEvent(i, 0);
		}
		run.runAll();
		assertEquals(State.COMPLETE, run.last.state());
		assertTrue(run.player.isPlayerOnlyMuted(FakePlayerRun.TRACK), "the track is still silent in the player until the run ends");

		run.player.stopsByItself();

		assertEquals(List.of(FollowPlayback.EndReason.PLAYER_STOPPED), run.ends);
		assertFalse(run.player.isPlayerOnlyMuted(FakePlayerRun.TRACK));
		assertEquals(100, run.player.tempoPercent());
		assertEquals(0, run.player.listenerCount());
	}

	@Test
	public void aStopOfTheLearnerEndsTheSessionWithTheStateGivenBackAndTheEventsStillOpenNotJudged() {
		FakePlayerRun run = new FakePlayerRun(fiveNotes(), 100);
		run.start();
		run.playEvent(0, 0);
		run.runUntil(run.target(1) - 100 * MS);
		run.session.stop();
		run.playback.stop();
		run.runAll();

		assertEquals(State.STOPPED, run.last.state());
		assertEquals(Status.MATCHED, result(run, 0).status());
		assertEquals(Status.PENDING, result(run, 4).status(), "an event never reached stays pending: it was neither played nor missed");
		assertFalse(run.player.isPlayerOnlyMuted(FakePlayerRun.TRACK));
		assertEquals(0, run.ends.size());
	}
}
