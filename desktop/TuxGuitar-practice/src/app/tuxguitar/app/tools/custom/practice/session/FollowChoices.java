package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.configuration.TGConfigManager;

/**
 * What the learner chose for Follow, kept from one launch to the next (spec 016, FR-005): the position of the pitch permissiveness, under its own key, beside
 * Arcade's ({@link ArcadeChoices}), which it never touches. It lives in a small configuration module of its own, like Arcade's: nothing is added to the
 * recognition settings. A missing or unknown value gives {@link FollowLeniencyPosition#PERMISSIVE}, and an unreadable store keeps it: never an error.
 */
public final class FollowChoices {

	static final String MODULE = "tuxguitar-practice-follow";
	public static final String LENIENCY_KEY = "practice.follow.leniency";

	private final ArcadeChoices.Persistence persistence;

	public FollowChoices(ArcadeChoices.Persistence persistence) {
		this.persistence = persistence;
	}

	/** The choices kept in the configuration of the application. */
	public static FollowChoices of(TGContext context) {
		return new FollowChoices(new ArcadeChoices.Persistence() {
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

	/** The position of the permissiveness: the last one chosen, Permissive when none was. */
	public FollowLeniencyPosition leniency() {
		try {
			return FollowLeniencyPosition.of(this.persistence.get(LENIENCY_KEY));
		} catch (RuntimeException exception) {
			return FollowLeniencyPosition.DEFAULT;
		}
	}

	/** @return false when it could not be written (nothing was stored); never throws */
	public boolean setLeniency(FollowLeniencyPosition position) {
		try {
			this.persistence.set(LENIENCY_KEY, position.name());
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}
}
