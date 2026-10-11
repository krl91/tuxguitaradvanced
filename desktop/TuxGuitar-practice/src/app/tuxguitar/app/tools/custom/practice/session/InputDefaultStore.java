package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

/** The remembered default input in the configuration of the application: one id, kept from one run of the application to the next. */
public final class InputDefaultStore {

	static final String MODULE = "tuxguitar-practice-input";
	private static final String KEY = "default.input";

	private InputDefaultStore() {
	}

	/** The id of the remembered input, or null when none is remembered. */
	public static String get(TGContext context) {
		String value = new TGConfigManager(context, MODULE).getStringValue(KEY);
		return (value == null || value.isEmpty()) ? null : value;
	}

	/** Remembers this input, replacing the one that was remembered. */
	public static void set(TGContext context, String inputId) {
		TGConfigManager config = new TGConfigManager(context, MODULE);
		config.setValue(KEY, inputId);
		config.save();
	}
}
