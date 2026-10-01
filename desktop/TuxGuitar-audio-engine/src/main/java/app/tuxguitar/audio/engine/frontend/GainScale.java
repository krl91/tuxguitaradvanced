package app.tuxguitar.audio.engine.frontend;

/**
 * The input gain as a slider of whole decibels, shared by the tuner and the practice window so both
 * show the same scale (spec 002, FR-060): position 0 is the minimum gain, {@link #NEUTRAL_POSITION} is 0 dB.
 */
public final class GainScale {

	/** Smallest slider position: −6 dB, the lowest gain {@link InputGain} accepts. */
	public static final int MIN_DB = (int) Math.round(InputGain.toDb(InputGain.MIN_MULTIPLIER));
	/** Largest slider position: +18 dB, the highest gain {@link InputGain} accepts. */
	public static final int MAX_DB = (int) Math.round(InputGain.toDb(InputGain.MAX_MULTIPLIER));
	public static final int POSITIONS = MAX_DB - MIN_DB;
	public static final int NEUTRAL_POSITION = -MIN_DB;

	private GainScale() {
	}

	/** The multiplier of a slider position (0 … {@link #POSITIONS}); an out-of-range position is limited. */
	public static double toMultiplier(int position) {
		int bounded = Math.max(0, Math.min(POSITIONS, position));
		return InputGain.fromDb(MIN_DB + bounded);
	}

	/** The slider position nearest to a multiplier. */
	public static int toPosition(double multiplier) {
		int db = (int) Math.round(InputGain.toDb(multiplier));
		return Math.max(0, Math.min(POSITIONS, db - MIN_DB));
	}

	/** The gain in decibels as shown to the learner, for example "+6 dB", "0 dB", "−3 dB". */
	public static String format(double multiplier) {
		int db = (int) Math.round(InputGain.toDb(multiplier));
		return (db > 0 ? "+" + db : Integer.toString(db)) + " dB";
	}
}
