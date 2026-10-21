package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeStarter;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequenceBuilder;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * Spec 014 (T010, FR-003): Arcade widens the on-time window of the player, so the starter takes the window as an argument. With the window given, the plan
 * uses it; the existing {@code prepare} gives the same plan as before, with the window of the settings; a window outside the range of spec 012 (40 to 200 ms)
 * is refused as it always was. Both {@link FollowStarter} and {@link ArcadeStarter} have the overload: the Arcade path goes through the second.
 */
public class FollowStarterWindowTest {

	private static final class Environment implements FollowStarter.Environment {
		final AtomicReference<FollowTimeline> timeline = new AtomicReference<FollowTimeline>();
		final FakeTempoClock clock = new FakeTempoClock(ticks -> {
			long whole = (long) Math.floor(ticks);
			double part = ticks - whole;
			FollowTimeline built = Environment.this.timeline.get();
			return built.nanosAtTick(whole) * (1.0 - part) + built.nanosAtTick(whole + 1) * part;
		}, FollowStarter.FIRST_TICK, 80_000L);
		final FakeMidiPlayer player = new FakeMidiPlayer(this.clock).withInitialState(100, false, true).countDownNanos(LEAD);
		final RouteCalibrations calibrations = new RouteCalibrations();

		Environment() {
			this.clock.setNow(T0);
		}

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
			return new AudioRoute("input", "output", 48000, "1ch 16bit");
		}

		public RouteCalibrations calibrations() {
			return this.calibrations;
		}

		public InputAssessment assessment() {
			return new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE);
		}
	}

	private static SongFixture song() {
		SongFixture fixture = new SongFixture(2);
		for (int i = 0; i < 8; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 5);
		}
		return fixture;
	}

	private static FollowSettings settings(int windowMillis) {
		FollowSettings settings = new FollowSettings();
		settings.setMode(FollowSettings.Mode.ARCADE);
		settings.setWindowMillis(windowMillis);
		return settings;
	}

	private static FollowStarter.Plan viaFollowStarter(SongFixture fixture, FollowSettings settings, Integer windowMillis) {
		TGTrack track = fixture.song.getTrack(0);
		PracticeSequence sequence = PracticeSequenceBuilder.build(fixture.song, track, TrackTuning.ofAnyStrings(track), null, 0L);
		Environment environment = new Environment();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		FollowStarter.Plan plan = windowMillis == null
				? FollowStarter.prepare(new TGContext(), fixture.song, sequence, track.getNumber(), settings, parameters, null, null, environment, s -> { }, Runnable::run, r -> { }, null, null)
				: FollowStarter.prepare(new TGContext(), fixture.song, sequence, track.getNumber(), settings, windowMillis, parameters, null, null, environment, s -> { }, Runnable::run,
						r -> { }, null, null);
		environment.timeline.set(plan.timeline());
		return plan;
	}

	private static FollowStarter.Plan viaArcadeStarter(SongFixture fixture, FollowSettings settings, Integer windowMillis) {
		TGTrack track = fixture.song.getTrack(0);
		PracticeSequence sequence = PracticeSequenceBuilder.build(fixture.song, track, TrackTuning.ofAnyStrings(track), null, 0L);
		Environment environment = new Environment();
		PracticeParameters parameters = PracticeParameters.of(RecognitionSettings.defaults());
		FollowStarter.Plan plan = windowMillis == null
				? ArcadeStarter.prepareFrom(new TGContext(), fixture.song, track, sequence, settings, parameters, null, null, environment, s -> { }, Runnable::run, r -> { }, null, null)
				: ArcadeStarter.prepareFrom(new TGContext(), fixture.song, track, sequence, settings, windowMillis, parameters, null, null, environment, s -> { }, Runnable::run,
						r -> { }, null, null);
		environment.timeline.set(plan.timeline());
		return plan;
	}

	@Test
	public void theExistingPrepareUsesTheWindowOfTheSettingsAsBefore() {
		assertEquals(100, viaFollowStarter(song(), settings(100), null).windowMillis());
		assertEquals(70, viaFollowStarter(song(), settings(70), null).windowMillis());
		assertEquals(100, viaArcadeStarter(song(), settings(100), null).windowMillis());
		assertEquals(160, viaArcadeStarter(song(), settings(160), null).windowMillis());
	}

	@Test
	public void theWindowGivenIsTheOneOfThePlanWhateverTheSettingsHold() {
		assertEquals(125, viaFollowStarter(song(), settings(100), 125).windowMillis());
		assertEquals(200, viaFollowStarter(song(), settings(100), 200).windowMillis());
		assertEquals(150, viaArcadeStarter(song(), settings(100), 150).windowMillis());
		assertEquals(40, viaArcadeStarter(song(), settings(100), 40).windowMillis());
	}

	@Test
	public void theWindowGivenAndTheOneOfTheSettingsGiveTheSamePlanWhenTheyAreEqual() {
		FollowStarter.Plan existing = viaArcadeStarter(song(), settings(100), null);
		FollowStarter.Plan given = viaArcadeStarter(song(), settings(100), 100);
		assertEquals(existing.windowMillis(), given.windowMillis());
		assertEquals(existing.percent(), given.percent());
		assertEquals(existing.sequence().getEvents().size(), given.sequence().getEvents().size());
		assertEquals(existing.startTick(), given.startTick());
	}

	@Test
	public void aWindowOutsideTheRangeOfSpecTwelveIsRefusedAsAlways() {
		assertThrows(IllegalArgumentException.class, () -> viaFollowStarter(song(), settings(100), 39));
		assertThrows(IllegalArgumentException.class, () -> viaFollowStarter(song(), settings(100), 201));
		assertThrows(IllegalArgumentException.class, () -> viaArcadeStarter(song(), settings(100), 300));
	}
}
