package app.tuxguitar.app.view.toolbar.main;

/**
 * A main tool bar item defined by a plugin (see {@link TGMainToolBarItemRegistry}). Immutable.
 * {@link #getName()} is the name used in tool bar layouts and also the property key of the item's
 * tooltip, like the names of the built-in items (for example {@code transport.start}).
 */
public final class TGMainToolBarPluginItem {

	private final String name;
	private final String group;
	private final String actionName;
	private final String iconFileName;
	private final boolean checkable;
	private final TGMainToolBarItemUpdater updater;
	private final String defaultAfter;

	/**
	 * @param name unique item name, also the tooltip property key
	 * @param group group shown in the tool bar settings dialog
	 * @param actionName the action executed when the item is clicked
	 * @param iconFileName icon resolved like the built-in icons (skins/&lt;skin&gt;/&lt;name&gt;)
	 * @param checkable true for an item that shows a checked state
	 * @param updater enabled / checked state, may be null
	 * @param defaultAfter name of the item after which the item appears in the default layout, or null
	 */
	public TGMainToolBarPluginItem(String name, String group, String actionName, String iconFileName,
			boolean checkable, TGMainToolBarItemUpdater updater, String defaultAfter) {
		if( name == null || name.isEmpty() || actionName == null || actionName.isEmpty() ) {
			throw new IllegalArgumentException("a name and an action are required");
		}
		this.name = name;
		this.group = group;
		this.actionName = actionName;
		this.iconFileName = iconFileName;
		this.checkable = checkable;
		this.updater = updater;
		this.defaultAfter = defaultAfter;
	}

	public String getName() {
		return this.name;
	}

	public String getGroup() {
		return this.group;
	}

	public String getActionName() {
		return this.actionName;
	}

	public String getIconFileName() {
		return this.iconFileName;
	}

	public boolean isCheckable() {
		return this.checkable;
	}

	public TGMainToolBarItemUpdater getUpdater() {
		return this.updater;
	}

	public String getDefaultAfter() {
		return this.defaultAfter;
	}
}
