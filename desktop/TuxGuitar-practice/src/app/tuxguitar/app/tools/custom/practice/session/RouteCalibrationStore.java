package app.tuxguitar.app.tools.custom.practice.session;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * The calibrations of the routes in the configuration of the application (spec 002 T097, FR-058): they survive a run, each under its own route, and
 * what cannot be read is ignored. A key of a route is any text, so it is stored encoded in a form a property name accepts.
 */
public final class RouteCalibrationStore {

	static final String MODULE = "tuxguitar-practice-calibration";
	private static final String PREFIX = "route.";

	private RouteCalibrationStore() {
	}

	public static RouteCalibrations getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, RouteCalibrationStore.class.getName(), new TGSingletonFactory<RouteCalibrations>() {
			@Override
			public RouteCalibrations createInstance(TGContext singletonContext) {
				return new RouteCalibrations(new RouteCalibrations.Persistence() {
					public Map<String, String> load() {
						TGConfigManager config = new TGConfigManager(singletonContext, MODULE);
						Map<String, String> entries = new LinkedHashMap<String, String>();
						if (config.getProperties().getStringKeys() != null) {
							for (String name : config.getProperties().getStringKeys()) {
								String key = decodeName(name);
								if (key != null) {
									entries.put(key, config.getProperties().getValue(name));
								}
							}
						}
						return entries;
					}

					public void save(Map<String, String> entries) {
						TGConfigManager config = new TGConfigManager(singletonContext, MODULE);
						config.clear();
						for (Map.Entry<String, String> entry : entries.entrySet()) {
							config.setValue(encodeName(entry.getKey()), entry.getValue());
						}
						config.save();
					}
				});
			}
		});
	}

	/** The name of a property for the key of a route: URL-safe base64 of its UTF-8 text. */
	static String encodeName(String routeKey) {
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(routeKey.getBytes(StandardCharsets.UTF_8));
	}

	/** The key of the route a property name stands for, or null when the name is not one. */
	static String decodeName(String name) {
		if (name == null || !name.startsWith(PREFIX)) {
			return null;
		}
		try {
			return new String(Base64.getUrlDecoder().decode(name.substring(PREFIX.length())), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException notBase64) {
			return null;
		}
	}
}
