package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.EventResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.PitchResult;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.app.tools.custom.practice.session.TimingContext.PrecisionReason;
import app.tuxguitar.app.tools.custom.practice.session.TimingContext.RhythmPrecision;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.song.models.effects.TGEffectGrace;

/**
 * The tempo-following session against its contract (spec 002 T089; contracts/tempo-following.md): every event is judged on its own window, ON_TIME, EARLY,
 * LATE, MISSED or EXTRA, a mistake never moves the following events, the time of an onset is that of its transient and not of the moment it was
 * decided, nothing is judged before the clocks are ready, and the states of the session. Fake clocks and scripted frames, no wait.
 * <p>
 * At 120 beats a minute (a beat of 500 ms) the NORMAL preset has a tight window of 75 ms and an outer window of 200 ms each side of the target.
 */
@Disabled("T095: the session is not implemented yet; the contract is written first")
public class FollowSessionTest {

	/** Five quarter notes of 64, 65, 66, 67 (measure 0) and 68 (measure 1), on the first string. */
	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for( int beat = 0 ; beat < 4 ; beat ++ ) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	private static EventResult result(FollowHarness harness, int index) {
		return harness.last.results().get(index);
	}

	private static FollowHarness started() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		assertEquals(State.COUNT_IN, harness.session.start());
		return harness;
	}

	// ------------------------------------------------------------------ the five timing verdicts

	@Test
	public void everyNotePlayedOnTheBeatIsOnTime() {
		FollowHarness harness = started();
		for( int i = 0 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		harness.runAll();

		for( int i = 0 ; i < 5 ; i ++ ) {
			EventResult r = result(harness, i);
			assertEquals(Status.MATCHED, r.status(), "event " + i);
			assertEquals(PitchResult.CORRECT, r.pitch());
			assertEquals(TimingVerdict.ON_TIME, r.timing());
			assertEquals(0L, r.offsetNanos());
			assertEquals(1, r.attempts());
		}
		assertEquals(5, harness.last.counts().onTime());
		assertEquals(State.COMPLETE, harness.last.state());
	}

	@Test
	public void theOffsetOfTheOnsetGivesOnTimeEarlyOrLateWithItsExactLimits() {
		FollowHarness harness = started();
		harness.playEvent(0, -60);      // inside the tight window
		harness.playEvent(1, -150);     // before the tight window: early
		harness.playEvent(2, 150);      // after the tight window: late
		harness.playEvent(3, 75);       // exactly the end of the tight window: still on time
		harness.playEvent(4, -200);     // exactly the start of the outer window: early
		harness.runAll();

		assertEquals(TimingVerdict.ON_TIME, result(harness, 0).timing());
		assertEquals(-60 * MS, result(harness, 0).offsetNanos());
		assertEquals(TimingVerdict.EARLY, result(harness, 1).timing());
		assertEquals(-150 * MS, result(harness, 1).offsetNanos());
		assertEquals(TimingVerdict.LATE, result(harness, 2).timing());
		assertEquals(150 * MS, result(harness, 2).offsetNanos());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 3).timing());
		assertEquals(75 * MS, result(harness, 3).offsetNanos());
		assertEquals(TimingVerdict.EARLY, result(harness, 4).timing());
		assertEquals(1, harness.last.counts().late());
		assertEquals(2, harness.last.counts().early());
		assertEquals(2, harness.last.counts().onTime());
	}

	@Test
	public void aNoteThatIsNotPlayedIsMissedOnlyOnceItsWindowHasClosed() {
		FollowHarness harness = started();
		for( int i : new int[] { 0, 1, 3, 4 } ) {
			harness.playEvent(i, 0);
		}
		long target = harness.target(2);

		harness.runUntil(target + 200 * MS);
		assertEquals(Status.PENDING, result(harness, 2).status(), "missed while its window was still open");
		harness.runUntil(target + 201 * MS);
		assertEquals(Status.PENDING, result(harness, 2).status(), "the last nanosecond of the window is still in the window");
		harness.runUntil(target + 206 * MS);
		assertEquals(Status.MISSED, result(harness, 2).status());
		assertEquals(TimingVerdict.MISSED, result(harness, 2).timing());
		assertEquals(PitchResult.NONE, result(harness, 2).pitch());

		harness.runAll();
		assertEquals(1, harness.last.counts().missed());
		assertEquals(4, harness.last.counts().onTime(), "the other notes were not moved by the miss");
	}

	@Test
	public void aNoteBetweenTwoWindowsIsAnExtraNoteAndMovesNothing() {
		FollowHarness harness = started();
		for( int i = 0 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		// 250 ms after the second event: outside its window (200 ms) and outside the next one (which begins 300 ms after)
		harness.play(harness.target(1) + 250 * MS, 70, 200 * MS);
		harness.runAll();

		assertEquals(1, harness.last.counts().extra());
		assertEquals(1, harness.last.extras().size());
		assertEquals(harness.target(1) + 250 * MS, harness.last.extras().get(0).wallNanos());
		assertEquals(5, harness.last.counts().onTime());
	}

	@Test
	public void aSecondAttackInTheWindowOfAnEventAlreadyMatchedIsExtra() {
		FollowHarness harness = started();
		harness.play(harness.target(1), 65, 110 * MS);
		harness.play(harness.target(1) + 120 * MS, 65, 110 * MS);
		harness.runAll();

		assertEquals(Status.MATCHED, result(harness, 1).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing());
		assertEquals(2, result(harness, 1).attempts());
		assertEquals(1, harness.last.counts().extra());
	}

	// ------------------------------------------------------------------ the pitch, apart from the timing

	@Test
	public void aWrongNoteInTheWindowIsAWrongNoteNotAMissAndKeepsItsTiming() {
		FollowHarness harness = started();
		harness.play(harness.target(0), 65, 600 * MS);          // F4 where E4 is written
		for( int i = 1 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		harness.runAll();

		EventResult wrong = result(harness, 0);
		assertEquals(Status.WRONG_PITCH, wrong.status());
		assertEquals(PitchResult.WRONG, wrong.pitch());
		assertEquals(TimingVerdict.ON_TIME, wrong.timing(), "the timing of the attempt is told apart from its pitch");
		assertEquals(1, harness.last.counts().wrongPitch());
		assertEquals(0, harness.last.counts().missed());
		assertEquals(4, harness.last.counts().onTime());
	}

	@Test
	public void aWrongNoteFollowedByTheRightOneInTheWindowIsMatched() {
		FollowHarness harness = started();
		harness.play(harness.target(0) - 150 * MS, 65, 150 * MS);
		harness.play(harness.target(0) + 40 * MS, 64, 600 * MS);
		harness.runAll();

		EventResult r = result(harness, 0);
		assertEquals(Status.MATCHED, r.status());
		assertEquals(PitchResult.CORRECT, r.pitch());
		assertEquals(TimingVerdict.ON_TIME, r.timing());
		assertEquals(40 * MS, r.offsetNanos());
		assertEquals(2, r.attempts());
	}

	// ------------------------------------------------------------------ the transient, not the decision

	@Test
	public void theTimeOfAnOnsetIsThatOfItsTransientAndNotOfTheMomentItWasDecided() {
		FollowHarness harness = started();
		// played 60 ms late: on time. The pitch is stable (and the note decided) 100 ms later, 160 ms after the target, well after the tight window
		harness.playEvent(0, 60);
		// played 85 ms late: late, whatever the decision
		harness.playEvent(1, 85);
		harness.runAll();

		assertEquals(TimingVerdict.ON_TIME, result(harness, 0).timing());
		assertEquals(60 * MS, result(harness, 0).offsetNanos(), "the offset is that of the transient, not of the decision");
		assertEquals(TimingVerdict.LATE, result(harness, 1).timing());
		assertEquals(85 * MS, result(harness, 1).offsetNanos());
	}

	// ------------------------------------------------------------------ resynchronisation (FR-053)

	@Test
	public void severalMistakesInARowNeverMoveTheFollowingEvents() {
		FollowHarness harness = started();
		harness.playEvent(0, 0);                                           // fine
		// event 1 is not played
		harness.play(harness.target(2), 70, 600 * MS);                     // event 2: a wrong note
		harness.play(harness.target(3) + 260 * MS, 67, 150 * MS);          // event 3: far too late: it is an extra note
		harness.playEvent(4, -150);                                        // event 4: early
		harness.play(harness.target(0) + 250 * MS, 71, 150 * MS);          // an extra note between events
		harness.runAll();

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 0).timing());
		assertEquals(Status.MISSED, result(harness, 1).status());
		assertEquals(Status.WRONG_PITCH, result(harness, 2).status());
		assertEquals(Status.MISSED, result(harness, 3).status());
		assertEquals(Status.MATCHED, result(harness, 4).status());
		assertEquals(TimingVerdict.EARLY, result(harness, 4).timing());
		assertEquals(2, harness.last.counts().extra());
		assertEquals(State.COMPLETE, harness.last.state());
	}

	@Test
	public void randomErrorsNeverChangeTheVerdictOfAnEventThatWasPlayedWell() {
		// every event gets an action chosen at random; its outcome depends on its own action only
		final int exact = 0, early = 1, late = 2, miss = 3, wrong = 4, tooLate = 5;
		Random random = new Random(20261007L);
		for( int trial = 0 ; trial < 60 ; trial ++ ) {
			FollowHarness harness = started();
			int[] action = new int[5];
			long[] offset = new long[5];
			for( int i = 0 ; i < 5 ; i ++ ) {
				action[i] = random.nextInt(6);
				int midi = harness.sequence.getEvents().get(i).getExpectedMidi();
				switch( action[i] ) {
					case 0: offset[i] = (random.nextInt(29) - 14) * 5L; harness.play(harness.target(i) + offset[i] * MS, midi, 150 * MS); break;
					case 1: offset[i] = -(80 + random.nextInt(24) * 5L); harness.play(harness.target(i) + offset[i] * MS, midi, 150 * MS); break;
					case 2: offset[i] = 80 + random.nextInt(24) * 5L; harness.play(harness.target(i) + offset[i] * MS, midi, 150 * MS); break;
					case 4: harness.play(harness.target(i) + 5 * MS, midi + 1, 150 * MS); break;
					case 5: harness.play(harness.target(i) + (230 + random.nextInt(12) * 5L) * MS, midi, 150 * MS); break;
					default: break;
				}
			}
			harness.runAll();
			for( int i = 0 ; i < 5 ; i ++ ) {
				EventResult r = result(harness, i);
				String why = "trial " + trial + " event " + i + " action " + action[i];
				switch( action[i] ) {
					case exact: assertEquals(TimingVerdict.ON_TIME, r.timing(), why); assertEquals(Status.MATCHED, r.status(), why); break;
					case early: assertEquals(TimingVerdict.EARLY, r.timing(), why); assertEquals(Status.MATCHED, r.status(), why); break;
					case late: assertEquals(TimingVerdict.LATE, r.timing(), why); assertEquals(Status.MATCHED, r.status(), why); break;
					case wrong: assertEquals(Status.WRONG_PITCH, r.status(), why); break;
					default: assertEquals(Status.MISSED, r.status(), why); break;
				}
				if( action[i] == exact || action[i] == early || action[i] == late ) {
					assertEquals(offset[i] * MS, r.offsetNanos(), why);
				}
			}
			int tooLateCount = 0;
			for( int a : action ) {
				tooLateCount += (a == tooLate ? 1 : 0);
			}
			assertEquals(tooLateCount, harness.last.counts().extra(), "trial " + trial + ": the notes that are too late are the extra notes");
			assertEquals(State.COMPLETE, harness.last.state());
		}
	}

	// ------------------------------------------------------------------ nothing is judged before the clocks are ready

	@Test
	public void noFrameIsJudgedBeforeTheClocksAreReady() {
		FollowHarness harness = started();
		harness.captureReady = false;
		harness.playEvent(0, 0);
		harness.play(harness.target(1), 70, 600 * MS);                  // a wrong note, while nothing can be judged
		harness.play(harness.target(1) + 250 * MS, 71, 150 * MS);       // an extra note, the same
		for( int i = 2 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		harness.runUntil(harness.target(2) - 150 * MS);
		assertEquals(RhythmPrecision.NOT_READY, harness.last.precision());
		assertEquals(0, harness.last.counts().extra(), "an extra note was given before the clocks were ready");
		assertEquals(0, harness.last.counts().wrongPitch(), "a wrong note was given before the clocks were ready");
		assertEquals(0, harness.last.counts().missed(), "a missed note was given before the clocks were ready");

		harness.captureReady = true;
		harness.runAll();
		assertEquals(Status.NOT_JUDGED, result(harness, 0).status());
		assertEquals(Status.NOT_JUDGED, result(harness, 1).status());
		assertEquals(0, harness.last.counts().extra());
		assertEquals(0, harness.last.counts().wrongPitch());
		assertEquals(0, harness.last.counts().missed());
		assertEquals(2, harness.last.counts().notJudged());
		for( int i = 2 ; i < 5 ; i ++ ) {
			assertEquals(Status.MATCHED, result(harness, i).status(), "event " + i);
			assertEquals(TimingVerdict.ON_TIME, result(harness, i).timing());
		}
		assertEquals(RhythmPrecision.NORMAL, harness.last.precision());
	}

	@Test
	public void thePlayerClockNotReadyStopsTheJudgementToo() {
		FollowHarness harness = started();
		harness.playerReady = false;
		for( int i = 0 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		harness.runUntil(harness.target(3));
		assertEquals(0, harness.last.counts().onTime());
		assertEquals(RhythmPrecision.NOT_READY, harness.last.precision());
		harness.playerReady = true;
		harness.runAll();
		assertEquals(Status.NOT_JUDGED, result(harness, 0).status());
		assertEquals(Status.MATCHED, result(harness, 4).status());
	}

	// ------------------------------------------------------------------ the three separate things

	@Test
	public void theLatencyCompensationMovesTheOnsetAndTheRouteWithoutOneIsSaidToBeReduced() {
		// the route delays everything by 120 ms
		FollowHarness calibrated = started();
		calibrated.routeLatency = 120 * MS;
		calibrated.compensation = LatencyCompensation.calibrated(120.0);
		for( int i = 0 ; i < 5 ; i ++ ) {
			calibrated.playEvent(i, 0);
		}
		calibrated.runAll();
		assertEquals(5, calibrated.last.counts().onTime());
		assertEquals(RhythmPrecision.NORMAL, calibrated.last.precision());

		FollowHarness uncalibrated = started();
		uncalibrated.routeLatency = 120 * MS;
		uncalibrated.compensation = LatencyCompensation.none();
		for( int i = 0 ; i < 5 ; i ++ ) {
			uncalibrated.playEvent(i, 0);
		}
		uncalibrated.runAll();
		assertEquals(5, uncalibrated.last.counts().late(), "without the compensation the same notes are late");
		assertEquals(RhythmPrecision.REDUCED, uncalibrated.last.precision());
		assertTrue(uncalibrated.last.precisionReasons().contains(PrecisionReason.NOT_CALIBRATED));

		// a negative offset, adjusted by hand
		FollowHarness manual = started();
		manual.routeLatency = -40 * MS;
		manual.compensation = LatencyCompensation.manual(-40.0);
		manual.playEvent(2, 0);
		manual.runAll();
		assertEquals(TimingVerdict.ON_TIME, result(manual, 2).timing());
		assertEquals(0L, result(manual, 2).offsetNanos());
	}

	// ------------------------------------------------------------------ the states

	@Test
	public void theSessionStartsOnlyWhenTheTrackIsAcceptedAndTheInputUsable() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 100);
		assertEquals(State.PREPARING, harness.session.snapshot().state());
		assertEquals(5, harness.session.snapshot().results().size());
		assertEquals(Status.PENDING, harness.session.snapshot().results().get(0).status());

		harness.session.setTrackAccepted(false);
		assertEquals(State.PREPARING, harness.session.start());
		harness.session.setTrackAccepted(true);
		harness.session.setAssessment(new InputAssessment(InputAssessment.Status.UNUSABLE, InputAssessment.Reason.NO_SIGNAL));
		assertEquals(State.PREPARING, harness.session.start());
		harness.session.setAssessment(new InputAssessment(InputAssessment.Status.SUBOPTIMAL, InputAssessment.Reason.TOO_LOW));
		assertEquals(State.COUNT_IN, harness.session.start());
		// starting again changes nothing
		assertEquals(State.COUNT_IN, harness.session.start());
	}

	@Test
	public void theCountInLeadsToFollowingWhenTheFirstWindowOpens() {
		FollowHarness harness = started();
		harness.runUntil(harness.target(0) - 205 * MS);
		assertEquals(State.COUNT_IN, harness.last.state());
		// a note played during the count-in is neither judged nor an extra note
		harness.runUntil(harness.target(0) - 195 * MS);
		assertEquals(State.FOLLOWING, harness.last.state());
	}

	@Test
	public void aPausedSessionJudgesNothingAndItsEventsAreNotJudgedNotMissed() {
		FollowHarness harness = started();
		harness.playEvent(0, 0);
		for( int i = 1 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		harness.runUntil(harness.target(1) - 100 * MS);
		harness.session.pause(PracticeSession.PauseReason.INPUT_UNUSABLE);
		harness.runUntil(harness.target(1) + 600 * MS);
		assertEquals(State.PAUSED, harness.last.state());
		assertEquals(0, harness.last.counts().extra(), "a note played while paused was judged");
		harness.session.resume(PracticeSession.PauseReason.INPUT_UNUSABLE);
		harness.runAll();

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(Status.NOT_JUDGED, result(harness, 1).status(), "an event closed while paused is neither matched nor missed");
		assertEquals(0, harness.last.counts().missed());
		assertEquals(Status.MATCHED, result(harness, 3).status());
		assertEquals(State.COMPLETE, harness.last.state());
	}

	@Test
	public void theSessionNeverWaitsOnAMissedNoteAndCompletesEvenIfNothingIsPlayed() {
		FollowHarness harness = started();
		harness.runAll();

		assertEquals(State.COMPLETE, harness.last.state());
		assertEquals(5, harness.last.counts().missed());
		assertEquals(-1, harness.last.openEvent());
	}

	@Test
	public void stoppingEndsTheSessionFromAnyStateAndKeepsWhatWasJudged() {
		FollowHarness harness = started();
		harness.playEvent(0, 0);
		harness.runUntil(harness.target(0) + 400 * MS);
		harness.session.stop();
		assertEquals(State.STOPPED, harness.session.snapshot().state());
		assertEquals(Status.MATCHED, harness.session.snapshot().results().get(0).status());
		// nothing more happens: frames are ignored and the session cannot be started again
		harness.playEvent(1, 0);
		harness.runAll();
		assertEquals(State.STOPPED, harness.last.state());
		assertEquals(Status.PENDING, result(harness, 1).status());
		assertEquals(State.STOPPED, harness.session.start());

		FollowHarness idle = new FollowHarness(fiveNotes(), 100);
		idle.session.stop();
		assertEquals(State.STOPPED, idle.session.snapshot().state());
	}

	// ------------------------------------------------------------------ tempo changes, repeats, other tempo

	@Test
	public void theWindowsFollowTheTempoOfEachMeasure() {
		SongFixture fixture = new SongFixture(3);
		fixture.tempoChange(1, 60);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 2, 1, 1);
		fixture.quarter(1, 0, 1, 2);
		fixture.quarter(1, 2, 1, 3);
		FollowHarness harness = new FollowHarness(fixture, 100);
		harness.session.start();
		// 120 beats a minute: a tight window of 75 ms; 60 beats a minute: 150 ms. The same 140 ms are late in the first and on time in the second.
		harness.playEvent(0, 140);
		harness.playEvent(1, 0);
		harness.playEvent(2, 140);
		harness.playEvent(3, 155);
		harness.runAll();

		assertEquals(TimingVerdict.LATE, result(harness, 0).timing());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 2).timing());
		assertEquals(TimingVerdict.LATE, result(harness, 3).timing());
		assertEquals(State.COMPLETE, harness.last.state());
	}

	@Test
	public void aRepeatedPassageIsJudgedOnceForEachPass() {
		SongFixture fixture = new SongFixture(2);
		fixture.repeat(0, 0, 2);
		fixture.quarter(0, 0, 1, 0);
		fixture.quarter(0, 2, 1, 1);
		fixture.quarter(1, 0, 1, 2);
		FollowHarness harness = new FollowHarness(fixture, 100);
		harness.session.start();
		assertEquals(5, harness.sequence.getEvents().size());
		// the second pass is played late, the first one on time
		for( int i = 0 ; i < 5 ; i ++ ) {
			harness.playEvent(i, i < 2 ? 0 : (i < 4 ? 100 : 0));
		}
		harness.runAll();

		assertEquals(TimingVerdict.ON_TIME, result(harness, 0).timing());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing());
		assertEquals(TimingVerdict.LATE, result(harness, 2).timing());
		assertEquals(TimingVerdict.LATE, result(harness, 3).timing());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 4).timing());
		assertEquals(5, harness.last.counts().onTime() + harness.last.counts().late());
	}

	@Test
	public void aSlowerTempoPercentageStretchesTheTimesAndTheWindows() {
		FollowHarness harness = new FollowHarness(fiveNotes(), 50);
		harness.session.start();
		for( int i = 0 ; i < 5 ; i ++ ) {
			harness.playEvent(i, 0);
		}
		// at 50 % a quarter lasts a second: the targets are twice as far apart, and the tight window follows the beat (150 ms instead of 75)
		assertEquals(1000 * MS, harness.target(1) - harness.target(0));
		harness.runAll();
		assertEquals(TimingVerdict.ON_TIME, result(harness, 4).timing());
		assertEquals(5, harness.last.counts().onTime());
		assertEquals(0, harness.last.counts().extra());
	}

	// ------------------------------------------------------------------ simultaneous events

	@Test
	public void aGraceNoteAndItsPrincipalNoteShareATimeAndAreToldApartByTheirPitch() {
		SongFixture fixture = new SongFixture(1);
		TGEffectGrace grace = fixture.factory().newEffectGrace();
		grace.setFret(2);
		fixture.quarter(0, 1, 3, 4).getEffect().setGrace(grace);
		FollowHarness harness = new FollowHarness(fixture, 100);
		harness.session.start();
		assertEquals(2, harness.sequence.getEvents().size());
		assertEquals(harness.target(0), harness.target(1), "the grace note and the principal note are at the same time");
		// the grace note a little before the beat, the principal note on it
		harness.play(harness.target(0) - 60 * MS, harness.sequence.getEvents().get(0).getExpectedMidi(), 50 * MS);
		harness.play(harness.target(1), harness.sequence.getEvents().get(1).getExpectedMidi(), 600 * MS);
		harness.runAll();

		assertEquals(Status.MATCHED, result(harness, 0).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 0).timing());
		assertEquals(Status.MATCHED, result(harness, 1).status());
		assertEquals(TimingVerdict.ON_TIME, result(harness, 1).timing());
		assertEquals(0, harness.last.counts().extra());
	}
}
