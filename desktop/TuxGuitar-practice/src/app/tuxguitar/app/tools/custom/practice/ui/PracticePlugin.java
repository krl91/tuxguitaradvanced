package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPlugin;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Interactive Practice plugin.
 * Lifecycle skeleton only: the practice item and actions are added by later tasks.
 */
public class PracticePlugin implements TGPlugin {

	public static final String MODULE_ID = "tuxguitar-practice";

	public String getModuleId() {
		return MODULE_ID;
	}

	public void connect(TGContext context) throws TGPluginException {
		// nothing to register yet
	}

	public void disconnect(TGContext context) throws TGPluginException {
		// nothing to unregister yet
	}
}
