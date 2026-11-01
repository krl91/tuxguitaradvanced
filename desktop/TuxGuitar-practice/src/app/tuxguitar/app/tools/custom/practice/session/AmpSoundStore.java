package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

/**
 * What the amp window keeps from one launch to the next (spec 022, contracts/amp-window.md, data-model "AmpSoundSettings", FR-009), in a
 * configuration module of its own, like the Arcade choices. This part holds the settings and the current chain (US1); the named sounds
 * and the sound per score come with US3. A missing or unreadable value gives the default: never an error.
 */
public final class AmpSoundStore {

	static final String MODULE = "tuxguitar-practice-amp";
	static final String ENABLED = "enabled";
	static final String OUTPUT = "output";
	static final String GUITAR_VOLUME = "guitar.volume";
	static final String CURRENT_CHAIN = "chain.current";
	static final String WINDOW_BOUNDS = "window.bounds";

	private final ArcadeChoices.Persistence persistence;

	public AmpSoundStore(ArcadeChoices.Persistence persistence) {
		this.persistence = persistence;
	}

	/** The store kept in the configuration of the application. */
	public static AmpSoundStore of(TGContext context) {
		return new AmpSoundStore(new ArcadeChoices.Persistence() {
			public String get(String key) {
				return new TGConfigManager(context, MODULE).getStringValue(key);
			}

			public void set(String key, String value) {
				TGConfigManager config = new TGConfigManager(context, MODULE);
				config.setValue(key, value);
				config.save();
			}
		});
	}

	public AmpSoundSettings settings() {
		AmpSoundSettings defaults = AmpSoundSettings.defaults();
		String enabled = read(ENABLED);
		String output = read(OUTPUT);
		return new AmpSoundSettings("true".equals(enabled), output != null ? output : defaults.output(), number(read(GUITAR_VOLUME), defaults.guitarVolume()),
				bounds(read(WINDOW_BOUNDS)));
	}

	public void save(AmpSoundSettings settings) {
		write(ENABLED, Boolean.toString(settings.enabled()));
		write(OUTPUT, settings.output());
		write(GUITAR_VOLUME, Double.toString(settings.guitarVolume()));
		int[] bounds = settings.windowBounds();
		write(WINDOW_BOUNDS, bounds == null ? "" : bounds[0] + "," + bounds[1] + "," + bounds[2] + "," + bounds[3]);
	}

	/** The chain in use, with the learner's unsaved changes; Clean when none was kept or it cannot be read. */
	public ChainSettings currentChain() {
		String text = read(CURRENT_CHAIN);
		return (text == null ? ReadyMadeChains.CLEAN : ChainSettings.parse(text).orElse(ReadyMadeChains.CLEAN));
	}

	public void saveCurrentChain(ChainSettings chain) {
		write(CURRENT_CHAIN, chain.toText());
	}

	private String read(String key) {
		try {
			String value = this.persistence.get(key);
			return (value == null || value.trim().isEmpty() ? null : value.trim());
		} catch (RuntimeException exception) {
			// an unreadable configuration keeps the defaults
			return null;
		}
	}

	private void write(String key, String value) {
		try {
			this.persistence.set(key, value);
		} catch (RuntimeException exception) {
			// not kept this time: the window goes on with the value in memory
		}
	}

	private static double number(String text, double fallback) {
		if (text == null) {
			return fallback;
		}
		try {
			double value = Double.parseDouble(text);
			return Double.isNaN(value) ? fallback : Math.max(0, Math.min(1, value));
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static int[] bounds(String text) {
		if (text == null) {
			return null;
		}
		String[] parts = text.split(",");
		if (parts.length != 4) {
			return null;
		}
		int[] bounds = new int[4];
		try {
			for (int i = 0; i < 4; i++) {
				bounds[i] = Integer.parseInt(parts[i].trim());
			}
		} catch (NumberFormatException exception) {
			return null;
		}
		return (bounds[2] > 0 && bounds[3] > 0 ? bounds : null);
	}
}
