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
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueTarget;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.recognition.TargetEvidence;
import app.tuxguitar.audio.engine.technique.NoteSegment;

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
	/** Attack-difference acceptance (spec 004); null: the f0 and chord rules judge. */
	private PolyphonicAcceptance polyphonic;
	private static final long NEIGHBOUR_VETO_NANOS = 120L * 1_000_000L;
	private static final double NEIGHBOUR_CONFIDENCE = 0.8;
	private long neighbourHeardNanos = Long.MIN_VALUE;
	private final long articulationLatencyNanos;
	/** Judges the written techniques of the accepted notes (spec 002); null: no technique line. */
	private TechniqueJudge techniqueJudge;
	private static final int RECENT_FRAMES = 64;
	private static final long TECHNIQUE_WINDOW_NANOS = 1_500_000_000L;
	/**
	 * A bent note is observed from its acceptance for its written length at the score tempo, never less than 1.0 s (a learner in
	 * wait mode does not play at the score tempo) and never more than 2.5 s (about the longest a guitar note sustains), research R5 addendum.
	 */
	private static final long BEND_WINDOW_MIN_NANOS = 1_000_000_000L;
	private static final long BEND_WINDOW_MAX_NANOS = 2_500_000_000L;
	/** A pitch within this many semitones of the written path counts as on the path: a bend is never exact. */
	private static final double BEND_PATH_MARGIN_SEMITONES = 0.5;
	/** The tolerance of the bend target is the detector's: what is called on pitch when the bend is judged. */
	private static final app.tuxguitar.audio.engine.technique.BendDetector.Parameters BEND_DETECTOR = app.tuxguitar.audio.engine.technique.BendDetector.Parameters.defaults();
	/** How far before the acceptance a note's segment may start (acceptance follows the attack by about 100 ms). */
	private static final long TECHNIQUE_LEAD_NANOS = 150L * 1_000_000L;
	/** The last hops (timestamp, MIDI pitch or NaN, level, onset) so a note's segment can start at its attack. */
	private final java.util.ArrayDeque<double[]> recentFrames = new java.util.ArrayDeque<double[]>();
	private long pendingWindowNanos = TECHNIQUE_WINDOW_NANOS;
	/**
	 * The hops of the bend in progress, from the moment its run started on the written pitch: the note is accepted only when the bend has
	 * reached its target, so the rise is over when the acceptance comes and the detector must still see it (owner's decision of 2026-10-07).
	 */
	private final List<double[]> bendFrames = new ArrayList<double[]>();
	private boolean bendCollecting;
	private long bendTargetStartNanos = -1;
	/** The pitch has touched the written peak of the bend (the rise is made): the release, if written, is what is left. */
	private boolean bendPeakReached;
	private static final int BEND_FRAMES_MAX = 800;
	/** The bent note whose observation window is open (null: none) and when it ends. */
	private PracticeEvent bendEvent;
	private long bendWindowEndNanos;
	private PracticeEvent pendingEvent;
	private List<TechniqueTarget> pendingTargets;
	private final List<double[]> pendingFrames = new ArrayList<double[]>();
	private long pendingStartNanos;
	private PracticeEvent techniqueEvent;
	private List<TechniqueResult> techniqueResults = java.util.Collections.<TechniqueResult>emptyList();
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
	/**
	 * A new attack this soon after an acceptance is the learner playing faster than recognition follows (the acceptance comes about
	 * 0.1 s after its attack and the evidence of that attack is ignored for 0.22 s: about 0.3 s between notes, research R11).
	 */
	private static final long SPEED_WINDOW_NANOS = 200L * 1_000_000L;
	/** The limit is only shown when the note that followed has still not been recognised after this long. */
	private static final long SPEED_SHOW_NANOS = 600L * 1_000_000L;
	private long lastAcceptNanos = Long.MIN_VALUE;
	/** The level the learner plays at, for the "input too weak for fast recognition" message (FR-015). */
	private final InputQuality inputQuality = new InputQuality(PracticeParameters.WEAK_FAST_DBFS);
	private long speedHitNanos = Long.MIN_VALUE;
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
	 * Judges notes and chords from the attack evidence of the frames (spec 004) instead of the single
	 * pitch and the chord spectrum, whenever frames carry it; null goes back to those rules.
	 */
	public void setPolyphonicAcceptance(PolyphonicAcceptance acceptance) {
		this.polyphonic = acceptance;
	}

	/**
	 * Judges the written techniques of the notes accepted from now on and reports the results in the
	 * snapshots; they never change what is accepted (FR-030). Null stops judging.
	 */
	public void setTechniqueJudge(TechniqueJudge judge) {
		this.techniqueJudge = judge;
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
		this.expectedPitchSink.accept(this.state == State.RUNNING && event != null ? expectedPitchesOf(event) : null);
	}

	/**
	 * A reliable single pitch a semitone or a whole tone from the expected note says that neighbour was
	 * played: the attack evidence cannot tell such close pitches apart at low frequencies, the pitch
	 * estimate can (spec 004). Anything else the estimate says (a ringing bass) is ignored.
	 */
	private boolean closeNeighbourHeard(AnalysisFrame frame, int expectedMidi) {
		double f0 = frame.getF0Hz();
		if( !Double.isFinite(f0) || f0 <= 0.0 || !(frame.getConfidence() >= NEIGHBOUR_CONFIDENCE) ) {
			return false;
		}
		double exact = 69.0 + 12.0 * Math.log(f0 / 440.0) / Math.log(2.0);
		int midi = (int) Math.round(exact);
		double cents = (exact - midi) * 100.0;
		int distance = Math.abs(midi - expectedMidi);
		return (distance == 1 || distance == 2) && Math.abs(cents) <= this.tolerance.getPracticeAcceptCents();
	}

	/** True when the attack evidence shows the pitch as one that has just been struck, not one still ringing. */
	private static boolean newlyStruck(app.tuxguitar.audio.engine.analysis.AttackEvidence evidence, int midi) {
		return evidence.getMaximum() > 0.0 && evidence.amplitudeOf(midi) >= 0.5 * evidence.getMaximum();
	}

	/** The fret of each pitch of {@link #expectedPitchesOf(PracticeEvent)} (0 when unknown). */
	private static int[] expectedFretsOf(PracticeEvent event, int[] pitches) {
		int[] frets = new int[pitches.length];
		if( event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			frets[0] = Math.max(0, event.getFret());
			return frets;
		}
		for( int i = 0 ; i < pitches.length ; i ++ ) {
			int fret = Integer.MAX_VALUE;
			for( PracticeEvent.ChordNote note : event.getChordNotes() ) {
				if( note.getMidi() == pitches[i] ) {
					fret = Math.min(fret, note.getFret());
				}
			}
			frets[i] = (fret == Integer.MAX_VALUE ? 0 : fret);
		}
		return frets;
	}

	/** The pitches to hear for a note or a chord; null for any other event. */
	private static int[] expectedPitchesOf(PracticeEvent event) {
		if( event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			return new int[] { event.getExpectedMidi() };
		}
		if( event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
			return event.getRequiredPitches();
		}
		return null;
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
		collectTechniqueFrame(frame);
		this.inputQuality.offer(frame);
		if( this.state != State.RUNNING ) {
			return snapshot();
		}
		long now = frame.getTimestampNanos();
		if( frame.isOnset() && this.lastAcceptNanos != Long.MIN_VALUE ) {
			long transient_ = now - this.articulationLatencyNanos;
			if( transient_ > this.lastAcceptNanos - 20_000_000L && transient_ - this.lastAcceptNanos < SPEED_WINDOW_NANOS && this.speedHitNanos == Long.MIN_VALUE ) {
				this.speedHitNanos = now;
			}
		}
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
		if( PracticeTrace.enabled() ) {
			PracticeTrace.frame(frame, this.currentIndex, event == null ? null : expectedPitchesOf(event), this.verdict + "/" + this.articulationArmed);
		}
		this.feedback = null;
		this.chordFeedback = null;
		// a match counts only at a usable level: near-silence can give confident sub-guitar
		// readings (research T077), and clipping distorts the pitch (T076: a -40 dBFS floor
		// changed neither correctness nor latency on the real-guitar clips)
		boolean usableLevel = (frame.getLevelState() == AnalysisFrame.LevelState.SUITABLE
				|| frame.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH);
		if( this.polyphonic != null && frame.getAttackEvidence() != null ) {
			if( event != null && (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE || event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD) ) {
				int[] pitches = expectedPitchesOf(event);
				if( pitches.length == 1 && closeNeighbourHeard(frame, pitches[0]) ) {
					this.neighbourHeardNanos = now;
				}
				// the pitch estimate is unreliable during the attack itself: the veto outlasts the reading
				boolean vetoed = (this.neighbourHeardNanos != Long.MIN_VALUE && now - this.neighbourHeardNanos < NEIGHBOUR_VETO_NANOS);
				// a quiet note is still a note (a low string under a loud one): only clipping, which distorts the spectrum, stops the judgement
				// an expected pitch on the path of the bend just played cannot be told from the bend by its spectrum: it waits for its own attack
				// a note whose bend is required is accepted when the bend reaches its target, not on the spectrum of its base note
				boolean bendRequired = requiresMovement(event);
				boolean bentPitch = bendRequired || (pitches.length == 1 && this.bendEvent != null && now < this.bendWindowEndNanos && !this.articulationArmed
						&& pitches[0] >= corridorOf(this.bendEvent)[0] && pitches[0] <= corridorOf(this.bendEvent)[1]);
				if( !bentPitch && this.polyphonic.offer((!vetoed && frame.getLevelState() != AnalysisFrame.LevelState.CLIPPING ? frame.getAttackEvidence() : null), now, pitches,
						expectedFretsOf(event, pitches), this.tolerance) ) {
					accept(event, now);
					return snapshot();
				}
				// the single-pitch and chord rules keep judging too: they are the safe path for a clean single note (spec 004, SC-004)
			}
		}
		if( event != null && event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			onNoteFrame(frame, event, usableLevel);
		} else if( event != null && event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD ) {
			onChordFrame(frame, event, usableLevel);
		}
		return snapshot();
	}

	/** True for a written bend that rises (a pre-bend alone, or a release, has nothing to reach after the start): its note needs the bend made. */
	private static boolean requiresBend(PracticeEvent event) {
		if( event.getBendPath() == null ) {
			return false;
		}
		double rise = (event.getBendPath().getPeakSemitones() - event.getBendPath().getPoints().get(0).getSemitones()) * 100.0;
		return rise >= BEND_DETECTOR.minWrittenMoveCents;
	}

	/** True for a written release (the pitch falls by at least 25 cents from the peak to the end of the path): its note needs the release made. */
	private static boolean requiresRelease(PracticeEvent event) {
		if( event.getBendPath() == null ) {
			return false;
		}
		double fall = (event.getBendPath().getPeakSemitones() - event.getBendPath().getTargetSemitones()) * 100.0;
		return fall >= BEND_DETECTOR.minWrittenMoveCents;
	}

	/** A written bend or release: the note is accepted when the movement is made, not on its first pitch. */
	private static boolean requiresMovement(PracticeEvent event) {
		return requiresBend(event) || requiresRelease(event);
	}

	/** True when the pitch has come down to the written end of the release (within the detector's tolerance, and the path margin below). */
	private boolean onReleaseEnd(PracticeEvent event) {
		if( !this.reading.hasPitch() ) {
			return false;
		}
		double base = bendBase(event);
		double end = event.getBendPath().getTargetSemitones();
		double fall = (event.getBendPath().getPeakSemitones() - end) * 100.0;
		double tolerance = Math.max(BEND_DETECTOR.onToleranceCents, BEND_DETECTOR.onToleranceShare * fall) / 100.0;
		double midi = this.reading.getMidi() + this.reading.getCents() / 100.0;
		return midi >= base + end - BEND_PATH_MARGIN_SEMITONES && midi <= base + end + tolerance;
	}

	/** True when the pitch has reached the written target of the bend (within the detector's tolerance) and has not gone past the path. */
	private boolean onBendTarget(PracticeEvent event) {
		if( !this.reading.hasPitch() ) {
			return false;
		}
		double base = bendBase(event);
		double peak = event.getBendPath().getPeakSemitones();
		double rise = (peak - event.getBendPath().getPoints().get(0).getSemitones()) * 100.0;
		double tolerance = Math.max(BEND_DETECTOR.onToleranceCents, BEND_DETECTOR.onToleranceShare * rise) / 100.0;
		double midi = this.reading.getMidi() + this.reading.getCents() / 100.0;
		return midi >= base + peak - tolerance && midi <= base + peak + BEND_PATH_MARGIN_SEMITONES;
	}

	private static long bendWindowNanos(PracticeEvent event) {
		long written = (long) (event.getBendWrittenSeconds() * 1e9);
		return Math.max(BEND_WINDOW_MIN_NANOS, Math.min(BEND_WINDOW_MAX_NANOS, written));
	}

	/** The pitches (MIDI, fractional) a bent note covers: from the lowest to the highest point of its path, with the margin. */
	private static double[] corridorOf(PracticeEvent event) {
		double low = Double.POSITIVE_INFINITY;
		double high = Double.NEGATIVE_INFINITY;
		double base = bendBase(event);
		for( app.tuxguitar.app.tools.custom.practice.model.PitchPath.Point point : event.getBendPath().getPoints() ) {
			low = Math.min(low, point.getSemitones());
			high = Math.max(high, point.getSemitones());
		}
		return new double[] { base + low - BEND_PATH_MARGIN_SEMITONES, base + high + BEND_PATH_MARGIN_SEMITONES };
	}

	/** The fretted pitch of a bent note: the base carried by the targets of its path. */
	private static double bendBase(PracticeEvent event) {
		for( TechniqueTarget target : event.getTechniqueTargets() ) {
			if( target.getBaseMidi() != TechniqueTarget.NONE ) {
				return target.getBaseMidi();
			}
		}
		return event.getExpectedMidi();
	}

	private boolean onPath(double[] corridor) {
		if( corridor == null || !this.reading.hasPitch() ) {
			return false;
		}
		double midi = this.reading.getMidi() + this.reading.getCents() / 100.0;
		return midi >= corridor[0] && midi <= corridor[1];
	}

	/**
	 * The corridor in which a pitch is a bend moving along its written path, and not a wrong note: the path of the current
	 * event while it is expected, and the path of the bent note just accepted while its observation window is open.
	 */
	private double[] activeCorridor(PracticeEvent event, long now) {
		if( event != null && event.getBendPath() != null ) {
			return corridorOf(event);
		}
		if( this.bendEvent != null && now < this.bendWindowEndNanos ) {
			return corridorOf(this.bendEvent);
		}
		return null;
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
				&& previous.getExpectedMidi() != event.getExpectedMidi()
				&& !onBentPathOf(previous, event);
	}

	/**
	 * The pitch of the expected note is on the written path of the bent note before it: the bend itself reaching it is not a new note,
	 * which needs its own attack (a hammer-on is a pitch change without attack, a bend is not).
	 */
	private static boolean onBentPathOf(PracticeEvent previous, PracticeEvent event) {
		if( previous.getBendPath() == null ) {
			return false;
		}
		double[] corridor = corridorOf(previous);
		return event.getExpectedMidi() >= corridor[0] && event.getExpectedMidi() <= corridor[1];
	}

	private void onNoteFrame(AnalysisFrame frame, PracticeEvent event, boolean usableLevel) {
		long now = frame.getTimestampNanos();
		this.feedback = Feedback.evaluate(frame, this.reading, event.getExpectedMidi(), event.getFret(), this.tolerance, !this.articulationArmed);
		// a bent note whose run began on its written pitch goes on along the written path: a bend that leaves the fretted pitch at once
		// (400 cents a second is 25 cents in 60 ms) would never hold the pitch for the stability window
		boolean alongPath = (usableLevel && event.getBendPath() != null && this.matchStartNanos >= 0 && onPath(corridorOf(event)));
		if( usableLevel && (alongPath || this.tolerance.matches(this.reading, event.getExpectedMidi(), event.getFret())) ) {
			if( this.matchStartNanos < 0 ) {
				this.matchStartNanos = now;
				if( requiresMovement(event) && this.techniqueJudge != null ) {
					// the bend starts here: its hops are kept from the first one of its run (not from before: the previous note may still ring)
					this.bendFrames.clear();
					for( double[] hop : this.recentFrames ) {
						if( hop[0] >= now ) {
							this.bendFrames.add(hop);
						}
					}
					this.bendCollecting = true;
				}
			}
			boolean stable;
			if( requiresMovement(event) ) {
				// the written bend, then its written release, are required: the note is accepted when the last of them is made and held
				if( requiresBend(event) && !this.bendPeakReached && onBendTarget(event) ) {
					this.bendPeakReached = true;
				}
				boolean riseMade = !requiresBend(event) || this.bendPeakReached;
				boolean inZone = (requiresRelease(event) ? riseMade && onReleaseEnd(event) : onBendTarget(event));
				if( inZone ) {
					if( this.bendTargetStartNanos < 0 ) {
						this.bendTargetStartNanos = now;
					}
				} else {
					this.bendTargetStartNanos = -1;
				}
				stable = this.bendTargetStartNanos >= 0 && now - this.bendTargetStartNanos >= requiredStabilityNanos(frame, event);
				if( !stable && (this.feedback.getKind() == Feedback.Kind.WAITING || this.feedback.getKind() == Feedback.Kind.INCORRECT) ) {
					// along the path the pitch is no longer the written note, and it is not an error: the bend, then the release, is what is expected
					this.feedback = (riseMade ? Feedback.WAITING_FOR_RELEASE : Feedback.WAITING_FOR_BEND);
				}
			} else {
				stable = now - this.matchStartNanos >= requiredStabilityNanos(frame, event);
			}
			if( stable && (this.articulationArmed || isPitchChange(event)) ) {
				accept(event, now);
				return;
			}
		} else {
			// only the match run: a wrong-note run continues through non-matching frames
			// a bend in progress is not ended by a dropout of the signal (a level too low to be judged, a pitch not read: no information),
			// only by a pitch that is read and is off the written path
			boolean dropout = (event.getBendPath() != null && this.matchStartNanos >= 0
					&& !(usableLevel && this.reading.hasPitch() && !onPath(corridorOf(event))));
			if( !dropout ) {
				this.matchStartNanos = -1;
				this.bendTargetStartNanos = -1;
				this.bendPeakReached = false;
				this.bendCollecting = false;
				this.bendFrames.clear();
			}
		}
		// a wrong note is reported only when it is a freshly played, reliable note held for the
		// stability window: a note still ringing, a glitch or noise shows no cross (prefer waiting)
		if( this.feedback.getKind() == Feedback.Kind.INCORRECT ) {
			// a pitch moving along a written bend path is the bend, not a wrong note
			if( onPath(activeCorridor(event, now)) ) {
				this.wrongMidi = -1;
				this.feedback = Feedback.WAITING;
				return;
			}
			// a note that rings on from the previous one is not a wrong note: with attack evidence, a wrong note must be a newly struck one
			if( this.polyphonic != null && frame.getAttackEvidence() != null && !newlyStruck(frame.getAttackEvidence(), this.feedback.getDetectedMidi()) ) {
				this.wrongMidi = -1;
				this.feedback = Feedback.WAITING;
				return;
			}
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
		this.pendingEvent = null;
		this.bendEvent = null;
		this.lastAcceptNanos = Long.MIN_VALUE;
		this.speedHitNanos = Long.MIN_VALUE;
		this.inputQuality.reset();
		this.pendingFrames.clear();
		this.techniqueEvent = null;
		this.techniqueResults = java.util.Collections.<TechniqueResult>emptyList();
		resetMatch();
		passAutomatically();
		this.passageResults.clear();
		this.retries = 0;
		this.decision = PassageStatus.Decision.NONE;
		beginPassage();
	}

	private void collectTechniqueFrame(AnalysisFrame frame) {
		if( this.techniqueJudge == null ) {
			return;
		}
		double f0 = frame.getF0Hz();
		double midi = (Double.isFinite(f0) && f0 > 0.0 && frame.getConfidence() >= 0.8 ? 69.0 + 12.0 * Math.log(f0 / 440.0) / Math.log(2.0) : Double.NaN);
		double[] hop = new double[] { frame.getTimestampNanos(), midi, frame.getRmsDbfs(), frame.isOnset() ? 1.0 : 0.0 };
		this.recentFrames.addLast(hop);
		if( this.recentFrames.size() > RECENT_FRAMES ) {
			this.recentFrames.removeFirst();
		}
		if( this.bendCollecting ) {
			this.bendFrames.add(hop);
			if( this.bendFrames.size() > BEND_FRAMES_MAX ) {
				this.bendFrames.remove(0);
			}
		}
		if( this.pendingEvent != null ) {
			this.pendingFrames.add(hop);
			if( frame.getTimestampNanos() - this.pendingStartNanos > this.pendingWindowNanos ) {
				finishTechniques();
			}
		}
	}

	/** The note of the pending event is over (or has lasted long enough): judge its techniques. */
	private void finishTechniques() {
		if( this.pendingEvent == null ) {
			return;
		}
		NoteSegment segment = null;
		int n = this.pendingFrames.size();
		if( n >= 2 ) {
			double[] midi = new double[n];
			double[] level = new double[n];
			boolean[] onset = new boolean[n];
			for( int i = 0 ; i < n ; i ++ ) {
				double[] hop = this.pendingFrames.get(i);
				midi[i] = hop[1];
				level[i] = hop[2];
				onset[i] = hop[3] > 0.0;
			}
			double hopSeconds = (this.pendingFrames.get(n - 1)[0] - this.pendingFrames.get(0)[0]) / (n - 1) / 1e9;
			if( hopSeconds > 0.0 ) {
				segment = new NoteSegment(midi, level, onset, hopSeconds);
			}
		}
		this.techniqueEvent = this.pendingEvent;
		this.techniqueResults = this.techniqueJudge.judge(this.pendingTargets, segment);
		this.pendingEvent = null;
		this.pendingTargets = null;
		this.pendingFrames.clear();
	}

	/** A newly accepted note: judges the previous one, then starts collecting this one's techniques. */
	private void beginTechniques(PracticeEvent event, long timestampNanos) {
		if( this.techniqueJudge == null ) {
			return;
		}
		finishTechniques();
		List<TechniqueTarget> targets = new ArrayList<TechniqueTarget>();
		boolean needsMeasure = false;
		for( TechniqueTarget target : this.sequence.targetsOf(event.getSequenceIndex()) ) {
			if( target.getTechnique().isJudged() ) {
				targets.add(target);
				needsMeasure |= !this.techniqueJudge.isNeverVerdict(target.getTechnique());
			}
		}
		if( targets.isEmpty() ) {
			// no technique on this note: the result of the note just judged (if any) stays shown
			return;
		}
		if( !needsMeasure ) {
			// nothing to measure: the answer is known now ("not verifiable") and shown with the acceptance
			this.techniqueEvent = event;
			this.techniqueResults = this.techniqueJudge.judge(targets, null);
			return;
		}
		this.techniqueEvent = null;
		this.techniqueResults = java.util.Collections.<TechniqueResult>emptyList();
		this.pendingEvent = event;
		this.pendingTargets = targets;
		this.pendingStartNanos = timestampNanos;
		this.pendingWindowNanos = (event.getBendPath() != null ? bendWindowNanos(event) : TECHNIQUE_WINDOW_NANOS);
		if( event.getBendPath() != null && !this.bendFrames.isEmpty() ) {
			// a bend: its segment starts when its run began on the written pitch
			this.pendingFrames.addAll(this.bendFrames);
			return;
		}
		// the note began with its attack: its segment starts there (or at the oldest hop kept)
		for( double[] hop : this.recentFrames ) {
			if( hop[0] >= Math.max(this.armTransientNanos, timestampNanos - TECHNIQUE_LEAD_NANOS) ) {
				this.pendingFrames.add(hop);
			}
		}
	}

	private void accept(PracticeEvent event, long timestampNanos) {
		beginTechniques(event, timestampNanos);
		if( event.getBendPath() != null ) {
			this.bendEvent = event;
			this.bendWindowEndNanos = timestampNanos + bendWindowNanos(event);
		} else {
			this.bendEvent = null;
		}
		if( PracticeTrace.enabled() ) {
			PracticeTrace.accepted(timestampNanos, event.getSequenceIndex());
		}
		this.neighbourHeardNanos = Long.MIN_VALUE;
		this.lastAcceptNanos = timestampNanos;
		this.speedHitNanos = Long.MIN_VALUE;
		this.outcomes.put(event.getSequenceIndex(), Outcome.ACCEPTED);
		this.lastAccepted = event;
		this.acceptedCount ++;
		this.feedback = Feedback.CORRECT;
		this.chordFeedback = null;
		this.armReferenceNanos = timestampNanos;
		this.articulationArmed = false;
		if( this.polyphonic != null ) {
			this.polyphonic.rearm(timestampNanos);
		}
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
		if( this.polyphonic != null ) {
			this.polyphonic.rearmAtNextFrame();
		}
	}

	/** Clears the per-event attempt state (single-note match, wrong-note run, chord run). */
	private void resetMatch() {
		this.matchStartNanos = -1;
		this.bendTargetStartNanos = -1;
		this.bendPeakReached = false;
		this.bendCollecting = false;
		this.bendFrames.clear();
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
				this.confidence, this.chordFeedback, this.verdict, passageStatus(), this.acceptedCount,
				this.techniqueEvent, this.techniqueResults, speedLimit(), this.state == State.RUNNING && this.inputQuality.isPoor(), this.inputQuality.getPlayedLevelDbfs());
	}

	/** True when the learner struck a note faster than recognition follows and it is still waiting: shown as a limit, never as an error. */
	private boolean speedLimit() {
		return this.state == State.RUNNING && this.speedHitNanos != Long.MIN_VALUE && this.lastFrameNanos - this.speedHitNanos >= SPEED_SHOW_NANOS;
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
