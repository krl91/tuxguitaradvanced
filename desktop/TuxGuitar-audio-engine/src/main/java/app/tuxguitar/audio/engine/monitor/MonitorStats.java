package app.tuxguitar.audio.engine.monitor;

/** The counters shown in the diagnostic and the estimated delay (spec 022 data-model, FR-002). A snapshot. */
public final class MonitorStats {

	private final long droppedBlocks;
	private final long silentBlocks;
	private final long overBudgetBlocks;
	private final long howls;
	private final long clippedInputBlocks;
	private final double estimatedDelayMillis;

	MonitorStats(long droppedBlocks, long silentBlocks, long overBudgetBlocks, long howls, long clippedInputBlocks, double estimatedDelayMillis) {
		this.droppedBlocks = droppedBlocks;
		this.silentBlocks = silentBlocks;
		this.overBudgetBlocks = overBudgetBlocks;
		this.howls = howls;
		this.clippedInputBlocks = clippedInputBlocks;
		this.estimatedDelayMillis = estimatedDelayMillis;
	}

	/** Blocks dropped because the output was late. */
	public long droppedBlocks() {
		return this.droppedBlocks;
	}

	/** Blocks played as silence because the input was late. */
	public long silentBlocks() {
		return this.silentBlocks;
	}

	public long overBudgetBlocks() {
		return this.overBudgetBlocks;
	}

	public long howls() {
		return this.howls;
	}

	/** Input blocks with a sample at or above -0.1 dBFS. */
	public long clippedInputBlocks() {
		return this.clippedInputBlocks;
	}

	/** Input block + processing block + the output's buffer, in ms; NaN while no output is open. */
	public double estimatedDelayMillis() {
		return this.estimatedDelayMillis;
	}
}
