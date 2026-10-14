package app.tuxguitar.app.tools.custom.practice.ui;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.tuxguitar.ui.UIComponent;
import app.tuxguitar.ui.UIFactory;
import app.tuxguitar.ui.event.UISelectionEvent;
import app.tuxguitar.ui.event.UISelectionListener;
import app.tuxguitar.ui.widget.UIContainer;
import app.tuxguitar.ui.widget.UIScale;
import app.tuxguitar.ui.widget.UISelectItem;

/**
 * A headless implementation of the abstract UI toolkit for tests: every widget keeps its state
 * and listeners, and {@link UIScale} follows the contract of SWT's native {@code Scale} as found in
 * SWT 4.37 cocoa ({@code javap -c org.eclipse.swt.widgets.Scale}): range 0…100 by default,
 * {@code setMinimum} ignored when negative or not below the maximum, {@code setMaximum} ignored
 * when not above the minimum, the selection clamped to the range. Programmatic changes fire no
 * selection event (as in SWT); {@link #userMoves} and {@link #userSelects} simulate the learner.
 *
 * <p>It exercises the real page code; it is not a substitute for the native SWT test
 * ({@code RecognitionSettingsSwtTest}, profile {@code swt-gui-tests}).</p>
 */
final class SwtContractToolkit {

	/** State of one widget. */
	static final class Widget implements InvocationHandler {
		final Class<?> type;
		final Object parent;
		final Map<String, Object> properties = new HashMap<String, Object>();
		final List<UISelectionListener> listeners = new ArrayList<UISelectionListener>();
		final List<Object> children = new ArrayList<Object>();
		final List<UISelectItem<?>> items = new ArrayList<UISelectItem<?>>();
		Object selectedValue;
		int minimum = 0;
		int maximum = 100;
		int value = 0;
		boolean disposed;
		Object proxy;

		Widget(Class<?> type, Object parent) {
			this.type = type;
			this.parent = parent;
			this.properties.put("Visible", Boolean.TRUE);
			this.properties.put("Enabled", Boolean.TRUE);
		}

		public Object invoke(Object proxy, Method method, Object[] args) {
			String name = method.getName();
			int count = (args == null ? 0 : args.length);
			if( method.getDeclaringClass() == Object.class ) {
				if( name.equals("equals") ) {
					return proxy == args[0];
				}
				if( name.equals("hashCode") ) {
					return System.identityHashCode(proxy);
				}
				return this.type.getSimpleName() + this.properties;
			}
			if( UIScale.class.isAssignableFrom(this.type) ) {
				switch( name ) {
					case "setMinimum":
						int minimum = (Integer) args[0];
						if( minimum >= 0 && minimum < this.maximum ) {
							this.minimum = minimum;
							this.value = Math.max(this.value, minimum);
						}
						return null;
					case "setMaximum":
						int maximum = (Integer) args[0];
						if( maximum > this.minimum ) {
							this.maximum = maximum;
							this.value = Math.min(this.value, maximum);
						}
						return null;
					case "setValue":
						this.value = Math.max(this.minimum, Math.min(this.maximum, (Integer) args[0]));
						return null;
					case "getValue":
						return this.value;
					case "getMinimum":
						return this.minimum;
					case "getMaximum":
						return this.maximum;
					default:
						break;
				}
			}
			switch( name ) {
				case "addSelectionListener":
					this.listeners.add((UISelectionListener) args[0]);
					return null;
				case "removeSelectionListener":
					this.listeners.remove(args[0]);
					return null;
				case "addItem":
					this.items.add((UISelectItem<?>) args[0]);
					return null;
				case "removeItems":
					this.items.clear();
					this.selectedValue = null;
					return null;
				case "getItemCount":
					return this.items.size();
				case "setSelectedValue":
					this.selectedValue = null;
					for( UISelectItem<?> item : this.items ) {
						if( item.getValue() != null && item.getValue().equals(args[0]) ) {
							this.selectedValue = item.getValue();
						}
					}
					return null;
				case "getSelectedValue":
					return this.selectedValue;
				case "getParent":
					return this.parent;
				case "dispose":
					this.disposed = true;
					return null;
				case "isDisposed":
					return this.disposed;
				case "getPackedSize":
					// a window refits itself when what it shows changes (WindowFit): the headless toolkit has no layout, a packed size of zero is the honest answer
					return new app.tuxguitar.ui.resource.UISize(0f, 0f);
				case "getBounds":
					Object bounds = this.properties.get("Bounds");
					return bounds != null ? bounds : new app.tuxguitar.ui.resource.UIRectangle(0f, 0f, 0f, 0f);
				default:
					break;
			}
			if( name.startsWith("add") && name.endsWith("Listener") || name.startsWith("remove") && name.endsWith("Listener") ) {
				return null;
			}
			if( name.startsWith("set") && count == 1 ) {
				this.properties.put(name.substring(3), args[0]);
				return null;
			}
			if( (name.startsWith("get") || name.startsWith("is")) && count == 0 ) {
				Object stored = this.properties.get(name.substring(name.startsWith("get") ? 3 : 2));
				return (stored != null ? stored : defaultValue(method.getReturnType()));
			}
			return defaultValue(method.getReturnType());
		}
	}

	private final List<Widget> created = new ArrayList<Widget>();

	/** A factory whose every {@code create…} method returns a new headless widget. */
	UIFactory factory() {
		return (UIFactory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { UIFactory.class }, (proxy, method, args) -> {
			if( method.getDeclaringClass() == Object.class ) {
				return (method.getName().equals("equals") ? proxy == args[0] : method.getName().equals("hashCode") ? System.identityHashCode(proxy) : "factory");
			}
			Object parent = (args != null && args.length > 0 && args[0] instanceof UIContainer ? args[0] : null);
			return create(method.getReturnType(), parent);
		});
	}

	Object create(Class<?> type, Object parent) {
		if( !type.isInterface() ) {
			return defaultValue(type);
		}
		Widget widget = new Widget(type, parent);
		Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { type }, widget);
		widget.proxy = proxy;
		this.created.add(widget);
		if( parent != null ) {
			state(parent).children.add(proxy);
		}
		return proxy;
	}

	static Widget state(Object proxy) {
		return (Widget) Proxy.getInvocationHandler(proxy);
	}

	/** The learner drags a slider to a position (the native selection changes, then the event fires). */
	static void userMoves(UIScale scale, int position) {
		Widget widget = state(scale);
		widget.value = Math.max(widget.minimum, Math.min(widget.maximum, position));
		fire(scale);
	}

	/** The learner clicks a button, a check box or picks a list entry. */
	static void userSelects(Object control) {
		fire(control);
	}

	private static void fire(Object control) {
		for( UISelectionListener listener : new ArrayList<UISelectionListener>(state(control).listeners) ) {
			listener.onSelect(new UISelectionEvent((UIComponent) control));
		}
	}

	/** Every widget created under a container, recursively. */
	static List<Object> descendants(Object container) {
		List<Object> result = new ArrayList<Object>();
		for( Object child : state(container).children ) {
			result.add(child);
			result.addAll(descendants(child));
		}
		return result;
	}

	private static Object defaultValue(Class<?> type) {
		if( type == boolean.class ) {
			return false;
		}
		if( type == int.class || type == long.class || type == short.class || type == byte.class ) {
			return type == long.class ? (Object) 0L : (Object) 0;
		}
		if( type == float.class ) {
			return 0f;
		}
		if( type == double.class ) {
			return 0d;
		}
		return null;
	}
}
