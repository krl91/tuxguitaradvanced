package app.tuxguitar.app.tools.custom.practice.session;

import java.util.Collections;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
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
	private final double confidence;
	private final ChordFeedback chordFeedback;
	private final PracticeSession.Verdict verdict;
	private final PassageStatus passageStatus;
	private final int acceptedCount;
	private final PracticeEvent techniqueEvent;
	private final boolean speedLimit;
	private final List<TechniqueResult> techniqueResults;

	PracticeSnapshot(PracticeSession.State state, int currentIndex, int eventCount, PracticeEvent currentEvent,
			PitchReading reading, Set<PracticeSession.PauseReason> pauseReasons, Feedback feedback,
			PracticeEvent lastAccepted, PracticeEvent lastPassed, PracticeSession.Outcome lastPassedOutcome, double confidence,
			ChordFeedback chordFeedback, PracticeSession.Verdict verdict, PassageStatus passageStatus, int acceptedCount,
			PracticeEvent techniqueEvent, List<TechniqueResult> techniqueResults, boolean speedLimit) {
		this.speedLimit = speedLimit;
		this.techniqueEvent = techniqueEvent;
		this.techniqueResults = techniqueResults;
		this.acceptedCount = acceptedCount;
		this.passageStatus = passageStatus;
		this.chordFeedback = chordFeedback;
		this.verdict = verdict;
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
		this.confidence = confidence;
	}

	/**
	 * True when a note was struck faster than recognition follows (about 0.3 s after the previous one) and the expected note is still
	 * waiting: a limit of the recognition to show as such, never as a mistake of the learner (spec 004 FR-008).
	 */
	public boolean isSpeedLimit() {
		return this.speedLimit;
	}

	/** The event whose techniques {@link #getTechniqueResults()} judge (the last one whose note is over), or null. */
	public PracticeEvent getTechniqueEvent() {
		return this.techniqueEvent;
	}

	/**
	 * One result per judged written technique of {@link #getTechniqueEvent()}: shown beside the pitch
	 * result, never part of it (FR-030). Empty when that event has no judged technique.
	 */
	public List<TechniqueResult> getTechniqueResults() {
		return this.techniqueResults;
	}

	/** Feedback for the current expected chord; null for other events. */
	public ChordFeedback getChordFeedback() {
		return this.chordFeedback;
	}

	/** The check / cross to show (NONE for silence, noise or while listening). */
	public PracticeSession.Verdict getVerdict() {
		return this.verdict;
	}

	/**
	 * Acceptances so far; increases with every acceptance, so the UI can tell a new acceptance of
	 * the same event (a passage retry) from a repeated snapshot.
	 */
	public int getAcceptedCount() {
		return this.acceptedCount;
	}

	/** Passage, attempt and summary (FR-088–FR-090); null when the session has no passage policy. */
	public PassageStatus getPassageStatus() {
		return this.passageStatus;
	}

	/** Pitch confidence (0–1) of the latest analysis frame. */
	public double getConfidence() {
		return this.confidence;
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
