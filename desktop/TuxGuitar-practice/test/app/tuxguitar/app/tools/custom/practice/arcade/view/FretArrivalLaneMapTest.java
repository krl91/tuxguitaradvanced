package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The map of the predefined fret lanes (spec 003 FR-066), built from the grid as it is painted: its endpoints, its independence from strings, its rebuild with the camera. */
public class FretArrivalLaneMapTest {

	private static final NeckModel NECK = NeckModel.of(SceneFixture.sixStrings(), 24);

	private static FretArrivalLaneMap map(Viewport viewport, Camera camera) {
		IsometricProjection projection = new IsometricProjection(viewport);
		return FretArrivalLaneMap.fromPaintedFrets(PaintedFretGrid.of(NECK, projection, camera), projection);
	}

	@Test
	public void theLaneOfACellEndsInTheMiddleOfItsUpperEdgeBetweenTheTwoPaintedWires() {
		Viewport viewport = new Viewport(1280, 720);
		Camera camera = new Camera(-20.0, 330.0);
		IsometricProjection projection = new IsometricProjection(viewport);
		PaintedFretGrid grid = PaintedFretGrid.of(NECK, projection, camera);
		FretArrivalLaneMap lanes = FretArrivalLaneMap.fromPaintedFrets(grid, projection);
		for (int fret = 1; fret <= 12; fret++) {
			FretArrivalLaneMap.Lane lane = lanes.laneForFret(fret);
			assertEquals((grid.wireX(fret - 1) + grid.wireX(fret)) / 2.0, lane.end().x(), 1e-9, "fret " + fret);
			assertEquals(grid.neckTopY(), lane.end().y(), 1e-9, "fret " + fret + " ends on the top edge of the neck");
			assertTrue(Math.abs(lane.end().x() - grid.wireX(fret)) > 1.0 && Math.abs(lane.end().x() - grid.wireX(fret - 1)) > 1.0, "not on a wire");
		}
		FretArrivalLaneMap.Lane open = lanes.laneForFret(0);
		assertTrue(open.end().x() < grid.wireX(0), "the open strings have a lane before the nut");
		assertEquals((grid.preNutX() + grid.wireX(0)) / 2.0, open.end().x(), 1e-9);
	}

	@Test
	public void aLaneIsTheSameObjectWhateverTheStringAndItsPointsAreTheStraightWayFromTheHorizon() {
		FretArrivalLaneMap lanes = map(new Viewport(1280, 720), new Camera(-20.0, 330.0));
		assertSame(lanes.laneForFret(5), lanes.laneForFret(5), "the lane is selected by the fret alone");
		FretArrivalLaneMap.Lane lane = lanes.laneForFret(5);
		assertEquals(lane.origin().x(), lane.at(0.0).x(), 1e-9);
		assertEquals(lane.origin().y(), lane.at(0.0).y(), 1e-9);
		assertEquals(lane.end().x(), lane.at(1.0).x(), 1e-9);
		assertEquals(lane.end().y(), lane.at(1.0).y(), 1e-9);
		double q = IsometricProjection.depthShare(0.5);
		assertEquals(lane.origin().x() + (lane.end().x() - lane.origin().x()) * q, lane.at(0.5).x(), 1e-9, "on the straight way, in perspective");
		assertEquals(lane.end().x(), lane.at(7.0).x(), 1e-9, "it stops at its end");
	}

	@Test
	public void thePointsOfALaneAreThoseOfTheApproachFieldOfTheSameFret() {
		Viewport viewport = new Viewport(1280, 720);
		Camera camera = new Camera(-20.0, 330.0);
		IsometricProjection projection = new IsometricProjection(viewport);
		FretArrivalLaneMap lanes = map(viewport, camera);
		for (int fret = 0; fret <= 12; fret++) {
			for (int step = 0; step <= 10; step++) {
				ScreenPoint expected = projection.approach(NECK.fretCentreX(fret), step / 10.0, camera);
				ScreenPoint actual = lanes.laneForFret(fret).at(step / 10.0);
				assertEquals(expected.x(), actual.x(), 1e-6, "fret " + fret + " step " + step);
				assertEquals(expected.y(), actual.y(), 1e-6);
			}
		}
	}

	@Test
	public void aMovedCameraGivesANewMapFromTheNewWiresAndNothingStaysOfTheOldOne() {
		Viewport viewport = new Viewport(1280, 720);
		FretArrivalLaneMap before = map(viewport, new Camera(-20.0, 330.0));
		FretArrivalLaneMap after = map(viewport, new Camera(100.0, 450.0));
		assertTrue(Math.abs(before.laneForFret(5).end().x() - after.laneForFret(5).end().x()) > 50.0, "the lane of the fret 5 moved with the neck");
		IsometricProjection projection = new IsometricProjection(viewport);
		PaintedFretGrid grid = PaintedFretGrid.of(NECK, projection, new Camera(100.0, 450.0));
		assertEquals((grid.wireX(4) + grid.wireX(5)) / 2.0, after.laneForFret(5).end().x(), 1e-9);
	}
}
