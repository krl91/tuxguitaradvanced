package app.tuxguitar.app.tools.custom.practice.arcade.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The projection (spec 003 T033, visual-design.md): the hit line is fixed, progress 1 is exactly on it, the rest is a pure function of the time left. */
public class IsometricProjectionTest {

	private static final Viewport VIEW = new Viewport(1200, 800);
	private static final Camera CAMERA = new Camera(100.0, 400.0);
	private final IsometricProjection projection = new IsometricProjection(VIEW);

	@Test
	public void anEventAtProgressOneIsOnTheHitLineAboveItsFretColumn() {
		for (double x : new double[] { 120.0, 250.0, 380.0 }) {
			ScreenPoint point = this.projection.approach(x, 1.0, CAMERA);
			assertEquals(this.projection.hitLineY(), point.y(), 1e-9, "the hit line is fixed");
			assertEquals(this.projection.neckX(x, CAMERA), point.x(), 1e-9, "above the column of its fret on the neck");
			assertEquals(1.0, point.scale(), 1e-12);
		}
	}

	@Test
	public void atProgressZeroTheEventIsAtTheHorizonAndSmallAndTheWayIsMonotone() {
		ScreenPoint far = this.projection.approach(250.0, 0.0, CAMERA);
		assertEquals(this.projection.horizonY(), far.y(), 1e-9);
		assertEquals(IsometricProjection.FAR_SCALE, far.scale(), 1e-12);
		double previousY = far.y();
		double previousScale = far.scale();
		for (int step = 1; step <= 100; step++) {
			ScreenPoint point = this.projection.approach(250.0, step / 100.0, CAMERA);
			assertTrue(point.y() > previousY, "it comes down towards the hit line");
			assertTrue(point.scale() > previousScale, "and grows");
			previousY = point.y();
			previousScale = point.scale();
		}
	}

	@Test
	public void theProgressIsLimitedToTheWayAndTheProjectionIsAPureFunction() {
		assertEquals(this.projection.approach(250.0, 1.0, CAMERA).y(), this.projection.approach(250.0, 7.0, CAMERA).y(), 1e-12, "never past the hit line");
		assertEquals(this.projection.approach(250.0, 0.0, CAMERA).y(), this.projection.approach(250.0, -3.0, CAMERA).y(), 1e-12);
		ScreenPoint first = this.projection.approach(222.0, 0.37, CAMERA);
		ScreenPoint second = new IsometricProjection(new Viewport(1200, 800)).approach(222.0, 0.37, new Camera(100.0, 400.0));
		assertEquals(first.x(), second.x(), 0.0);
		assertEquals(first.y(), second.y(), 0.0);
	}

	@Test
	public void theNeckIsBelowTheHitLineWithTheLowStringAtTheTop() {
		int strings = 6;
		double previous = -Double.MAX_VALUE;
		for (int row = 0; row < strings; row++) {
			double y = this.projection.stringY(row, strings);
			assertTrue(y > previous, "a higher string is lower on the screen: the lowest string is at the top (FR-067)");
			assertTrue(y > this.projection.hitLineY(), "the strings are below the hit line");
			assertTrue(y < VIEW.height());
			previous = y;
		}
	}

	@Test
	public void aWiderCameraShrinksTheNeckAndMovingItPansTheColumns() {
		double narrow = this.projection.neckX(300.0, new Camera(200.0, 400.0)) - this.projection.neckX(200.0, new Camera(200.0, 400.0));
		double wide = this.projection.neckX(300.0, new Camera(0.0, 600.0)) - this.projection.neckX(200.0, new Camera(0.0, 600.0));
		assertTrue(narrow > wide, "100 mm take less room when more of the neck is shown");
		assertEquals(this.projection.neckX(150.0, new Camera(100.0, 400.0)), this.projection.neckX(250.0, new Camera(200.0, 500.0)), 1e-9, "panning by 100 mm moves the column by the same amount");
	}

	@Test
	public void theProportionsFollowTheViewport() {
		IsometricProjection big = new IsometricProjection(new Viewport(2400, 1600));
		assertEquals(this.projection.hitLineY() * 2.0, big.hitLineY(), 1e-9);
		assertEquals(this.projection.approach(250.0, 0.5, CAMERA).x() * 2.0, big.approach(250.0, 0.5, CAMERA).x(), 1e-9);
		assertThrows(IllegalArgumentException.class, () -> new Viewport(0, 100));
		assertThrows(IllegalArgumentException.class, () -> new Camera(5.0, 5.0));
	}

	@Test
	public void thePerspectiveGivesTheLastSecondsMuchOfTheLaneAndKeepsTheFarOnesClose() {
		assertEquals(0.0, IsometricProjection.depthShare(0.0), 1e-12);
		assertEquals(1.0, IsometricProjection.depthShare(1.0), 1e-12);
		double previous = -1.0;
		for (int step = 0; step <= 100; step++) {
			double share = IsometricProjection.depthShare(step / 100.0);
			assertTrue(share > previous, "strictly increasing");
			previous = share;
		}
		// the last third of the time before the onset is nearly half of the way, the first third less than a quarter
		assertTrue(1.0 - IsometricProjection.depthShare(2.0 / 3.0) > 0.45, "the last third covers nearly half of the lane");
		assertTrue(IsometricProjection.depthShare(1.0 / 3.0) < 0.25, "the first third covers little");
		assertEquals(IsometricProjection.depthShare(0.5), (this.projection.approach(250.0, 0.5, CAMERA).y() - this.projection.horizonY()) / (this.projection.hitLineY() - this.projection.horizonY()), 1e-9);
	}

	/** The tilt of the plane is the learner's (owner, 2026-10-11): the scale at the horizon and the height of the horizon, 0.35 and 0.18 by default. */
	@Test
	public void theScaleAtTheHorizonAndTheHeightOfTheHorizonAreSettableAndDefaultToTheOwnersChoice() {
		assertEquals(0.35, IsometricProjection.DEFAULT_FAR_SCALE, 0.0);
		assertEquals(0.18, IsometricProjection.DEFAULT_HORIZON, 0.0);
		assertEquals(0.18 * VIEW.height(), this.projection.horizonY(), 1e-9);
		assertEquals(0.35, this.projection.approach(250.0, 0.0, CAMERA).scale(), 1e-12);
		IsometricProjection flatter = new IsometricProjection(VIEW, 0.75, 0.04);
		assertEquals(0.04 * VIEW.height(), flatter.horizonY(), 1e-9);
		assertEquals(0.75, flatter.approach(250.0, 0.0, CAMERA).scale(), 1e-12);
		assertEquals(0.75, flatter.farScale(), 0.0);
		assertEquals(flatter.hitLineY(), flatter.approach(250.0, 1.0, CAMERA).y(), 1e-9, "the hit line stays where it is");
		assertEquals(0.75, flatter.horizonOrigin(600.0).scale(), 1e-12);
	}
}
