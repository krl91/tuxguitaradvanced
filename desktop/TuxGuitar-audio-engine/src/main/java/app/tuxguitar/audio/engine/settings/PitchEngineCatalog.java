package app.tuxguitar.audio.engine.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.analysis.TarsosFastYinEstimator;

/**
 * The pitch-detection engines that really exist (task T102): today only FastYin (TarsosDSP).
 * The setting offers "Automatic", which resolves to the recommended engine; a future engine is
 * added here as another {@link Engine} and becomes an extra choice. Nothing is listed that the
 * code cannot run.
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
			wanted = (overridden != null ? overridden : getRecommended().kind());
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
