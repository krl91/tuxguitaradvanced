package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The colours of the demo fire (spec 018, research R4): sixteen heat steps from dark red through red, orange and yellow to white, each translucent. Step 0
 * is cold and draws nothing. The opacity grows in proportion to the step up to 85 % (the owner's look gate; 60 % before), so that the middle of the width, at a fifth of the
 * corners' heat, keeps at most 22 % of their opacity (spec 009 FR-001); discreet is 60 % of it. Pure.
 *
 * <p>The steps, the colours, the most opacity and the share of discreet come from a {@link FireTuning} (spec 019); the constants below are Arcade's defaults.
 */
final class FireColours {

	/** The heat steps, 0 (cold) to 15 (white). */
	static final int STEPS = 16;

	/** The opacity of the hottest step. */
	static final double MOST = 0.85;

	/** The colour scale, from the coolest lit step to the hottest. */
	private static final int[] SCALE = { 0x5A0A04, 0xB7210F, 0xF26A12, 0xFFD23C, 0xFFF6D8 };

	private FireColours() {
	}

	/** A colour of the default scale, the coolest first. */
	static int scale(int index) {
		return SCALE[index];
	}

	/** The parameters of the colours of the scale, in a holder of their own so that the catalogue and this class may start in either order. */
	private static final class Scale {
		static final FireParameter[] PARAMETERS = { FireParameter.COLOUR_1, FireParameter.COLOUR_2, FireParameter.COLOUR_3, FireParameter.COLOUR_4,
				FireParameter.COLOUR_5 };
	}

	private static FireParameter scaleParameter(int index) {
		return Scale.PARAMETERS[index];
	}

	/** The colour of a lit step, 1 to 15. */
	static int colour(int step) {
		return colour(step, FireTuning.ARCADE_DEFAULTS);
	}

	/** The colour of a lit step, 1 to the hottest step of the tuning. */
	static int colour(int step, FireTuning tuning) {
		int steps = tuning.integer(FireParameter.COLOUR_STEPS);
		double t = (Math.max(1, Math.min(steps - 1, step)) - 1) / (double) (steps - 2) * (SCALE.length - 1);
		int i = Math.min(SCALE.length - 2, (int) Math.floor(t));
		double f = t - i;
		int a = tuning.colour(scaleParameter(i));
		int b = tuning.colour(scaleParameter(i + 1));
		return mix(a >> 16, b >> 16, f) << 16 | mix(a >> 8, b >> 8, f) << 8 | mix(a, b, f);
	}

	/** The opacity of a step: 0 for a cold cell. */
	static double alpha(int step, boolean discreet) {
		return alpha(step, discreet, FireTuning.ARCADE_DEFAULTS);
	}

	/** The opacity of a step with a tuning: 0 for a cold cell. */
	static double alpha(int step, boolean discreet, FireTuning tuning) {
		if (step <= 0) {
			return 0.0;
		}
		int steps = tuning.integer(FireParameter.COLOUR_STEPS);
		return tuning.value(FireParameter.OPACITY_MOST) * Math.min(step, steps - 1) / (steps - 1) * (discreet ? tuning.value(FireParameter.DISCREET_SHARE) : 1.0);
	}

	private static int mix(int a, int b, double f) {
		return (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
	}
}
