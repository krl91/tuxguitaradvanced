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
	private final Feedback feedback;
	private final PracticeEvent lastAccepted;
	private final PracticeEvent lastPassed;
	private final PracticeSession.Outcome lastPassedOutcome;

	PracticeSnapshot(PracticeSession.State state, int currentIndex, int eventCount, PracticeEvent currentEvent,
			PitchReading reading, Set<PracticeSession.PauseReason> pauseReasons, Feedback feedback,
			PracticeEvent lastAccepted, PracticeEvent lastPassed, PracticeSession.Outcome lastPassedOutcome) {
		this.state = state;
		this.currentIndex = currentIndex;
		this.eventCount = eventCount;
		this.currentEvent = currentEvent;
		this.reading = reading;
		this.pauseReasons = (pauseReasons.isEmpty() ? Collections.<PracticeSession.PauseReason>emptySet()
				: Collections.unmodifiableSet(EnumSet.copyOf(pauseReasons)));
		this.feedback = feedback;
		this.lastAccepted = lastAccepted;
		this.lastPassed = lastPassed;
		this.lastPassedOutcome = lastPassedOutcome;
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

	/** Feedback for the current expected note (CORRECT right after an acceptance); null otherwise. */
	public Feedback getFeedback() {
		return this.feedback;
	}

	/** The most recently accepted event of this run, or null. */
	public PracticeEvent getLastAccepted() {
		return this.lastAccepted;
	}

	/** The most recently skipped or automatically passed event of this run, or null. */
	public PracticeEvent getLastPassed() {
		return this.lastPassed;
	}

	/** SKIPPED or AUTO_PASSED for {@link #getLastPassed()}; never ACCEPTED. */
	public PracticeSession.Outcome getLastPassedOutcome() {
		return this.lastPassedOutcome;
	}
}
