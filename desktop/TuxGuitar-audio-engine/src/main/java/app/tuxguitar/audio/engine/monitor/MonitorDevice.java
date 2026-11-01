package app.tuxguitar.audio.engine.monitor;

/**
 * The only place that touches the hardware (spec 022, contracts/monitor-engine.md, research R1 addendum): a duplex device that opens the
 * sound's own input, beside the recognition's capture, and the output. Opened on the engine's thread.
 */
public interface MonitorDevice {

	/** {@code ""} for the system's default input or output; {@code inputChannel} −1 for the first channel. */
	Opened open(String inputName, int inputChannel, String outputName, int blockFrames) throws MonitorDeviceException;

	interface Opened extends AutoCloseable {

		/** The input device's current rate, at which the chain runs (the device resamples to the output's rate). */
		float sampleRate();

		/** Reads one mono block; waits for at most about one block time. Returns the frames read, 0 when no input came (a gap). */
		int read(float[] block, int n) throws MonitorDeviceException;

		/** Writes one stereo block; never waits longer than about one block time. */
		void write(float[] left, float[] right, int n) throws MonitorDeviceException;

		/** The frames between a sample entering the input and its sound: both devices' declared latencies and both buffers. */
		int estimatedLatencyFrames();

		/** Input blocks dropped because they were not read in time (more than two waiting). */
		long droppedBlocks();

		@Override
		void close();
	}
}
