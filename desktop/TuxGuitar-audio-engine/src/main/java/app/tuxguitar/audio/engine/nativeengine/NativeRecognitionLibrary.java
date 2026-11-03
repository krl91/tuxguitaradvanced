package app.tuxguitar.audio.engine.nativeengine;

/**
 * The entry points of the native recognition (spec 023, contract §1), in the crate {@code tuxguitar-monitor-native} loaded once with the amp sound's
 * device ({@link NativeLibrary}). A handle stands for one opened input and its analysis thread. The records are those of {@link FrameRecords}; the
 * parameters those of {@link NativeParameters}.
 */
public final class NativeRecognitionLibrary {

	private NativeRecognitionLibrary() {
	}

	/** Loads the library once; null when it is there, else the reason it could not be loaded. */
	public static String load() {
		return NativeLibrary.load();
	}

	/** Opens the input ({@code ""} the default, channel −1 the first) at the device's current rate and starts the analysis thread. */
	public static native long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException;

	public static native float sampleRate(long handle);

	public static native void configure(long handle, float[] parameters);

	public static native void setSpectrumEnabled(long handle, boolean on);

	/** null or empty: none. */
	public static native void setExpectedPitches(long handle, int[] midi);

	/** null required: none. */
	public static native void setExpectedChord(long handle, int[] required, int[] tolerated);

	/** null or empty: off. */
	public static native void setAttackCandidates(long handle, int[] pitches);

	/** The number of whole records copied; 0 on timeout, −1 once the stream stopped, −2 after close. */
	public static native int nextFrames(long handle, float[] records, int timeoutMillis);

	/** As {@link #nextFrames}, for the hop blocks (produced only while enabled). */
	public static native int nextHops(long handle, float[] hops, int timeoutMillis);

	public static native void setHopsEnabled(long handle, boolean on);

	public static native long droppedBlocks(long handle);

	/** Idempotent. */
	public static native void close(long handle);

	/** Offline: the records of the whole recording, window 2048 and hop 256, no front end; deterministic. */
	public static native float[] analyze(float[] mono, int rate, float[] parameters, boolean spectrum, int[] attackCandidates, int[] chordRequired,
			int[] chordTolerated);
}
