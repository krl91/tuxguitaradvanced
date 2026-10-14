package app.tuxguitar.audio.engine.analysis;

/**
 * Receives every hop of the signal the analysis sees (after the input front end), on the analysis thread (spec 010, contract invariants 16 and 18). Nothing
 * here may block or allocate per hop. {@link #configure(int)} comes before the first hop, at every reconfiguration of the engine, and for a listener added
 * while the engine runs, always on the analysis thread.
 */
public interface HopListener {

	/** The sample rate of the hops that follow. */
	void configure(int sampleRate);

	/** One hop: {@code length} samples from {@code offset}. The array is reused by the caller: copy what must be kept. */
	void hop(float[] samples, int offset, int length);
}
