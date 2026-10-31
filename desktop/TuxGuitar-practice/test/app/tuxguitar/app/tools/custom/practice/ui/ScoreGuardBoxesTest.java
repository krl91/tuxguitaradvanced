package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Spec 019 (T020, SC-003, research R8): the boxes the score fire must not cover, in the coordinates of the score's canvas. */
public class ScoreGuardBoxesTest {

	private static final Object CURRENT = new Object();
	private static final Object NEXT = new Object();
	private static final Object OFF_SCREEN = new Object();

	/** A layout stub: the frame {x, y, width, height} of each event, canvas-local, as the frame painter gives it. */
	private static ScoreGuardBoxes.Frames frames() {
		Map<Object, double[]> frames = new HashMap<Object, double[]>();
		frames.put(CURRENT, new double[] { 120, 300, 40, 60 });
		frames.put(NEXT, new double[] { 180, 300, 30, 60 });
		return frames::get;
	}

	@Test
	public void theFramesOfTheCurrentAndNextEventsAreKeptAsTheyAre() {
		List<double[]> boxes = ScoreGuardBoxes.of(frames(), CURRENT, NEXT, null, 100, 80);
		assertEquals(2, boxes.size());
		assertArrayEquals(new double[] { 120, 300, 160, 360 }, boxes.get(0), 0.0);
		assertArrayEquals(new double[] { 180, 300, 210, 360 }, boxes.get(1), 0.0);
	}

	@Test
	public void thePaletteIsMovedFromTheScreenToTheCanvas() {
		List<double[]> boxes = ScoreGuardBoxes.of(frames(), null, null, new PaletteDodge.Box(900, 700, 200, 60), 100, 80);
		assertEquals(1, boxes.size());
		assertArrayEquals(new double[] { 800, 620, 1000, 680 }, boxes.get(0), 0.0);
	}

	@Test
	public void whatIsUnknownIsLeftOut() {
		assertTrue(ScoreGuardBoxes.of(frames(), null, null, null, 0, 0).isEmpty());
		assertEquals(1, ScoreGuardBoxes.of(frames(), OFF_SCREEN, NEXT, null, 0, 0).size(), "an event off the visible score has no frame");
		assertEquals(1, ScoreGuardBoxes.of(frames(), CURRENT, null, null, 0, 0).size(), "no next event");
		assertTrue(ScoreGuardBoxes.of(frames(), OFF_SCREEN, OFF_SCREEN, null, 0, 0).isEmpty());
	}

	@Test
	public void aBoxPartlyOutsideTheCanvasIsKept() {
		ScoreGuardBoxes.Frames frames = event -> new double[] { -20, 500, 60, 200 };
		List<double[]> boxes = ScoreGuardBoxes.of(frames, CURRENT, null, null, 0, 0);
		assertArrayEquals(new double[] { -20, 500, 40, 700 }, boxes.get(0), 0.0);
	}
}
