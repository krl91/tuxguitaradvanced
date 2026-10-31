package app.tuxguitar.app.tools.custom.practice.session;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import app.tuxguitar.app.tools.custom.practice.arcade.view.FireParameter;
import app.tuxguitar.app.tools.custom.practice.arcade.view.FireTuning;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

/**
 * The settings of both fires kept from one launch to the next (spec 019 FR-007, FR-019, research R5): the values of the Advanced tab, under
 * {@code arcade.<key>} and {@code score.<key>}, and the choice of the score fire under {@code score.setting}, in a small configuration module of their own
 * like {@link ArcadeChoices} and {@link FollowChoices}. Only the values that differ from their default are written. A missing, unreadable or out-of-range
 * value gives the default or the value brought within range, and a store that fails keeps the defaults: never an error.
 */
public final class FireTuningStore {

	static final String MODULE = "tuxguitar-practice-fire";
	public static final String SETTING_KEY = "score.setting";

	private final ArcadeChoices.Persistence persistence;

	public FireTuningStore(ArcadeChoices.Persistence persistence) {
		this.persistence = persistence;
	}

	/** The settings kept in the configuration of the application. */
	public static FireTuningStore of(TGContext context) {
		return new FireTuningStore(new ArcadeChoices.Persistence() {
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

	/** The key of a value of a group. */
	public static String key(FireTuning.Group group, FireParameter parameter) {
		return prefix(group) + parameter.key();
	}

	/** The values of a group: the default for every value not kept or not readable. */
	public FireTuning load(FireTuning.Group group) {
		Map<FireParameter, Double> values = new EnumMap<FireParameter, Double>(FireParameter.class);
		for (FireParameter parameter : FireParameter.values()) {
			Double value = parse(parameter, read(key(group, parameter)));
			if (value != null) {
				values.put(parameter, value);
			}
		}
		return FireTuning.of(group, values);
	}

	/**
	 * Keeps the values of a group: each value that differs from its default is written, and each one back at its default is cleared.
	 *
	 * @return false when a value could not be written; never throws
	 */
	public boolean save(FireTuning tuning) {
		boolean all = true;
		for (FireParameter parameter : FireParameter.values()) {
			String text = tuning.isDefault(parameter) ? "" : format(parameter, tuning.value(parameter));
			all &= write(key(tuning.group(), parameter), text);
		}
		return all;
	}

	/** Clears every value of a group: it is back to its defaults; the other group is not touched. */
	public boolean reset(FireTuning.Group group) {
		boolean all = true;
		for (FireParameter parameter : FireParameter.values()) {
			all &= write(key(group, parameter), "");
		}
		return all;
	}

	/** The choice of the score fire: the last one kept, Off when none was (FR-007). */
	public FlameSetting scoreSetting() {
		String value = read(SETTING_KEY);
		if (value != null) {
			for (FlameSetting setting : FlameSetting.values()) {
				if (setting.name().equals(value.trim())) {
					return setting;
				}
			}
		}
		return FlameSetting.OFF;
	}

	/** @return false when it could not be written; never throws */
	public boolean setScoreSetting(FlameSetting setting) {
		return write(SETTING_KEY, (setting != null ? setting : FlameSetting.OFF).name());
	}

	private static String prefix(FireTuning.Group group) {
		return group == FireTuning.Group.SCORE ? "score." : "arcade.";
	}

	private static String format(FireParameter parameter, double value) {
		if (parameter.kind() == FireParameter.Kind.COLOUR) {
			return String.format(Locale.ROOT, "#%06X", ((int) value) & 0xFFFFFF);
		}
		return Double.toString(value);
	}

	private static Double parse(FireParameter parameter, String text) {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		String value = text.trim();
		try {
			if (parameter.kind() == FireParameter.Kind.COLOUR) {
				if (!value.startsWith("#") || value.length() != 7) {
					return null;
				}
				return (double) Integer.parseInt(value.substring(1), 16);
			}
			double number = Double.parseDouble(value);
			return Double.isNaN(number) || Double.isInfinite(number) ? null : number;
		} catch (NumberFormatException exception) {
			return null;
		}
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
