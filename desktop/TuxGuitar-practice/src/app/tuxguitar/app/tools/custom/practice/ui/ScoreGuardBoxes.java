package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The boxes the score fire must not cover (spec 019 FR-005, SC-003, research R8), in the coordinates of the score's canvas, as {x1, y1, x2, y2}: the
 * frame of the event to play and of the next one, as the frame painter draws them, and the practice palette, moved from the screen to the canvas. What is
 * unknown is left out: an event off the visible score, no next event, a closed palette. A box partly outside the canvas is kept as it is: the fire fades
 * near it up to the edge. Pure.
 */
final class ScoreGuardBoxes {

	/** The frame of an event's notes, {x, y, width, height} in the canvas, or null when it is not on the visible score. */
	interface Frames {
		double[] frame(Object event);
	}

	private ScoreGuardBoxes() {
	}

	/**
	 * @param palette the palette's box on the screen, or null
	 * @param originX the screen x of the canvas's top-left corner
	 * @param originY the screen y of the canvas's top-left corner
	 */
	static List<double[]> of(Frames frames, Object current, Object next, PaletteDodge.Box palette, double originX, double originY) {
		List<double[]> boxes = new ArrayList<double[]>();
		addFrame(boxes, frames, current);
		addFrame(boxes, frames, next);
		if (palette != null) {
			double x = palette.x() - originX;
			double y = palette.y() - originY;
			boxes.add(new double[] { x, y, x + palette.width(), y + palette.height() });
		}
		return boxes;
	}

	private static void addFrame(List<double[]> boxes, Frames frames, Object event) {
		double[] frame = (event != null ? frames.frame(event) : null);
		if (frame != null) {
			boxes.add(new double[] { frame[0], frame[1], frame[0] + frame[2], frame[1] + frame[3] });
		}
	}
}
