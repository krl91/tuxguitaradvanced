package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeScoring;
import app.tuxguitar.app.tools.custom.practice.session.FlameSetting;
import app.tuxguitar.app.tools.custom.practice.arcade.view.IsometricProjection;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeRenderEvent;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ArcadeScene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.NeckModel;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityController;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.StringColours;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;
import app.tuxguitar.app.tools.custom.practice.session.FollowTimeline;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;

/**
 * The frame is built from the player's clock at the moment of the paint (spec 003 T041, FR-055): the same instant always gives the same picture, whatever
 * frames were painted, skipped or stalled before it, and a pause, a seek or a clock that is not ready are followed at once.
 */
public class ArcadeFrameSourceTest {

	private static final long FIRST_TICK = 960L;
	private static final long MS = 1_000_000L;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private final SongFixture song = new SongFixture(2);
	private final FollowTimeline timeline;
	private final List<ArcadeRenderEvent> events = new ArrayList<ArcadeRenderEvent>();
	private final ArcadeScene scene;
	private final FakeTempoClock clock;
	private final QualityController quality = new QualityController(ArcadeCanvas.FRAME_NANOS);
	private final ArcadeFrameSource source;

	public ArcadeFrameSourceTest() {
		for (int i = 0; i < 8; i++) {
			song.quarter(i / 4, i % 4, 1 + i % 6, i * 2 + 1);
		}
		song.tempoChange(1, 150);
		this.timeline = FollowTimeline.build(song.song, song.sequence(), 100);
		List<PracticeEvent> practice = song.sequence().getEvents();
		for (int i = 0; i < practice.size(); i++) {
			events.add(ArcadeRenderEvent.of(i, practice.get(i), timeline));
		}
		TrackTuning tuning = new TrackTuning(new int[] { 64, 59, 55, 50, 45, 40 }, 0, null);
		NeckModel neck = NeckModel.of(tuning, 24);
		this.scene = new ArcadeScene(events, neck, StringColours.forStrings(6), CameraTrack.build(events, neck, CameraTrack.Settings.standard()),
				SceneFixture.ENGLISH, false);
		this.clock = new FakeTempoClock(ticks -> timeline.nanosAtTick((long) ticks), FIRST_TICK, 100_000L);
		this.source = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, () -> null, () -> 100, quality);
	}

	private static String describe(Scene scene) {
		return scene.items().toString();
	}

	@Test
	public void theInstantOnTheTimelineIsTheTickOfThePlayerTurnedIntoItsNanoseconds() {
		clock.start(FIRST_TICK);
		clock.advance(1234 * MS);
		assertEquals(1234 * MS, source.timelineNanosAt(clock.nowNanos()), 2 * MS);
		clock.advance(3000 * MS);
		assertEquals(4234 * MS, source.timelineNanosAt(clock.nowNanos()), 2 * MS, "across the change of tempo too");
	}

	@Test
	public void theMarkerOfAnEventIsOnTheHitLineWhenThePlayerReachesItsTick() {
		clock.start(FIRST_TICK);
		clock.setNow(0);
		for (int i = 0; i < events.size(); i++) {
			long wall = clock.wallNanosAt(FIRST_TICK + timeline.targetTicks(i));
			clock.setNow(wall);
			Scene picture = source.frame(wall, SCREEN);
			SceneItem marker = picture.forEvent(i, Role.MARKER_FOOTPRINT).get(0);
			assertEquals(SCREEN.height() * IsometricProjection.NECK_NEAR_EDGE, marker.value(1), 0.5, "event " + i);
		}
	}

	@Test
	public void theSamePlayerPositionGivesTheSamePictureWhateverWasPaintedBefore() {
		clock.start(FIRST_TICK);
		clock.advance(2000 * MS);
		String direct = describe(source.frame(clock.nowNanos(), SCREEN));

		ArcadeFrameSource other = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, () -> null, () -> 100, new QualityController(ArcadeCanvas.FRAME_NANOS));
		for (int i = 0; i < 20; i++) {
			other.frame(clock.nowNanos() - (20 - i) * 37 * MS, SCREEN);
			other.painted(40 * MS);
		}
		assertEquals(direct, describe(source.frame(clock.nowNanos(), SCREEN)), "the source keeps nothing between frames");
		assertEquals(QualityTier.PERFORMANCE, other.tier(), "the slow frames only lowered the quality");
	}

	@Test
	public void aPauseFreezesThePictureASeekMovesItAtOnceAndAClockNotReadyShowsNoEvent() {
		clock.start(FIRST_TICK);
		clock.advance(1500 * MS);
		clock.pause();
		String frozen = describe(source.frame(clock.nowNanos(), SCREEN));
		clock.advance(5000 * MS);
		assertEquals(frozen, describe(source.frame(clock.nowNanos(), SCREEN)), "paused: the same picture");
		clock.resume();
		clock.advance(300 * MS);
		assertFalse(frozen.equals(describe(source.frame(clock.nowNanos(), SCREEN))));

		clock.seek(FIRST_TICK + timeline.targetTicks(5));
		Scene afterSeek = source.frame(clock.nowNanos(), SCREEN);
		assertEquals(SCREEN.height() * IsometricProjection.NECK_NEAR_EDGE, afterSeek.forEvent(5, Role.MARKER_FOOTPRINT).get(0).value(1), 0.5, "the seek is followed immediately");

		clock.setReady(false);
		assertEquals(ArcadeFrameSource.NOT_READY_NANOS, source.timelineNanosAt(clock.nowNanos()));
		Scene waiting = source.frame(clock.nowNanos(), SCREEN);
		assertTrue(waiting.withRole(Role.MARKER).isEmpty() && waiting.withRole(Role.TIMING_BAR).isEmpty(), "no event before the clock can date the player");
		assertFalse(waiting.withRole(Role.STRING).isEmpty(), "but the neck is there");
		clock.setReady(true);
		clock.stopClock();
		assertEquals(ArcadeFrameSource.NOT_READY_NANOS, source.timelineNanosAt(clock.nowNanos()), "a stopped player says nothing");
	}

	@Test
	public void duringTheCountInTheNotesComeFromTheFarEndWhileTheClockHoldsStillAndTheClockTakesOverWhenTheMusicBegins() {
		clock.start(FIRST_TICK);
		clock.pause();
		ArcadeFrameSource counting = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, () -> null, () -> 100, quality, 2000 * MS, 0L);
		long wall = clock.nowNanos();
		Scene first = counting.frame(wall, SCREEN);
		assertEquals(describe(scene.build(-2000 * MS, SCREEN, null, 100, quality.tier())), describe(first), "at the first click the music is a whole count-in away");
		assertTrue(first.forEvent(0, Role.MARKER).size() <= 1);
		clock.advance(1000 * MS);
		Scene half = counting.frame(clock.nowNanos(), SCREEN);
		assertEquals(describe(scene.build(-1000 * MS, SCREEN, null, 100, quality.tier())), describe(half), "half way through the count-in, though the clock did not move");
		clock.advance(1000 * MS);
		clock.resume();
		clock.advance(500 * MS);
		Scene playing = counting.frame(clock.nowNanos(), SCREEN);
		assertEquals(describe(scene.build(counting.timelineNanosAt(clock.nowNanos()), SCREEN, null, 100, quality.tier())), describe(playing), "then the clock of the player");
	}

	@Test
	public void aSecondRunDoesNotBeginWithTheClockStillAtTheEndOfTheFirst() {
		// the player still holds the end of the song of the run before: the picture must not follow it
		clock.start(FIRST_TICK + 3000);
		clock.pause();
		ArcadeFrameSource counting = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, () -> null, () -> 100, quality, 2000 * MS, 0L);
		assertEquals(describe(scene.build(-2000 * MS, SCREEN, null, 100, quality.tier())), describe(counting.frame(clock.nowNanos(), SCREEN)), "the music is a count-in away");
		clock.advance(1500 * MS);
		assertEquals(describe(scene.build(-500 * MS, SCREEN, null, 100, quality.tier())), describe(counting.frame(clock.nowNanos(), SCREEN)), "still on its own time");
		clock.seek(FIRST_TICK);
		clock.advance(500 * MS);
		clock.resume();
		clock.advance(300 * MS);
		Scene playing = counting.frame(clock.nowNanos(), SCREEN);
		assertEquals(describe(scene.build(counting.timelineNanosAt(clock.nowNanos()), SCREEN, null, 100, quality.tier())), describe(playing), "the clock of the player once it is at the start");
	}

	@Test
	public void theFirstEventComesInOneMoveThroughTheHandoverFromTheCountInToTheClockOfThePlayer() {
		clock.start(FIRST_TICK);
		clock.pause();
		ArcadeFrameSource counting = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, () -> null, () -> 100, quality, 2000 * MS, 0L);
		double previous = Double.NaN;
		double worst = 0.0;
		for (int step = 0; step <= 560; step++) {
			// the music begins when the count-in is over: the clock of the player holds the start until then
			if (step == 400) {
				clock.resume();
			}
			Scene scene = counting.frame(clock.nowNanos(), SCREEN);
			List<SceneItem> markers = scene.forEvent(0, Role.MARKER);
			if (!markers.isEmpty()) {
				double y = markers.get(0).value(1);
				if (!Double.isNaN(previous)) {
					worst = Math.max(worst, Math.abs(y - previous));
				}
				previous = y;
			}
			clock.advance(5 * MS);
		}
		assertTrue(worst < 12.0, "the first marker moved " + worst + " px between two frames of 5 ms");
	}

	@Test
	public void aPauseHoldsThePictureStillAndAResumeCountsInAgainFromWhereTheMusicWasWhateverTheMomentOfThePause() {
		// paused during the first count-in: the picture does not go on by itself, and the run counts in again from the start
		clock.start(FIRST_TICK);
		clock.pause();
		ArcadeFrameSource counting = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, () -> null, () -> 100, quality, 2000 * MS, 0L);
		counting.frame(clock.nowNanos(), SCREEN);
		clock.advance(1000 * MS);
		counting.setPaused(true, clock.nowNanos());
		String frozen = describe(scene.build(-1000 * MS, SCREEN, null, 100, quality.tier()));
		assertEquals(frozen, describe(counting.frame(clock.nowNanos(), SCREEN)), "paused: the picture holds");
		clock.advance(3000 * MS);
		assertEquals(frozen, describe(counting.frame(clock.nowNanos(), SCREEN)), "paused for a long time: still the same picture");
		counting.setPaused(false, clock.nowNanos());
		assertEquals(describe(scene.build(-2000 * MS, SCREEN, null, 100, quality.tier())), describe(counting.frame(clock.nowNanos(), SCREEN)), "resumed: a whole count-in before the music");
		clock.advance(1000 * MS);
		assertEquals(describe(scene.build(-1000 * MS, SCREEN, null, 100, quality.tier())), describe(counting.frame(clock.nowNanos(), SCREEN)), "and it goes on with the clicks");
		clock.advance(1000 * MS);
		clock.resume();
		clock.advance(300 * MS);
		Scene playing = counting.frame(clock.nowNanos(), SCREEN);
		assertEquals(describe(scene.build(counting.timelineNanosAt(clock.nowNanos()), SCREEN, null, 100, quality.tier())), describe(playing), "then the clock of the player");

		// paused in the music: the picture holds, and a resume counts in again from where the music was
		counting.setPaused(true, clock.nowNanos());
		long at = counting.timelineNanosAt(clock.nowNanos());
		clock.pause();
		String held = describe(scene.build(at, SCREEN, null, 100, quality.tier()));
		clock.advance(5000 * MS);
		assertEquals(held, describe(counting.frame(clock.nowNanos(), SCREEN)), "paused in the music: the picture holds");
		counting.setPaused(false, clock.nowNanos());
		assertEquals(describe(scene.build(at - 2000 * MS, SCREEN, null, 100, quality.tier())), describe(counting.frame(clock.nowNanos(), SCREEN)),
				"resumed: a count-in before the place where the music was");
	}

	// ---- the stage flames (spec 009 FR-004, FR-010, R11) ----

	private static final long WALL_STEP = 50 * MS;

	/** The run as the picture sees it: a score and a count of judged events, changed by the test. */
	private static final class Play implements java.util.function.Supplier<ArcadeRun.View> {
		int judged = 10;
		double share = 1.0;

		@Override
		public ArcadeRun.View get() {
			return new ArcadeRun.View(new ArrayList<ArcadeRun.EventState>(), (int) Math.round(this.share * ArcadeScoring.standard().maximum(this.judged)), 0, 0, 0, this.judged);
		}
	}

	private ArcadeFrameSource flaming(Play play) {
		return new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, play, () -> 100, quality, 2000 * MS, 0L, FlameSetting.FULL, true, ArcadeScoring.standard());
	}

	/** The level the picture shows: the height of its tallest tongue against the tallest possible one. */
	private static double levelOf(Scene scene) {
		java.util.Map<Integer, double[]> pieces = new java.util.HashMap<Integer, double[]>();
		for (SceneItem strip : scene.withRole(Role.FLAME_TONGUE)) {
			double[] p = pieces.computeIfAbsent(strip.tgString(), k -> new double[] { Double.MAX_VALUE, -Double.MAX_VALUE });
			for (int i = 0; i < 4; i++) {
				p[0] = Math.min(p[0], strip.value(2 * i + 1));
				p[1] = Math.max(p[1], strip.value(2 * i + 1));
			}
		}
		double tallest = 0.0;
		for (double[] p : pieces.values()) {
			tallest = Math.max(tallest, p[1] - p[0]);
		}
		return tallest / (0.55 * SCREEN.height());
	}

	private static String flamesOf(Scene scene) {
		StringBuilder text = new StringBuilder();
		for (SceneItem item : scene.items()) {
			if (item.role() == Role.FLAME_TONGUE || item.role() == Role.FLAME_EMBER) {
				text.append(item).append('\n');
			}
		}
		return text.toString();
	}

	@Test
	public void theFlameClockStandsStillInAPauseAndTwoPicturesInAPauseHaveTheSameFlames() {
		clock.start(FIRST_TICK);
		clock.pause();
		ArcadeFrameSource counting = flaming(new Play());
		for (int i = 0; i < 6; i++) {
			counting.frame(clock.nowNanos(), SCREEN);
			clock.advance(WALL_STEP);
		}
		long before = counting.flameClockNanos();
		assertTrue(before > 0L, "the clock went on with the pictures");
		counting.setPaused(true, clock.nowNanos());
		String held = flamesOf(counting.frame(clock.nowNanos(), SCREEN));
		clock.advance(7000 * MS);
		assertEquals(held, flamesOf(counting.frame(clock.nowNanos(), SCREEN)), "paused: the same flames, tongues and sway");
		assertEquals(counting.flameClockNanos(), counting.flameClockNanos());
		assertEquals(before, counting.flameClockNanos(), WALL_STEP + 1, "the flame clock did not move in the pause");
	}

	@Test
	public void afterAResumeTheLevelStaysHeldThroughTheCountInWhileTheTonguesKeepMovingThenGoesOnFromTheSameValue() {
		clock.start(FIRST_TICK);
		clock.pause();
		Play play = new Play();
		ArcadeFrameSource counting = flaming(play);
		for (int i = 0; i < 24; i++) {
			counting.frame(clock.nowNanos(), SCREEN);
			clock.advance(WALL_STEP);
		}
		double full = levelOf(counting.frame(clock.nowNanos(), SCREEN));
		assertEquals(1.0, full, 1e-6, "the play has been perfect for a while: full flames");

		counting.setPaused(true, clock.nowNanos());
		play.share = 0.5; // the share falls to its lowest while the run is paused and counting in: the level must not follow it
		clock.advance(5000 * MS);
		assertEquals(full, levelOf(counting.frame(clock.nowNanos(), SCREEN)), 1e-9, "held in the pause");

		counting.setPaused(false, clock.nowNanos());
		long clockAtResume = counting.flameClockNanos();
		String previous = null;
		boolean moved = false;
		for (int i = 0; i < 30; i++) {
			clock.advance(WALL_STEP);
			Scene picture = counting.frame(clock.nowNanos(), SCREEN);
			assertEquals(full, levelOf(picture), 1e-9, "held through the count-in, picture " + i);
			String flames = flamesOf(picture);
			moved |= previous != null && !previous.equals(flames);
			previous = flames;
		}
		assertTrue(moved, "the tongues go on moving during the count-in");
		assertTrue(counting.flameClockNanos() > clockAtResume, "the flame clock goes on during the count-in");

		// the music begins: the clock of the player moves and the picture goes back to it; the level is released and goes on from where it was
		clock.advance(500 * MS);
		clock.resume();
		clock.advance(300 * MS);
		double first = levelOf(counting.frame(clock.nowNanos(), SCREEN));
		assertEquals(full, first, 0.02, "no jump at the end of the count-in");
		double after = first;
		for (int i = 0; i < 60; i++) {
			clock.advance(WALL_STEP);
			double now = levelOf(counting.frame(clock.nowNanos(), SCREEN));
			assertTrue(now <= after + 1e-9 && after - now <= 0.05 / 3.0 + 1e-6, "it falls by small steps: " + after + " to " + now);
			after = now;
		}
		assertTrue(after < full - 0.5, "and it follows its target now: " + after);
	}

	@Test
	public void theFlameClockNeverGoesBackAlthoughThePictureInstantDoesAtAResume() {
		clock.start(FIRST_TICK);
		clock.pause();
		ArcadeFrameSource counting = flaming(new Play());
		long last = -1L;
		for (int i = 0; i < 20; i++) {
			counting.frame(clock.nowNanos(), SCREEN);
			assertTrue(counting.flameClockNanos() >= last);
			last = counting.flameClockNanos();
			clock.advance(WALL_STEP);
		}
		counting.setPaused(true, clock.nowNanos());
		assertEquals(last, counting.flameClockNanos(), WALL_STEP + 1);
		last = counting.flameClockNanos();
		clock.advance(3000 * MS);
		counting.setPaused(false, clock.nowNanos());
		for (int i = 0; i < 60; i++) {
			clock.advance(WALL_STEP);
			counting.frame(clock.nowNanos(), SCREEN);
			assertTrue(counting.flameClockNanos() >= last, "never back: the picture instant went back by a count-in, the flame clock did not");
			last = counting.flameClockNanos();
		}
	}

	@Test
	public void aSourceWithoutFlamesPaintsNoneWhateverTheRun() {
		clock.start(FIRST_TICK);
		clock.pause();
		Play play = new Play();
		ArcadeFrameSource plain = new ArcadeFrameSource(scene, timeline, FIRST_TICK, clock, play, () -> 100, quality, 2000 * MS, 0L);
		for (int i = 0; i < 30; i++) {
			Scene picture = plain.frame(clock.nowNanos(), SCREEN);
			assertTrue(picture.withRole(Role.FLAME_TONGUE).isEmpty() && picture.withRole(Role.FLAME_EMBER).isEmpty());
			clock.advance(WALL_STEP);
		}
	}
}
