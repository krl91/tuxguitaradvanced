package app.tuxguitar.audio.engine.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * The persisted audio-recognition settings of one {@code TGContext} (task T102), shared by the
 * tuner, practice and the audio engine. Loaded once (validated and repaired, never failing),
 * replaced as a whole by {@link #update}, and saved in the module configuration
 * {@value #MODULE}. Readers get an immutable snapshot through a volatile field: no lock and no
 * allocation for a reader. Listeners are told after a change, on the thread that made it.
 */
public final class RecognitionSettingsStore {

	/** Configuration module (file) of these settings. */
	public static final String MODULE = "tuxguitar-audio-recognition";

	/** Where the text values live; a TuxGuitar configuration file in the application, a map in tests. */
	public interface Persistence {
		Map<String, String> read();

		void write(Map<String, String> values);
	}

	public interface Listener {
		void settingsChanged(RecognitionSettings previous, RecognitionSettings current);
	}

	private final Persistence persistence;
	private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
	private final RecognitionSettings.Loaded loaded;
	/** The default layer: what the Configuration window edits and the file holds. */
	private volatile RecognitionSettings defaults;
	/** Values laid over the default layer for the input in use (a device profile, spec 013), or null. */
	private volatile SettingsOverlay overlay;
	/** The effective settings readers get: the default layer with the overlay, kept ready so that a reader does not allocate. */
	private volatile RecognitionSettings settings;

	public RecognitionSettingsStore(Persistence persistence) {
		this.persistence = persistence;
		Map<String, String> stored;
		try {
			stored = persistence.read();
		} catch (RuntimeException exception) {
			// an unreadable configuration must not prevent the tuner or practice from working
			stored = null;
		}
		this.loaded = RecognitionSettings.fromStrings(stored);
		this.defaults = this.loaded.getSettings();
		this.settings = this.defaults;
	}

	public static RecognitionSettingsStore getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, RecognitionSettingsStore.class.getName(), new TGSingletonFactory<RecognitionSettingsStore>() {
			@Override
			public RecognitionSettingsStore createInstance(TGContext singletonContext) {
				return new RecognitionSettingsStore(new ConfigPersistence(singletonContext));
			}
		});
	}

	/**
	 * The effective settings: the default layer, with the active profile's values laid over it when there is one (spec 013). With no profile this is the
	 * default layer itself. Every reader calls this.
	 */
	public RecognitionSettings get() {
		return this.settings;
	}

	/**
	 * The default layer, what the Configuration window edits and what is saved. A writer starts from this, never from {@link #get()}: otherwise the values
	 * of the active profile would be copied into the default.
	 */
	public RecognitionSettings getDefault() {
		return this.defaults;
	}

	/** What validation repaired when the configuration was read (for diagnostics and tests). */
	public RecognitionSettings.Loaded getLoaded() {
		return this.loaded;
	}

	/**
	 * Replaces the default layer, saves it and notifies the listeners when the effective settings changed (with no profile, when the settings changed).
	 * The listeners are told the effective settings before and after.
	 */
	public void update(RecognitionSettings settings) {
		if( settings == null ) {
			throw new IllegalArgumentException("settings are required");
		}
		RecognitionSettings previous;
		RecognitionSettings current;
		synchronized (this) {
			previous = this.settings;
			this.defaults = settings;
			current = effective(settings, this.overlay);
			this.settings = current;
			try {
				this.persistence.write(settings.toStrings());
			} catch (RuntimeException exception) {
				// the values still apply for this run; the file is rewritten on the next change
			}
		}
		if( !previous.equals(current) ) {
			for( Listener listener : this.listeners ) {
				listener.settingsChanged(previous, current);
			}
		}
	}

	/**
	 * Lays a device profile over the default layer (null for none): the readers then get the profile's values for the profile parameters and the
	 * default's for everything else. Nothing is written to the recognition file; the listeners are told when the effective settings change.
	 */
	public void setActiveProfile(SettingsOverlay profile) {
		RecognitionSettings previous;
		RecognitionSettings current;
		synchronized (this) {
			previous = this.settings;
			this.overlay = profile;
			current = effective(this.defaults, profile);
			this.settings = current;
		}
		if( !previous.equals(current) ) {
			for( Listener listener : this.listeners ) {
				listener.settingsChanged(previous, current);
			}
		}
	}

	private static RecognitionSettings effective(RecognitionSettings defaults, SettingsOverlay overlay) {
		return overlay == null ? defaults : overlay.applyTo(defaults);
	}

	/** Restores every recommended value of the default layer. */
	public void restoreDefaults() {
		update(RecognitionSettings.defaults());
	}

	/** Restores the recommended values of one section of the default layer. */
	public void restoreDefaults(Section section) {
		update(this.defaults.withDefaults(section));
	}

	public void addListener(Listener listener) {
		if( !this.listeners.contains(listener) ) {
			this.listeners.add(listener);
		}
	}

	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}

	/** The application configuration file of {@link #MODULE}; only recognition keys are kept. */
	static final class ConfigPersistence implements Persistence {
		private final TGContext context;

		ConfigPersistence(TGContext context) {
			this.context = context;
		}

		public Map<String, String> read() {
			TGConfigManager config = new TGConfigManager(this.context, MODULE);
			Map<String, String> values = new LinkedHashMap<String, String>();
			if( config.getProperties().getStringKeys() != null ) {
				for( String key : config.getProperties().getStringKeys() ) {
					values.put(key, config.getProperties().getValue(key));
				}
			}
			return values;
		}

		public void write(Map<String, String> values) {
			TGConfigManager config = new TGConfigManager(this.context, MODULE);
			// rewritten as a whole: keys of other versions are dropped (lib TGConfigManager note)
			config.clear();
			for( Map.Entry<String, String> entry : values.entrySet() ) {
				config.setValue(entry.getKey(), entry.getValue());
			}
			config.save();
		}
	}
}
