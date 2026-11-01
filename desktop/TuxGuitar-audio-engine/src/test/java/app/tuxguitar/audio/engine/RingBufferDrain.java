package app.tuxguitar.audio.engine;

/** Whether the analysis has read everything written to the engine's ring buffer (spec 022 T009); here because the ring buffer is package-private. */
public final class RingBufferDrain {

	private RingBufferDrain() {
	}

	public static boolean drained(AudioEngine engine) {
		return engine.getRingBuffer().availableToRead() == 0;
	}
}
