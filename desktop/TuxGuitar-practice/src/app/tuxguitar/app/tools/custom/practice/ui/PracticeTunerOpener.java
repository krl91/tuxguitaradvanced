package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.action.TGActionException;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.util.TGContext;

/**
 * The real way to the tuner for the start window (spec 010 R5): the action {@code tuner.tuner} (the id the tuner plugin maps) run through the action manager,
 * and the open state of the tuner read from {@link PracticeTunerContext}, the source {@code InputEvents} uses. The action also brings an already open tuner
 * forward. The closed listener runs on the thread that closes the tuner (the user-interface thread).
 */
final class PracticeTunerOpener implements PreRunWindow.TunerOpener {

	static final String ACTION_ID = "tuner.tuner";

	private final TGContext context;
	private final PracticeTunerContext shared;
	private final PracticeTunerContext.Listener sharedListener = changed -> changed();
	private Runnable closedListener;
	private boolean sawOpen;
	/** The practice track the preparation window holds, lifted while the tuner is open (the tuner refuses to open with one set). */
	private TGTrack lifted;

	PracticeTunerOpener(TGContext context) {
		this.context = context;
		this.shared = PracticeTunerContext.getInstance(context);
	}

	@Override
	public boolean available() {
		return TGActionManager.getInstance(this.context).getAction(ACTION_ID) != null;
	}

	@Override
	public void open() {
		// the preparation window sets a practice track as soon as it exists, and the tuner stays closed while one is set (both listen to the same input): no run is going
		// on during the check, so the track is lifted while the tuner is open and given back when it closes
		TGTrack track = this.shared.getPracticeTrack();
		if (track != null) {
			synchronized (this) {
				this.lifted = track;
			}
			this.shared.setPracticeTrack(null);
		}
		run();
		boolean open = this.shared.isTunerOpen();
		synchronized (this) {
			this.sawOpen = open;
		}
		if (!open) {
			giveTrackBack(); // the tuner did not open: nothing to wait for
		}
	}

	private void giveTrackBack() {
		TGTrack track;
		synchronized (this) {
			track = this.lifted;
			this.lifted = null;
		}
		if (track != null && this.shared.getPracticeTrack() == null) {
			this.shared.setPracticeTrack(track);
		}
	}

	@Override
	public void toFront() {
		run();
	}

	private void run() {
		try {
			TGActionManager.getInstance(this.context).execute(ACTION_ID);
		} catch (TGActionException exception) {
			// the tuner could not be opened; the window is told nothing more: it keeps waiting for a close that never comes, and its Cancel button is the way out
		}
	}

	@Override
	public void setClosedListener(Runnable listener) {
		synchronized (this) {
			if (listener != null && this.closedListener == null) {
				this.shared.addListener(this.sharedListener);
			} else if (listener == null && this.closedListener != null) {
				this.shared.removeListener(this.sharedListener);
			}
			this.closedListener = listener;
			this.sawOpen = false;
		}
		if (listener == null) {
			giveTrackBack(); // the start ended (cancelled) while the tuner was open
		}
	}

	private void changed() {
		Runnable toRun = null;
		synchronized (this) {
			if (this.shared.isTunerOpen()) {
				this.sawOpen = true;
			} else if (this.sawOpen) {
				this.sawOpen = false;
				toRun = this.closedListener;
			}
		}
		if (toRun != null) {
			giveTrackBack();
			toRun.run();
		}
	}
}
