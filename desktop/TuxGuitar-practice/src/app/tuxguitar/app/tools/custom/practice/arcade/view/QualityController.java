package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * Chooses the quality tier from the measured cost of the last frames (spec 003 FR-054, T040). It looks at render durations only, never at the musical
 * clock. It falls fast, one tier at a time, when the recent average cost is close to the frame budget, and it recovers slowly: only after a long run of
 * cheap frames, and the wait doubles each time a recovery was followed by a new fall, so that it cannot oscillate. Deterministic: the same costs in the
 * same order give the same tiers.
 */
public final class QualityController {

	/** The frames averaged to detect pressure. */
	public static final int WINDOW = 8;
	/** The share of the budget above which the average cost makes the tier fall. */
	public static final double PRESSURE = 0.80;
	/** The share of the budget below which a frame counts towards a recovery. */
	public static final double COMFORT = 0.50;
	/** The cheap frames in a row needed to recover one tier the first time. */
	public static final int RECOVERY_FRAMES = 120;
	/** The longest wait, in frames. */
	public static final int MAX_RECOVERY_FRAMES = 3840;

	private final long budgetNanos;
	private final long[] recent = new long[WINDOW];
	private int count;
	private int cursor;
	private QualityTier tier = QualityTier.FULL;
	private int cheapInARow;
	private int recoveryFrames = RECOVERY_FRAMES;
	private boolean justRecovered;
	private int changes;

	/** @param budgetNanos the time of a frame at the target rate (16.7 ms at 60 frames/s) */
	public QualityController(long budgetNanos) {
		this.budgetNanos = budgetNanos;
	}

	public QualityTier tier() {
		return this.tier;
	}

	/** How many times the tier changed, for the diagnostics. */
	public int changes() {
		return this.changes;
	}

	/** Records the cost of the frame just painted (or skipped, at its cost so far) and returns the tier for the next one. */
	public QualityTier frame(long costNanos) {
		this.recent[this.cursor] = costNanos;
		this.cursor = (this.cursor + 1) % WINDOW;
		this.count = Math.min(WINDOW, this.count + 1);
		if (costNanos <= COMFORT * this.budgetNanos) {
			this.cheapInARow++;
		} else {
			this.cheapInARow = 0;
		}
		if (this.count == WINDOW && average() > PRESSURE * this.budgetNanos && this.tier != QualityTier.PERFORMANCE) {
			this.tier = this.tier.cheaper();
			this.changes++;
			if (this.justRecovered) {
				this.recoveryFrames = Math.min(MAX_RECOVERY_FRAMES, this.recoveryFrames * 2);
			}
			this.justRecovered = false;
			settle();
		} else if (this.cheapInARow >= this.recoveryFrames && this.tier != QualityTier.FULL) {
			this.tier = this.tier.richer();
			this.changes++;
			this.justRecovered = true;
			settle();
		}
		return this.tier;
	}

	private double average() {
		long sum = 0L;
		for (long cost : this.recent) {
			sum += cost;
		}
		return (double) sum / WINDOW;
	}

	/** After a change the old measurements say nothing about the new tier. */
	private void settle() {
		this.count = 0;
		this.cursor = 0;
		this.cheapInARow = 0;
	}
}
