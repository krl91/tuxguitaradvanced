package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.ui.ArcadeSession;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The floating measure (spec 003 FR-076, the owner, 2026-10-11): the picture holds what comes in the length of one measure, so that it always shows the same
 * sum of beats; a measure is never shown for less than a second.
 */
public class FloatingMeasureTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static SceneFixture notes(long lookAheadNanos) {
		SongFixture song = new SongFixture(3);
		for (int i = 0; i < 8; i++) {
			song.quarter(1 + i / 4, i % 4, 1 + i % 6, 3 + i);
		}
		return new SceneFixture(song, SceneFixture.sixStrings(), 100, CameraTrack.Settings.standard().withLookAhead(lookAheadNanos));
	}

	@Test
	public void onlyTheEventsOfTheNextMeasureOfTimeAreInThePictureAndNoneBeyond() {
		long measure = 2_000L * MS;
		SceneFixture fixture = notes(measure);
		long now = fixture.onset(0) - 1_900 * MS;
		Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
		for (int index = 0; index < fixture.events.size(); index++) {
			boolean due = fixture.onset(index) - now <= measure;
			assertEquals(due, !scene.forEvent(index).isEmpty(), "event " + index + " is " + (fixture.onset(index) - now) / MS + " ms ahead");
		}
		assertTrue(scene.forEvent(0).size() > 0 && scene.forEvent(7).isEmpty(), "a note of the following measure is not yet there");
	}

	@Test
	public void aNoteEntersAtTheHorizonOneMeasureBeforeItsOnsetAndReachesTheNeckAtTheOnset() {
		long measure = 2_400L * MS;
		SceneFixture fixture = notes(measure);
		IsometricProjection projection = new IsometricProjection(SCREEN);
		Scene entering = fixture.scene(false).build(fixture.onset(0) - measure + 20 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertTrue(entering.forEvent(0, Role.MARKER_FOOTPRINT).get(0).value(1) < projection.horizonY() + 0.05 * (projection.hitLineY() - projection.horizonY()), "at the horizon");
		Scene onset = fixture.scene(false).build(fixture.onset(0), SCREEN, null, 100, QualityTier.FULL);
		assertEquals(SCREEN.height() * IsometricProjection.NECK_NEAR_EDGE, onset.forEvent(0, Role.MARKER_FOOTPRINT).get(0).value(1), 0.5, "at the foot of the neck at the onset");
	}

	@Test
	public void theLengthOfAMeasureComesFromTheTimelineAndIsNeverBelowOneSecond() {
		SceneFixture fixture = notes(5_000L * MS);
		long start = fixture.timeline.targetTicks(0);
		long fourQuarters = 4 * 960L;
		long length = ArcadeSession.measureNanos(fixture.timeline, start, fourQuarters);
		assertEquals(fixture.timeline.nanosAtTick(start + fourQuarters) - fixture.timeline.nanosAtTick(start), length, "a measure of four quarters, at the tempo of the run");
		assertTrue(length >= ArcadeSession.MIN_LOOK_AHEAD_NANOS);
		assertEquals(ArcadeSession.MIN_LOOK_AHEAD_NANOS, ArcadeSession.measureNanos(fixture.timeline, start, 10L), "a very short measure is shown for at least a second");
	}
}
