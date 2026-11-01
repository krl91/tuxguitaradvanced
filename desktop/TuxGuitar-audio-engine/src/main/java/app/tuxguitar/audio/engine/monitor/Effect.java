package app.tuxguitar.audio.engine.monitor;

/**
 * One element of the chain (spec 022, contracts/monitor-engine.md). {@link #prepare} allocates every buffer once; {@link #process} runs on
 * the output thread and never allocates, locks or does I/O. The same input and settings always give the same output.
 */
public interface Effect {

	void prepare(float sampleRate, int maxBlock);

	/** In place allowed ({@code in == out}). */
	void process(float[] in, float[] out, int n);

	/** Clears delay lines and envelopes. */
	void reset();
}
