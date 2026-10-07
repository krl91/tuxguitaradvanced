package app.tuxguitar.app.tools.custom.practice.session;

/** The sound of the calibration: a short click through the output the learner hears the backing from (spec 002 T097, FR-059). */
public interface ClickPlayer {

	/**
	 * Plays one short click now.
	 *
	 * @return the wall time ({@code System.nanoTime()} scale) at which the click was handed to the output, or {@link Long#MIN_VALUE} when the click
	 *         could not be played
	 */
	long click();

	/** Makes nothing more sound: no further click, and a click still ringing is cut. */
	void silence();
}
