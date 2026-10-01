package app.tuxguitar.audio.engine.analysis;

/**
 * What the last level check measured (spec 002, FR-066): the level while the learner played and the
 * background level between notes, both after the input gain that was set. Immutable.
 */
public final class LevelCheckStats {

	private final double playingRmsDbfs;
	private final double playingPeakDbfs;
	private final double quietRmsDbfs;
	private final boolean hasPlaying;
	private final boolean hasQuiet;

	public LevelCheckStats(boolean hasPlaying, double playingRmsDbfs, double playingPeakDbfs, boolean hasQuiet, double quietRmsDbfs) {
		this.hasPlaying = hasPlaying;
		this.playingRmsDbfs = playingRmsDbfs;
		this.playingPeakDbfs = playingPeakDbfs;
		this.hasQuiet = hasQuiet;
		this.quietRmsDbfs = quietRmsDbfs;
	}

	public boolean hasPlaying() {
		return this.hasPlaying;
	}

	/** Mean RMS of the frames where the learner was playing (average of the frame levels in dB). */
	public double getPlayingRmsDbfs() {
		return this.playingRmsDbfs;
	}

	/** The loudest peak seen while playing. */
	public double getPlayingPeakDbfs() {
		return this.playingPeakDbfs;
	}

	public boolean hasQuiet() {
		return this.hasQuiet;
	}

	/** Typical RMS of the frames without playing (the quietest part, to ignore residual ringing). */
	public double getQuietRmsDbfs() {
		return this.quietRmsDbfs;
	}
}
