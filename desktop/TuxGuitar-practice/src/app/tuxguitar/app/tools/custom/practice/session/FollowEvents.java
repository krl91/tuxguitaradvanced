package app.tuxguitar.app.tools.custom.practice.session;

import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.session.PracticeTunerContext;
import app.tuxguitar.util.TGContext;

/**
 * The events of the tempo-following mode: the input and the tuner pause the session and a new song stops it, as in the wait mode ({@link InputEvents}),
 * but the player's start and stop are <b>not</b> its business: the run plays the player itself and {@link FollowPlayback} tells what a play of the
 * learner means (T096).
 */
public final class FollowEvents extends InputEvents<FollowSession.Snapshot> {

	public FollowEvents(TGContext context, SessionLoop<FollowSession.Snapshot> loop, FollowHandler handler, CurrentInputSelection selection,
			PracticeTunerContext shared) {
		super(context, loop, handler, selection, shared);
	}
}
