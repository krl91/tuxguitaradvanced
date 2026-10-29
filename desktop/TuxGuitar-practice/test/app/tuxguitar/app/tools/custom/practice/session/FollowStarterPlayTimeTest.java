package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowHarness.Note;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * Spec 016 (T027, US3): Follow is prepared with the time to play of the learner ({@link PlayTimeWindow}) and the recognition of its pitch permissiveness
 * ({@link FollowLeniency}); the existing {@code prepare}, Arcade's path, still judges with the timing window. The environment is a fake: no audio engine, no player,
 * no thread.
 */
public class FollowStarterPlayTimeTest {

	private static final class FakeEnvironment implements FollowStarter.Environment {
		FakeMidiPlayer player;
		final RouteCalibrations calibrations = new RouteCalibrations();

		public FollowPlayer player() {
			return this.player;
		}

		public long wallNanosOf(AnalysisFrame frame) {
			return T0 + frame.getTimestampNanos();
		}

		public boolean captureReady() {
			return true;
		}

		public double lagSpreadMillis() {
			return 5.0;
		}

		public AudioRoute route() {
			return new AudioRoute("microphone", "speakers", 48000, "1ch 16bit");
		}

		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		public InputAssessment assessment() {
			return new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		}
	}

	private interface Preparation {
		FollowStarter.Plan prepare(SongFixture fixture, FollowSettings settings, FakeEnvironment environment);
	}

	private static final Preparation FOLLOW = (fixture, settings, environment) -> FollowStarter.prepareFollow(new TGContext(), fixture.song, fixture.sequence(), 1,
			settings, FollowLeniencyPosition.PERMISSIVE, RecognitionSettings.defaults(), null, null, environment, s -> { }, Runnable::run, r -> { }, null, null);

	private static final Preparation EXISTING = (fixture, settings, environment) -> FollowStarter.prepare(new TGContext(), fixture.song, fixture.sequence(), 1, settings,
			PracticeParameters.of(RecognitionSettings.defaults()), null, null, environment, s -> { }, Runnable::run, r -> { }, null, null);

	/** One prepared run on a fake player whose clock follows the timeline the starter built (as {@code FollowStarterTest} does). */
	private static final class Prepared {
		final FakeEnvironment environment = new FakeEnvironment();
		final FollowStarter.Plan plan;
		final FakeTempoClock clock;
		final FakeMidiPlayer player;
		final List<Note> notes = new ArrayList<Note>();
		final Set<Note> flagged = new HashSet<Note>();
		FollowSession.Snapshot last;
		long now = T0;

		Prepared(SongFixture fixture, FollowSettings settings, Preparation preparation) {
			final FollowTimeline[] built = new FollowTimeline[1];
			this.clock = new FakeTempoClock(ticks -> {
				long whole = (long) Math.floor(ticks);
				double part = ticks - whole;
				return built[0].nanosAtTick(whole) * (1.0 - part) + built[0].nanosAtTick(whole + 1) * part;
			}, FollowStarter.FIRST_TICK, 40_000L);
			this.clock.setNow(T0);
			this.player = new FakeMidiPlayer(this.clock).withInitialState(100, false, true).countDownNanos(LEAD);
			this.environment.player = this.player;
			this.plan = preparation.prepare(fixture, settings, this.environment);
			built[0] = this.plan.timeline();
		}

		long target(int event) {
			return T0 + LEAD + this.plan.timeline().targetNanos(event);
		}

		void play(long wall, int midi) {
			this.notes.add(new Note(wall, 150 * MS, seconds -> midi));
		}

		int midi(int event) {
			return this.plan.sequence().getEvents().get(event).getExpectedMidi();
		}

		List<FollowSession.EventResult> run() {
			this.plan.session().start();
			assertEquals(FollowPlayback.Result.STARTED, this.plan.run().start());
			long end = T0 + LEAD + this.plan.timeline().durationNanos() + 1_500 * MS;
			while (this.now < end) {
				if (this.now > this.clock.nowNanos()) {
					this.player.advance(this.now - this.clock.nowNanos());
				}
				this.last = this.plan.session().onFrame(FollowHarness.frameOf(this.notes, this.flagged, this.now));
				this.now += HOP;
			}
			return this.last.results();
		}
	}

	/** Five quarter notes of 64, 65, 66, 67 and 68 at 120 beats a minute: 500 ms apart. */
	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	/**
	 * One note at 120 beats a minute (the count-in of the fake player is the one of that tempo), then, in the second measure at 300 beats a minute, quarters 200 ms
	 * apart; events 1 and 2 have the same pitch.
	 */
	private static SongFixture closeNotes() {
		SongFixture fixture = new SongFixture(2);
		fixture.quarter(0, 0, 1, 0);
		fixture.tempoChange(1, 300);
		int[] frets = { 2, 2, 4, 5 };
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(1, beat, 1, frets[beat]);
		}
		return fixture;
	}

	private static FollowSettings settings(int playTimeMillis) {
		FollowSettings settings = new FollowSettings();
		settings.setMode(FollowSettings.Mode.FOLLOW);
		settings.setWindowMillis(100);
		settings.setPlayTimeMillis(playTimeMillis);
		return settings;
	}

	/** Every event of five notes played on time but the third, played {@code lateMillis} after its red. */
	private static List<FollowSession.EventResult> thirdNoteLate(FollowSettings settings, Preparation preparation, long lateMillis) {
		Prepared prepared = new Prepared(fiveNotes(), settings, preparation);
		for (int event = 0; event < 5; event++) {
			prepared.play(prepared.target(event) + (event == 2 ? lateMillis * MS : 0L), prepared.midi(event));
		}
		return prepared.run();
	}

	@Test
	public void aNotePlayedTwoHundredFiftyMillisecondsAfterItsRedIsAcceptedWithThreeHundredAndMissedWithTwoHundred() {
		List<FollowSession.EventResult> generous = thirdNoteLate(settings(300), FOLLOW, 250);
		assertEquals(Status.MATCHED, generous.get(2).status());
		assertEquals(TimingVerdict.LATE, generous.get(2).timing(), "after half the time to play: late, but accepted");
		List<FollowSession.EventResult> shorter = thirdNoteLate(settings(200), FOLLOW, 250);
		assertEquals(Status.MISSED, shorter.get(2).status());
		assertEquals(RefusalReason.LATE_OUTSIDE, shorter.get(2).reason());
	}

	@Test
	public void anAttackOneHundredNinetyMillisecondsAfterTheFirstOfTwoCloseNotesCountsEarlyForTheSecond() {
		// spec US3 scenario 2: 200 ms apart, the first may be played up to 180 ms after its red, the second from 20 ms before its own
		Prepared prepared = new Prepared(closeNotes(), settings(300), FOLLOW);
		assertEquals(200 * MS, prepared.target(2) - prepared.target(1));
		assertEquals(prepared.midi(1), prepared.midi(2), "the same pitch: only the time tells the two events apart");
		prepared.play(prepared.target(0), prepared.midi(0));
		prepared.play(prepared.target(1) + 190 * MS, prepared.midi(2));
		prepared.play(prepared.target(3), prepared.midi(3));
		prepared.play(prepared.target(4), prepared.midi(4));
		List<FollowSession.EventResult> results = prepared.run();
		assertEquals(Status.MATCHED, results.get(0).status());
		assertEquals(Status.MISSED, results.get(1).status(), "190 ms is beyond the 180 ms of the first");
		assertEquals(Status.MATCHED, results.get(2).status());
		assertEquals(TimingVerdict.EARLY, results.get(2).timing(), "10 ms before the red of the second");
	}

	@Test
	public void theExistingPrepareArcadesPathStillJudgesWithTheTimingWindow() {
		// the timing window (100 ms each side) accepts this note late; a time to play of 100 ms would not, and the existing prepare does not read it
		assertEquals(Status.MISSED, thirdNoteLate(settings(100), FOLLOW, 250).get(2).status());
		List<FollowSession.EventResult> shortTime = thirdNoteLate(settings(100), EXISTING, 250);
		List<FollowSession.EventResult> longTime = thirdNoteLate(settings(600), EXISTING, 250);
		assertEquals(Status.MATCHED, shortTime.get(2).status());
		assertEquals(FollowReasonParityTest.describe(shortTime), FollowReasonParityTest.describe(longTime), "the same verdicts whatever the time to play");
	}

	@Test
	public void thePlanCarriesTheTimeToPlayAndTheRecognitionOfThePosition() {
		FollowStarter.Plan follow = new Prepared(fiveNotes(), settings(450), FOLLOW).plan;
		assertEquals(450, follow.playTimeMillis());
		assertSame(FollowLeniencyPosition.PERMISSIVE, follow.leniency());
		assertEquals(FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, RecognitionSettings.defaults()), follow.recognition());
		assertEquals(FollowLeniency.apply(FollowLeniencyPosition.PERMISSIVE, RecognitionSettings.defaults()).get(RecognitionParameters.NOTE_STABILITY),
				follow.recognition().get(RecognitionParameters.NOTE_STABILITY));
		FollowStarter.Plan existing = new Prepared(fiveNotes(), settings(450), EXISTING).plan;
		assertEquals(-1, existing.playTimeMillis(), "the existing prepare judges with the window");
		assertNull(existing.leniency());
		assertNull(existing.recognition());
	}

	@Test
	public void atTheDefaultSettingsTheRightPitchTwentyCentsOutOfTunePlayedTwoHundredFiftyMillisecondsLateIsAccepted() {
		// spec US3 scenario 1 (T032): time to play 300 ms, Permissive
		Prepared prepared = new Prepared(fiveNotes(), settings(PlayTimeWindow.DEFAULT_MILLIS), FOLLOW);
		for (int event = 0; event < 5; event++) {
			final double midi = prepared.midi(event) + (event == 2 ? 0.20 : 0.0);
			prepared.notes.add(new Note(prepared.target(event) + (event == 2 ? 250 * MS : 0L), 150 * MS, seconds -> midi));
		}
		List<FollowSession.EventResult> results = prepared.run();
		assertEquals(Status.MATCHED, results.get(2).status());
		assertEquals(TimingVerdict.LATE, results.get(2).timing());
	}
}
