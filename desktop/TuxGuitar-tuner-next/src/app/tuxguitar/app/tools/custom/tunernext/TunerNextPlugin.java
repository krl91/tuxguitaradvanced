package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.app.tools.custom.TGToolItemPlugin;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

/**
 * Evolved guitar tuner. Replaces the legacy tuner in builds that ship it, so it keeps the
 * legacy module id and action id (existing shortcut and enable settings carry over); the two
 * tuner jars are never packaged together.
 */
public class TunerNextPlugin extends TGToolItemPlugin {

	public static final String MODULE_ID = "tuxguitar-tuner";

	public static final String ACTION_ID = "tuner.tuner";

	private TunerWindow window;

	public String getModuleId() {
		return MODULE_ID;
	}

	protected String getItemName() {
		return ACTION_ID;
	}

	protected void doAction(TGContext context) {
		if( this.window == null ) {
			this.window = new TunerWindow(context);
		}
		this.window.open();
	}

	public void disconnect(TGContext context) throws TGPluginException {
		if( this.window != null ) {
			this.window.close();
			this.window = null;
		}
		super.disconnect(context);
	}
}
