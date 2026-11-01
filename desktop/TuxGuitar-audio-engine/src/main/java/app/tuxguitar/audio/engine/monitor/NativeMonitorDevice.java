package app.tuxguitar.audio.engine.monitor;

import java.util.function.Supplier;

/**
 * The duplex device of the sound (spec 022, contracts/monitor-engine.md, research R1 addendum): the guitar's input and the output, opened
 * by the native crate through cpal, beside the recognition's capture. When the library cannot be loaded, opening fails with the reason
 * (FR-013): there is no Java Sound fallback.
 */
public final class NativeMonitorDevice implements MonitorDevice {

	private final Supplier<String> loader;

	public NativeMonitorDevice() {
		this(NativeMonitorLibrary::load);
	}

	/** @param loader loads the library: null when it is there, else the reason it is not */
	NativeMonitorDevice(Supplier<String> loader) {
		this.loader = loader;
	}

	/**
	 * The frames between a sample entering the input and its sound, at the input's rate (research R8): both devices' declared latencies
	 * (the output's counted at the input's rate), the input buffer and the output buffer (one block each) and the output ring's target fill (two blocks as the output
	 * takes its block; measured by the native probe T063 within about a millisecond at 64 and 128 frames).
	 */
	static int estimate(int inputDeclared, int outputDeclared, float inputRate, float outputRate, int blockFrames) {
		float output = outputRate > 0 ? outputDeclared * inputRate / outputRate : outputDeclared;
		return inputDeclared + Math.round(output) + 4 * blockFrames;
	}

	@Override
	public Opened open(String inputName, int inputChannel, String outputName, int blockFrames) throws MonitorDeviceException {
		String failure = this.loader.get();
		if (failure != null) {
			throw new MonitorDeviceException("the sound's device library (" + NativeMonitorLibrary.NAME + ") could not be loaded: " + failure);
		}
		long handle = NativeMonitorLibrary.open(inputName != null ? inputName : "", inputChannel, outputName != null ? outputName : "", blockFrames);
		if (handle == 0) {
			throw new MonitorDeviceException("the sound's device could not be opened");
		}
		float inputRate = NativeMonitorLibrary.sampleRate(handle);
		int latency = estimate(NativeMonitorLibrary.inputLatency(handle), NativeMonitorLibrary.outputLatency(handle), inputRate,
				NativeMonitorLibrary.outputRate(handle), blockFrames);
		return new Opened() {
			private boolean closed;

			@Override
			public float sampleRate() {
				return inputRate;
			}

			@Override
			public int read(float[] block, int n) throws MonitorDeviceException {
				return NativeMonitorLibrary.read(handle, block, n);
			}

			@Override
			public void write(float[] left, float[] right, int n) throws MonitorDeviceException {
				NativeMonitorLibrary.write(handle, left, right, n);
			}

			@Override
			public int estimatedLatencyFrames() {
				return latency;
			}

			@Override
			public long droppedBlocks() {
				return NativeMonitorLibrary.droppedBlocks(handle);
			}

			@Override
			public void close() {
				if (!this.closed) {
					this.closed = true;
					NativeMonitorLibrary.close(handle);
				}
			}
		};
	}
}
