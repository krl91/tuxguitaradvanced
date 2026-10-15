package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowHarness.Note;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.State;
import app.tuxguitar.app.tools.custom.practice.session.FollowSession.Status;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * What the learner chose in the window reaches the session (spec 002 T098): the percentage is the timeline and the tempo of the player, the
 * tolerance the windows, the calibration of the route the compensation of the latency, the start the first event judged, and the clocks the gate of
 * the verdicts. The environment is a fake: no audio engine, no player, no thread.
 */
public class FollowStarterTest {

	private static final AudioRoute ROUTE = new AudioRoute("microphone", "speakers", 48000, "1ch 16bit");

	/** The application as a run sees it. */
	private static final class FakeEnvironment implements FollowStarter.Environment {
		FakeMidiPlayer player;
		boolean captureReady = true;
		double lagSpread = 5.0;
		AudioRoute route = ROUTE;
		final RouteCalibrations calibrations = new RouteCalibrations();

		public FollowPlayer player() {
			return this.player;
		}

		public long wallNanosOf(AnalysisFrame frame) {
			return T0 + frame.getTimestampNanos();
		}

		public boolean captureReady() {
			return this.captureReady;
		}

		public double lagSpreadMillis() {
			return this.lagSpread;
		}

		public AudioRoute route() {
			return this.route;
		}

		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		public InputAssessment assessment() {
			return new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		}
	}

	private static SongFixture fiveNotes() {
		SongFixture fixture = new SongFixture(2);
		for (int beat = 0; beat < 4; beat++) {
			fixture.quarter(0, beat, 1, beat);
		}
		fixture.quarter(1, 0, 1, 4);
		return fixture;
	}

	/** One prepared run on a fake player whose clock follows the timeline the starter built. */
	private static final class Prepared {
		final SongFixture fixture;
		final FakeEnvironment environment = new FakeEnvironment();
		final FollowStarter.Plan plan;
		final FakeTempoClock clock;
		final FakeMidiPlayer player;
		final List<Note> notes = new ArrayList<Note>();
		final Set<Note> flagged = new HashSet<Note>();
		final List<FollowSession.Snapshot> snapshots = new ArrayList<FollowSession.Snapshot>();
		FollowSession.Snapshot last;
		long now = T0;

		Prepared(SongFixture fixture, FollowSettings settings, int cursorMeasure) {
			this.fixture = fixture;
			final FollowTimeline[] built = new FollowTimeline[1];
			this.clock = new FakeTempoClock(ticks -> {
				long whole = (long) Math.floor(ticks);
				double part = ticks - whole;
				return built[0].nanosAtTick(whole) * (1.0 - part) + built[0].nanosAtTick(whole + 1) * part;
			}, FollowStarter.FIRST_TICK, 40_000L);
			this.clock.setNow(T0);
			this.player = new FakeMidiPlayer(this.clock).withInitialState(100, false, true).countDownNanos(LEAD);
			this.environment.player = this.player;
			// the sequence as the window builds it, with the start resolved from the cursor
			PracticeSequence chosen = (cursorMeasure < 0 ? fixture.sequence()
					: PracticeSequenceBuilder.build(fixture.song, fixture.track(), TrackTuning.of(fixture.track(), null),
							fixture.track().getMeasure(cursorMeasure), fixture.track().getMeasure(cursorMeasure).getStart()));
			this.plan = FollowStarter.prepare(new TGContext(), fixture.song, chosen, 1, settings, PracticeParameters.of(RecognitionSettings.defaults()), null,
					null, this.environment, this.snapshots::add, Runnable::run, reason -> { }, null, null);
			built[0] = this.plan.timeline();
		}

		long target(int event) {
			return T0 + LEAD + this.plan.timeline().targetNanos(event);
		}

		void play(long wall, int midi) {
			this.notes.add(new Note(wall, 600 * MS, seconds -> midi));
		}

		void start() {
			this.plan.session().start();
			assertEquals(FollowPlayback.Result.STARTED, this.plan.run().start());
		}

		void runAll() {
			long end = T0 + LEAD + this.plan.timeline().durationNanos() + 1_500 * MS;
			while (this.now < end) {
				if (this.now > this.clock.nowNanos()) {
					this.player.advance(this.now - this.clock.nowNanos());
				}
				this.last = this.plan.session().onFrame(FollowHarness.frameOf(this.notes, this.flagged, this.now));
				this.now += HOP;
			}
		}
	}

	/** The window is given in milliseconds; 75 is the old Normal, which these tests were written with, so that their figures do not move (spec 012). */
	private static FollowSettings settings(int percent, int windowMillis) {
		FollowSettings settings = new FollowSettings();
		settings.setMode(FollowSettings.Mode.FOLLOW);
		settings.setPercent(percent);
		settings.setWindowMillis(windowMillis);
		return settings;
	}

	@Test
	public void thePercentageIsTheTimelineAndTheTempoOfThePlayer() {
		Prepared slow = new Prepared(fiveNotes(), settings(70, 75), -1);
		Prepared full = new Prepared(fiveNotes(), settings(100, 75), -1);

		assertEquals(70, slow.plan.percent());
		assertEquals(70, slow.plan.timeline().percent());
		assertEquals(100, full.plan.timeline().percent());
		// the same score at 70 % lasts 100/70 as long
		assertEquals(full.plan.timeline().durationNanos() * 100.0 / 70.0, slow.plan.timeline().durationNanos(), 2.0);
		assertEquals(100, slow.player.tempoPercent(), "nothing is changed in the player before the run starts");
		assertTrue(slow.player.calls().isEmpty());

		slow.start();
		assertEquals(70, slow.player.tempoPercent(), "the player plays at the percentage the timeline was built for");
		assertEquals(FollowStarter.FIRST_TICK, slow.player.tickPosition());
		slow.plan.run().stop();
		assertEquals(100, slow.player.tempoPercent());
	}

	@Test
	public void thePercentageIsKeptWithinItsBounds() {
		FollowSettings settings = new FollowSettings();
		assertEquals(FollowSettings.MIN_PERCENT, settings.setPercent(1));
		assertEquals(FollowSettings.MAX_PERCENT, settings.setPercent(900));
		assertEquals(100, settings.setPercent(100));
	}

	@Test
	public void theWindowTheLearnerChoseIsTheOneOfTheJudgement() {
		// a note 100 ms late on a quarter note of 500 ms: late for a window of 75 ms each side, on time for one of 125 ms
		int[] widths = { 75, 125 };
		TimingVerdict[] verdicts = new TimingVerdict[2];
		for (int i = 0; i < 2; i++) {
			Prepared run = new Prepared(fiveNotes(), settings(100, widths[i]), -1);
			assertEquals(widths[i], run.plan.windowMillis());
			run.environment.calibrations.set(ROUTE, LatencyCompensation.calibrated(0.0));
			run.start();
			for (int e = 0; e < 5; e++) {
				run.play(run.target(e) + (e == 1 ? 100 * MS : 0), run.plan.sequence().getEvents().get(e).getExpectedMidi());
			}
			run.runAll();
			verdicts[i] = run.last.results().get(1).timing();
		}
		assertEquals(TimingVerdict.LATE, verdicts[0]);
		assertEquals(TimingVerdict.ON_TIME, verdicts[1]);
	}

	@Test
	public void theCalibrationOfTheRouteIsTheLatencyCompensationAndItsAbsenceIsSaid() {
		// the route delays everything by 120 ms: the notes are heard 120 ms after they were played
		FollowSession.Snapshot calibrated = null;
		FollowSession.Snapshot uncalibrated = null;
		for (int pass = 0; pass < 2; pass++) {
			Prepared run = new Prepared(fiveNotes(), settings(100, 75), -1);
			if (pass == 0) {
				run.environment.calibrations.set(ROUTE, LatencyCompensation.calibrated(120.0));
			}
			run.start();
			for (int e = 0; e < 5; e++) {
				run.play(run.target(e) + 120 * MS, run.plan.sequence().getEvents().get(e).getExpectedMidi());
			}
			run.runAll();
			if (pass == 0) {
				calibrated = run.last;
			} else {
				uncalibrated = run.last;
			}
		}
		assertEquals(5, calibrated.counts().onTime());
		assertEquals(TimingContext.RhythmPrecision.NORMAL, calibrated.precision());
		assertTrue(calibrated.precisionReasons().isEmpty());
		assertEquals(0, uncalibrated.counts().onTime(), "without the compensation the same notes are judged late");
		assertEquals(TimingContext.RhythmPrecision.REDUCED, uncalibrated.precision());
		assertTrue(uncalibrated.precisionReasons().contains(TimingContext.PrecisionReason.NOT_CALIBRATED));
	}

	@Test
	public void aCalibrationOfAnotherRouteIsNotUsedAndAnUnstableRouteIsSaid() {
		Prepared run = new Prepared(fiveNotes(), settings(100, 75), -1);
		run.environment.calibrations.set(new AudioRoute("USB interface", "speakers", 48000, "1ch 16bit"), LatencyCompensation.calibrated(30.0));
		run.environment.lagSpread = 150.0;
		run.start();
		run.runAll();

		assertTrue(run.last.precisionReasons().contains(TimingContext.PrecisionReason.NOT_CALIBRATED), "the calibration of the USB route is not this route's");
		assertTrue(run.last.precisionReasons().contains(TimingContext.PrecisionReason.UNSTABLE_ROUTE));
		assertEquals(TimingContext.RhythmPrecision.REDUCED, run.last.precision(), "reduced, never blocked: the run went on and ended");
		assertEquals(State.COMPLETE, run.last.state());
	}

	@Test
	public void theRunJudgesTheEventsFromTheStartTheLearnerChoseAndStartsThePlayerThere() {
		SongFixture fixture = fiveNotes();
		Prepared run = new Prepared(fixture, settings(100, 75), 1);

		assertEquals(1, run.plan.sequence().getEvents().size(), "only the last note is left");
		assertEquals(FollowStarter.FIRST_TICK + 3840L, run.plan.startTick(), "the player starts at the beginning of the measure of that note");
		run.start();
		assertEquals(FollowStarter.FIRST_TICK + 3840L, run.player.tickPosition());
	}

	@Test
	public void noVerdictBeforeTheCaptureClockIsReady() {
		Prepared run = new Prepared(fiveNotes(), settings(100, 75), -1);
		run.environment.captureReady = false;
		run.start();
		for (int e = 0; e < 5; e++) {
			run.play(run.target(e), run.plan.sequence().getEvents().get(e).getExpectedMidi());
		}
		run.runAll();

		assertEquals(0, run.last.counts().onTime());
		assertEquals(0, run.last.counts().missed());
		assertEquals(0, run.last.counts().extra());
		assertEquals(TimingContext.RhythmPrecision.NOT_READY, run.last.precision());
	}
}
