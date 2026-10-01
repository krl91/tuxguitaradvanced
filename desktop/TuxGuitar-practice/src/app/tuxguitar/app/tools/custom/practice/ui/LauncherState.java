package app.tuxguitar.app.tools.custom.practice.ui;

/**
 * What the transport-bar Practice button shows (spec 002, FR-003, FR-004): whether practice is
 * open, can be opened, or has nothing to practise and why. Immutable.
 */
public final class LauncherState {

	public enum Kind {
		INACTIVE,
		ACTIVE,
		UNAVAILABLE
	}

	public enum Reason {
		NONE,
		NO_SONG,
		NO_GUITAR_TRACK
	}

	private final Kind kind;
	private final Reason reason;

	private LauncherState(Kind kind, Reason reason) {
		this.kind = kind;
		this.reason = reason;
	}

	/**
	 * @param windowOpen whether the practice window is open
	 * @param songOpen whether a song is loaded
	 * @param hasPracticeTrack whether the song has a track practice can work on (ignored without a song)
	 */
	public static LauncherState of(boolean windowOpen, boolean songOpen, boolean hasPracticeTrack) {
		if( windowOpen ) {
			return new LauncherState(Kind.ACTIVE, Reason.NONE);
		}
		if( !songOpen ) {
			return new LauncherState(Kind.UNAVAILABLE, Reason.NO_SONG);
		}
		if( !hasPracticeTrack ) {
			return new LauncherState(Kind.UNAVAILABLE, Reason.NO_GUITAR_TRACK);
		}
		return new LauncherState(Kind.INACTIVE, Reason.NONE);
	}

	public Kind getKind() {
		return this.kind;
	}

	public Reason getReason() {
		return this.reason;
	}

	/**
	 * Only a missing song disables the button. A song with nothing to practise leaves it enabled: the
	 * practice window opens and explains why (a greyed icon would just look like a missing button),
	 * while the tooltip already says it.
	 */
	public boolean isEnabled() {
		return this.reason != Reason.NO_SONG;
	}

	public boolean isChecked() {
		return this.kind == Kind.ACTIVE;
	}

	/** The tooltip property key: the item's own name, or a variant telling why it is unavailable. */
	public String tooltipKey(String itemName) {
		switch( this.reason ) {
			case NO_SONG:
				return itemName + ".no-song";
			case NO_GUITAR_TRACK:
				return itemName + ".no-track";
			default:
				return itemName;
		}
	}
}
