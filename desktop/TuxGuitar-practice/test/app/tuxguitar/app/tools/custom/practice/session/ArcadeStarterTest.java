package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.MS;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeStarter;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * Preparing an Arcade run (spec 003 T022, T023, T024, FR-002, FR-010, FR-011, FR-014): the learner's track is explicit, it alone is silent in the player,
 * everything else plays, the song is not touched, and everything is given back. A fake player and clock: no sound, no thread.
 */
public class ArcadeStarterTest {

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

	/** A song with a guitar (track 1), a bass (track 2), drums (track 3) and a second guitar (track 4). */
	private static SongFixture band() {
		SongFixture fixture = new SongFixture(2);
		for (int i = 0; i < 8; i++) {
			fixture.quarter(i / 4, i % 4, 1, i % 5);
		}
		TGTrack bass = fixture.addTrack();
		bass.setStrings(fixture.manager.createDefaultInstrumentStrings(4));
		for (int i = 0; i < 4; i++) {
			fixture.note(bass, i / 4, i % 4, 0, 3, i, TGDuration.QUARTER);
		}
		TGTrack drums = fixture.addTrack();
		fixture.channelOf(drums).setBank(TGChannel.DEFAULT_PERCUSSION_BANK);
		fixture.note(drums, 0, 0, 0, 1, 36, TGDuration.QUARTER);
		TGTrack rhythm = fixture.addTrack();
		fixture.note(rhythm, 0, 0, 0, 2, 3, TGDuration.QUARTER);
		return fixture;
	}

	private static FollowStarter.Plan prepare(SongFixture fixture, TGTrack track, Environment environment, int percent) {
		FollowSettings settings = new FollowSettings();
		settings.setMode(FollowSettings.Mode.FOLLOW);
		settings.setPercent(percent);
		FollowStarter.Plan plan = ArcadeStarter.prepare(new TGContext(), fixture.song, track, null, 0L, settings, PracticeParameters.of(RecognitionSettings.defaults()), null,
				null, environment, snapshot -> { }, Runnable::run, reason -> { }, null, null);
		environment.timeline.set(plan.timeline());
		return plan;
	}

	private static String digest(SongFixture fixture) {
		StringBuilder digest = new StringBuilder();
		for (int t = 0; t < fixture.song.countTracks(); t++) {
			TGTrack track = fixture.song.getTrack(t);
			digest.append(track.getNumber()).append(':').append(track.isMute()).append(track.isSolo()).append(track.countMeasures()).append(' ');
		}
		return digest.toString();
	}

	@Test
	public void theLearnerMustChooseATrackAndOnlyAPerformableOneIsAccepted() {
		SongFixture fixture = band();
		Environment environment = new Environment();
		FollowSettings settings = new FollowSettings();
		assertThrows(IllegalArgumentException.class, () -> ArcadeStarter.prepare(new TGContext(), fixture.song, null, null, 0L, settings,
				PracticeParameters.of(RecognitionSettings.defaults()), null, null, environment, s -> { }, Runnable::run, r -> { }, null, null), "no track chosen");
		TGTrack drums = fixture.song.getTrack(2);
		assertThrows(IllegalArgumentException.class, () -> prepare(fixture, drums, environment, 100), "percussion cannot be performed");
		TGTrack foreign = new SongFixture(1).track();
		assertThrows(IllegalArgumentException.class, () -> prepare(fixture, foreign, environment, 100), "a track of another song");
	}

	@Test
	public void onlyTheChosenTrackIsSilentInThePlayerAndTheSongIsNotTouched() {
		SongFixture fixture = band();
		String before = digest(fixture);
		Environment environment = new Environment();
		TGTrack bass = fixture.song.getTrack(1);

		FollowStarter.Plan plan = prepare(fixture, bass, environment, 70);
		assertEquals(4, plan.sequence().getTrackTuning().getStringCount(), "the sequence is that of the bass, with its own four strings");
		assertEquals(4, plan.sequence().getEvents().size());
		assertEquals(70, plan.percent());
		assertEquals(before, digest(fixture), "preparing changes nothing");

		assertEquals(FollowPlayback.Result.STARTED, plan.run().start());

		assertTrue(environment.player.isPlayerOnlyMuted(2), "the bass is the learner's: silent in the player");
		assertFalse(environment.player.isPlayerOnlyMuted(1), "the guitar plays");
		assertFalse(environment.player.isPlayerOnlyMuted(3), "the drums play");
		assertFalse(environment.player.isPlayerOnlyMuted(4), "the second guitar plays");
		assertEquals(70, environment.player.tempoPercent());
		assertEquals(before, digest(fixture), "the mute and solo of the song are the song's: the run muted the player only");

		plan.run().stop();

		assertFalse(environment.player.isPlayerOnlyMuted(2));
		assertEquals(100, environment.player.tempoPercent());
		assertEquals(before, digest(fixture));
		assertEquals(0, environment.player.listenerCount());
	}

	@Test
	public void aSongWithOnlyTheChosenTrackKeepsItSilentAndStillCountsIn() {
		SongFixture fixture = new SongFixture(1);
		for (int i = 0; i < 4; i++) {
			fixture.quarter(0, i, 1, i);
		}
		Environment environment = new Environment();

		FollowStarter.Plan plan = prepare(fixture, fixture.track(), environment, 100);
		assertEquals(FollowPlayback.Result.STARTED, plan.run().start());

		assertTrue(environment.player.isPlayerOnlyMuted(1), "the chosen track is not re-enabled to avoid a silent backing (FR-014)");
		assertTrue(environment.player.countDownEnabled(), "the count-in is given");
		assertTrue(environment.player.isCounting());
		plan.run().stop();
		assertFalse(environment.player.isPlayerOnlyMuted(1));
	}

	@Test
	public void theBackingFollowsTheSpeedTheTempoChangesAndTheRepeatsOfTheJudging() {
		SongFixture fixture = band();
		fixture.tempoChange(1, 60);
		fixture.repeat(0, 0, 2);
		Environment environment = new Environment();
		FollowStarter.Plan slow = prepare(fixture, fixture.track(), environment, 50);
		FollowStarter.Plan full = prepare(fixture, fixture.track(), new Environment(), 100);

		assertEquals(50, slow.timeline().percent());
		assertEquals(full.timeline().durationNanos() * 2, slow.timeline().durationNanos(), 2.0, "half the speed, twice the time, for the backing and the judging alike");
		// the player runs the same expanded measures: the repeat plays measure 0 twice, so there are more played measures than measures
		assertTrue(slow.timeline().targetTicks(slow.sequence().getEvents().size() - 1) > 2 * 3840L, "the events of the repeat and of the second measure come after it");
		assertEquals(FollowPlayback.Result.STARTED, slow.run().start());
		assertEquals(50, environment.player.tempoPercent());
		assertNotNull(slow.timeline());
		slow.run().stop();
	}
}
