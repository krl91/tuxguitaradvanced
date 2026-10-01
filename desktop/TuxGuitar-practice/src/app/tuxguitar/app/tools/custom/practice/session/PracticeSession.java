package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticePassages;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;

/**
 * Wait-for-correct-note practice state machine (data-model.md "State machine").
 * Single-threaded: every method runs on the session thread ({@link SessionLoop}).
 *
 * <p>An expected note is accepted only after a fresh articulation — an onset whose transient
 * is later than the last reference point (the previous acceptance, Start, Resume, Restart or
 * Skip) — and readings that are reliable (confidence gate, usable level), on the expected note
 * (octave included) and within the practice tolerance, continuing without interruption for the
 * stability window (owner requirement 2026-09-28, extending FR-022 to every note: waiting is
 * preferred over a false success). Silence, low-confidence noise, transients without a stable
 * matching pitch, uncertain and incorrect readings never advance (FR-007). Thresholds are
 * parameters, not product constants.</p>
 */
public final class PracticeSession implements SessionLoop.Handler<PracticeSnapshot> {

	public enum State {
		PREPARING,
		RUNNING,
		PAUSED,
		READY_TO_RESUME,
		COMPLETE,
		STOPPED
	}

	public enum PauseReason {
		INPUT_UNUSABLE,
		TUNER_OPEN,
		INPUT_CHANGED,
		PLAYBACK
	}

	/**
	 * The judgement shown as a symbol (owner requirement 2026-09-28): a green check right after an
	 * acceptance, a red cross only for a confirmed wrong attempt, nothing otherwise (silence,
	 * noise, listening). Shown for {@link #VERDICT_HOLD_NANOS} at most.
	 */
	public enum Verdict {
		NONE,
		CORRECT,
		WRONG
	}

	/** Provisional display time of a verdict. */
	public static final long VERDICT_HOLD_NANOS = 1_000_000_000L;

	public enum Outcome {
		ACCEPTED,
		SKIPPED,
		AUTO_PASSED
	}

	private final PracticeTrackSelection selection;
	private final PracticeSequence sequence;
	private final NoteInterpreter interpreter;
	private final TolerancePolicy tolerance;
	private final long stabilityNanos;
	/** Confidence-scaled stability (research R3 L2): off until {@link #setEarlyStability}. */
	private double earlyMargin = Double.POSITIVE_INFINITY;
	private long earlyStabilityNanos;
	private Consumer<int[]> expectedPitchSink;
	private final long articulationLatencyNanos;
	private final Map<Integer, Outcome> outcomes = new HashMap<Integer, Outcome>();
	private final Set<PauseReason> pauseReasons = EnumSet.noneOf(PauseReason.class);

	private State state = State.PREPARING;
	private InputAssessment assessment = InputAssessment.UNCHECKED;
	private PitchReading reading = PitchReading.noReading();
	private double confidence;
	private int currentIndex;
	private long matchStartNanos = -1;
	/** Onsets whose transient is not later than this do not arm acceptance. */
	private long armReferenceNanos = Long.MIN_VALUE;
	/** Timestamp of the latest analysis frame, whatever the state. */
	private long lastFrameNanos = Long.MIN_VALUE;
	private boolean articulationArmed;
	/** Attack time of the onset that armed the current attempt. */
	private long armTransientNanos;
	private Feedback feedback;
	private PracticeEvent lastAccepted;
	private PracticeEvent lastPassed;
	private Outcome lastPassedOutcome;
	private final ChordMatcher chordMatcher;
	private int chordRun;
	private ChordFeedback chordFeedback;
	/** The last confirmed wrong strum of the current chord, shown until the next attempt succeeds. */
	private ChordFeedback lastIncomplete;
	private int wrongMidi = -1;
	private long wrongStartNanos;
	private Verdict verdict = Verdict.NONE;
	private long verdictNanos;
	private final PassagePolicy passagePolicy;
	private final PracticePassages passages;
	/** Index of the passage being attempted, or -1. */
	private int attemptPassage = -1;
	private int attempt;
	/** Events of the current attempt with a confirmed wrong verdict or skipped, each once. */
	private final Set<Integer> attemptErrors = new HashSet<Integer>();
	private final Map<Integer, PassageResult> passageResults = new TreeMap<Integer, PassageResult>();
	private int retries;
	/** Acceptances since the session was created; never reset, so each acceptance is distinguishable. */
	private int acceptedCount;
	private PassageStatus.Decision decision = PassageStatus.Decision.NONE;
	private int decisionErrors;
	private int decisionJudged;
	private int decisionAttempt;

	private enum PassageResult {
		PASSED_FIRST_ATTEMPT,
		PASSED_AFTER_RETRY,
		NOT_PASSED
	}

	/**
	 * @param stabilityNanos how long matching readings must continue before acceptance
	 * @param articulationLatencyNanos how long after its transient an onset is reported by the
	 *        analysis; onsets whose transient precedes the previous acceptance are ignored
	 */
	public PracticeSession(PracticeTrackSelection selection, PracticeSequence sequence, NoteInterpreter interpreter,
			TolerancePolicy tolerance, long stabilityNanos, long articulationLatencyNanos) {
		this(selection, sequence, interpreter, tolerance, stabilityNanos, articulationLatencyNanos,
				new ChordMatcher(ChordMatcher.Parameters.defaults()));
	}

	public PracticeSession(PracticeTrackSelection selection, PracticeSequence sequence, NoteInterpreter interpreter,
			TolerancePolicy tolerance, long stabilityNanos, long articulationLatencyNanos, ChordMatcher chordMatcher) {
		this(selection, sequence, interpreter, tolerance, stabilityNanos, articulationLatencyNanos, chordMatcher, null);
	}

	/**
	 * @param passagePolicy passage retries (FR-088–FR-090), or null to practise straight through
	 */
	public PracticeSession(PracticeTrackSelection selection, PracticeSequence sequence, NoteInterpreter interpreter,
			TolerancePolicy tolerance, long stabilityNanos, long articulationLatencyNanos, ChordMatcher chordMatcher,
			PassagePolicy passagePolicy) {
		this.passagePolicy = passagePolicy;
		this.passages = (passagePolicy != null ? PracticePassages.build(sequence, passagePolicy.getMeasuresPerPassage()) : null);
		this.chordMatcher = chordMatcher;
		this.selection = selection;
		this.sequence = sequence;
		this.interpreter = interpreter;
		this.tolerance = tolerance;
		this.stabilityNanos = stabilityNanos;
		this.articulationLatencyNanos = articulationLatencyNanos;
		this.currentIndex = sequence.getStartIndex();
	}

	/**
	 * When the audio's evidence for the expected pitch beats every neighbour by at least
	 * {@code margin}, the note is accepted after {@code stabilityNanos} instead of the standard
	 * stability (never longer than it). The standard stability stays the fallback.
	 */
	public void setEarlyStability(double margin, long stabilityNanos) {
		this.earlyMargin = margin;
		this.earlyStabilityNanos = stabilityNanos;
	}

	/**
	 * Receives the pitches expected now (null when none) on every event change, from the session
	 * thread; the practice window passes them to the audio engine so frames carry the evidence.
	 */
	public void setExpectedPitchSink(Consumer<int[]> sink) {
		this.expectedPitchSink = sink;
		publishExpectedPitches();
	}

	private void publishExpectedPitches() {
		if( this.expectedPitchSink == null ) {
			return;
		}
		PracticeEvent event = currentEvent();
		int[] pitches = null;
		if( this.state == State.RUNNING && event != null ) {
			if( event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
				pitches = new int[] { event.getExpectedMidi() };
			} else if( event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
				pitches = event.getRequiredPitches();
			}
		}
		this.expectedPitchSink.accept(pitches);
	}

	private long requiredStabilityNanos(AnalysisFrame frame, PracticeEvent event) {
		TargetEvidence evidence = frame.getTargetEvidence();
		if( evidence != null && evidence.of(event.getExpectedMidi()) != null
				&& evidence.marginOver(event.getExpectedMidi()) >= this.earlyMargin ) {
			return Math.min(this.stabilityNanos, this.earlyStabilityNanos);
		}
		return this.stabilityNanos;
	}

	/** The latest input assessment; an unusable input is required to pause the session. */
	public void setAssessment(InputAssessment assessment) {
		this.assessment = assessment;
	}

	@Override
	public PracticeSnapshot onCommand(PracticeCommand command) {
		switch( command ) {
			case START:
				if( this.state == State.PREPARING && canStart() ) {
					enterRunning(this.currentIndex);
				}
				break;
			case RESUME:
				if( this.state == State.READY_TO_RESUME ) {
					this.state = State.RUNNING;
					disarm();
					resetMatch();
				}
				break;
			case RESTART:
				if( this.state == State.COMPLETE ) {
					this.outcomes.clear();
					enterRunning(this.sequence.getStartIndex());
				}
				break;
			case STOP:
				this.state = State.STOPPED;
				break;
			case SKIP:
				if( this.state == State.RUNNING && currentEvent() != null ) {
					PracticeEvent skipped = currentEvent();
					recordError(skipped);
					this.outcomes.put(skipped.getSequenceIndex(), Outcome.SKIPPED);
					this.lastPassed = skipped;
					this.lastPassedOutcome = Outcome.SKIPPED;
					this.feedback = null;
					disarm();
					resetMatch();
					advance();
				}
				break;
			default:
				return null;
		}
		publishExpectedPitches();
		return snapshot();
	}

	@Override
	public PracticeSnapshot onFrame(AnalysisFrame frame) {
		this.reading = this.interpreter.interpret(frame.getF0Hz(), frame.getConfidence(), this.assessment);
		this.confidence = frame.getConfidence();
		this.lastFrameNanos = frame.getTimestampNanos();
		if( this.state != State.RUNNING ) {
			return snapshot();
		}
		long now = frame.getTimestampNanos();
		if( frame.isOnset() && now - this.articulationLatencyNanos > this.armReferenceNanos ) {
			if( !this.articulationArmed ) {
				this.armTransientNanos = now - this.articulationLatencyNanos;
			}
			this.articulationArmed = true;
			// a new attempt clears a wrong verdict
			if( this.verdict == Verdict.WRONG ) {
				this.verdict = Verdict.NONE;
			}
		}
		if( this.verdict != Verdict.NONE && now - this.verdictNanos > VERDICT_HOLD_NANOS ) {
			this.verdict = Verdict.NONE;
		}
		PracticeEvent event = currentEvent();
		this.feedback = null;
		this.chordFeedback = null;
		// a match counts only at a usable level: near-silence can give confident sub-guitar
		// readings (research T077), and clipping distorts the pitch (T076: a -40 dBFS floor
		// changed neither correctness nor latency on the real-guitar clips)
		boolean usableLevel = (frame.getLevelState() == AnalysisFrame.LevelState.SUITABLE
				|| frame.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH);
		if( event != null && event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			onNoteFrame(frame, event, usableLevel);
		} else if( event != null && event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
			onChordFrame(frame, event, usableLevel);
		}
		return snapshot();
	}

	/**
	 * A stable pitch that differs from the note just accepted can only be the new note (research R3
	 * lever L3): it needs no confirmed attack, which saves the attack confirmation delay in runs and
	 * is the only way to hear legato notes. The same pitch, the first note and anything after a skip
	 * or an automatically passed event still need a fresh attack.
	 */
	private boolean isPitchChange(PracticeEvent event) {
		PracticeEvent previous = this.lastAccepted;
		return previous != null
				&& previous.getKind() == PracticeEvent.Kind.EXPECTED_NOTE
				&& previous.getSequenceIndex() == event.getSequenceIndex() - 1
				&& this.outcomes.get(previous.getSequenceIndex()) == Outcome.ACCEPTED
				&& previous.getExpectedMidi() != event.getExpectedMidi();
	}

	private void onNoteFrame(AnalysisFrame frame, PracticeEvent event, boolean usableLevel) {
		long now = frame.getTimestampNanos();
		this.feedback = Feedback.evaluate(frame, this.reading, event.getExpectedMidi(), this.tolerance, !this.articulationArmed);
		if( usableLevel && this.tolerance.matches(this.reading, event.getExpectedMidi()) ) {
			if( this.matchStartNanos < 0 ) {
				this.matchStartNanos = now;
			}
			boolean stable = now - this.matchStartNanos >= requiredStabilityNanos(frame, event);
			if( stable && (this.articulationArmed || isPitchChange(event)) ) {
				accept(event, now);
				return;
			}
		} else {
			// only the match run: a wrong-note run continues through non-matching frames
			this.matchStartNanos = -1;
		}
		// a wrong note is reported only when it is a freshly played, reliable note held for the
		// stability window: a note still ringing, a glitch or noise shows no cross (prefer waiting)
		if( this.feedback.getKind() == Feedback.Kind.INCORRECT ) {
			if( !this.articulationArmed ) {
				this.wrongMidi = -1;
				this.feedback = Feedback.WAITING;
				return;
			}
			if( this.feedback.getDetectedMidi() != this.wrongMidi ) {
				this.wrongMidi = this.feedback.getDetectedMidi();
				this.wrongStartNanos = now;
			}
			if( now - this.wrongStartNanos >= this.stabilityNanos ) {
				setVerdict(Verdict.WRONG, now);
			} else {
				this.feedback = Feedback.WAITING;
			}
		} else {
			this.wrongMidi = -1;
		}
	}

	/**
	 * Expected chord (FR-079–FR-082, FR-086): after a fresh strum, every verifiable pitch must be
	 * heard in {@link ChordMatcher.Parameters#stableHops} consecutive frames within
	 * {@link ChordMatcher.Parameters#windowNanos} of the strum's attack; when the window ends
	 * first, the strum is a confirmed wrong chord (missing pitches listed) and a new strum is needed.
	 */
	private void onChordFrame(AnalysisFrame frame, PracticeEvent event, boolean usableLevel) {
		long now = frame.getTimestampNanos();
		SpectrumFrame spectrum = frame.getSpectrum();
		if( spectrum == null || !usableLevel ) {
			this.chordRun = 0;
			this.chordFeedback = (this.lastIncomplete != null ? this.lastIncomplete
					: (frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING ? ChordFeedback.UNCERTAIN : ChordFeedback.WAITING));
			return;
		}
		ChordMatcher.Result result = this.chordMatcher.check(spectrum, event.getRequiredPitches(), event.getToleratedPitches());
		this.chordRun = (result.isComplete() ? this.chordRun + 1 : 0);
		if( !this.articulationArmed ) {
			this.chordFeedback = (result.isComplete() ? new ChordFeedback(ChordFeedback.Kind.STRUM_AGAIN, new int[0], result.getUnverifiable())
					: (this.lastIncomplete != null ? this.lastIncomplete : ChordFeedback.WAITING));
			return;
		}
		if( this.chordRun >= this.chordMatcher.getParameters().stableHops ) {
			accept(event, now);
			return;
		}
		if( now - this.armTransientNanos > this.chordMatcher.getParameters().windowNanos ) {
			this.lastIncomplete = new ChordFeedback(ChordFeedback.Kind.INCOMPLETE, result.getMissing(), result.getUnverifiable());
			this.chordFeedback = this.lastIncomplete;
			setVerdict(Verdict.WRONG, now);
			// the attempt is over: the next one needs a new strum
			this.articulationArmed = false;
			this.armReferenceNanos = now;
			this.chordRun = 0;
			return;
		}
		this.chordFeedback = new ChordFeedback(ChordFeedback.Kind.WAITING, new int[0], result.getUnverifiable());
	}

	private void setVerdict(Verdict verdict, long now) {
		this.verdict = verdict;
		this.verdictNanos = now;
		if( verdict == Verdict.WRONG ) {
			recordError(currentEvent());
		}
	}

	/**
	 * A confirmed wrong verdict or a Skip on a judged event counts once per event and attempt
	 * (FR-089); rests, auto-passed and unsupported events never count.
	 */
	private void recordError(PracticeEvent event) {
		if( this.passages != null && event != null && PracticePassages.isJudged(event)
				&& this.passages.passageOf(event.getSequenceIndex()) == this.attemptPassage ) {
			this.attemptErrors.add(event.getSequenceIndex());
		}
	}

	/** Starts the first attempt of the passage holding the current event. */
	private void beginPassage() {
		this.attemptPassage = (this.passages != null && currentEvent() != null ? this.passages.passageOf(this.currentIndex) : -1);
		this.attempt = 1;
		this.attemptErrors.clear();
	}

	/**
	 * Called after moving past an event: when practice left the attempted passage, decides at the
	 * end of the attempt (FR-089): repeat it from its first event when the error rate is above the
	 * limit and retries remain, otherwise move on and record the result.
	 */
	private void endAttemptIfLeft() {
		if( this.attemptPassage < 0 ) {
			return;
		}
		if( currentEvent() != null && this.passages.passageOf(this.currentIndex) == this.attemptPassage ) {
			return;
		}
		PracticePassages.Passage passage = this.passages.getPassages().get(this.attemptPassage);
		int errors = this.attemptErrors.size();
		int judged = passage.getJudgedCount();
		boolean failed = this.passagePolicy.isFailed(errors, judged);
		this.decisionErrors = errors;
		this.decisionJudged = judged;
		this.decisionAttempt = this.attempt;
		if( failed && this.attempt <= this.passagePolicy.getMaxRetries() ) {
			this.decision = PassageStatus.Decision.RETRY;
			this.retries ++;
			int end = (this.state == State.COMPLETE ? this.sequence.getEvents().size() : this.currentIndex);
			for( int i = passage.getFirstIndex() ; i < end ; i ++ ) {
				this.outcomes.remove(i);
			}
			this.state = State.RUNNING;
			this.currentIndex = passage.getFirstIndex();
			this.attempt ++;
			this.attemptErrors.clear();
			// acceptance was already disarmed by the acceptance or Skip that ended the attempt,
			// so the last note still ringing cannot accept the passage's first one (FR-086)
			newEvent();
			resetMatch();
			passAutomatically();
			return;
		}
		if( judged > 0 ) {
			this.decision = (failed ? PassageStatus.Decision.MOVED_ON : PassageStatus.Decision.PASSED);
			this.passageResults.put(this.attemptPassage, failed ? PassageResult.NOT_PASSED
					: (this.attempt == 1 ? PassageResult.PASSED_FIRST_ATTEMPT : PassageResult.PASSED_AFTER_RETRY));
		}
		beginPassage();
	}

	/** Pauses running practice for a cause (FR-043, FR-044, FR-058, FR-065). */
	public PracticeSnapshot pause(PauseReason reason) {
		if( this.state == State.RUNNING || this.state == State.PAUSED || this.state == State.READY_TO_RESUME ) {
			this.pauseReasons.add(reason);
			this.state = State.PAUSED;
			resetMatch();
			publishExpectedPitches();
		}
		return snapshot();
	}

	/** A pause cause is resolved; practice waits for an explicit Resume once none remain. */
	public PracticeSnapshot resolve(PauseReason reason) {
		this.pauseReasons.remove(reason);
		if( this.state == State.PAUSED && this.pauseReasons.isEmpty() ) {
			this.state = State.READY_TO_RESUME;
			publishExpectedPitches();
		}
		return snapshot();
	}

	public State getState() {
		return this.state;
	}

	/** Acceptances since the session was created (see {@link PracticeSnapshot#getAcceptedCount()}). */
	public int getAcceptedCount() {
		return this.acceptedCount;
	}

	public int getCurrentIndex() {
		return this.currentIndex;
	}

	public Outcome getOutcome(int sequenceIndex) {
		return this.outcomes.get(sequenceIndex);
	}

	private boolean canStart() {
		InputAssessment.Status status = this.assessment.getStatus();
		return this.selection.isAccepted() && !this.sequence.isEmpty()
				&& (status == InputAssessment.Status.SUITABLE || status == InputAssessment.Status.SUBOPTIMAL);
	}

	private void enterRunning(int index) {
		this.currentIndex = index;
		this.state = State.RUNNING;
		disarm();
		newEvent();
		this.verdict = Verdict.NONE;
		this.feedback = null;
		this.lastAccepted = null;
		this.lastPassed = null;
		this.lastPassedOutcome = null;
		resetMatch();
		passAutomatically();
		this.passageResults.clear();
		this.retries = 0;
		this.decision = PassageStatus.Decision.NONE;
		beginPassage();
	}

	private void accept(PracticeEvent event, long timestampNanos) {
		this.outcomes.put(event.getSequenceIndex(), Outcome.ACCEPTED);
		this.lastAccepted = event;
		this.acceptedCount ++;
		this.feedback = Feedback.CORRECT;
		this.chordFeedback = null;
		this.armReferenceNanos = timestampNanos;
		this.articulationArmed = false;
		setVerdict(Verdict.CORRECT, timestampNanos);
		resetMatch();
		advance();
	}

	/** Moves to the next event, passing unsupported ones automatically (FR-011, FR-013). */
	private void advance() {
		this.currentIndex ++;
		newEvent();
		passAutomatically();
		endAttemptIfLeft();
		publishExpectedPitches();
	}

	private void passAutomatically() {
		while( this.currentIndex < this.sequence.getEvents().size()
				&& this.sequence.getEvents().get(this.currentIndex).getKind() == PracticeEvent.Kind.AUTO_PASS ) {
			PracticeEvent passed = this.sequence.getEvents().get(this.currentIndex);
			this.outcomes.put(passed.getSequenceIndex(), Outcome.AUTO_PASSED);
			this.lastPassed = passed;
			this.lastPassedOutcome = Outcome.AUTO_PASSED;
			this.currentIndex ++;
		}
		if( this.currentIndex >= this.sequence.getEvents().size() ) {
			this.state = State.COMPLETE;
		}
	}

	/** Only an articulation after now can arm acceptance (Start, Resume, Restart, Skip). */
	private void disarm() {
		this.articulationArmed = false;
		this.armReferenceNanos = this.lastFrameNanos;
	}

	/** Clears the per-event attempt state (single-note match, wrong-note run, chord run). */
	private void resetMatch() {
		this.matchStartNanos = -1;
		this.wrongMidi = -1;
		this.chordRun = 0;
	}

	/** Moving to another event forgets the previous event's wrong strum. */
	private void newEvent() {
		this.lastIncomplete = null;
		publishExpectedPitches();
	}

	private PracticeEvent currentEvent() {
		if( this.state == State.COMPLETE || this.currentIndex >= this.sequence.getEvents().size() ) {
			return null;
		}
		return this.sequence.getEvents().get(this.currentIndex);
	}

	private PracticeSnapshot snapshot() {
		return new PracticeSnapshot(this.state, this.currentIndex, this.sequence.getEvents().size(), currentEvent(),
				this.reading, this.pauseReasons, this.feedback, this.lastAccepted, this.lastPassed, this.lastPassedOutcome,
				this.confidence, this.chordFeedback, this.verdict, passageStatus(), this.acceptedCount);
	}

	private PassageStatus passageStatus() {
		if( this.passages == null ) {
			return null;
		}
		PracticePassages.Passage passage = (this.attemptPassage >= 0 ? this.passages.getPassages().get(this.attemptPassage) : null);
		PassageStatus.Summary summary = null;
		if( this.state == State.COMPLETE ) {
			int first = 0;
			int afterRetry = 0;
			List<PassageStatus.MeasureRange> notPassed = new ArrayList<PassageStatus.MeasureRange>();
			for( Map.Entry<Integer, PassageResult> entry : this.passageResults.entrySet() ) {
				if( entry.getValue() == PassageResult.PASSED_FIRST_ATTEMPT ) {
					first ++;
				} else if( entry.getValue() == PassageResult.PASSED_AFTER_RETRY ) {
					afterRetry ++;
				} else {
					PracticePassages.Passage left = this.passages.getPassages().get(entry.getKey());
					notPassed.add(new PassageStatus.MeasureRange(left.getFirstMeasure(), left.getLastMeasure()));
				}
			}
			summary = new PassageStatus.Summary(this.passageResults.size(), first, afterRetry, notPassed, this.retries);
		}
		return new PassageStatus(passage != null ? passage.getNumber() : 0, this.passages.getPassages().size(),
				passage != null ? passage.getFirstMeasure() : 0, passage != null ? passage.getLastMeasure() : 0,
				this.attempt, this.passagePolicy.getMaxRetries() + 1, this.attemptErrors.size(),
				passage != null ? passage.getJudgedCount() : 0, this.decision, this.decisionErrors, this.decisionJudged,
				this.decisionAttempt, summary, (int) Math.round(this.passagePolicy.getMaxErrorRate() * 100));
	}
}
