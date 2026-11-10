package app.tuxguitar.audio.engine.monitor;

/**
 * Opens the package-private {@link Chain} and {@link FeedbackGuard} to {@code fx.GuitarFxReferenceTest} (spec 027, T005), which writes the
 * Java references GuitarFX's Rust port is compared with. Both run in blocks of {@link MonitorEngine#BLOCK_FRAMES} frames, as the engine
 * runs them.
 */
public final class GuitarFxReferenceRuns {

	private GuitarFxReferenceRuns() {
	}

	/** The chain with {@code first}, then {@code second} from the block that starts at {@code changeAt} (none when {@code second} is null). */
	public static float[] chain(ChainSettings first, ChainSettings second, int changeAt, float rate, float[] input) {
		int block = MonitorEngine.BLOCK_FRAMES;
		Chain chain = new Chain();
		chain.prepare(rate, block);
		chain.apply(first);
		float[] output = input.clone();
		float[] buffer = new float[block];
		for( int offset = 0 ; offset < output.length ; offset += block ) {
			if( second != null && offset == changeAt ) {
				chain.apply(second);
			}
			int n = Math.min(block, output.length - offset);
			System.arraycopy(output, offset, buffer, 0, n);
			chain.process(buffer, n);
			System.arraycopy(buffer, 0, output, offset, n);
		}
		return output;
	}

	/** One value per block: 1 once the guard has found a howl (it stays found), 0 before. */
	public static float[] guard(float rate, float[] input) {
		int block = MonitorEngine.BLOCK_FRAMES;
		FeedbackGuard guard = new FeedbackGuard(rate, block);
		float[] found = new float[(input.length + block - 1) / block];
		float[] buffer = new float[block];
		for( int offset = 0, b = 0 ; offset < input.length ; offset += block, b++ ) {
			int n = Math.min(block, input.length - offset);
			System.arraycopy(input, offset, buffer, 0, n);
			found[b] = guard.process(buffer, n) ? 1f : 0f;
		}
		return found;
	}
}
