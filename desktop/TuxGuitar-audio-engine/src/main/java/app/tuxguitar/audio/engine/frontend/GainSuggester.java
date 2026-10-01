package app.tuxguitar.audio.engine.frontend;

import app.tuxguitar.audio.engine.analysis.LevelCheckStats;

/**
 * Proposes a gain from the statistics of the level check (spec 002, FR-066). The check measured the
 * signal after the gain that was set, so the proposal is that gain corrected by the distance between
 * the measured playing level and a target. It never changes the gain itself.
 */
public final class GainSuggester {

	/** Playing level aimed at (RMS): comfortably inside the "suitable" band (−40 … −6 dBFS). */
	public static final double TARGET_PLAYING_RMS_DBFS = -22.0;
	/** The loudest peak after the gain must stay under this, to leave room before clipping. */
	public static final double MAX_PEAK_DBFS = -3.0;
	/** A suggestion within this many decibels of the current gain counts as "nothing to change". */
	public static final double NO_CHANGE_DB = 1.5;
	/** Below this distance between playing and background level, a boost mostly amplifies noise. */
	public static final double MIN_SIGNAL_TO_NOISE_DB = 20.0;

	private GainSuggester() {
	}

	/**
	 * @param stats what the level check measured, after the gain {@code currentGain}
	 * @param currentGain the gain that was applied during the check
	 * @return the suggestion, or null when the check heard no playing
	 */
	public static GainSuggestion suggest(LevelCheckStats stats, double currentGain) {
		if( stats == null || !stats.hasPlaying() ) {
			return null;
		}
		double changeDb = TARGET_PLAYING_RMS_DBFS - stats.getPlayingRmsDbfs();
		// keep the loudest peak under the limit
		changeDb = Math.min(changeDb, MAX_PEAK_DBFS - stats.getPlayingPeakDbfs());
		double suggested = InputGain.fromDb(InputGain.toDb(currentGain) + changeDb);
		suggested = Math.max(InputGain.MIN_MULTIPLIER, Math.min(InputGain.MAX_MULTIPLIER, suggested));
		double appliedChangeDb = InputGain.toDb(suggested) - InputGain.toDb(currentGain);
		if( Math.abs(appliedChangeDb) < NO_CHANGE_DB ) {
			return new GainSuggestion(currentGain, GainSuggestion.Reason.OK);
		}
		if( appliedChangeDb > 0.0 ) {
			if( isNoiseDominated(stats) ) {
				return new GainSuggestion(suggested, GainSuggestion.Reason.NOISE_DOMINATED);
			}
			return new GainSuggestion(suggested, GainSuggestion.Reason.TOO_LOW);
		}
		return new GainSuggestion(suggested, GainSuggestion.Reason.TOO_HIGH);
	}

	/** True when the playing barely stands out from the background noise, so boosting it amplifies mostly noise. */
	public static boolean isNoiseDominated(LevelCheckStats stats) {
		return stats.hasQuiet() && (stats.getPlayingRmsDbfs() - stats.getQuietRmsDbfs()) < MIN_SIGNAL_TO_NOISE_DB;
	}
}
