package app.tuxguitar.app.tools.custom.tunernext;

import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.singleton.TGSingletonFactory;
import app.tuxguitar.util.singleton.TGSingletonUtil;

/**
 * Tuner state kept for the application run only (FR-055): the tuning the learner explicitly chose,
 * if any. Not saved across launches; closing and reopening the tuner keeps it.
 */
public final class TunerSessionState {

	private volatile TunerTuning explicitChoice;

	TunerSessionState() {
	}

	public static TunerSessionState getInstance(TGContext context) {
		return TGSingletonUtil.getInstance(context, TunerSessionState.class.getName(), new TGSingletonFactory<TunerSessionState>() {
			@Override
			public TunerSessionState createInstance(TGContext singletonContext) {
				return new TunerSessionState();
			}
		});
	}

	/** The learner explicitly chose this tuning (preset or custom). */
	public void choose(TunerTuning tuning) {
		this.explicitChoice = tuning;
	}

	public TunerTuning getExplicitChoice() {
		return this.explicitChoice;
	}

	/**
	 * The guided tuning (FR-052, FR-055): the learner's explicit choice; else the Practice Track
	 * (practice context); else the selected track when it is guitar-confirmed (standalone); else
	 * Standard E.
	 *
	 * @param practiceTrack the Practice Track's tuning during practice or its preparation, or null
	 * @param confirmedSelectedTrack the selected track's tuning if it is guitar-confirmed, or null
	 */
	public TunerTuning effectiveTuning(TunerTuning practiceTrack, TunerTuning confirmedSelectedTrack) {
		if( this.explicitChoice != null ) {
			return this.explicitChoice;
		}
		if( practiceTrack != null ) {
			return practiceTrack;
		}
		if( confirmedSelectedTrack != null ) {
			return confirmedSelectedTrack;
		}
		return TunerTuning.standard();
	}
}
