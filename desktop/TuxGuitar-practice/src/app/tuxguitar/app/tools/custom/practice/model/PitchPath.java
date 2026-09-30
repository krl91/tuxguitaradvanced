package app.tuxguitar.app.tools.custom.practice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pitch movement written for a note, for example a bend: ordered points of (relative time in
 * the note from 0 to 1, semitones above the fretted pitch). Immutable.
 */
public final class PitchPath {

	public static final class Point {
		private final double relativeTime;
		private final double semitones;

		private Point(double relativeTime, double semitones) {
			this.relativeTime = relativeTime;
			this.semitones = semitones;
		}

		public double getRelativeTime() {
			return this.relativeTime;
		}

		public double getSemitones() {
			return this.semitones;
		}
	}

	private final List<Point> points;

	private PitchPath(List<Point> points) {
		this.points = Collections.unmodifiableList(points);
	}

	/**
	 * @param values pairs (relative time, semitones), times strictly increasing within 0…1
	 */
	public static PitchPath of(double... values) {
		if( values.length == 0 || values.length % 2 != 0 ) {
			throw new IllegalArgumentException("pairs (relative time, semitones) are required");
		}
		List<Point> points = new ArrayList<Point>();
		double previousTime = -1.0;
		for( int i = 0 ; i < values.length ; i += 2 ) {
			double time = values[i];
			if( time < 0.0 || time > 1.0 || time <= previousTime ) {
				throw new IllegalArgumentException("times must be strictly increasing within [0, 1]: " + time);
			}
			points.add(new Point(time, values[i + 1]));
			previousTime = time;
		}
		return new PitchPath(points);
	}

	public List<Point> getPoints() {
		return this.points;
	}

	/** Semitones reached at the end of the path. */
	public double getTargetSemitones() {
		return this.points.get(this.points.size() - 1).getSemitones();
	}

	/** The largest number of semitones above the fretted pitch reached along the path. */
	public double getPeakSemitones() {
		double peak = Double.NEGATIVE_INFINITY;
		for( Point point : this.points ) {
			peak = Math.max(peak, point.getSemitones());
		}
		return peak;
	}

	/** Semitones at a relative time, interpolated linearly between the points (clamped to the ends). */
	public double semitonesAt(double relativeTime) {
		Point first = this.points.get(0);
		if( relativeTime <= first.getRelativeTime() ) {
			return first.getSemitones();
		}
		for( int i = 1 ; i < this.points.size() ; i ++ ) {
			Point next = this.points.get(i);
			if( relativeTime <= next.getRelativeTime() ) {
				Point previous = this.points.get(i - 1);
				double span = next.getRelativeTime() - previous.getRelativeTime();
				double part = (relativeTime - previous.getRelativeTime()) / span;
				return previous.getSemitones() + part * (next.getSemitones() - previous.getSemitones());
			}
		}
		return getTargetSemitones();
	}
}
