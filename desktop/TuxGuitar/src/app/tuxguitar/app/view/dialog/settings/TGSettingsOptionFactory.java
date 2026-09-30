package app.tuxguitar.app.view.dialog.settings;

import app.tuxguitar.app.view.dialog.settings.items.TGSettingsOption;
import app.tuxguitar.ui.toolbar.UIToolBar;
import app.tuxguitar.ui.widget.UILayoutContainer;

/**
 * Creates a Configuration-window option contributed by a plugin (registered in
 * {@link TGSettingsOptionRegistry}); called each time the window opens.
 */
public interface TGSettingsOptionFactory {

	/** Stable identifier: a factory registered twice with the same id is kept once. */
	String getId();

	TGSettingsOption createOption(TGSettingsEditor editor, UIToolBar toolBar, UILayoutContainer parent);
}
