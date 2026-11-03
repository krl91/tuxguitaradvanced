package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;

/**
 * The pitch-detection engines that really exist (task T102). {@link #getEngines()} lists the Java pitch estimators: today only FastYin (TarsosDSP).
 * {@link #getRecognitionEngines()} lists the engines of the menu (spec 023): the native engine first when its library loads, then Java; the setting's
 * "Automatic" stands for the first one. Nothing is listed that the code cannot run.
 */
public final class PitchEngineCatalog {

	/** The only choice while a single engine exists: the recommended engine. */
	public static final String AUTOMATIC = "automatic";
	public static final String FASTYIN = "fastyin";
	/** The native engine (spec 023): the analysis in the crate {@code tuxguitar-monitor-native}. */
	public static final String NATIVE = "native";
	/** The system property that overrides Automatic only ({@code java} or {@code native}; empty or blank counts as unset). */
	public static final String ENGINE_PROPERTY = "tuxguitar.recognition.engine";

	/** Where an engine's analysis runs (spec 023). */
	public enum Kind {
		JAVA, NATIVE
	}

	/** The log of the fallback to Java when the native library does not load: one line per log (one per process for the shared one). */
	public static final class FallbackLog {
		private final java.util.function.Consumer<String> sink;
		private final java.util.concurrent.atomic.AtomicBoolean written = new java.util.concurrent.atomic.AtomicBoolean();

		public FallbackLog(java.util.function.Consumer<String> sink) {
			this.sink = sink;
		}

		void libraryMissing(String failure) {
			if (this.written.compareAndSet(false, true)) {
				this.sink.accept("TuxGuitar recognition: the native engine was asked but its library could not be loaded (" + failure + "); the Java engine is used.");
			}
		}
	}

	private static final FallbackLog PROCESS_LOG = new FallbackLog(System.err::println);

	/** One available engine. */
	public interface Engine {
		/** Stable identifier stored in the configuration. */
		String getId();

		/** Display name (not translated: a product name). */
		String getName();

		/** A new estimator for this sample rate, with the standard analysis window and hop. */
		PitchEstimator create(int sampleRate);

		/** Where this engine's analysis runs. */
		default Kind kind() {
			return Kind.JAVA;
		}
	}

	private static final Engine FASTYIN_ENGINE = new Engine() {
		public String getId() {
			return FASTYIN;
		}

		public String getName() {
			return "FastYin";
		}

		public PitchEstimator create(int sampleRate) {
			return new TarsosFastYinEstimator(sampleRate, TarsosFastYinEstimator.DEFAULT_WINDOW_SIZE, TarsosFastYinEstimator.DEFAULT_HOP_SIZE);
		}
	};

	private static final List<Engine> ENGINES = Collections.unmodifiableList(new ArrayList<Engine>(Collections.singletonList(FASTYIN_ENGINE)));

	/** The Java engine as the menu names it (spec 023): FastYin's estimator. */
	private static final Engine JAVA_RECOGNITION_ENGINE = new Engine() {
		public String getId() {
			return FASTYIN;
		}

		public String getName() {
			return "Java (FastYin)";
		}

		public PitchEstimator create(int sampleRate) {
			return FASTYIN_ENGINE.create(sampleRate);
		}
	};

	/** The native engine (spec 023): its analysis runs in the crate; {@code create} gives the Java estimator, for the callers that only need one. */
	private static final Engine NATIVE_ENGINE = new Engine() {
		public String getId() {
			return NATIVE;
		}

		public String getName() {
			return "Native (Rust)";
		}

		public PitchEstimator create(int sampleRate) {
			return FASTYIN_ENGINE.create(sampleRate);
		}

		@Override
		public Kind kind() {
			return Kind.NATIVE;
		}
	};

	private static final List<Engine> RECOGNITION_ENGINES_WITH_NATIVE = Collections.unmodifiableList(java.util.Arrays.asList(NATIVE_ENGINE, JAVA_RECOGNITION_ENGINE));
	private static final List<Engine> RECOGNITION_ENGINES_JAVA_ONLY = Collections.singletonList(JAVA_RECOGNITION_ENGINE);

	private PitchEngineCatalog() {
	}

	/** Every engine the code can run, the recommended one first. */
	public static List<Engine> getEngines() {
		return ENGINES;
	}

	public static Engine getRecommended() {
		return ENGINES.get(0);
	}

	/** The engine for a stored choice; {@link #AUTOMATIC} and unknown identifiers give the recommended one. */
	public static Engine resolve(String id) {
		for( Engine engine : ENGINES ) {
			if( engine.getId().equals(id) ) {
				return engine;
			}
		}
		return getRecommended();
	}

	/** The engines of the menu: the native engine first when its library loads, then Java; Java only when it does not. */
	public static List<Engine> getRecognitionEngines() {
		return recognitionEngines(app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary.load());
	}

	/** The engine "Automatic" stands for: the first one of the menu. */
	public static Engine getRecommendedRecognitionEngine() {
		return getRecognitionEngines().get(0);
	}

	/** @param libraryFailure null when the native library is loaded, else why not */
	static List<Engine> recognitionEngines(String libraryFailure) {
		return (libraryFailure == null ? RECOGNITION_ENGINES_WITH_NATIVE : RECOGNITION_ENGINES_JAVA_ONLY);
	}

	static Engine recommendedRecognitionEngine(String libraryFailure) {
		return recognitionEngines(libraryFailure).get(0);
	}

	/** Why the native engine these settings ask for cannot run (its library does not load), or null when they do not ask for it or it loads. */
	public static String nativeLibraryFailure(RecognitionSettings settings) {
		String choice = settings.get(RecognitionParameters.ENGINE_PITCH);
		String property = System.getProperty(ENGINE_PROPERTY);
		if (!asksForNative(choice, property)) {
			return null;
		}
		return nativeLibraryFailure(choice, property, app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary.load());
	}

	/** Only a hand-made native choice, or Automatic with the property set to native, asks for it: Automatic alone is Java without the library. */
	static String nativeLibraryFailure(String choice, String property, String libraryFailure) {
		return (asksForNative(choice, property) ? libraryFailure : null);
	}

	private static boolean asksForNative(String choice, String property) {
		if (FASTYIN.equals(choice)) {
			return false;
		}
		return NATIVE.equals(choice) || propertyKind(property) == Kind.NATIVE;
	}

	/** The kind of engine these settings run: the stored choice, the system property for Automatic, Java when the native library does not load. */
	public static Kind resolveKind(RecognitionSettings settings) {
		String choice = settings.get(RecognitionParameters.ENGINE_PITCH);
		String property = System.getProperty(ENGINE_PROPERTY);
		Kind wanted = resolveKind(choice, property, null, PROCESS_LOG);
		if (wanted != Kind.NATIVE) {
			return wanted;
		}
		return resolveKind(choice, property, app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary.load(), PROCESS_LOG);
	}

	/**
	 * @param choice the stored choice ({@link #FASTYIN}, {@link #NATIVE}, {@link #AUTOMATIC} or anything else, read as Automatic)
	 * @param property the system property, or null
	 * @param libraryFailure null when the native library is loaded, else why not
	 */
	static Kind resolveKind(String choice, String property, String libraryFailure, FallbackLog log) {
		Kind wanted;
		if (FASTYIN.equals(choice)) {
			wanted = Kind.JAVA;
		} else if (NATIVE.equals(choice)) {
			wanted = Kind.NATIVE;
		} else {
			Kind overridden = propertyKind(property);
			wanted = (overridden != null ? overridden : recommendedRecognitionEngine(libraryFailure).kind());
		}
		if (wanted == Kind.NATIVE && libraryFailure != null) {
			log.libraryMissing(libraryFailure);
			return Kind.JAVA;
		}
		return wanted;
	}

	private static Kind propertyKind(String property) {
		if (property == null) {
			return null;
		}
		String value = property.trim().toLowerCase(java.util.Locale.ROOT);
		if ("java".equals(value)) {
			return Kind.JAVA;
		}
		if (NATIVE.equals(value)) {
			return Kind.NATIVE;
		}
		return null;
	}
}
