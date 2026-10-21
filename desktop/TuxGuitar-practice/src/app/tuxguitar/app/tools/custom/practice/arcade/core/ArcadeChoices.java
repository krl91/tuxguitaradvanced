package app.tuxguitar.app.tools.custom.practice.arcade.core;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

/**
 * What the player chose in Arcade, kept from one launch to the next (spec 014, FR-006, FR-012): the level and the leniency position. They live in a small
 * configuration module of their own, like the remembered input: nothing is added to the recognition settings, so their page, their file and their tests are
 * as they were. A missing, empty, non-numeric, out-of-range or unknown value gives the default (level 1 the first time, the fun position) or the nearest level,
 * and an unreadable file keeps the defaults: never an error.
 */
public final class ArcadeChoices {

	static final String MODULE = "tuxguitar-practice-arcade";
	static final String LEVEL_KEY = "arcade.level";
	static final String LENIENCY_KEY = "arcade.leniency";

	/** Where the choices are read and written; the application's configuration in practice, a map in a test. */
	public interface Persistence {
		String get(String key);

		void set(String key, String value);
	}

	private final Persistence persistence;

	public ArcadeChoices(Persistence persistence) {
		this.persistence = persistence;
	}

	/** The choices kept in the configuration of the application. */
	public static ArcadeChoices of(TGContext context) {
		return new ArcadeChoices(new Persistence() {
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

	/** The level proposed: the last one chosen, level 1 when none was. */
	public ArcadeLevel level() {
		String value = read(LEVEL_KEY);
		if (value != null) {
			try {
				return ArcadeLevel.of(Integer.parseInt(value.trim()));
			} catch (NumberFormatException exception) {
				// not a number: the default
			}
		}
		return ArcadeLevel.LOWEST;
	}

	/** @return false when it could not be written (nothing was stored); never throws */
	public boolean setLevel(ArcadeLevel level) {
		return write(LEVEL_KEY, Integer.toString(level.number()));
	}

	/** The position of the leniency: the last one chosen, the fun position when none was. */
	public ArcadeLeniencyPosition leniency() {
		return ArcadeLeniencyPosition.of(read(LENIENCY_KEY));
	}

	/** @return false when it could not be written (nothing was stored); never throws */
	public boolean setLeniency(ArcadeLeniencyPosition position) {
		return write(LENIENCY_KEY, position.name());
	}

	private String read(String key) {
		try {
			return this.persistence.get(key);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private boolean write(String key, String value) {
		try {
			this.persistence.set(key, value);
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}
}
