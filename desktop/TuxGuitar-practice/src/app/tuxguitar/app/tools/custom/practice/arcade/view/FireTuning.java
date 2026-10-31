package app.tuxguitar.app.tools.custom.practice.arcade.view;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * The values of one fire (spec 019 FR-014, FR-019, data model "FireTuning"): Arcade's or the score's, each value brought back within its range. Immutable; the
 * defaults of a group give exactly the pictures of before this spec (FR-017). Integers are rounded, colours are kept to their red, green and blue, and a
 * missing or not-a-number value is the group's default: never an error.
 */
public final class FireTuning {

	/** Whose fire: Arcade's, or the one at the bottom of the score in Wait and Follow. */
	public enum Group {
		ARCADE, SCORE;

		/** The key of its title in the Advanced tab. */
		public String labelKey() {
			return "practice.fire.group." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final FireTuning ARCADE_DEFAULTS = of(Group.ARCADE, Collections.<FireParameter, Double>emptyMap());

	public static final FireTuning SCORE_DEFAULTS = of(Group.SCORE, Collections.<FireParameter, Double>emptyMap());

	private final Group group;
	private final double[] values;

	private FireTuning(Group group, double[] values) {
		this.group = group;
		this.values = values;
	}

	/**
	 * @param values the values chosen; a parameter left out, null or not a number takes the group's default, and every value is brought within its range
	 */
	public static FireTuning of(Group group, Map<FireParameter, Double> values) {
		Group chosen = (group != null ? group : Group.ARCADE);
		FireParameter[] parameters = FireParameter.values();
		double[] all = new double[parameters.length];
		for (FireParameter parameter : parameters) {
			Double value = (values != null ? values.get(parameter) : null);
			all[parameter.ordinal()] = clamped(parameter, value != null ? value.doubleValue() : Double.NaN, chosen);
		}
		return new FireTuning(chosen, all);
	}

	/** The defaults of a group. */
	public static FireTuning defaults(Group group) {
		return group == Group.SCORE ? SCORE_DEFAULTS : ARCADE_DEFAULTS;
	}

	/** A value brought within the range of its parameter: rounded for an integer, red, green and blue for a colour, the default when not a number. */
	public static double clamped(FireParameter parameter, double value, Group group) {
		if (Double.isNaN(value)) {
			return parameter.defaultFor(group);
		}
		if (parameter.kind() == FireParameter.Kind.COLOUR) {
			return (double) (((long) value) & 0xFFFFFFL);
		}
		double within = Math.max(parameter.min(), Math.min(parameter.max(), value));
		return parameter.kind() == FireParameter.Kind.INTEGER ? Math.round(within) : within;
	}

	public Group group() {
		return this.group;
	}

	public double value(FireParameter parameter) {
		return this.values[parameter.ordinal()];
	}

	public int integer(FireParameter parameter) {
		return (int) Math.round(this.values[parameter.ordinal()]);
	}

	/** A colour 0xRRGGBB. */
	public int colour(FireParameter parameter) {
		return (int) this.values[parameter.ordinal()] & 0xFFFFFF;
	}

	/** The same tuning with one value changed, brought within its range. */
	public FireTuning with(FireParameter parameter, double value) {
		double[] copy = this.values.clone();
		copy[parameter.ordinal()] = clamped(parameter, value, this.group);
		return new FireTuning(this.group, copy);
	}

	/** Whether this value is its group's default. */
	public boolean isDefault(FireParameter parameter) {
		return Double.compare(value(parameter), parameter.defaultFor(this.group)) == 0;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof FireTuning)) {
			return false;
		}
		FireTuning tuning = (FireTuning) other;
		return this.group == tuning.group && Arrays.equals(this.values, tuning.values);
	}

	@Override
	public int hashCode() {
		return 31 * this.group.hashCode() + Arrays.hashCode(this.values);
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(this.group.name());
		for (FireParameter parameter : FireParameter.values()) {
			text.append(' ').append(parameter.key()).append('=').append(value(parameter));
		}
		return text.toString();
	}
}
