package app.tuxguitar.audio.engine.frontend;

/**
 * One step of the input front end (spec 002, contracts/signal-frontend.md): it works in place on a
 * hop of samples, before any analysis. Stages are preallocated and never allocate per hop; they are
 * driven by the analysis thread only.
 */
public interface FrontEndStage {

	/** Processes {@code length} samples of {@code hop} in place. */
	void process(float[] hop, int length);

	/** Forgets all history (a gap in the input, a new input). */
	void reset();

	/** Delay this stage adds, in analysis hops (0 when it works sample by sample). */
	int latencyHops();
}
