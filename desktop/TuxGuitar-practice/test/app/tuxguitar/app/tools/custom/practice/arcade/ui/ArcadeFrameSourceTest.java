package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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
}
