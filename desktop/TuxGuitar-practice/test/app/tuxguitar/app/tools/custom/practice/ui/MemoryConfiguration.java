package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;
import app.tuxguitar.util.properties.TGProperties;
import app.tuxguitar.util.properties.TGPropertiesManager;

/**
 * TuxGuitar's configuration files kept in memory (module → key → value), installed in a
 * {@link TGContext} through the real {@link TGPropertiesManager}: the application's
 * {@code TGConfigManager} path is exercised, and a new context on the same files is a relaunch.
 */
final class MemoryConfiguration {

	final Map<String, Map<String, String>> files = new HashMap<String, Map<String, String>>();

	/** A new application run reading and writing these files. */
	TGContext newRun() {
		TGContext context = new TGContext();
		TGPropertiesManager manager = TGPropertiesManager.getInstance(context);
		manager.setPropertiesFactory(() -> new MapProperties());
		manager.addPropertiesReader(TGConfigManager.RESOURCE, (properties, module) -> {
			Map<String, String> file = this.files.get(module);
			if( file != null ) {
				for( Map.Entry<String, String> entry : file.entrySet() ) {
					properties.setValue(entry.getKey(), entry.getValue());
				}
			}
		});
		manager.addPropertiesWriter(TGConfigManager.RESOURCE, (properties, module) -> {
			Map<String, String> file = new LinkedHashMap<String, String>();
			for( String key : properties.getStringKeys() ) {
				file.put(key, properties.getValue(key));
			}
			this.files.put(module, file);
		});
		return context;
	}

	private static final class MapProperties implements TGProperties {
		private final Map<String, String> values = new LinkedHashMap<String, String>();

		public String getValue(String key) {
			return this.values.get(key);
		}

		public void setValue(String key, String value) {
			this.values.put(key, value);
		}

		public void remove(String key) {
			this.values.remove(key);
		}

		public void clear() {
			this.values.clear();
		}

		public void update(Properties newProperties) {
			for( String key : newProperties.stringPropertyNames() ) {
				this.values.put(key, newProperties.getProperty(key));
			}
		}

		public Set<String> getStringKeys() {
			return this.values.keySet();
		}
	}
}
