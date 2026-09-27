package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.editor.TGEditorManager;
import app.tuxguitar.editor.event.TGUpdateEvent;
import app.tuxguitar.event.TGEvent;
import app.tuxguitar.event.TGEventListener;
import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.util.TGContext;

/**
 * Connects a running practice session to TuxGuitar events. Starting playback pauses practice
 * until an explicit Resume (FR-065); editing or replacing the song stops it (FR-066); selecting
 * another track or moving the cursor neither stops nor retargets it (FR-003). Every change is
 * posted to the session thread.
 */
public class SessionEvents implements TGEventListener {

	private final TGContext context;
	private final SessionLoop<PracticeSnapshot> loop;
	private final PracticeSession session;

	public SessionEvents(TGContext context, SessionLoop<PracticeSnapshot> loop, PracticeSession session) {
		this.context = context;
		this.loop = loop;
		this.session = session;
	}

	public void connect() {
		MidiPlayer.getInstance(this.context).addListener(this);
		TGEditorManager.getInstance(this.context).addUpdateListener(this);
	}

	public void disconnect() {
		MidiPlayer.getInstance(this.context).removeListener(this);
		TGEditorManager.getInstance(this.context).removeUpdateListener(this);
	}

	public void processEvent(TGEvent event) {
		if( MidiPlayerEvent.EVENT_TYPE.equals(event.getEventType()) ) {
			int type = ((Integer) event.getAttribute(MidiPlayerEvent.PROPERTY_NOTIFICATION_TYPE)).intValue();
			if( type == MidiPlayerEvent.NOTIFY_STARTED ) {
				this.loop.post(() -> this.session.pause(PracticeSession.PauseReason.PLAYBACK));
			} else if( type == MidiPlayerEvent.NOTIFY_STOPPED ) {
				this.loop.post(() -> this.session.resolve(PracticeSession.PauseReason.PLAYBACK));
			}
		} else if( TGUpdateEvent.EVENT_TYPE.equals(event.getEventType()) ) {
			int type = ((Integer) event.getAttribute(TGUpdateEvent.PROPERTY_UPDATE_MODE)).intValue();
			if( type == TGUpdateEvent.MEASURE_UPDATED || type == TGUpdateEvent.SONG_UPDATED || type == TGUpdateEvent.SONG_LOADED ) {
				this.loop.postCommand(PracticeCommand.STOP);
			}
		}
	}
}
