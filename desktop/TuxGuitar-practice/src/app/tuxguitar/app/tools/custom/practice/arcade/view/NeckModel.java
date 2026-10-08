package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.TrackTuning;

/**
 * The physical neck of the chosen track in model space, in millimetres (spec 003 FR-027, SC-011, visual-design.md). The position of fret {@code n} from
 * the nut is {@code x(n) = L * (1 - 2^(-n/12))} for the scale length {@code L}: the twelfth fret is at {@code L / 2}. Nothing here is taken from the
 * generated concept images. The string count is the track's own; the scale length is that of the instrument family (25.5 inches for six or more
 * strings, 34 inches for four or five); the gauges, the width of the neck and the markers are the usual ones of a real instrument and are a visual
 * model, not a claim about a particular guitar.
 */
public final class NeckModel {

	/** A position inlay: single, or double at the octaves. */
	public static final class Marker {
		private final int fret;
		private final boolean isDouble;

		Marker(int fret, boolean isDouble) {
			this.fret = fret;
			this.isDouble = isDouble;
		}

		public int fret() {
			return this.fret;
		}

		public boolean isDouble() {
			return this.isDouble;
		}
	}

	/** 25.5 inches in millimetres. */
	public static final double GUITAR_SCALE_MM = 647.7;
	/** 34 inches in millimetres. */
	public static final double BASS_SCALE_MM = 863.6;
	/** How far before the nut an open string is drawn, in millimetres. */
	static final double OPEN_STRING_OFFSET_MM = 14.0;

	private static final int[] SINGLE_MARKERS = { 3, 5, 7, 9, 15, 17, 19, 21 };
	private static final int[] DOUBLE_MARKERS = { 12, 24 };

	private final StringLayout layout;
	private final int[] openMidiByRow;
	private final double scaleLength;
	private final int fretCount;
	private final double nutWidth;
	private final double widthAtTwelfth;

	private NeckModel(StringLayout layout, int[] openMidiByRow, double scaleLength, int fretCount, double nutWidth, double widthAtTwelfth) {
		this.layout = layout;
		this.openMidiByRow = openMidiByRow;
		this.scaleLength = scaleLength;
		this.fretCount = fretCount;
		this.nutWidth = nutWidth;
		this.widthAtTwelfth = widthAtTwelfth;
	}

	/** The neck of a track: its strings (string 1 first in the tuning) and a number of frets. */
	public static NeckModel of(TrackTuning tuning, int fretCount) {
		int count = tuning.getStringCount();
		int[] open = tuning.getOpenStringMidi();
		int[] byRow = new int[count];
		StringLayout layout = StringLayout.of(count);
		for (int string = 1; string <= count; string++) {
			byRow[layout.rowOf(string)] = open[string - 1] + tuning.getOffset();
		}
		boolean bass = count <= 5;
		return new NeckModel(layout, byRow, bass ? BASS_SCALE_MM : GUITAR_SCALE_MM, fretCount, bass ? 38.0 : 43.0, bass ? 52.0 : 52.0);
	}

	public StringLayout layout() {
		return this.layout;
	}

	public double scaleLength() {
		return this.scaleLength;
	}

	public int fretCount() {
		return this.fretCount;
	}

	/** The distance from the nut to the fret wire {@code n}. */
	public double fretX(int n) {
		return this.scaleLength * (1.0 - Math.pow(2.0, -n / 12.0));
	}

	/** Where the finger of fret {@code n} goes: between the two wires; for 0, the open string, drawn before the nut. */
	public double fretCentreX(int n) {
		return n <= 0 ? -OPEN_STRING_OFFSET_MM : (fretX(n - 1) + fretX(n)) / 2.0;
	}

	/** The position inlays of the neck, within its frets. */
	public List<Marker> markers() {
		List<Marker> markers = new ArrayList<Marker>();
		for (int fret : SINGLE_MARKERS) {
			if (fret <= this.fretCount) {
				markers.add(new Marker(fret, false));
			}
		}
		for (int fret : DOUBLE_MARKERS) {
			if (fret <= this.fretCount) {
				markers.add(new Marker(fret, true));
			}
		}
		Collections.sort(markers, (a, b) -> Integer.compare(a.fret, b.fret));
		return markers;
	}

	/** The open pitch (MIDI, with the track's offset) of the string of a row, 0 being the lowest. */
	public int openMidi(int row) {
		return this.openMidiByRow[row];
	}

	/** The diameter of the string at a row (0 is the lowest), in millimetres: from the pitch of the open string, thicker for lower strings. */
	public double gauge(int row) {
		return 0.25 + (64 - this.openMidiByRow[row]) * 0.0375;
	}

	/** The width of the neck at a distance from the nut: it widens regularly from the nut to the twelfth fret and goes on. */
	public double widthAt(double xMm) {
		return this.nutWidth + (this.widthAtTwelfth - this.nutWidth) * (xMm / (this.scaleLength / 2.0));
	}

	/** The height of the string of a row above the centre line of the neck at a distance from the nut, in millimetres (the high strings are higher). */
	public double stringY(int row, double xMm) {
		int count = this.layout.count();
		double usable = widthAt(xMm) - 2.0 * 3.5;
		double spacing = count == 1 ? 0.0 : usable / (count - 1);
		return (row - (count - 1) / 2.0) * spacing;
	}
}
