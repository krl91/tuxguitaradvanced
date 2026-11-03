package app.tuxguitar.audio.engine.backend;

import java.util.function.Supplier;

import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.nativeengine.NativeRecognitionLibrary;
import app.tuxguitar.audio.engine.settings.PitchEngineCatalog;
import app.tuxguitar.audio.engine.settings.RecognitionSettings;
import app.tuxguitar.util.TGContext;

/**
 * How {@code AudioEngine} chooses between the backends (spec 023, contract §2). Only {@code AudioEngine.getInstance} gives one ({@link #PRODUCTION}); the
 * engines of the package constructors have none and keep the Java backend, so a test's fake input is never bypassed.
 */
public interface RecognitionBackendFactory {

	RecognitionBackendFactory PRODUCTION = new RecognitionBackendFactory() {
	};

	/** The engine these settings run (the setting, the system property, whether the library loads). */
	default PitchEngineCatalog.Kind resolveKind(RecognitionSettings settings) {
		return PitchEngineCatalog.resolveKind(settings);
	}

	/** Why the native engine these settings ask for cannot run (its library does not load), or null: the run then tells it with a notice. */
	default String nativeLibraryFailure(RecognitionSettings settings) {
		return PitchEngineCatalog.nativeLibraryFailure(settings);
	}

	/** A native backend on the practice input; {@code parameters} gives the values of {@code configure} at each open. */
	default NativeBackend createNative(TGContext context, CurrentInputSelection selection, Supplier<float[]> parameters) {
		return new NativeBackend(context, selection, NativeRecognitionLibrary.Api.JNI, parameters);
	}
}
