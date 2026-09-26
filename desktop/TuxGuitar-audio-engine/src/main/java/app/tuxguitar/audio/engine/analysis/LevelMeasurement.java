package app.tuxguitar.audio.engine.analysis;

/** Level metrics for one mono analysis window. Immutable. */
public final class LevelMeasurement {

	private final double rmsDbfs;
	private final double peakDbfs;
	private final double clippedRatio;
	private final double exactZeroRatio;
	private final AnalysisFrame.LevelState levelState;

	public LevelMeasurement(double rmsDbfs, double peakDbfs, double clippedRatio, double exactZeroRatio, AnalysisFrame.LevelState levelState) {
		this.rmsDbfs = rmsDbfs;
		this.peakDbfs = peakDbfs;
		this.clippedRatio = clippedRatio;
		this.exactZeroRatio = exactZeroRatio;
		this.levelState = levelState;
	}

	public double getRmsDbfs() {
		return this.rmsDbfs;
	}

	public double getPeakDbfs() {
		return this.peakDbfs;
	}

	public double getClippedRatio() {
		return this.clippedRatio;
	}

	public double getExactZeroRatio() {
		return this.exactZeroRatio;
	}

	public AnalysisFrame.LevelState getLevelState() {
		return this.levelState;
	}

	/** All-zero PCM means no samples were received; it does not prove permission denial. */
	public boolean isNoSignal() {
		return this.exactZeroRatio == 1.0;
	}
}
