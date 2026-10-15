package app.tuxguitar.audio.engine.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * The saved collection of device profiles, one per device, in a configuration module of its own (spec 013, FR-007). It is read once; an unreadable or
 * foreign entry is skipped (that device then uses the default configuration) and kept as it is in the file, so that saving another profile does not
 * destroy what a later version of the application wrote. Every change rewrites the whole file; when the write fails the library and the file keep their
 * previous content and the call returns false.
 */
public final class ProfileLibrary {

	/** The configuration module (file) of the library: not the recognition store's, which rewrites itself as a whole and drops other keys. */
	public static final String MODULE = "tuxguitar-recognition-profiles";

	private static final String PREFIX = "profile.";

	private final ProfilePersistence persistence;
	private final Map<String, RecognitionProfile> profiles = new LinkedHashMap<String, RecognitionProfile>();
	/** Entries this version cannot read (corrupt, later layout, other keys), written back unchanged. */
	private final Map<String, String> kept = new TreeMap<String, String>();
	private final Set<String> skipped = new LinkedHashSet<String>();

	public ProfileLibrary(ProfilePersistence persistence) {
		this.persistence = persistence;
		Map<String, String> stored;
		try {
			stored = persistence.read();
		} catch (RuntimeException exception) {
			stored = null;
		}
		if (stored == null) {
			return;
		}
		Map<String, Map<String, String>> byId = new TreeMap<String, Map<String, String>>();
		for (Map.Entry<String, String> entry : stored.entrySet()) {
			String key = entry.getKey();
			int dot = key.startsWith(PREFIX) ? key.indexOf('.', PREFIX.length()) : -1;
			if (dot < 0) {
				this.kept.put(key, entry.getValue());
				continue;
			}
			String id = key.substring(PREFIX.length(), dot);
			Map<String, String> fields = byId.get(id);
			if (fields == null) {
				fields = new LinkedHashMap<String, String>();
				byId.put(id, fields);
			}
			fields.put(key.substring(dot + 1), entry.getValue());
		}
		for (Map.Entry<String, Map<String, String>> entry : byId.entrySet()) {
			RecognitionProfile profile = parse(entry.getKey(), entry.getValue());
			if (profile != null) {
				this.profiles.put(entry.getKey(), profile);
			} else {
				this.skipped.add(entry.getKey());
				for (Map.Entry<String, String> field : entry.getValue().entrySet()) {
					this.kept.put(PREFIX + entry.getKey() + "." + field.getKey(), field.getValue());
				}
			}
		}
	}

	public static ProfileLibrary getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, ProfileLibrary.class.getName(), new TGSingletonFactory<ProfileLibrary>() {
			@Override
			public ProfileLibrary createInstance(TGContext singletonContext) {
				return new ProfileLibrary(new ConfigPersistence(singletonContext));
			}
		});
	}

	public synchronized RecognitionProfile get(DeviceKey key) {
		return this.profiles.get(key.storageId());
	}

	public synchronized List<DeviceKey> keys() {
		List<DeviceKey> keys = new ArrayList<DeviceKey>();
		for (RecognitionProfile profile : this.profiles.values()) {
			keys.add(profile.getKey());
		}
		return keys;
	}

	/** The storage ids of the entries that were skipped when the file was read (for diagnostics and tests). */
	public synchronized Set<String> getSkippedIds() {
		return Collections.unmodifiableSet(new LinkedHashSet<String>(this.skipped));
	}

	/** Saves {@code profile} as the one of its device and writes the file; false (and nothing changed) when the write fails. */
	public synchronized boolean save(RecognitionProfile profile) {
		String id = profile.getKey().storageId();
		Map<String, RecognitionProfile> next = new LinkedHashMap<String, RecognitionProfile>(this.profiles);
		next.put(id, profile);
		Map<String, String> nextKept = withoutDevice(this.kept, id);
		if (!write(next, nextKept)) {
			return false;
		}
		this.profiles.clear();
		this.profiles.putAll(next);
		this.kept.clear();
		this.kept.putAll(nextKept);
		this.skipped.remove(id);
		return true;
	}

	/** Deletes the profile of one device (and its unreadable entries) and writes the file; false when the write fails. Nothing to delete is not an error. */
	public synchronized boolean remove(DeviceKey key) {
		String id = key.storageId();
		Map<String, String> nextKept = withoutDevice(this.kept, id);
		if (!this.profiles.containsKey(id) && nextKept.size() == this.kept.size()) {
			return true;
		}
		Map<String, RecognitionProfile> next = new LinkedHashMap<String, RecognitionProfile>(this.profiles);
		next.remove(id);
		if (!write(next, nextKept)) {
			return false;
		}
		this.profiles.clear();
		this.profiles.putAll(next);
		this.kept.clear();
		this.kept.putAll(nextKept);
		this.skipped.remove(id);
		return true;
	}

	private static Map<String, String> withoutDevice(Map<String, String> kept, String id) {
		Map<String, String> result = new TreeMap<String, String>();
		String prefix = PREFIX + id + ".";
		for (Map.Entry<String, String> entry : kept.entrySet()) {
			if (!entry.getKey().startsWith(prefix)) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	private boolean write(Map<String, RecognitionProfile> profiles, Map<String, String> kept) {
		Map<String, String> all = new TreeMap<String, String>(kept);
		for (Map.Entry<String, RecognitionProfile> entry : profiles.entrySet()) {
			serialize(entry.getKey(), entry.getValue(), all);
		}
		try {
			this.persistence.write(all);
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	// ---------------------------------------------------------------- the stored form

	private static String rate(RecognitionRate rate) {
		return rate.getNotesAccepted() + "/" + rate.getNotesTotal() + "/" + rate.getChordsAccepted() + "/" + rate.getChordsTotal();
	}

	private static RecognitionRate parseRate(String text) {
		String[] parts = text.split("/");
		if (parts.length != 4) {
			throw new IllegalArgumentException("a rate has four numbers");
		}
		return new RecognitionRate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
	}

	private static void serialize(String id, RecognitionProfile profile, Map<String, String> into) {
		String prefix = PREFIX + id + ".";
		into.put(prefix + "layout", Integer.toString(RecognitionProfile.LAYOUT));
		into.put(prefix + "sampleRate", Integer.toString(profile.getSampleRate()));
		into.put(prefix + "date", Long.toString(profile.getDateMillis()));
		into.put(prefix + "origin", profile.getOrigin().name());
		into.put(prefix + "gain", Double.toString(profile.getFrontEnd().getGainMultiplier()));
		into.put(prefix + "noiseReduction", Boolean.toString(profile.getFrontEnd().isNoiseReduction()));
		if (profile.getFoundRate() != null) {
			into.put(prefix + "found", rate(profile.getFoundRate()));
		}
		if (profile.getDefaultRate() != null) {
			into.put(prefix + "default", rate(profile.getDefaultRate()));
		}
		for (Map.Entry<RecognitionParameter.Number, Double> value : profile.getValues().entrySet()) {
			into.put(prefix + "value." + value.getKey().getKey(), value.getKey().format(value.getValue()));
		}
	}

	/** The profile stored under {@code id}, or null when it cannot be read as one (nothing is thrown). */
	private static RecognitionProfile parse(String id, Map<String, String> fields) {
		try {
			DeviceKey key = DeviceKey.fromStorageId(id);
			if (key == null || !key.storageId().equals(id)) {
				return null;
			}
			if (RecognitionProfile.LAYOUT != Integer.parseInt(required(fields, "layout"))) {
				return null;
			}
			int sampleRate = Integer.parseInt(required(fields, "sampleRate"));
			long date = Long.parseLong(required(fields, "date"));
			RecognitionProfile.Origin origin = RecognitionProfile.Origin.valueOf(required(fields, "origin"));
			double gain = Double.parseDouble(required(fields, "gain"));
			boolean noise = strictBoolean(required(fields, "noiseReduction"));
			Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
			Set<String> known = new LinkedHashSet<String>(java.util.Arrays.asList("layout", "sampleRate", "date", "origin", "gain", "noiseReduction", "found", "default"));
			for (Map.Entry<String, String> field : fields.entrySet()) {
				if (field.getKey().startsWith("value.")) {
					RecognitionParameter.Number parameter = null;
					for (RecognitionParameter.Number candidate : ProfileParameters.LIST) {
						if (field.getKey().equals("value." + candidate.getKey())) {
							parameter = candidate;
						}
					}
					if (parameter == null) {
						return null;
					}
					values.put(parameter, Double.valueOf(Double.parseDouble(field.getValue())));
				} else if (!known.contains(field.getKey())) {
					return null;
				}
			}
			RecognitionRate found = fields.containsKey("found") ? parseRate(fields.get("found")) : null;
			RecognitionRate defaults = fields.containsKey("default") ? parseRate(fields.get("default")) : null;
			return new RecognitionProfile(key, sampleRate, date, values, found, defaults, new FrontEndState(gain, noise), origin);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static String required(Map<String, String> fields, String name) {
		String value = fields.get(name);
		if (value == null) {
			throw new IllegalArgumentException("missing " + name);
		}
		return value;
	}

	private static boolean strictBoolean(String text) {
		if (text.equals("true")) {
			return true;
		}
		if (text.equals("false")) {
			return false;
		}
		throw new IllegalArgumentException("not a boolean: " + text);
	}

	/** The application configuration file of {@link #MODULE}. */
	static final class ConfigPersistence implements ProfilePersistence {
		private final TGContext context;

		ConfigPersistence(TGContext context) {
			this.context = context;
		}

		public Map<String, String> read() {
			TGConfigManager config = new TGConfigManager(this.context, MODULE);
			Map<String, String> values = new LinkedHashMap<String, String>();
			if (config.getProperties().getStringKeys() != null) {
				for (String key : config.getProperties().getStringKeys()) {
					values.put(key, config.getProperties().getValue(key));
				}
			}
			return values;
		}

		public void write(Map<String, String> values) {
			TGConfigManager config = new TGConfigManager(this.context, MODULE);
			config.clear();
			for (Map.Entry<String, String> entry : values.entrySet()) {
				config.setValue(entry.getKey(), entry.getValue());
			}
			config.save();
		}
	}
}
