package app.tuxguitar.audio.engine.frontend;

/** A gain proposed from the level check (spec 002, FR-066). It is only ever applied when the learner confirms it. Immutable. */
public final class GainSuggestion {

	public enum Reason {
		/** The level is fine: nothing to change (the multiplier equals the current gain). */
		OK,
		/** The input was too weak: a boost is suggested. */
		TOO_LOW,
		/** The input was too loud: a reduction is suggested. */
		TOO_HIGH,
		/** The input is weak and noisy: a boost would mostly amplify noise; raise the gain on the interface or use a direct input. */
		NOISE_DOMINATED
	}

	private final double multiplier;
	private final Reason reason;

	public GainSuggestion(double multiplier, Reason reason) {
		this.multiplier = multiplier;
		this.reason = reason;
	}

	/** The total gain to set (not a change relative to the current one). */
	public double getMultiplier() {
		return this.multiplier;
	}

	public Reason getReason() {
		return this.reason;
	}
}
