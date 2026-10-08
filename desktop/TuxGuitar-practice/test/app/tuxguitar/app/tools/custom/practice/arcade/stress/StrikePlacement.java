package app.tuxguitar.app.tools.custom.practice.arcade.stress;

import app.tuxguitar.audio.engine.StressInput;

/**
 * Where, in the stream of samples given to the live chain, the learner of the stress run strikes (spec 003 T047). Pure.
 */
final class StrikePlacement {

	static final int BLOCK = 1024;

	private StrikePlacement() {
	}

	/**
	 * The first sample of a strike. The timeline is aligned on the stream by a whole number of blocks (the nearest to the reading of the player's clock), and the
	 * strike falls at its own time from the start of the timeline, counted in samples: so the recording is the same sequence of samples in every run, shifted by
	 * whole blocks, and the analysis (which works on blocks and hops) sees the same attacks at the same places of its frames.
	 *
	 * @param streamStartNanos the wall time at which the stream of samples began
	 * @param zeroWallNanos the wall time at which the player's timeline was at its start, as the clock of the player gave it when the run was aligned
	 * @param strikeNanos the time of the strike from the start of the timeline
	 */
	static long startSample(long streamStartNanos, long zeroWallNanos, long strikeNanos) {
		long blockNanos = BLOCK * 1_000_000_000L / StressInput.SAMPLE_RATE;
		long zeroBlocks = Math.round((zeroWallNanos - streamStartNanos) / (double) blockNanos);
		return zeroBlocks * BLOCK + Math.round(strikeNanos * (double) StressInput.SAMPLE_RATE / 1e9);
	}
}
