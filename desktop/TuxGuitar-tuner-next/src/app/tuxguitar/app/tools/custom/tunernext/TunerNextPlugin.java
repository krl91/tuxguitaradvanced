package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPlugin;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Evolved guitar tuner. Replaces the legacy tuner in builds that ship it, so it keeps the
 * legacy module id and action id; the two tuner jars are never packaged together.
 * Lifecycle skeleton only: the tuner item and window are added by later tasks.
 */
public class TunerNextPlugin implements TGPlugin {

	public static final String MODULE_ID = "tuxguitar-tuner";

	public static final String ACTION_ID = "tuner.tuner";

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
