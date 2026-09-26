package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.audio.engine.pitch.PitchReading;

/**
 * What the practice UI shows, published by the session thread. Immutable.
 */
public final class PracticeSnapshot {

	private final PracticeSession.State state;
	private final int currentIndex;
	private final int eventCount;
	private final PracticeEvent currentEvent;
	private final PitchReading reading;
	private final Set<PracticeSession.PauseReason> pauseReasons;

	PracticeSnapshot(PracticeSession.State state, int currentIndex, int eventCount, PracticeEvent currentEvent,
			PitchReading reading, Set<PracticeSession.PauseReason> pauseReasons) {
		this.state = state;
		this.currentIndex = currentIndex;
		this.eventCount = eventCount;
		this.currentEvent = currentEvent;
		this.reading = reading;
		this.pauseReasons = (pauseReasons.isEmpty() ? Collections.<PracticeSession.PauseReason>emptySet()
				: Collections.unmodifiableSet(EnumSet.copyOf(pauseReasons)));
	}

	public PracticeSession.State getState() {
		return this.state;
	}

	public int getCurrentIndex() {
		return this.currentIndex;
	}

	public int getEventCount() {
		return this.eventCount;
	}

	/** Null when there is no current event (before start or after completion). */
	public PracticeEvent getCurrentEvent() {
		return this.currentEvent;
	}

	/** Latest interpreted reading; never null. */
	public PitchReading getReading() {
		return this.reading;
	}

	public Set<PracticeSession.PauseReason> getPauseReasons() {
		return this.pauseReasons;
	}
}
