package app.tuxguitar.audio.engine.calibration;

/**
 * The starting values of the detection of "a sound was played" (spec 013, FR-004), written once and used by the live listening mode and by the offline
 * cutting of the recording. Adjustable after the owner's trial.
 */
public final class SoundDetection {

	/** Two sounds less than this apart (the strings of a strummed chord, a bounce) count as one. */
	public static final int REFRACTORY_MILLIS = 250;
	/**
	 * A segment starts this long before the instant at which the attack was detected: the detector reports an attack some tens of milliseconds after it
	 * began (measured at about 72 ms on a plucked string), and a segment must contain the whole attack.
	 */
	public static final int SEGMENT_LEAD_MILLIS = 150;
	/** The listening stops this long after the last expected sound was heard. */
	public static final int STOP_AFTER_LAST_MILLIS = 1000;
	/** It gives up after this long without a sound. */
	public static final int SILENCE_TIMEOUT_MILLIS = 20000;
	/** It gives up after this long in all. */
	public static final int TOTAL_TIMEOUT_MILLIS = 180000;

	private SoundDetection() {
	}
}
