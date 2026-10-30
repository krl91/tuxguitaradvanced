package app.tuxguitar.app.tools.custom.practice.arcade.view;

/**
 * The colours of the demo fire (spec 018, research R4): sixteen heat steps from dark red through red, orange and yellow to white, each translucent. Step 0
 * is cold and draws nothing. The opacity grows in proportion to the step up to 85 % (the owner's look gate; 60 % before), so that the middle of the width, at a fifth of the
 * corners' heat, keeps at most 22 % of their opacity (spec 009 FR-001); discreet is 60 % of it. Pure.
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

	/** The colour of a lit step, 1 to 15. */
	static int colour(int step) {
		double t = (Math.max(1, Math.min(STEPS - 1, step)) - 1) / (double) (STEPS - 2) * (SCALE.length - 1);
		int i = Math.min(SCALE.length - 2, (int) Math.floor(t));
		double f = t - i;
		int a = SCALE[i];
		int b = SCALE[i + 1];
		return mix(a >> 16, b >> 16, f) << 16 | mix(a >> 8, b >> 8, f) << 8 | mix(a, b, f);
	}

	/** The opacity of a step: 0 for a cold cell. */
	static double alpha(int step, boolean discreet) {
		if (step <= 0) {
			return 0.0;
		}
		return MOST * Math.min(step, STEPS - 1) / (STEPS - 1) * (discreet ? FlameDrawing.DISCREET : 1.0);
	}

	private static int mix(int a, int b, double f) {
		return (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
	}
}
