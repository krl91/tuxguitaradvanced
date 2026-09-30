package app.tuxguitar.app.tools.custom.practice.ui;

import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import app.tuxguitar.app.editor.TGExternalBeatViewerManager;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.TGSynchronizer;

/**
 * The practice frames in the score (FR-014, FR-073, FR-074): the current event's notes framed
 * red on the score staff and the tablature through TuxGuitar's external beat viewer API,
 * separate from the normal cursor, which it never moves. A just-accepted event is framed green
 * for {@link #ACCEPTED_FLASH_MILLIS} while the next event is already framed red (proposal Q4).
 * Hidden whenever the session stops. UI thread only.
 */
public class PracticeHighlight {

	/** Provisional, not learner-configurable (FR-074). */
	public static final long ACCEPTED_FLASH_MILLIS = 250;

	private final TGContext context;
	private Timer timer;
	private PracticeEvent shownEvent;
	private PracticeEvent flashedEvent;
	private int acceptedCount;
	private PracticeEvent lastAccepted;

	public PracticeHighlight(TGContext context) {
		this.context = context;
	}

	/**
	 * Frames the current event; a newly accepted event is flashed green.
	 *
	 * @param current the current practice event, or null for none
	 * @param lastAccepted the most recently accepted event of the run, or null
	 * @param acceptedCount the session's acceptance count: a new count flashes even the same
	 *        event again (accepted anew after a passage retry)
	 */
	public void show(PracticeEvent current, PracticeEvent lastAccepted, int acceptedCount) {
		boolean changed = false;
		if( lastAccepted != null && (lastAccepted != this.lastAccepted || acceptedCount != this.acceptedCount) ) {
			this.flashedEvent = lastAccepted;
			scheduleFlashEnd(lastAccepted);
			changed = true;
		}
		this.lastAccepted = lastAccepted;
		this.acceptedCount = acceptedCount;
		if( current != this.shownEvent ) {
			this.shownEvent = current;
			changed = true;
		}
		if( changed ) {
			update();
		}
	}

	public void hide() {
		this.shownEvent = null;
		this.flashedEvent = null;
		this.lastAccepted = null;
		this.acceptedCount = 0;
		cancelTimer();
		TGExternalBeatViewerManager.getInstance(this.context).hideExternalBeat();
	}

	private void update() {
		if( this.shownEvent == null && this.flashedEvent == null ) {
			TGExternalBeatViewerManager.getInstance(this.context).hideExternalBeat();
			return;
		}
		PracticeEvent beatSource = (this.shownEvent != null ? this.shownEvent : this.flashedEvent);
		List<TGNote> expected = (this.shownEvent != null ? this.shownEvent.getNotes() : Collections.<TGNote>emptyList());
		List<TGNote> accepted = (this.flashedEvent != null ? this.flashedEvent.getNotes() : null);
		TGExternalBeatViewerManager.getInstance(this.context).showExternalNotes(beatSource.getBeat(), expected, accepted);
	}

	private void scheduleFlashEnd(final PracticeEvent flashed) {
		if( this.timer == null ) {
			this.timer = new Timer("practice-highlight", true);
		}
		this.timer.schedule(new TimerTask() {
			public void run() {
				TGSynchronizer.getInstance(PracticeHighlight.this.context).executeLater(() -> endFlash(flashed));
			}
		}, ACCEPTED_FLASH_MILLIS);
	}

	private void endFlash(PracticeEvent flashed) {
		if( this.flashedEvent == flashed ) {
			this.flashedEvent = null;
			update();
		}
	}

	private void cancelTimer() {
		if( this.timer != null ) {
			this.timer.cancel();
			this.timer = null;
		}
	}
}
