package app.tuxguitar.audio.engine.settings;

/** Values laid over the default settings for the input in use (a device profile, spec 013): what {@link RecognitionSettingsStore#setActiveProfile} takes. */
public interface SettingsOverlay {

	/** {@code base} with this overlay's values; every other value is the base's. */
	RecognitionSettings applyTo(RecognitionSettings base);
}
