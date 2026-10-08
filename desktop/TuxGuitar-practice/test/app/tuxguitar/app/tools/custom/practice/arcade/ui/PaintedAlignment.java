package app.tuxguitar.app.tools.custom.practice.arcade.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.arcade.view.Scene;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem;
import app.tuxguitar.app.tools.custom.practice.arcade.view.SceneItem.Role;

/**
 * The measure of the destination of an incoming note on pixels that a painter really painted (spec 003 FR-066, SC-024): the disk, its cell between the painted
 * fret wires, its painted string, the painted trajectory at the hit line. The pixels come from the real SWT canvas (native test) or from the renderer of the
 * application on a Java2D painter that has the contract of the toolkit's painters (every build).
 */
final class PaintedAlignment {

	private static final int TOLERANCE = 6;
	private static final double PIXELS = 1.5;

	private PaintedAlignment() {
	}

	/** The pixels of the canvas as the painter left them. */
	static final class Picture {
		final int width;
		final int height;
		final int[] rgb;

		Picture(int width, int height, int[] rgb) {
			this.width = width;
			this.height = height;
			this.rgb = rgb;
		}

		int at(int x, int y) {
			return x < 0 || y < 0 || x >= this.width || y >= this.height ? -1 : this.rgb[y * this.width + x];
		}

		boolean near(int x, int y, int colour) {
			int p = at(x, y);
			return p >= 0 && Math.abs(((p >> 16) & 0xFF) - ((colour >> 16) & 0xFF)) <= TOLERANCE && Math.abs(((p >> 8) & 0xFF) - ((colour >> 8) & 0xFF)) <= TOLERANCE
					&& Math.abs((p & 0xFF) - (colour & 0xFF)) <= TOLERANCE;
		}
	}

	/** The columns where a thin vertical line of this colour crosses the row, as the middle of each run of matching pixels. */
	static List<Double> verticalLines(Picture picture, int y, int colourOfLine, int fromX, int toX) {
		List<Double> lines = new ArrayList<Double>();
		int start = -1;
		for (int x = Math.max(0, fromX); x <= Math.min(picture.width - 1, toX) + 1; x++) {
			boolean on = x <= Math.min(picture.width - 1, toX) && lighterThan(picture.at(x, y), colourOfLine);
			if (on && start < 0) {
				start = x;
			} else if (!on && start >= 0) {
				lines.add((start + x - 1) / 2.0 + 0.5);
				start = -1;
			}
		}
		return lines;
	}

	static boolean lighterThan(int pixel, int threshold) {
		return pixel >= 0 && ((pixel >> 16) & 0xFF) >= ((threshold >> 16) & 0xFF) && ((pixel >> 8) & 0xFF) >= ((threshold >> 8) & 0xFF) && (pixel & 0xFF) >= (threshold & 0xFF);
	}

	static String stringRows(Scene scene) {
		StringBuilder rows = new StringBuilder();
		for (SceneItem item : scene.withRole(Role.STRING)) {
			rows.append(item.tgString()).append('=').append(item.value(1)).append(' ');
		}
		return rows.toString();
	}

	static String stringWidths(Scene scene) {
		StringBuilder rows = new StringBuilder();
		for (SceneItem item : scene.withRole(Role.STRING)) {
			rows.append(item.lineWidth()).append(' ');
		}
		return rows.toString();
	}

	/** Measures the painted picture of the scene against what the scene says and against the wires and strings that were painted. */
	static void check(Scene scene, Picture picture, int fret, double progress, String where, boolean cellRequired) throws Exception {
		try {
			measure(scene, picture, fret, progress, where, cellRequired);
		} catch (AssertionError failure) {
			// the picture that failed, for the eye: in the temporary folder, never in the repository
			java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(picture.width, picture.height, java.awt.image.BufferedImage.TYPE_INT_RGB);
			out.setRGB(0, 0, picture.width, picture.height, picture.rgb, 0, picture.width);
			java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"), "arcade-pixels/failed-" + where.replaceAll("[^A-Za-z0-9=.]+", "_") + ".png");
			file.getParentFile().mkdirs();
			javax.imageio.ImageIO.write(out, "png", file);
			throw new AssertionError(failure.getMessage() + " [picture: " + file + "]", failure);
		}
	}

	private static void measure(Scene scene, Picture picture, int fret, double progress, String where, boolean cellRequired) throws Exception {
		SceneItem disk = scene.forEvent(0, Role.TARGET_DISK).get(0);
		int expected = disk.colour();
		int cx = (int) Math.round(disk.value(0));
		int cy = (int) Math.round(disk.value(1));
		double radius = disk.value(2);

		// 1. the painter really put the opaque disk there, in the colour of the string (the system may shift the colours a little: the colour that was painted
		// is the one at the centre, and it must be that of the string within a wide margin, and the same all over the core)
		int colour = picture.at(cx, cy);
		assertTrue(colour >= 0 && Math.abs(((colour >> 16) & 0xFF) - ((expected >> 16) & 0xFF)) <= 48 && Math.abs(((colour >> 8) & 0xFF) - ((expected >> 8) & 0xFF)) <= 48
				&& Math.abs((colour & 0xFF) - (expected & 0xFF)) <= 48, where + ": the pixel at the centre of the disk is " + Integer.toHexString(colour) + ", not near " + Integer.toHexString(expected));
		if (System.getProperty("tuxguitar.arcade.pixels") != null || System.getenv("TUXGUITAR_ARCADE_PIXELS") != null) {
			java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(picture.width, picture.height, java.awt.image.BufferedImage.TYPE_INT_RGB);
			out.setRGB(0, 0, picture.width, picture.height, picture.rgb, 0, picture.width);
			java.io.File file = new java.io.File("/tmp/arcade-pixels/" + where.replaceAll("[^A-Za-z0-9=.]+", "_") + ".png");
			file.getParentFile().mkdirs();
			javax.imageio.ImageIO.write(out, "png", file);
		}

		// 2. the painted disk is measured, not assumed: from the middle of its core, the first light pixel on each side is its outline
		int reach = (int) Math.ceil(radius + 8);
		int outlineLeft = Integer.MIN_VALUE;
		int outlineRight = Integer.MIN_VALUE;
		int outlineTop = Integer.MIN_VALUE;
		int outlineBottom = Integer.MIN_VALUE;
		for (int d = 1; d <= reach; d++) {
			if (outlineLeft == Integer.MIN_VALUE && lighterThan(picture.at(cx - d, cy), 0xE8E8E8)) {
				outlineLeft = cx - d;
			}
			if (outlineRight == Integer.MIN_VALUE && lighterThan(picture.at(cx + d, cy), 0xE8E8E8)) {
				outlineRight = cx + d;
			}
			if (outlineTop == Integer.MIN_VALUE && lighterThan(picture.at(cx, cy - d), 0xE8E8E8)) {
				outlineTop = cy - d;
			}
			if (outlineBottom == Integer.MIN_VALUE && lighterThan(picture.at(cx, cy + d), 0xE8E8E8)) {
				outlineBottom = cy + d;
			}
		}
		assertTrue(outlineLeft != Integer.MIN_VALUE && outlineRight != Integer.MIN_VALUE && outlineTop != Integer.MIN_VALUE && outlineBottom != Integer.MIN_VALUE,
				where + ": the outline of the disk was painted around (" + cx + "," + cy + ") radius " + radius + ": left " + outlineLeft + " right " + outlineRight + " top " + outlineTop
						+ " bottom " + outlineBottom + " pixel at the centre " + Integer.toHexString(colour));
		double paintedX = (outlineLeft + outlineRight + 1) / 2.0;
		double paintedY = (outlineTop + outlineBottom + 1) / 2.0;
		assertEquals(disk.value(0), paintedX, PIXELS, where + ": the disk was painted at x " + paintedX + ", the scene says " + disk.value(0));
		assertEquals(disk.value(1), paintedY, PIXELS, where + ": the disk was painted at y " + paintedY + ", the scene says " + disk.value(1));

		// 3. the painted wires of the neck on both sides of the disk: the row is above the top string, inside the neck
		List<SceneItem> strings = scene.withRole(Role.STRING);
		double first = Math.min(strings.get(0).value(1), strings.get(1).value(1));
		double second = Math.max(strings.get(0).value(1), strings.get(1).value(1));
		int wireRow = (int) Math.round((first + second) / 2.0);
		List<Double> wires = verticalLines(picture, wireRow, 0x707080, 0, picture.width - 1);
		double left = Double.NaN;
		double right = Double.NaN;
		for (double wire : wires) {
			if (wire < paintedX - 2 && (Double.isNaN(left) || wire > left)) {
				left = wire;
			}
			if (wire > paintedX + 2 && (Double.isNaN(right) || wire < right)) {
				right = wire;
			}
		}
		if (!cellRequired && (Double.isNaN(left) || Double.isNaN(right))) {
			// the camera of a far-away next note leaves the wire of this cell out of the picture: only the disk and its string are measured
		} else if (fret == 0) {
			// the open string: the lane before the nut, whose edge is the first painted wire
			assertTrue(!Double.isNaN(right), where + ": a painted wire to the right of the open lane");
			assertTrue(paintedX < right, where + ": the open lane is before the nut");
		} else {
			assertTrue(!Double.isNaN(left) && !Double.isNaN(right), where + ": a painted wire on each side of the disk: " + wires + " around " + paintedX + "; the scene has the nut at " + scene.withRole(Role.NUT).size()
					+ (scene.withRole(Role.NUT).isEmpty() ? "" : " x " + scene.withRole(Role.NUT).get(0).value(0)));
			assertTrue(paintedX > left && paintedX < right, where + ": the disk (" + paintedX + ") is not between its two wires " + left + " and " + right);
			assertEquals((left + right) / 2.0, paintedX, PIXELS, where + ": the disk is not at the middle of its painted cell " + left + " .. " + right);
		}

		// 4. the painted string: beside the disk, on both sides, the one line that crosses the column is the string of the disk (the next strings are a spacing away);
		// its centre is the weighted middle of the rows that differ from the neck
		double sum = 0.0;
		int sides = 0;
		for (int side : new int[] { -1, 1 }) {
			// a column clear of every painted wire (a wire is not a string: it would cross the rows that are measured)
			int probe = (int) Math.round(paintedX + side * (radius + 48));
			for (int d = 30; d <= 120; d += 3) {
				int candidate = (int) Math.round(paintedX + side * (radius + d));
				boolean clear = candidate > picture.width * 0.07 && candidate < picture.width - 1;
				for (double wire : wires) {
					clear &= Math.abs(wire - candidate) > 4.0;
				}
				if (clear) {
					probe = candidate;
					break;
				}
			}
			int[] column = new int[13];
			for (int k = 0; k < column.length; k++) {
				column[k] = picture.at(probe, (int) Math.round(paintedY) - 6 + k);
			}
			int[] sorted = column.clone();
			java.util.Arrays.sort(sorted);
			int background = sorted[sorted.length / 2];
			double weight = 0.0;
			double moment = 0.0;
			for (int k = 0; k < column.length; k++) {
				if (column[k] < 0) {
					continue;
				}
				int deviation = Math.abs(((column[k] >> 16) & 0xFF) - ((background >> 16) & 0xFF)) + Math.abs(((column[k] >> 8) & 0xFF) - ((background >> 8) & 0xFF))
						+ Math.abs((column[k] & 0xFF) - (background & 0xFF));
				if (deviation > 30) {
					weight += deviation;
					moment += deviation * (Math.round(paintedY) - 6 + k + 0.5);
				}
			}
			if (weight > 0) {
				sum += moment / weight;
				sides++;
			}
		}
		assertTrue(sides > 0, where + ": the string of the disk was painted beside it");
		assertEquals(sum / sides, paintedY, PIXELS, where + ": the disk is not on the centre of its painted string (the scene draws the strings at " + stringRows(scene) + " and the disk at " + disk.value(1) + ", tgString " + disk.tgString() + ", line widths "
				+ stringWidths(scene) + ")");

		// 5. the painted trajectory reaches the hit line at the x of the disk
		SceneItem hit = scene.withRole(Role.HIT_LINE).get(0);
		int row = (int) Math.round(hit.value(1)) - 3;
		SceneItem trajectory = scene.forEvent(0, Role.TRAJECTORY).get(0);
		// the painted trajectory (or the marker that has reached the line) is the only thing within a few pixels of the disk's x that differs from the field on the row
		// of the hit line: the field is sampled well to the side, on the same row
		int field = picture.at((int) Math.round(paintedX) + 90, row) >= 0 ? picture.at((int) Math.round(paintedX) + 90, row) : picture.at((int) Math.round(paintedX) - 90, row);
		boolean found = false;
		for (int x = (int) Math.round(paintedX) - 3; x <= (int) Math.round(paintedX) + 3 && !found; x++) {
			int pixel = picture.at(x, row);
			int deviation = Math.abs(((pixel >> 16) & 0xFF) - ((field >> 16) & 0xFF)) + Math.abs(((pixel >> 8) & 0xFF) - ((field >> 8) & 0xFF)) + Math.abs((pixel & 0xFF) - (field & 0xFF));
			found = pixel >= 0 && deviation > 25;
		}
		assertTrue(found, where + ": nothing of the trajectory (or of the marker at the line) was painted around x " + paintedX + " on the row of the hit line");
	}
}
