package app.tuxguitar.audio.engine.technique;

/**
 * The pitch movement written for a bend, for the engine: ordered points of (relative time in the note
 * from 0 to 1, semitones above the fretted pitch). The engine keeps its own small type so that it does
 * not depend on the practice module. Immutable.
 */
public final class BendPath {

	private final double[] times;
	private final double[] semitones;

	private BendPath(double[] times, double[] semitones) {
		this.times = times;
		this.semitones = semitones;
	}

	/**
	 * @param pairs (relative time, semitones) pairs, times strictly increasing within 0 to 1
	 */
	public static BendPath of(double... pairs) {
		if( pairs.length == 0 || pairs.length % 2 != 0 ) {
			throw new IllegalArgumentException("pairs (relative time, semitones) are required");
		}
		int count = pairs.length / 2;
		double[] times = new double[count];
		double[] semitones = new double[count];
		double previous = -1.0;
		for( int i = 0 ; i < count ; i ++ ) {
			double time = pairs[2 * i];
			if( time < 0.0 || time > 1.0 || time <= previous ) {
				throw new IllegalArgumentException("times must be strictly increasing within [0, 1]: " + time);
			}
			times[i] = time;
			semitones[i] = pairs[2 * i + 1];
			previous = time;
		}
		return new BendPath(times, semitones);
	}

	/** Semitones above the fretted pitch when the note starts: above 0 for a pre-bend. */
	public double startSemitones() {
		return this.semitones[0];
	}

	/** The highest point of the path. */
	public double peakSemitones() {
		double peak = Double.NEGATIVE_INFINITY;
		for( double value : this.semitones ) {
			peak = Math.max(peak, value);
		}
		return peak;
	}

	/** Semitones above the fretted pitch at the end of the note: below the peak when the bend is released. */
	public double endSemitones() {
		return this.semitones[this.semitones.length - 1];
	}

	public double lowestSemitones() {
		double lowest = Double.POSITIVE_INFINITY;
		for( double value : this.semitones ) {
			lowest = Math.min(lowest, value);
		}
		return lowest;
	}
}
