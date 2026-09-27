package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.pitch.PitchReading;

/**
 * What the tuner shows for one analysis frame (FR-049–FR-051, FR-056). Immutable.
 * In chromatic mode the needle shows the cents from the detected note's own centre; in guided
 * mode it shows the deviation from the target string (the clicked one, else the nearest).
 * The needle deviation is clamped to ±{@link #NEEDLE_RANGE_CENTS}: left = flat, centre = in
 * tune, right = sharp.
 */
public final class TunerDisplay {

	public enum State {
		NO_READING,
		INPUT_TOO_LOW,
		INPUT_CLIPPING,
		FLAT,
		IN_TUNE,
		SHARP
	}

	public static final double NEEDLE_RANGE_CENTS = 50.0;

	private final State state;
	private final PitchReading reading;
	private final double frequencyHz;
	private final double deviationCents;
	private final int targetString;

	private TunerDisplay(State state, PitchReading reading, double frequencyHz, double deviationCents, int targetString) {
		this.state = state;
		this.reading = reading;
		this.frequencyHz = frequencyHz;
		this.deviationCents = deviationCents;
		this.targetString = targetString;
	}

	/**
	 * @param targets guided-mode targets (string 1 first), or null for chromatic mode
	 * @param selectedString the clicked target string (1–6), or 0 to use the nearest one
	 * @param inTuneCents the tuner's in-tune range (shared tolerance policy)
	 */
	public static TunerDisplay of(AnalysisFrame frame, PitchReading reading, int[] targets, int selectedString, double inTuneCents) {
		if( frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING ) {
			return new TunerDisplay(State.INPUT_CLIPPING, PitchReading.noReading(), Double.NaN, 0, selectedString);
		}
		if( !reading.hasPitch() || !reading.isReliable() ) {
			return new TunerDisplay(State.NO_READING, PitchReading.noReading(), Double.NaN, 0, selectedString);
		}
		if( frame.getLevelState() == AnalysisFrame.LevelState.TOO_LOW ) {
			return new TunerDisplay(State.INPUT_TOO_LOW, PitchReading.noReading(), Double.NaN, 0, selectedString);
		}
		double frequency = frame.getF0Hz();
		double deviation = reading.getCents();
		int target = 0;
		if( targets != null ) {
			target = (selectedString > 0 ? selectedString : nearestString(frequency, targets));
			deviation = 1200.0 * Math.log(frequency / hz(targets[target - 1])) / Math.log(2.0);
		}
		State state = (Math.abs(deviation) <= inTuneCents ? State.IN_TUNE : (deviation < 0 ? State.FLAT : State.SHARP));
		return new TunerDisplay(state, reading, frequency, deviation, target);
	}

	private static int nearestString(double frequency, int[] targets) {
		int nearest = 1;
		double best = Double.MAX_VALUE;
		for( int i = 0 ; i < targets.length ; i ++ ) {
			double distance = Math.abs(Math.log(frequency / hz(targets[i])));
			if( distance < best ) {
				best = distance;
				nearest = i + 1;
			}
		}
		return nearest;
	}

	private static double hz(int midi) {
		return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
	}

	public State getState() {
		return this.state;
	}

	/** The detected note (note, octave, own cents); no pitch unless the state is a tuning state. */
	public PitchReading getReading() {
		return this.reading;
	}

	/** NaN without a confident reading. */
	public double getFrequencyHz() {
		return this.frequencyHz;
	}

	/** Deviation shown by the needle, not clamped. */
	public double getDeviationCents() {
		return this.deviationCents;
	}

	/** Needle position from −1 (flat) through 0 (in tune) to +1 (sharp). */
	public double getNeedlePosition() {
		return Math.max(-1.0, Math.min(1.0, this.deviationCents / NEEDLE_RANGE_CENTS));
	}

	/** Target string in guided mode (1–6); 0 in chromatic mode or without a reading. */
	public int getTargetString() {
		return this.targetString;
	}

	public boolean isTuning() {
		return (this.state == State.FLAT || this.state == State.IN_TUNE || this.state == State.SHARP);
	}
}
