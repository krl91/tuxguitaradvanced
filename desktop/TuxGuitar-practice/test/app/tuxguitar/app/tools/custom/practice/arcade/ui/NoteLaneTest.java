package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.arcade.view.Camera;
import app.tuxguitar.app.tools.custom.practice.arcade.view.CameraTrack;
import app.tuxguitar.app.tools.custom.practice.arcade.view.IsometricProjection;
import app.tuxguitar.app.tools.custom.practice.arcade.view.QualityTier;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneBoxesAccess;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneFixture;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;
import app.tuxguitar.app.tools.custom.practice.arcade.view.TrackTuningFactory;
import app.tuxguitar.app.tools.custom.practice.arcade.view.Viewport;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;

/**
 * The predefined fret lanes (spec 003 FR-066, SC-024), measured on the primitives that the renderers really send to the painter: the wires, the top edge of
 * the neck, the lines of the lanes and the circles of the markers. For every cell, the lane ends at the middle of the upper edge between the two wires that
 * were painted; every note written at that fret uses exactly that lane, whatever its string, and keeps the colour of its string; the marker stays on the lane,
 * and is at its end at the onset; no line of the approach goes below the top edge of the neck. Frets 0, 1, 5, 10, 12 and 20 (and every other from 0 to 24 on
 * the lighter checks), a six-string guitar and a four-string bass, every string, a hundred and one progress values, three window sizes, a moving camera,
 * two densities, every tier, the selected path and the fallback.
 */
public class NoteLaneTest {

	private static final Viewport[] SIZES = { new Viewport(640, 360), new Viewport(1280, 720), new Viewport(2560, 1440) };
	private static final double TOLERANCE = 0.5;
	/** Fret 0 is not here: an open string is a bar across the lanes (FR-069, OpenBarTest). */
	private static final int[] FRETS = { 1, 5, 10, 12, 20 };

	private static TrackTuning bass() {
		return TrackTuningFactory.any(43, 38, 33, 28);
	}

	private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double length2 = dx * dx + dy * dy;
		double t = length2 == 0 ? 0 : Math.max(0.0, Math.min(1.0, ((px - x1) * dx + (py - y1) * dy) / length2));
		return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
	}

	private static PaintRecorder.Segment painted(PaintRecorder recorder, SceneItem item) {
		for (PaintRecorder.Segment segment : recorder.segments) {
			if (Math.abs(segment.x1 - item.value(0)) < 0.01 && Math.abs(segment.y1 - item.value(1)) < 0.01 && Math.abs(segment.x2 - item.value(2)) < 0.01
					&& Math.abs(segment.y2 - item.value(3)) < 0.01) {
				return segment;
			}
		}
		return null;
	}

	/** The centre of a painted shape. */
	private static final class Centre {
		final double cx;
		final double cy;

		Centre(double cx, double cy) {
			this.cx = cx;
			this.cy = cy;
		}
	}

	private static PaintRecorder.Circle paintedCircle(PaintRecorder recorder, SceneItem item) {
		for (PaintRecorder.Circle circle : recorder.circles) {
			if (circle.style == app.tuxguitar.ui.resource.UIPainter.PATH_FILL && Math.abs(circle.cx - item.value(0)) < 0.01 && Math.abs(circle.cy - item.value(1)) < 0.01
					&& Math.abs(circle.diameter - 2 * item.value(2)) < 0.01) {
				return circle;
			}
		}
		return null;
	}

	/** What was painted of the neck: the x of every wire that was drawn (the nut first), and the y of the top edge of the body. */
	private static final class PaintedNeck {
		final List<Double> wires = new ArrayList<Double>();
		double top = Double.NaN;

		PaintedNeck(Scene scene, PaintRecorder recorder) {
			List<Double> xs = new ArrayList<Double>();
			for (SceneItem item : scene.items()) {
				if (item.role() == Role.NUT || item.role() == Role.FRET_WIRE) {
					PaintRecorder.Segment segment = painted(recorder, item);
					assertNotNull(segment, "a wire of the scene was not sent to the painter");
					xs.add(segment.x1);
				} else if (item.role() == Role.NECK_BODY) {
					for (PaintRecorder.Polygon polygon : recorder.polygons) {
						if (Math.abs(polygon.xy[1] - item.value(1)) < 0.01 && Math.abs(polygon.xy[0] - item.value(0)) < 0.01 && Math.abs(polygon.xy[2] - item.value(2)) < 0.01) {
							this.top = polygon.minY();
						}
					}
				}
			}
			java.util.Collections.sort(xs);
			this.wires.addAll(xs);
			assertTrue(!Double.isNaN(this.top), "the body of the neck was painted");
		}

		/** The middle of the upper edge of the cell whose lane ends near this x: between the two painted wires around it. */
		double midpointAround(double x) {
			double left = Double.NaN;
			double right = Double.NaN;
			for (double wire : this.wires) {
				if (wire <= x && (Double.isNaN(left) || wire > left)) {
					left = wire;
				}
				if (wire > x && (Double.isNaN(right) || wire < right)) {
					right = wire;
				}
			}
			assertTrue(!Double.isNaN(left) && !Double.isNaN(right), "a painted wire on each side of x " + x + ": " + this.wires);
			return (left + right) / 2.0;
		}
	}

	private static Scene scene(SceneFixture fixture, long now, Viewport viewport, double uiScale, QualityTier tier) {
		return fixture.scene(false).withUiScale(uiScale).build(now, viewport, null, 100, tier);
	}

	/** One frame of the first event of the fixture, a single note: everything that is painted for it is checked against the lane of its fret. */
	private static double[] checkNote(SceneFixture fixture, int string, long now, double progress, Viewport viewport, double uiScale, QualityTier tier, PainterRenderer renderer,
			String where) {
		int fret = fixture.events.get(0).positions().get(0).fret();
		Scene scene = scene(fixture, now, viewport, uiScale, tier);
		PaintRecorder recorder = PaintRecorder.record(renderer, scene, tier);
		PaintedNeck neck = new PaintedNeck(scene, recorder);

		List<SceneItem> lanes = scene.forEvent(0, Role.TRAJECTORY);
		assertEquals(1, lanes.size(), where + ": one lane for the note");
		PaintRecorder.Segment lane = painted(recorder, lanes.get(0));
		assertNotNull(lane, where + ": the lane was sent to the painter");

		// 1. the end of the lane: the middle of the upper edge of the cell, between the two wires that were painted, on the top edge of the neck
		assertEquals(neck.top, lane.y2, TOLERANCE, where + ": the lane ends on the top edge of the neck");
		if (fret == 0) {
			assertTrue(lane.x2 < neck.wires.get(0), where + ": the lane of the open strings is before the nut");
			IsometricProjection projection = new IsometricProjection(viewport);
			assertEquals(projection.neckX(fixture.neck.fretCentreX(0), fixture.camera.at(now)), lane.x2, TOLERANCE, where + ": the documented lane before the nut");
		} else {
			double midpoint = neck.midpointAround(lane.x2);
			assertEquals(midpoint, lane.x2, TOLERANCE, where + ": the lane ends at the middle of its cell, between the wires " + neck.wires);
			// and it is the cell of the written fret: the wire before and the wire after the lane are the wires n-1 and n
		}

		// 2. nothing of the approach goes below the top edge of the neck: no line of the lanes, of the trails, no connector, no marker centre
		for (SceneItem item : scene.items()) {
			if (item.shape() == SceneItem.Shape.LINE && (item.role() == Role.TRAJECTORY || item.role() == Role.TRAIL || item.role() == Role.FIELD_GUIDE)) {
				assertTrue(Math.max(item.value(1), item.value(3)) <= neck.top + TOLERANCE, where + ": a line of the approach goes into the neck: " + item.role());
			}
			if (item.role() == Role.MARKER_FOOTPRINT) {
				assertTrue(item.value(1) <= neck.top + TOLERANCE, where + ": a footprint is below the top edge of the neck");
			}
			if (item.eventIndex() == 0 && item.shape() == SceneItem.Shape.LINE) {
				assertTrue(item.role() == Role.TRAJECTORY || item.role() == Role.TRAIL || item.role() == Role.MARKER_THREAD, where + ": nothing but the lane and its trail is drawn for the note: " + item.role());
			}
		}

		// 3. the marker really painted is on the lane at every progress, and at its end at the onset
		// the footprint of the marker stands on the lane (the marker floats above it, FR-071)
		SceneItem markerItem = scene.forEvent(0, Role.MARKER_FOOTPRINT).get(0);
		PaintRecorder.Polygon painted = recorder.roundedAt(markerItem.value(0), markerItem.value(1));
		assertNotNull(painted, where + ": the footprint of the marker was sent to the painter");
		Centre marker = new Centre((painted.minX() + painted.maxX()) / 2.0, (painted.minY() + painted.maxY()) / 2.0);
		assertTrue(distanceToSegment(marker.cx, marker.cy, lane.x1, lane.y1, lane.x2, lane.y2) <= TOLERANCE,
				where + ": the marker leaves its lane by " + distanceToSegment(marker.cx, marker.cy, lane.x1, lane.y1, lane.x2, lane.y2));
		double p = IsometricProjection.depthShare(progress);
		assertEquals(lane.x1 + (lane.x2 - lane.x1) * p, marker.cx, TOLERANCE, where + ": the marker is at its share of the lane");
		assertEquals(lane.y1 + (lane.y2 - lane.y1) * p, marker.cy, TOLERANCE, where);
		if (progress >= 1.0) {
			assertEquals(lane.x2, marker.cx, TOLERANCE, where + ": at the onset the marker is at the end of its lane");
			assertEquals(lane.y2, marker.cy, TOLERANCE, where);
		}

		// 4. the colour is that of the string
		assertEquals(fixture.colours.of(string), markerItem.colour(), where + ": the marker has the colour of its string");
		assertEquals(fixture.colours.of(string), lanes.get(0).colour(), where + ": and so has the lane");
		assertEquals(string, markerItem.tgString());
		return new double[] { lane.x1, lane.y1, lane.x2, lane.y2, marker.cx, marker.cy };
	}

	private static SceneFixture note(TrackTuning tuning, int string, int fret) {
		SongFixture song = new SongFixture(2);
		song.quarter(1, 0, string, fret);
		return new SceneFixture(song, tuning, 100);
	}

	@Test
	public void everyFretLaneEndsInTheMiddleOfItsPaintedCellAndTheMarkerKeepsToItOnEveryStringOfAGuitarAndABass() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (TrackTuning tuning : new TrackTuning[] { SceneFixture.sixStrings(), bass() }) {
			int strings = tuning.getStringCount();
			for (int fret : FRETS) {
				for (int string = 1; string <= strings; string++) {
					SceneFixture fixture = note(tuning, string, fret);
					for (Viewport viewport : SIZES) {
						for (int step = 0; step <= 100; step++) {
							double progress = step / 100.0;
							long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
							for (QualityTier tier : viewport.width() == 1280 ? QualityTier.values() : new QualityTier[] { QualityTier.FULL }) {
								checkNote(fixture, string, now, progress, viewport, 1.0, tier, PaintRecorder.selected(), strings + " strings, fret " + fret + " string " + string + " p="
										+ progress + " " + viewport.width() + " " + tier);
							}
						}
					}
				}
			}
		}
	}

	@Test
	public void everyFretFromTheOpenStringToTheLastHasItsLane() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (int fret = 1; fret <= 24; fret++) {
			SceneFixture fixture = note(SceneFixture.sixStrings(), 1 + fret % 6, fret);
			for (int step = 0; step <= 100; step += 10) {
				long now = fixture.onset(0) - (long) ((1.0 - step / 100.0) * look);
				checkNote(fixture, 1 + fret % 6, now, step / 100.0, SIZES[1], 1.0, QualityTier.FULL, PaintRecorder.selected(), "fret " + fret + " step " + step);
			}
		}
	}

	@Test
	public void notesOnDifferentStringsOfTheSameFretUseExactlyTheSameLaneAndKeepTheColourOfTheirStrings() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (TrackTuning tuning : new TrackTuning[] { SceneFixture.sixStrings(), bass() }) {
			int strings = tuning.getStringCount();
			for (int fret : FRETS) {
				for (int step = 0; step <= 100; step += 5) {
					double progress = step / 100.0;
					double[] first = null;
					java.util.Set<Integer> colours = new java.util.HashSet<Integer>();
					for (int string = 1; string <= strings; string++) {
						SceneFixture fixture = note(tuning, string, fret);
						long now = fixture.onset(0) - (long) ((1.0 - progress) * look);
						double[] geometry = checkNote(fixture, string, now, progress, SIZES[1], 1.0, QualityTier.FULL, PaintRecorder.selected(), "same fret " + fret + " string " + string
								+ " p=" + progress);
						if (first == null) {
							first = geometry;
						} else {
							for (int i = 0; i < geometry.length; i++) {
								assertEquals(first[i], geometry[i], 1e-9, strings + " strings, fret " + fret + " p=" + progress + ": string " + string + " does not use the lane of the others (value " + i + ")");
							}
						}
						colours.add(fixture.scene(false).build(now, SIZES[1], null, 100).forEvent(0, Role.MARKER).get(0).colour());
					}
					assertEquals(strings, colours.size(), "the strings keep their distinct colours on the same lane");
				}
			}
		}
	}

	@Test
	public void theFallbackAndTheHighDensityDisplayKeepTheSameLanes() {
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		for (int fret : FRETS) {
			for (int string = 1; string <= 6; string++) {
				SceneFixture fixture = note(SceneFixture.sixStrings(), string, fret);
				for (Viewport viewport : SIZES) {
					for (int step = 0; step <= 100; step += 10) {
						long now = fixture.onset(0) - (long) ((1.0 - step / 100.0) * look);
						checkNote(fixture, string, now, step / 100.0, viewport, 2.0, QualityTier.PERFORMANCE, PaintRecorder.fallback(), "fallback fret " + fret + " string " + string + " "
								+ viewport.width() + " step " + step);
					}
				}
			}
		}
	}

	@Test
	public void afterTheNeckMovesTheLanesAreTheOnesOfTheNewPaintedWiresAndEveryMarkerIsOnItsLane() {
		// the neck wanders between low and high frets so that the camera pans while the notes come in
		SongFixture song = new SongFixture(6);
		song.tempoChange(0, 150);
		int[] frets = { 1, 20, 3, 18, 0, 22, 5, 24 };
		for (int i = 0; i < frets.length; i++) {
			song.quarter(1 + i / 4, i % 4, 1 + i % 6, frets[i]);
		}
		SceneFixture fixture = new SceneFixture(song, SceneFixture.sixStrings(), 100);
		long look = CameraTrack.Settings.standard().lookAheadNanos;
		Viewport viewport = new Viewport(1280, 720);
		IsometricProjection projection = new IsometricProjection(viewport);
		int checked = 0;
		double firstLeft = Double.NaN;
		boolean moved = false;
		for (int event = 0; event < frets.length; event++) {
			for (int step = 0; step <= 100; step++) {
				long now = fixture.onset(event) - (long) ((1.0 - step / 100.0) * look);
				Scene scene = fixture.scene(false).build(now, viewport, SceneBoxesAccess.completedBefore(0, frets.length), 100);
				PaintRecorder recorder = PaintRecorder.record(PaintRecorder.selected(), scene, QualityTier.FULL);
				PaintedNeck neck = new PaintedNeck(scene, recorder);
				Camera camera = fixture.camera.at(now);
				if (Double.isNaN(firstLeft)) {
					firstLeft = camera.left();
				}
				moved |= Math.abs(camera.left() - firstLeft) > 1.0;
				for (SceneItem item : scene.items()) {
					if (item.role() != Role.MARKER_FOOTPRINT || item.eventIndex() < 0) {
						continue;
					}
					int fret = fixture.events.get(item.eventIndex()).positions().get(0).fret();
					// the lane of this fret in this frame: the line of the field that ends at the middle of the cell between the wires painted now
					double wanted = fret == 0 ? projection.neckX(fixture.neck.fretCentreX(0), camera) : neck.midpointAround(projection.neckX(fixture.neck.fretCentreX(fret), camera));
					PaintRecorder.Segment lane = null;
					for (SceneItem guide : scene.withRole(Role.FIELD_GUIDE)) {
						if (guide.value(3) == neck.top && Math.abs(guide.value(2) - wanted) <= TOLERANCE) {
							lane = painted(recorder, guide);
						}
					}
					assertNotNull(lane, "event " + item.eventIndex() + " fret " + fret + ": no painted lane ends at the middle of its painted cell (" + wanted + ")");
					assertTrue(distanceToSegment(item.value(0), item.value(1), lane.x1, lane.y1, lane.x2, lane.y2) <= TOLERANCE, "event " + item.eventIndex() + " " + item.role() + ": off its lane");
					assertTrue(item.value(1) <= neck.top + TOLERANCE);
					checked++;
				}
			}
		}
		assertTrue(moved, "the camera moved during the sequence");
		assertTrue(checked > 1000, "many markers were checked: " + checked);
	}

	@Test
	public void aLaneThatIsBuiltFromAStaleSnapshotOrEndsOnAWireIsRejectedByTheMeasure() {
		// the measure itself: a lane end that is on a wire, or off the middle by two pixels, is not the middle of the cell
		PaintedNeck neck = null;
		SceneFixture fixture = note(SceneFixture.sixStrings(), 1, 5);
		long now = fixture.onset(0);
		Scene scene = scene(fixture, now, SIZES[1], 1.0, QualityTier.FULL);
		neck = new PaintedNeck(scene, PaintRecorder.record(PaintRecorder.selected(), scene, QualityTier.FULL));
		double lane = scene.forEvent(0, Role.TRAJECTORY).get(0).value(2);
		double midpoint = neck.midpointAround(lane);
		assertEquals(midpoint, lane, TOLERANCE);
		for (double wire : neck.wires) {
			assertNotEquals(wire, lane, 1.0, "the lane does not end on a fret wire");
		}
		assertTrue(Math.abs(midpoint - (lane + 2.0)) > TOLERANCE, "two pixels off is detected");
	}
}
