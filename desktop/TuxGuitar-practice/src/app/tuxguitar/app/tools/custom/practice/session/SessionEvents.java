package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.player.base.MidiPlayerEvent;
import app.tuxguitar.util.TGContext;

/**
 * The events of the wait mode: everything {@link InputEvents} does, and starting playback pauses practice until an explicit Resume (FR-065) -
 * which the tempo-following mode never does, because it plays the player itself.
 */
public class SessionEvents extends InputEvents<PracticeSnapshot> {

	private final SessionLoop<PracticeSnapshot> loop;
	private final PracticeSession session;

	public SessionEvents(TGContext context, SessionLoop<PracticeSnapshot> loop, PracticeSession session,
			CurrentInputSelection selection, PracticeTunerContext shared) {
		super(context, loop, new Pausable<PracticeSnapshot>() {
			public void setAssessment(InputAssessment assessment) {
				session.setAssessment(assessment);
			}

			public PracticeSnapshot pause(PracticeSession.PauseReason reason) {
				return session.pause(reason);
			}

			public PracticeSnapshot resolve(PracticeSession.PauseReason reason) {
				return session.resolve(reason);
			}
		}, selection, shared);
		this.loop = loop;
		this.session = session;
	}

	@Override
	protected boolean watchesPlayer() {
		return true;
	}

	@Override
	protected void playerNotification(int type) {
		if( type == MidiPlayerEvent.NOTIFY_STARTED ) {
			this.loop.post(() -> this.session.pause(PracticeSession.PauseReason.PLAYBACK));
		} else if( type == MidiPlayerEvent.NOTIFY_STOPPED ) {
			this.loop.post(() -> this.session.resolve(PracticeSession.PauseReason.PLAYBACK));
		}
	}
}
