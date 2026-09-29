package app.tuxguitar.app.tools.custom.tunernext;

/**
 * Keeps the last reliable tuner reading on screen when the level drops (the string decays, the
 * learner stops, the input goes too low or clips): the note and needle stay where they were,
 * marked as held, together with the reason there is no live reading. A new reliable reading
 * replaces it; a change of mode, target or input clears it. UI thread only.
 */
public final class TunerHold {

	/** What to show for one frame. */
	public static final class Shown {
		private final TunerDisplay display;
		private final boolean held;
		private final TunerDisplay.State liveState;

		private Shown(TunerDisplay display, boolean held, TunerDisplay.State liveState) {
			this.display = display;
			this.held = held;
			this.liveState = liveState;
		}

		/** The live display, or the last reliable one while held. */
		public TunerDisplay getDisplay() {
			return this.display;
		}

		/** True when {@link #getDisplay()} is the last reliable reading, not a live one. */
		public boolean isHeld() {
			return this.held;
		}

		/** Why there is no live reading (NO_READING, INPUT_TOO_LOW, INPUT_CLIPPING); the live state otherwise. */
		public TunerDisplay.State getLiveState() {
			return this.liveState;
		}
	}

	private TunerDisplay last;

	public Shown update(TunerDisplay live) {
		return update(live, true);
	}

	/**
	 * @param holdEnabled the learner's "keep the last note" setting (T102); when off, the live
	 *        display is shown and nothing is kept
	 */
	public Shown update(TunerDisplay live, boolean holdEnabled) {
		if( !holdEnabled ) {
			this.last = null;
			return new Shown(live, false, live.getState());
		}
		if( live.isTuning() ) {
			this.last = live;
			return new Shown(live, false, live.getState());
		}
		if( this.last != null ) {
			return new Shown(this.last, true, live.getState());
		}
		return new Shown(live, false, live.getState());
	}

	/** Forgets the held reading (mode, target or input changed). */
	public void clear() {
		this.last = null;
	}
}
