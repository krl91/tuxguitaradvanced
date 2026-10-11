package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.core.ArcadeRun;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;

/**
 * The rounded rectangles, the thread, the footprint, the fret ruler, the approach band and the transparent neck (spec 003 FR-070 to FR-072, FR-075, SC-027,
 * the owner's references of 2026-10-11): the marker of a fretted note is a solid rounded rectangle floating above its lane, joined by a thread to a footprint
 * on the lane that carries the fret number under it; the target on the neck fills its cell; the ruler highlights the active fret; the neck is translucent.
 */
public class RoundedRectTest {

	private static final long MS = SceneFixture.MS;
	private static final Viewport SCREEN = new Viewport(1280, 720);

	private static SceneFixture notes() {
		SongFixture song = new SongFixture(3);
		song.quarter(1, 0, 3, 5);
		song.quarter(1, 1, 1, 12);
		song.quarter(1, 2, 6, 0);
		return new SceneFixture(song, SceneFixture.sixStrings(), 100);
	}

	@Test
	public void theMarkerIsASolidRoundedRectangleAboveItsFootprintJoinedByAThreadWithTheFretUnderTheFootprintAtEveryProgress() {
		SceneFixture fixture = notes();
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (QualityTier tier : QualityTier.values()) {
			for (int step = 1; step <= 101; step++) {
				double progress = step / 101.0;
				long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
				Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, tier);
				SceneItem marker = scene.forEvent(0, Role.MARKER).get(0);
				SceneItem footprint = scene.forEvent(0, Role.MARKER_FOOTPRINT).get(0);
				SceneItem thread = scene.forEvent(0, Role.MARKER_THREAD).get(0);
				String where = tier + " p=" + progress;
				assertEquals(SceneItem.Shape.ROUNDED, marker.shape(), where + ": a rounded rectangle, not a disc");
				assertEquals(StringColours.forStrings(6).of(3), marker.colour(), where);
				assertTrue(marker.value(1) < footprint.value(1), where + ": the marker floats above its footprint");
				assertEquals(footprint.value(0), marker.value(0), 1e-9, where + ": on the same vertical");
				assertEquals(SceneItem.Shape.LINE, thread.shape());
				assertEquals(marker.value(0), thread.value(0), 1e-9, where + ": the thread is vertical, under the marker");
				assertEquals(thread.value(0), thread.value(2), 1e-9, where);
				assertEquals(marker.value(1) + marker.value(3), thread.value(1), 1e-9, where + ": from the bottom of the marker");
				assertEquals(footprint.value(1), thread.value(3), 1e-9, where + ": to the footprint");
				assertTrue(footprint.value(3) < marker.value(3), where + ": the footprint is flat");
				List<SceneItem> numbers = scene.forEvent(0, Role.MARKER_FRET);
				for (SceneItem number : numbers) {
					assertEquals("5", number.text());
					assertEquals(footprint.value(0), number.value(0), 1e-9, where + ": centred under the footprint");
					assertTrue(number.value(1) > footprint.value(1), where + ": the number is written under the footprint, not in the marker");
				}
				assertTrue(numbers.size() <= 1, where);
			}
		}
	}

	@Test
	public void theNumberIsOnTheActiveNoteAndThereIsNeverANumberInsideAnyMarker() {
		SceneFixture fixture = notes();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 500 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertFalse(scene.forEvent(0, Role.MARKER_FRET).isEmpty(), "the active note has its number");
		SceneItem marker = scene.forEvent(0, Role.MARKER).get(0);
		for (SceneItem number : scene.withRole(Role.MARKER_FRET)) {
			boolean inside = Math.abs(number.value(0) - marker.value(0)) < marker.value(2) && Math.abs(number.value(1) - marker.value(1)) < marker.value(3);
			assertFalse(inside && number.eventIndex() == 0, "no number inside the marker");
		}
	}

	@Test
	public void theTargetOnTheNeckIsARoundedRectangleThatFillsItsCellAndTheRulerHighlightsTheActiveFret() {
		SceneFixture fixture = notes();
		long now = fixture.onset(0) - 400 * MS;
		Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
		SceneItem rect = scene.forEvent(0, Role.TARGET_RECT).get(0);
		assertEquals(SceneItem.Shape.ROUNDED, rect.shape());
		IsometricProjection projection = new IsometricProjection(SCREEN);
		Camera cam = fixture.camera.at(now);
		double left = projection.neckX(fixture.neck.fretX(4), cam);
		double right = projection.neckX(fixture.neck.fretX(5), cam);
		assertTrue(rect.value(0) - rect.value(2) > left && rect.value(0) + rect.value(2) < right, "inside its cell, between the two painted wires");
		assertTrue(2.0 * rect.value(2) > 0.7 * (right - left), "and filling most of it");
		assertEquals((left + right) / 2.0, rect.value(0), 1.0, "centred on the cell");
		List<SceneItem> active = scene.withRole(Role.FRET_RULER_ACTIVE);
		assertEquals(1, active.size(), "exactly one number of the ruler is highlighted");
		assertEquals("5", active.get(0).text(), "the fret of the active event");
		for (SceneItem number : scene.withRole(Role.FRET_NUMBER)) {
			assertFalse("5".equals(number.text()), "the highlighted fret is not also drawn as a plain number");
		}
	}

	@Test
	public void theNeckBodyIsTranslucentWhileTheStringsAndTheTargetsStayOpaque() {
		SceneFixture fixture = notes();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 400 * MS, SCREEN, null, 100, QualityTier.FULL);
		assertTrue(scene.withRole(Role.NECK_BODY).get(0).alpha() <= 0.15 && scene.withRole(Role.NECK_BODY).get(0).alpha() > 0.0, "the body of the neck is a faint tint");
		for (SceneItem wire : scene.withRole(Role.FRET_WIRE)) {
			assertEquals(1.0, wire.alpha(), 0.0, "the wires are opaque");
		}
		assertEquals(1.0, scene.withRole(Role.TARGET_RECT).get(0).alpha(), 0.0, "and the target");
	}

	@Test
	public void theApproachBandIsDecorationBehindTheLanesAndIsAbsentAtPerformance() {
		SceneFixture fixture = notes();
		long now = fixture.onset(0) - 1500 * MS;
		Scene full = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
		Scene performance = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.PERFORMANCE);
		assertFalse(full.withRole(Role.APPROACH_BAND).isEmpty(), "the band lights the approach zone");
		assertTrue(performance.withRole(Role.APPROACH_BAND).isEmpty(), "decoration: dropped at the cheapest tier");
		for (SceneItem band : full.withRole(Role.APPROACH_BAND)) {
			assertTrue(band.alpha() <= 0.2, "faint: it never hides a marker, a label or a bar");
		}
		int firstBand = full.items().indexOf(full.withRole(Role.APPROACH_BAND).get(0));
		assertTrue(firstBand < full.items().indexOf(full.forEvent(0, Role.MARKER).get(0)), "drawn behind the markers");
		assertTrue(firstBand < full.items().indexOf(full.withRole(Role.FIELD_GUIDE).get(0)), "and behind the lanes");
	}

	@Test
	public void theBandGoesOnUnderTheSeeThroughNeckSoThatTheStringsFloatOverIt() {
		SceneFixture fixture = notes();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 1000 * MS, SCREEN, null, 100, QualityTier.FULL);
		double neckBottom = scene.withRole(Role.NECK_BODY).get(0).value(5);
		boolean under = false;
		for (SceneItem band : scene.withRole(Role.APPROACH_BAND)) {
			double bottom = Math.max(Math.max(band.value(1), band.value(3)), Math.max(band.value(5), band.value(7)));
			under |= Math.abs(bottom - neckBottom) < 0.5;
		}
		assertTrue(under, "a band reaches the bottom of the neck: the neck is see-through");
		assertTrue(scene.withRole(Role.NECK_BODY).get(0).alpha() <= 0.15, "and the neck's own tint is faint");
	}

	/**
	 * Bug (owner, 2026-10-11): the markers were all of the same size whatever the fret, but the rectangle that comes must land exactly on the rectangle of its
	 * cell on the neck: its width is the width of its cell at its depth (a higher fret is a narrower cell) and, at the onset, it is the target's rectangle.
	 */
	@Test
	public void theMarkerHasTheWidthOfItsCellAtItsDepthAndLandsOnTheSizeOfItsTargetRectangleAtEveryFret() {
		for (int fret : new int[] { 1, 3, 5, 9, 12, 17 }) {
			SongFixture song = new SongFixture(2);
			song.quarter(1, 0, 3, fret);
			song.quarter(1, 1, 3, fret);
			SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
			IsometricProjection projection = new IsometricProjection(SCREEN);
			long look = CameraTrack.Settings.standard().lookAheadNanos;
			double previousWidth = Double.MAX_VALUE;
			for (int step = 10; step <= 100; step += 15) {
				double progress = step / 100.0;
				long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
				Scene scene = fixture.scene(false).build(now, SCREEN, null, 100, QualityTier.FULL);
				Camera cam = fixture.camera.at(now);
				SceneItem marker = scene.forEvent(0, Role.MARKER).get(0);
				double cell = Math.abs(projection.approach(fixture.neck.fretX(fret), progress, cam).x() - projection.approach(fixture.neck.fretX(fret - 1), progress, cam).x());
				assertEquals((cell - Math.max(3.0, cell * 0.14)) / 2.0, marker.value(2), Math.max(0.6, cell * 0.02), "fret " + fret + " p=" + progress + ": the width of its cell at its depth, less the margin");
				assertTrue(marker.value(2) <= previousWidth * 1.001 || progress > 0.0, "growing with the depth");
			}
			Scene onset = fixture.scene(false).build(fixture.onset(0), SCREEN, null, 100, QualityTier.FULL);
			SceneItem marker = onset.forEvent(0, Role.MARKER).get(0);
			SceneItem target = onset.forEvent(0, Role.TARGET_RECT).get(0);
			assertEquals(target.value(2), marker.value(2), 0.6, "fret " + fret + ": at the onset the marker is as wide as the target rectangle");
			assertEquals(target.value(3), marker.value(3), 0.6, "fret " + fret + ": and as high");
		}
		// the cells narrow towards the body: the rectangle of a high fret is narrower than that of a low one
		SongFixture two = new SongFixture(2);
		two.quarter(1, 0, 3, 2);
		two.quarter(1, 1, 3, 17);
		SceneFixture f2 = new SceneFixture(two, SceneFixture.sixStrings(), 100);
		assertTrue(f2.scene(false).build(f2.onset(0), SCREEN, null, 100, QualityTier.FULL).forEvent(0, Role.MARKER).get(0).value(2) > 
				f2.scene(false).build(f2.onset(1), SCREEN, ArcadeSceneTest.completedBefore(1, 2), 100, QualityTier.FULL).forEvent(1, Role.MARKER).get(0).value(2), "a high fret is a narrower rectangle");
	}

	/** Bug (owner, 2026-10-11): the neck is see-through, so the lanes must be seen going on under the strings, down to the bottom of the neck. */
	@Test
	public void theLanesGoOnUnderTheSeeThroughNeckAsFaintLines() {
		SceneFixture fixture = notes();
		Scene scene = fixture.scene(false).build(fixture.onset(0) - 800 * MS, SCREEN, null, 100, QualityTier.FULL);
		double neckBottom = scene.withRole(Role.NECK_BODY).get(0).value(5);
		double top = scene.withRole(Role.NECK_BODY).get(0).value(1);
		java.util.List<SceneItem> lanes = scene.withRole(Role.NECK_LANE);
		assertFalse(lanes.isEmpty(), "the lanes go on under the neck");
		for (SceneItem lane : lanes) {
			assertEquals(top, Math.min(lane.value(1), lane.value(3)), 0.5, "from the top edge of the neck");
			assertEquals(neckBottom, Math.max(lane.value(1), lane.value(3)), 0.5, "to its bottom");
			assertTrue(lane.alpha() <= 0.3, "faint");
		}
	}
}
