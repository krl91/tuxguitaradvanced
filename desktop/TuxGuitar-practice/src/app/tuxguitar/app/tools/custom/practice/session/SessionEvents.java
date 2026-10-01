package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
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
 * another track or moving the cursor neither stops nor retargets it (FR-003). Changing the audio
 * input pauses it until the new input was checked usable; an unusable input (failed check, lost
 * device) pauses it until a check succeeds again; opening the tuner pauses it until the tuner is
 * closed; resuming always needs an explicit Resume (FR-043, FR-044, FR-058, FR-063). Every change
 * is posted to the session thread.
 */
public class SessionEvents implements TGEventListener {

	private final TGContext context;
	private final SessionLoop<PracticeSnapshot> loop;
	private final PracticeSession session;
	private final CurrentInputSelection selection;
	private final CurrentInputSelection.Listener selectionListener;
	private final PracticeTunerContext shared;
	private final PracticeTunerContext.Listener sharedListener;
	private boolean tunerOpen;
	private long inputRevision;
	private long revision;

	public SessionEvents(TGContext context, SessionLoop<PracticeSnapshot> loop, PracticeSession session,
			CurrentInputSelection selection, PracticeTunerContext shared) {
		this.context = context;
		this.shared = shared;
		this.sharedListener = changed -> tunerStateChanged(changed.isTunerOpen());
		this.loop = loop;
		this.session = session;
		this.selection = selection;
		this.selectionListener = this::selectionChanged;
		CurrentInputSelection.Snapshot current = selection.snapshot();
		this.inputRevision = current.getInputRevision();
		this.revision = current.getRevision();
	}

	public void connect() {
		MidiPlayer.getInstance(this.context).addListener(this);
		TGEditorManager.getInstance(this.context).addUpdateListener(this);
		this.selection.addListener(this.selectionListener);
		this.shared.addListener(this.sharedListener);
		tunerStateChanged(this.shared.isTunerOpen());
	}

	public void disconnect() {
		MidiPlayer.getInstance(this.context).removeListener(this);
		TGEditorManager.getInstance(this.context).removeUpdateListener(this);
		this.selection.removeListener(this.selectionListener);
		this.shared.removeListener(this.sharedListener);
	}

	/** The tuner opened or closed (FR-058): pause at the current event, or resolve that cause. */
	public void tunerStateChanged(boolean open) {
		synchronized (this) {
			if( open == this.tunerOpen ) {
				return;
			}
			this.tunerOpen = open;
		}
		if( open ) {
			this.loop.post(() -> this.session.pause(PracticeSession.PauseReason.TUNER_OPEN));
		} else {
			this.loop.post(() -> this.session.resolve(PracticeSession.PauseReason.TUNER_OPEN));
		}
	}

	/**
	 * Input selection or assessment changed (any thread). Notifications may arrive out of order;
	 * one older than the latest seen is ignored.
	 */
	public void selectionChanged(CurrentInputSelection.Snapshot snapshot) {
		final boolean inputChanged;
		synchronized (this) {
			if( snapshot.getRevision() <= this.revision ) {
				return;
			}
			this.revision = snapshot.getRevision();
			inputChanged = (snapshot.getInputRevision() != this.inputRevision);
			this.inputRevision = snapshot.getInputRevision();
		}
		final InputAssessment assessment = snapshot.getAssessment();
		this.loop.post(() -> {
			this.session.setAssessment(assessment);
			PracticeSnapshot result = null;
			if( inputChanged ) {
				result = this.session.pause(PracticeSession.PauseReason.INPUT_CHANGED);
			}
			if( assessment.isUnusable() ) {
				result = this.session.pause(PracticeSession.PauseReason.INPUT_UNUSABLE);
			} else if( assessment.getStatus() != InputAssessment.Status.UNCHECKED ) {
				this.session.resolve(PracticeSession.PauseReason.INPUT_UNUSABLE);
				result = this.session.resolve(PracticeSession.PauseReason.INPUT_CHANGED);
			}
			return result;
		});
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
			// Practice stops only when the learner asks (Stop) or when another song replaces this one. Measure
			// and song update events are NOT a reason to stop: the application fires them on its own (for
			// example when the measure-errors dialog moves the cursor), which used to cut practice off.
			if( type == TGUpdateEvent.SONG_LOADED ) {
				this.loop.postCommand(PracticeCommand.STOP);
			}
		}
	}
}
