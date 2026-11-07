package app.tuxguitar.audio.engine.nativeengine;

import java.util.function.IntSupplier;

/**
 * The entry points of the native recognition (spec 023, contract §1), in the crate {@code tuxguitar-monitor-native} loaded once with the amp sound's
 * device ({@link NativeLibrary}). A handle stands for one opened input and its analysis thread. The records are those of {@link FrameRecords}; the
 * parameters those of {@link NativeParameters}.
 */
public final class NativeRecognitionLibrary {

	private NativeRecognitionLibrary() {
	}

	/**
	 * The live entries as an object (contract §1), so that the native backend can run on a fake in the tests (T042). The default methods call the JNI
	 * ones; {@link #JNI} is that implementation. Its {@code open} loads the library first and fails with LIBRARY_MISSING when it cannot.
	 */
	public interface Api {

		Api JNI = new Api() {
			@Override
			public long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters, double[] tuning)
					throws NativeRecognitionException {
				String failure = load();
				if( failure != null ) {
					throw new NativeRecognitionException(NativeRecognitionException.Reason.LIBRARY_MISSING, failure);
				}
				checkLayout(NativeRecognitionLibrary::tuningLayout);
				return NativeRecognitionLibrary.openTuned(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters, tuning);
			}

			@Override
			public void configureTuning(long handle, double[] tuning) {
				NativeRecognitionLibrary.configureTuning(handle, tuning);
			}
		};

		/** Opens with the tuning array (spec 026); by default today's {@code open}, which uses the crate's default tuning. */
		default long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters, double[] tuning)
				throws NativeRecognitionException {
			return open(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters);
		}

		/** Replaces the tuning array of a running analysis (spec 026); by default nothing. */
		default void configureTuning(long handle, double[] tuning) {
		}

		default long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException {
			String failure = load();
			if( failure != null ) {
				throw new NativeRecognitionException(NativeRecognitionException.Reason.LIBRARY_MISSING, failure);
			}
			return NativeRecognitionLibrary.open(inputName, inputChannel, blockFrames, javaNanosAtOpen, parameters);
		}

		default float sampleRate(long handle) {
			return NativeRecognitionLibrary.sampleRate(handle);
		}

		default void configure(long handle, float[] parameters) {
			NativeRecognitionLibrary.configure(handle, parameters);
		}

		default void setSpectrumEnabled(long handle, boolean on) {
			NativeRecognitionLibrary.setSpectrumEnabled(handle, on);
		}

		default void setExpectedPitches(long handle, int[] midi) {
			NativeRecognitionLibrary.setExpectedPitches(handle, midi);
		}

		default void setExpectedChord(long handle, int[] required, int[] tolerated) {
			NativeRecognitionLibrary.setExpectedChord(handle, required, tolerated);
		}

		default void setAttackCandidates(long handle, int[] pitches) {
			NativeRecognitionLibrary.setAttackCandidates(handle, pitches);
		}

		default int nextFrames(long handle, float[] records, int timeoutMillis) {
			return NativeRecognitionLibrary.nextFrames(handle, records, timeoutMillis);
		}

		default int nextHops(long handle, float[] hops, int timeoutMillis) {
			return NativeRecognitionLibrary.nextHops(handle, hops, timeoutMillis);
		}

		default void setHopsEnabled(long handle, boolean on) {
			NativeRecognitionLibrary.setHopsEnabled(handle, on);
		}

		default long droppedBlocks(long handle) {
			return NativeRecognitionLibrary.droppedBlocks(handle);
		}

		default void close(long handle) {
			NativeRecognitionLibrary.close(handle);
		}

		default float[] strumClip(long handle) {
			return NativeRecognitionLibrary.strumClip(handle);
		}

		default void resetStrumClip(long handle) {
			NativeRecognitionLibrary.resetStrumClip(handle);
		}

		default float[] stats(long handle) {
			return NativeRecognitionLibrary.stats(handle);
		}
	}

	/**
	 * The version check of a loaded library (spec 026, contracts/native-parameters.md "Version check"): its tuning layout must be
	 * {@link NativeParameters#TUNING_LENGTH}, else it is refused as missing and the run falls back to the Java engine.
	 */
	static void checkLayout(IntSupplier layout) throws NativeRecognitionException {
		int length;
		try {
			length = layout.getAsInt();
		} catch (UnsatisfiedLinkError older) {
			throw new NativeRecognitionException(NativeRecognitionException.Reason.LIBRARY_MISSING, "native library too old: no tuning layout");
		}
		if( length != NativeParameters.TUNING_LENGTH ) {
			throw new NativeRecognitionException(NativeRecognitionException.Reason.LIBRARY_MISSING,
					"native library tuning layout " + length + ", expected " + NativeParameters.TUNING_LENGTH);
		}
	}

	/** Loads the library once; null when it is there, else the reason it could not be loaded. */
	public static String load() {
		return NativeLibrary.load();
	}

	/** Opens the input ({@code ""} the default, channel −1 the first) at the device's current rate and starts the analysis thread. */
	public static native long open(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters) throws NativeRecognitionException;

	/** As {@link #open}, with the tuning array of {@link NativeParameters#tuning} (spec 026). */
	public static native long openTuned(String inputName, int inputChannel, int blockFrames, long javaNanosAtOpen, float[] parameters, double[] tuning)
			throws NativeRecognitionException;

	/** The length of the tuning array this library reads ({@link NativeParameters#TUNING_LENGTH} when it matches). */
	public static native int tuningLayout();

	/** Replaces the tuning array of a running analysis, through the same mailbox as {@link #configure}. */
	public static native void configureTuning(long handle, double[] tuning);

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

	/** The strum clip measure's reading: verdict ordinal, clipped share, mean level in dBFS (NaN before a verdict). */
	public static native float[] strumClip(long handle);

	/** Starts a new strum clip check. */
	public static native void resetStrumClip(long handle);

	/** The dropped blocks, then the median and 99th percentile of one hop's analysis cost in nanoseconds since open. */
	public static native float[] stats(long handle);

	/**
	 * Offline: the records of the whole recording, window 2048 and hop 256, no front end; deterministic. {@code expectedPitches} (null or empty: none)
	 * adds the target evidence, for the parity tests; {@code chordRequired} null: no chord evidence.
	 */
	public static native float[] analyze(float[] mono, int rate, float[] parameters, boolean spectrum, int[] attackCandidates, int[] expectedPitches,
			int[] chordRequired, int[] chordTolerated);

	/** As {@link #analyze}, with the tuning array (spec 026): the parity tests of one non-default value per family. */
	public static native float[] analyzeTuned(float[] mono, int rate, float[] parameters, double[] tuning, boolean spectrum, int[] attackCandidates,
			int[] expectedPitches, int[] chordRequired, int[] chordTolerated);

	/** Test only: the hops of {@code mono} (hop 256) after the native front end set from {@code parameters}. */
	public static native float[] frontEnd(float[] mono, int rate, float[] parameters);

	/** Test only: the final reading of the native strum clip measure fed {@code mono} hop by hop (hop 256), as {@link #strumClip}. */
	public static native float[] strumClipOffline(float[] mono, int rate, float[] parameters);

	/*
	 * The analysis of a stream captured by Java, hop by hop (amendment of 2026-11-03, owner, during T039): with the native engine, a Java capture (the
	 * test inputs, or the Java capture when the native one cannot open) is analysed by the same native stages as the offline analysis, so the two give
	 * the same frames. No front end (Java applies it before the hop), no timestamps (Java dates the frames).
	 */

	/** A per-hop analysis at {@code rate} with these parameters ({@link NativeParameters}); closed by {@link #closeAnalysis}. */
	public static native long openAnalysis(int rate, float[] parameters) throws NativeRecognitionException;

	/** As {@link #openAnalysis}, with the tuning array (spec 026). */
	public static native long openAnalysisTuned(int rate, float[] parameters, double[] tuning) throws NativeRecognitionException;

	/** Spectrum on or off; switched on, it starts from an empty history (as {@code AnalysisService}). */
	public static native void setAnalysisSpectrum(long handle, boolean on);

	/** The attack candidates (null or empty: off); each call starts the attack analysis afresh. */
	public static native void setAnalysisAttackCandidates(long handle, int[] pitches);

	/** The expected pitches of the target evidence (null or empty: none). */
	public static native void setAnalysisExpectedPitches(long handle, int[] midi);

	/** One hop of 256 samples: the length of the record written into {@code record}, 0 while the window is not full, -1 when it does not fit. */
	public static native int analyzeHop(long handle, float[] hop, float[] record);

	/** Forgets the window and every history (a gap in the input). */
	public static native void resetAnalysis(long handle);

	public static native void closeAnalysis(long handle);
}
