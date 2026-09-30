package app.tuxguitar.app.view.toolbar.main;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * Where plugins add items to the main tool bar. The built-in items are defined inside
 * {@link TGMainToolBarConfigMap}, which a plugin cannot extend; this registry is the extension
 * point. It only holds data: it knows no plugin. One registry per {@link TGContext}.
 *
 * <p>A registered item is offered in the tool bar settings dialog, and appears in the default
 * layout after its {@code defaultAfter} item. A layout the user saved is never modified.</p>
 */
public final class TGMainToolBarItemRegistry {

	private final List<TGMainToolBarPluginItem> items = new CopyOnWriteArrayList<TGMainToolBarPluginItem>();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<Runnable>();

	/** Adds an item, replacing one registered with the same name. */
	public void register(TGMainToolBarPluginItem item) {
		synchronized( this.items ) {
			removeNamed(item.getName());
			this.items.add(item);
		}
		fireChanged();
	}

	/** Removes an item by name; does nothing when it is unknown. */
	public void unregister(String name) {
		boolean removed;
		synchronized( this.items ) {
			removed = removeNamed(name);
		}
		if( removed ) {
			fireChanged();
		}
	}

	public List<TGMainToolBarPluginItem> items() {
		return new ArrayList<TGMainToolBarPluginItem>(this.items);
	}

	/** Told whenever an item is registered or unregistered (the tool bar rebuilds itself). */
	public void addChangeListener(Runnable listener) {
		this.listeners.add(listener);
	}

	public void removeChangeListener(Runnable listener) {
		this.listeners.remove(listener);
	}

	/**
	 * The content of an area of the <b>default</b> layout with the registered items added: each item
	 * with a {@code defaultAfter} goes right after that item, when the item is in this area and the
	 * name is not already in the list. The given list is not modified.
	 */
	public List<String> withDefaultItems(List<String> content) {
		List<String> result = new ArrayList<String>(content);
		for( TGMainToolBarPluginItem item : this.items ) {
			String anchor = item.getDefaultAfter();
			if( anchor == null || result.contains(item.getName()) ) {
				continue;
			}
			int index = result.indexOf(anchor);
			if( index >= 0 ) {
				result.add(index + 1, item.getName());
			}
		}
		return result;
	}

	private boolean removeNamed(String name) {
		boolean removed = false;
		for( TGMainToolBarPluginItem existing : new ArrayList<TGMainToolBarPluginItem>(this.items) ) {
			if( existing.getName().equals(name) ) {
				this.items.remove(existing);
				removed = true;
			}
		}
		return removed;
	}

	private void fireChanged() {
		for( Runnable listener : this.listeners ) {
			try {
				listener.run();
			} catch (RuntimeException exception) {
				// one listener must not prevent the others from being told
			}
		}
	}

	public static TGMainToolBarItemRegistry getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TGMainToolBarItemRegistry.class.getName(),
				new TGSingletonFactory<TGMainToolBarItemRegistry>() {
					public TGMainToolBarItemRegistry createInstance(TGContext context) {
						return new TGMainToolBarItemRegistry();
					}
				});
	}
}
