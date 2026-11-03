package app.tuxguitar.audio.engine.nativeengine;

/**
 * The loading of the crate {@code tuxguitar-monitor-native} (spec 022, spec 023): one library for the amp sound's duplex device and the native
 * recognition, loaded once from {@code java.library.path} for both.
 */
public final class NativeLibrary {

	public static final String NAME = "tuxguitar_monitor_native";

	private static volatile String loadFailure;
	private static volatile boolean loaded;

	/**
	 * A folder of the crate's builds, given to the tests only ({@code -Dtuxguitar.native.library.dir}): the library is taken from
	 * {@code <folder>/<target>/release/} or {@code <folder>/release/} when found there. Empty or unset: {@code java.library.path}, as in the application.
	 */
	public static final String DIRECTORY_PROPERTY = "tuxguitar.native.library.dir";

	private NativeLibrary() {
	}

	static java.io.File built(String directory) {
		if (directory == null || directory.trim().isEmpty()) {
			return null;
		}
		String file = System.mapLibraryName(NAME);
		java.io.File root = new java.io.File(directory.trim());
		java.io.File[] targets = root.listFiles(java.io.File::isDirectory);
		if (targets != null) {
			java.util.Arrays.sort(targets);
			for (java.io.File target : targets) {
				java.io.File candidate = new java.io.File(new java.io.File(target, "release"), file);
				if (candidate.isFile()) {
					return candidate;
				}
			}
		}
		java.io.File host = new java.io.File(new java.io.File(root, "release"), file);
		return host.isFile() ? host : null;
	}

	/** Loads the library once; null when it is there, else the reason it could not be loaded. */
	public static synchronized String load() {
		if (!loaded && loadFailure == null) {
			try {
				java.io.File built = built(System.getProperty(DIRECTORY_PROPERTY));
				if (built != null) {
					System.load(built.getAbsolutePath());
				} else {
					System.loadLibrary(NAME);
				}
				loaded = true;
			} catch (UnsatisfiedLinkError | SecurityException error) {
				loadFailure = String.valueOf(error.getMessage());
			}
		}
		return loaded ? null : loadFailure;
	}
}
