package app.tuxguitar.app.tools.custom.practice.session;

/**
 * The positions of the pitch permissiveness of Follow (spec 016, FR-005), from today's recognition to a very permissive one. {@link FollowLeniency} gives each
 * position its values. Arcade has its own positions (spec 014); Wait and the tuner have none.
 */
public enum FollowLeniencyPosition {

	/** Exactly the recognition settings of the run, nothing relaxed. */
	STRICT,
	/** The default of Follow. */
	PERMISSIVE,
	/** The most permissive. */
	VERY_PERMISSIVE;

	public static final FollowLeniencyPosition DEFAULT = PERMISSIVE;

	/** The position of this name, whatever its case; the default for a missing or unknown name. */
	public static FollowLeniencyPosition of(String name) {
		if (name != null) {
			String wanted = name.trim();
			for (FollowLeniencyPosition position : values()) {
				if (position.name().equalsIgnoreCase(wanted)) {
					return position;
				}
			}
		}
		return DEFAULT;
	}
}
