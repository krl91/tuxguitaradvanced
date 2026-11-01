package app.tuxguitar.audio.engine.monitor;

/**
 * Runs the elements of the chain in order on the output thread: the pedals before the amp, the amp, the cabinet, the pedals after it
 * (spec 022, research R3). An empty slot is a pass-through; with no settings ({@code null}) the chain is dry.
 */
final class Chain {

	private ChainSettings settings;

	void prepare(float sampleRate, int maxBlock) {
	}

	/** Output thread only. */
	void apply(ChainSettings value) {
		this.settings = value;
	}

	ChainSettings settings() {
		return this.settings;
	}

	void process(float[] buffer, int n) {
		// the elements come with the user stories; until then every slot is a pass-through
	}

	void reset() {
	}
}
