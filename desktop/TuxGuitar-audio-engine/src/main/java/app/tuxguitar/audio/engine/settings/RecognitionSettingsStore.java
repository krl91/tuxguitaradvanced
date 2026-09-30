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
		this.settings = this.loaded.getSettings();
	}

	public static RecognitionSettingsStore getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, RecognitionSettingsStore.class.getName(), new TGSingletonFactory<RecognitionSettingsStore>() {
			@Override
			public RecognitionSettingsStore createInstance(TGContext singletonContext) {
				return new RecognitionSettingsStore(new ConfigPersistence(singletonContext));
			}
		});
	}

	/** The current validated settings. */
	public RecognitionSettings get() {
		return this.settings;
	}

	/** What validation repaired when the configuration was read (for diagnostics and tests). */
	public RecognitionSettings.Loaded getLoaded() {
		return this.loaded;
	}

	/** Replaces the settings, saves them and notifies the listeners when they changed. */
	public void update(RecognitionSettings settings) {
		if( settings == null ) {
			throw new IllegalArgumentException("settings are required");
		}
		RecognitionSettings previous;
		synchronized (this) {
			previous = this.settings;
			this.settings = settings;
			try {
				this.persistence.write(settings.toStrings());
			} catch (RuntimeException exception) {
				// the values still apply for this run; the file is rewritten on the next change
			}
		}
		if( !previous.equals(settings) ) {
			for( Listener listener : this.listeners ) {
				listener.settingsChanged(previous, settings);
			}
		}
	}

	/** Restores every recommended value. */
	public void restoreDefaults() {
		update(RecognitionSettings.defaults());
	}

	/** Restores the recommended values of one section. */
	public void restoreDefaults(Section section) {
		update(this.settings.withDefaults(section));
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
