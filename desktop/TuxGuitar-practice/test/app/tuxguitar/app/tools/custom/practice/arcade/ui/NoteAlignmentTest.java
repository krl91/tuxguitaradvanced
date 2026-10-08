package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.IsometricProjection;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.ScreenPoint;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The incoming notes follow their lanes (spec 003 FR-066, SC-024), checked on the primitives that the renderers really send to the painter: the marker is a
 * circle drawn on the straight segment from the horizon to the hit line that was drawn for it, inside the cell of its fret, at every progress; the segment ends
 * on the hit line at the x of the target disk on the neck, and the link down to the disk is another segment. Every fret from the open string to the last,
 * a hundred and one progress values on each way, a moving camera, three window sizes, two densities, every tier, the selected path and the fallback.
 */
public class NoteAlignmentTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport[] SIZES = { new Viewport(640, 360), new Viewport(1280, 720), new Viewport(2560, 1440) };
	private static final double TOLERANCE = 0.5;

	private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length2 = dx * dx + dy * dy;
		double t = length2 == 0 ? 0 : Math.max(0.0, Math.min(1.0, ((px - x1) * dx + (py - y1) * dy) / length2));
		return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
	}

	private static PaintRecorder.Segment segmentLike(PaintRecorder recorder, SceneItem item) {
		for (PaintRecorder.Segment segment : recorder.segments) {
			if (Math.abs(segment.x1 - item.value(0)) < 0.01 && Math.abs(segment.y1 - item.value(1)) < 0.01 && Math.abs(segment.x2 - item.value(2)) < 0.01
					&& Math.abs(segment.y2 - item.value(3)) < 0.01) {
				return segment;
			}
		}
		return null;
	}

	/** Checks one frame of the first event of the fixture, an incoming note that is the active one. */
	private static void checkFrame(SceneFixture fixture, long now, double progress, Viewport viewport, double uiScale, QualityTier tier, PainterRenderer renderer, String where) {
		int fret = fixture.events.get(0).positions().get(0).fret();
		Scene scene = fixture.scene(false).withUiScale(uiScale).build(now, viewport, null, 100, tier);
		PaintRecorder recorder = PaintRecorder.record(renderer, scene, tier);
		IsometricProjection projection = new IsometricProjection(viewport);
		app.tuxguitar.app.tools.custom.practice.arcade.view.Camera camera = fixture.camera.at(now);
		double x = fixture.neck.fretCentreX(fret);

		// the segment the application means: horizon to hit line in the lane of the cell; it must have been drawn
		ScreenPoint far = projection.approach(x, 0.0, camera);
		ScreenPoint landing = projection.approach(x, 1.0, camera);
		List<SceneItem> trajectories = scene.forEvent(0, Role.TRAJECTORY);
		assertEquals(1, trajectories.size(), where + ": one trajectory");
		PaintRecorder.Segment drawn = segmentLike(recorder, trajectories.get(0));
		assertNotNull(drawn, where + ": the trajectory was sent to the painter");
		assertEquals(far.x(), drawn.x1, 0.01, where);
		assertEquals(far.y(), drawn.y1, 0.01, where);
		assertEquals(landing.x(), drawn.x2, 0.01, where + ": it ends on the hit line in the lane of its cell");
		assertEquals(projection.hitLineY(), drawn.y2, 0.01, where + ": at the hit line");

		// the marker really drawn: its centre is on that segment, in its cell
		SceneItem markerItem = scene.forEvent(0, Role.MARKER).get(0);
		PaintRecorder.Circle marker = null;
		for (PaintRecorder.Circle circle : recorder.circles) {
			if (circle.style == app.tuxguitar.ui.resource.UIPainter.PATH_FILL && Math.abs(circle.cx - markerItem.value(0)) < 0.01 && Math.abs(circle.cy - markerItem.value(1)) < 0.01
					&& Math.abs(circle.diameter - 2 * markerItem.value(2)) < 0.01) {
				marker = circle;
			}
		}
		assertNotNull(marker, where + ": the marker was sent to the painter");
		assertTrue(distanceToSegment(marker.cx, marker.cy, drawn.x1, drawn.y1, drawn.x2, drawn.y2) <= TOLERANCE,
				where + ": the marker leaves its trajectory by " + distanceToSegment(marker.cx, marker.cy, drawn.x1, drawn.y1, drawn.x2, drawn.y2));
		ScreenPoint at = projection.approach(x, progress, camera);
		assertEquals(at.x(), marker.cx, TOLERANCE, where + ": the marker is at the projected centre of its cell");
		assertEquals(at.y(), marker.cy, TOLERANCE, where);
		double left = projection.approach(fixture.neck.cellLeftX(fret), progress, camera).x();
		double right = projection.approach(fixture.neck.cellRightX(fret), progress, camera).x();
		assertTrue(marker.cx >= Math.min(left, right) - 1e-9 && marker.cx <= Math.max(left, right) + 1e-9, where + ": the marker is outside its cell [" + left + ", " + right + "]: " + marker.cx);
		assertEquals((left + right) / 2.0, marker.cx, TOLERANCE, where + ": at the middle of the cell");

		// the target disk on the neck is under the landing point, and joined to it by another segment
		SceneItem diskItem = scene.forEvent(0, Role.TARGET_DISK).get(0);
		PaintRecorder.Circle disk = null;
		for (PaintRecorder.Circle circle : recorder.circles) {
			if (circle.style == app.tuxguitar.ui.resource.UIPainter.PATH_FILL && Math.abs(circle.cx - diskItem.value(0)) < 0.01 && Math.abs(circle.cy - diskItem.value(1)) < 0.01
					&& Math.abs(circle.diameter - 2 * diskItem.value(2)) < 0.01) {
				disk = circle;
			}
		}
		assertNotNull(disk, where + ": the target disk was sent to the painter");
		assertEquals(drawn.x2, disk.cx, TOLERANCE, where + ": same x at the landing point and at the target");
		assertTrue(disk.cy > drawn.y2 + 1.0, where + ": the target is below the hit line");
		List<SceneItem> links = scene.forEvent(0, Role.TARGET_LINK);
		assertEquals(1, links.size(), where + ": one link");
		PaintRecorder.Segment link = segmentLike(recorder, links.get(0));
		assertNotNull(link, where + ": the link was sent to the painter, as a segment of its own");
		assertEquals(drawn.x2, link.x1, 0.01, where);
		assertEquals(drawn.y2, link.y1, 0.01, where + ": it starts where the trajectory ends");
		assertEquals(disk.cx, link.x2, 0.01, where);
		assertEquals(disk.cy, link.y2, 0.01, where);
		// no segment goes from the horizon straight to the disk: the only one that starts at the horizon point ends on the hit line
		for (PaintRecorder.Segment segment : recorder.segments) {
			if (Math.abs(segment.x1 - far.x()) < 0.01 && Math.abs(segment.y1 - far.y()) < 0.01 && segment.width == drawn.width) {
				assertEquals(projection.hitLineY(), segment.y2, 0.01, where + ": a segment from the horizon must end on the hit line");
			}
		}
	}

	/** One note on the given fret, alone, so that it is the active event. */
	private static SceneFixture note(int fret) {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, 1 + fret % 6, fret);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void everyFretFromTheOpenStringToTheLastHasItsMarkerOnItsSegmentInItsCellAtEveryProgress() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (int fret = 0; fret <= 24; fret++) {
			SceneFixture fixture = note(fret);
			boolean heavy = fret % 6 == 0;
			for (Viewport viewport : heavy ? SIZES : new Viewport[] { SIZES[1] }) {
				for (int step = 0; step <= 100; step++) {
					double progress = step / 100.0;
					long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
					for (QualityTier tier : heavy ? QualityTier.values() : new QualityTier[] { QualityTier.FULL }) {
						checkFrame(fixture, now, progress, viewport, 1.0, tier, PaintRecorder.selected(), "fret " + fret + " p=" + progress + " " + viewport.width() + " " + tier);
					}
				}
			}
		}
	}

	@Test
	public void theFallbackAndTheHighDensityDisplayKeepTheSameAlignment() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (int fret : new int[] { 0, 1, 7, 12, 19, 24 }) {
			SceneFixture fixture = note(fret);
			for (Viewport viewport : SIZES) {
				for (int step = 0; step <= 100; step += 5) {
					long now = fixture.onset(0) - (long) ((1.0 - step / 100.0) * look);
					checkFrame(fixture, now, step / 100.0, viewport, 2.0, QualityTier.PERFORMANCE, PaintRecorder.fallback(), "fallback fret " + fret + " " + viewport.width() + " step " + step);
				}
			}
		}
	}

	@Test
	public void aMovingCameraDoesNotLetTheMarkerLeaveItsLane() {
		// the neck wanders between low and high frets so that the camera pans while the notes come in
		SongFixture song = new SongFixture(6);
		song.tempoChange(0, 150);
		int[] frets = { 1, 20, 3, 18, 0, 22, 5, 24 };
		for (int i = 0; i < frets.length; i++) {
			song.quarter(1 + i / 4, i % 4, 1 + i % 6, frets[i]);
		}
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		IsometricProjection projection = new IsometricProjection(new Viewport(1280, 720));
		int checked = 0;
		for (int event = 0; event < frets.length; event++) {
			for (int step = 0; step <= 100; step++) {
				double progress = step / 100.0;
				long now = fixture.onset(event) - (long) ((1.0 - progress) * look);
				// every note on the screen, active or compact, is on the lane of its cell at this instant
				Scene scene = fixture.scene(false).build(now, new Viewport(1280, 720), SceneBoxesAccess.completedBefore(0, frets.length), 100);
				app.tuxguitar.app.tools.custom.practice.arcade.view.Camera camera = fixture.camera.at(now);
				for (SceneItem item : scene.items()) {
					if ((item.role() == Role.MARKER || item.role() == Role.PREVIEW) && item.eventIndex() >= 0) {
						int fret = fixture.events.get(item.eventIndex()).positions().get(0).fret();
						double p = Math.max(0.0, Math.min(1.0, 1.0 - (double) (fixture.onset(item.eventIndex()) - now) / look));
						ScreenPoint at = projection.approach(fixture.neck.fretCentreX(fret), p, camera);
						assertEquals(at.x(), item.value(0), TOLERANCE, "event " + item.eventIndex() + " " + item.role() + " at " + now);
						assertEquals(at.y(), item.value(1), TOLERANCE);
						double left = projection.approach(fixture.neck.cellLeftX(fret), p, camera).x();
						double right = projection.approach(fixture.neck.cellRightX(fret), p, camera).x();
						assertTrue(item.value(0) >= Math.min(left, right) - 1e-9 && item.value(0) <= Math.max(left, right) + 1e-9, "in its own cell");
						checked++;
					}
				}
			}
		}
		assertTrue(checked > 1000, "many markers were checked: " + checked);
	}
}
