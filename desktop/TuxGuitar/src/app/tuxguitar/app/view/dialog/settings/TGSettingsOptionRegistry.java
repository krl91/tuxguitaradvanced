package app.tuxguitar.app.view.dialog.settings;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * Configuration-window options contributed by plugins. The window's own options are fixed in
 * {@link TGSettingsEditor}; plugins register a factory when connected and remove it when
 * disconnected, and the window appends the registered options after its own, in registration
 * order.
 */
public class TGSettingsOptionRegistry {

	private final List<TGSettingsOptionFactory> factories = new ArrayList<TGSettingsOptionFactory>();

	public static TGSettingsOptionRegistry getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGSettingsOptionRegistry.class.getName(), new TGSingletonFactory<TGSettingsOptionRegistry>() {
			public TGSettingsOptionRegistry createInstance(TGContext context) {
				return new TGSettingsOptionRegistry();
			}
		});
	}

	public synchronized void register(TGSettingsOptionFactory factory) {
		for( TGSettingsOptionFactory registered : this.factories ) {
			if( registered.getId().equals(factory.getId()) ) {
				return;
			}
		}
		this.factories.add(factory);
	}

	public synchronized void unregister(String id) {
		for( int i = this.factories.size() - 1 ; i >= 0 ; i -- ) {
			if( this.factories.get(i).getId().equals(id) ) {
				this.factories.remove(i);
			}
		}
	}

	public synchronized List<TGSettingsOptionFactory> getFactories() {
		return new ArrayList<TGSettingsOptionFactory>(this.factories);
	}
}
