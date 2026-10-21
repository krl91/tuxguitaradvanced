package app.tuxguitar.app.tools.custom.practice.arcade.core;

/**
 * The positions of the permissiveness of the Arcade recognition (spec 014, FR-002), from the recognition of the practice modes to a very permissive one.
 * {@link ArcadeLeniency} gives each position its values.
 */
public enum ArcadeLeniencyPosition {

	/** Exactly the recognition and the timing of the practice modes: nothing is relaxed. */
	LIKE_PRACTICE,
	/** The default of Arcade: quicker and more forgiving. */
	FUN,
	/** The most permissive. */
	VERY_PERMISSIVE;

	public static final ArcadeLeniencyPosition DEFAULT = FUN;

	/** The position of this name, whatever its case; the default for a missing or unknown name. */
	public static ArcadeLeniencyPosition of(String name) {
		if (name != null) {
			String wanted = name.trim();
			for (ArcadeLeniencyPosition position : values()) {
				if (position.name().equalsIgnoreCase(wanted)) {
					return position;
				}
			}
		}
		return DEFAULT;
	}
}
