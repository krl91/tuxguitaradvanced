package app.tuxguitar.audio.engine.monitor;

/**
 * The entry points of the native duplex device (spec 022, research R1 addendum): the Rust crate {@code tuxguitar-monitor-native}, loaded
 * from {@code java.library.path} as TuxGuitar's other native libraries are. A handle stands for one opened device; every call for a
 * handle comes from the engine's one thread.
 */
final class NativeMonitorLibrary {

	static final String NAME = "tuxguitar_monitor_native";

	private static volatile String loadFailure;
	private static volatile boolean loaded;

	private NativeMonitorLibrary() {
	}

	/** Loads the library once; null when it is there, else the reason it could not be loaded. */
	static synchronized String load() {
		if (!loaded && loadFailure == null) {
			try {
				System.loadLibrary(NAME);
				loaded = true;
			} catch (UnsatisfiedLinkError | SecurityException error) {
				loadFailure = String.valueOf(error.getMessage());
			}
		}
		return loaded ? null : loadFailure;
	}

	static native long open(String inputName, int inputChannel, String outputName, int blockFrames) throws MonitorDeviceException;

	static native float sampleRate(long handle);

	static native float outputRate(long handle);

	static native int inputLatency(long handle);

	static native int outputLatency(long handle);

	static native long droppedBlocks(long handle);

	static native int read(long handle, float[] block, int frames) throws MonitorDeviceException;

	static native void write(long handle, float[] left, float[] right, int frames) throws MonitorDeviceException;

	static native void close(long handle);
}
