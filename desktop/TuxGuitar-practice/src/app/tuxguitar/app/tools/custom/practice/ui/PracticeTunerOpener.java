package app.tuxguitar.app.tools.custom.practice.ui;

import app.tuxguitar.action.TGActionException;
import app.tuxguitar.action.TGActionManager;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
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
		run();
		this.sawOpen = this.shared.isTunerOpen();
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
	public synchronized void setClosedListener(Runnable listener) {
		if (listener != null && this.closedListener == null) {
			this.shared.addListener(this.sharedListener);
		} else if (listener == null && this.closedListener != null) {
			this.shared.removeListener(this.sharedListener);
		}
		this.closedListener = listener;
		this.sawOpen = false;
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
			toRun.run();
		}
	}
}
