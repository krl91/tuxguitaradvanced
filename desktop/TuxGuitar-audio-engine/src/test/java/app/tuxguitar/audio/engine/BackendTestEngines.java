package app.tuxguitar.audio.engine;

import java.util.function.IntFunction;

import app.tuxguitar.audio.engine.analysis.ArticulationDetector;
import app.tuxguitar.audio.engine.analysis.PitchEstimator;
import app.tuxguitar.audio.engine.backend.RecognitionBackend;
import app.tuxguitar.audio.engine.backend.RecognitionBackendFactory;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputDeviceCatalog;
import app.tuxguitar.audio.engine.input.SampleRingBuffer;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;

/** Engines for the backend tests of other packages (spec 023): the engine's test constructors and ring buffer are package-private, the public API is not widened. */
public final class BackendTestEngines {

	private BackendTestEngines() {
	}

	/** An engine of the package constructors (the Java backend), with this estimator. */
	public static AudioEngine withEstimator(TGContext context, IntFunction<PitchEstimator> estimators) {
		RecognitionSettings settings = RecognitionSettings.defaults();
		return new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context), AudioEngine.DEFAULT_RING_BUFFER_CAPACITY,
				AudioEngine.levelAnalyzer(settings), new ArticulationDetector(), estimators, null);
	}

	/** An engine that chooses its backend with this factory, following this store. */
	public static AudioEngine withBackends(TGContext context, RecognitionSettingsStore store, IntFunction<PitchEstimator> estimators, RecognitionBackendFactory factory) {
		RecognitionSettings settings = store.get();
		return new AudioEngine(context, new InputDeviceCatalog(), CurrentInputSelection.getInstance(context), AudioEngine.DEFAULT_RING_BUFFER_CAPACITY,
				AudioEngine.levelAnalyzer(settings), AudioEngine.articulationDetector(settings), estimators, store, factory);
	}

	public static SampleRingBuffer ringOf(AudioEngine engine) {
		return engine.getRingBuffer();
	}

	public static RecognitionBackend activeBackendOf(AudioEngine engine) {
		return engine.getActiveBackend();
	}

	/** The consumers of the Java capture: 0 means no Java Sound line was asked for. */
	public static int javaCaptureConsumersOf(AudioEngine engine) {
		return engine.getJavaCaptureConsumerCount();
	}
}
