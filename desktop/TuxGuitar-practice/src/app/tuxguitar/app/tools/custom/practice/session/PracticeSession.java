package app.tuxguitar.app.tools.custom.practice.session;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.PracticeTrackSelection;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;

/**
 * Wait-for-correct-note practice state machine (data-model.md "State machine").
 * Single-threaded: every method runs on the session thread ({@link SessionLoop}).
 *
 * <p>An expected note is accepted only after readings that are reliable, on the expected note
 * (octave included) and within the practice tolerance have continued without interruption for
 * the stability window; a same-pitch note additionally needs an articulation whose transient
 * is later than the previous acceptance (FR-022). Silence, uncertain and incorrect readings
 * never advance (FR-007). Thresholds are parameters, not product constants.</p>
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
	private final long articulationLatencyNanos;
	private final Map<Integer, Outcome> outcomes = new HashMap<Integer, Outcome>();
	private final Set<PauseReason> pauseReasons = EnumSet.noneOf(PauseReason.class);

	private State state = State.PREPARING;
	private InputAssessment assessment = InputAssessment.UNCHECKED;
	private PitchReading reading = PitchReading.noReading();
	private int currentIndex;
	private long matchStartNanos = -1;
	private long lastAcceptanceNanos = Long.MIN_VALUE;
	private boolean articulationArmed;

	/**
	 * @param stabilityNanos how long matching readings must continue before acceptance
	 * @param articulationLatencyNanos how long after its transient an onset is reported by the
	 *        analysis; onsets whose transient precedes the previous acceptance are ignored
	 */
	public PracticeSession(PracticeTrackSelection selection, PracticeSequence sequence, NoteInterpreter interpreter,
			TolerancePolicy tolerance, long stabilityNanos, long articulationLatencyNanos) {
		this.selection = selection;
		this.sequence = sequence;
		this.interpreter = interpreter;
		this.tolerance = tolerance;
		this.stabilityNanos = stabilityNanos;
		this.articulationLatencyNanos = articulationLatencyNanos;
		this.currentIndex = sequence.getStartIndex();
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
			default:
				// Skip and unsupported events belong to User Story 5.
				return null;
		}
		return snapshot();
	}

	@Override
	public PracticeSnapshot onFrame(AnalysisFrame frame) {
		this.reading = this.interpreter.interpret(frame.getF0Hz(), frame.getConfidence(), this.assessment);
		if( this.state != State.RUNNING ) {
			return snapshot();
		}
		if( frame.isOnset() && frame.getTimestampNanos() - this.articulationLatencyNanos > this.lastAcceptanceNanos ) {
			this.articulationArmed = true;
		}
		PracticeEvent event = currentEvent();
		if( event != null && event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ) {
			if( this.tolerance.matches(this.reading, event.getExpectedMidi()) ) {
				if( this.matchStartNanos < 0 ) {
					this.matchStartNanos = frame.getTimestampNanos();
				}
				boolean stable = frame.getTimestampNanos() - this.matchStartNanos >= this.stabilityNanos;
				if( stable && (!event.isSameAsPrevious() || this.articulationArmed) ) {
					accept(event, frame.getTimestampNanos());
				}
			} else {
				resetMatch();
			}
		}
		return snapshot();
	}

	/** Pauses running practice for a cause (FR-043, FR-044, FR-058, FR-065). */
	public PracticeSnapshot pause(PauseReason reason) {
		if( this.state == State.RUNNING || this.state == State.PAUSED || this.state == State.READY_TO_RESUME ) {
			this.pauseReasons.add(reason);
			this.state = State.PAUSED;
			resetMatch();
		}
		return snapshot();
	}

	/** A pause cause is resolved; practice waits for an explicit Resume once none remain. */
	public PracticeSnapshot resolve(PauseReason reason) {
		this.pauseReasons.remove(reason);
		if( this.state == State.PAUSED && this.pauseReasons.isEmpty() ) {
			this.state = State.READY_TO_RESUME;
		}
		return snapshot();
	}

	public State getState() {
		return this.state;
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
		this.lastAcceptanceNanos = Long.MIN_VALUE;
		this.articulationArmed = false;
		resetMatch();
	}

	private void accept(PracticeEvent event, long timestampNanos) {
		this.outcomes.put(event.getSequenceIndex(), Outcome.ACCEPTED);
		this.lastAcceptanceNanos = timestampNanos;
		this.articulationArmed = false;
		resetMatch();
		this.currentIndex ++;
		if( this.currentIndex >= this.sequence.getEvents().size() ) {
			this.state = State.COMPLETE;
		}
	}

	private void resetMatch() {
		this.matchStartNanos = -1;
	}

	private PracticeEvent currentEvent() {
		if( this.state == State.COMPLETE || this.currentIndex >= this.sequence.getEvents().size() ) {
			return null;
		}
		return this.sequence.getEvents().get(this.currentIndex);
	}

	private PracticeSnapshot snapshot() {
		return new PracticeSnapshot(this.state, this.currentIndex, this.sequence.getEvents().size(), currentEvent(),
				this.reading, this.pauseReasons);
	}
}
