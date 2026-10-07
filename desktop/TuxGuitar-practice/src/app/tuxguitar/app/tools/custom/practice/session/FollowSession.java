package app.tuxguitar.app.tools.custom.practice.session;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;

/**
 * The tempo-following practice mode (spec 002 T095, contracts/tempo-following.md): the score advances with the player's clock and every expected event
 * is judged on its own window. Skeleton: the contract is in the tests (T089, T090), the behaviour comes with T095.
 */
public final class FollowSession {

	public enum State {
		PREPARING,
		COUNT_IN,
		FOLLOWING,
		PAUSED,
		COMPLETE,
		STOPPED
	}

	/** What became of an expected event. */
	public enum Status {
		/** Not resolved yet: its window is open or not reached. */
		PENDING,
		/** Heard with the right pitch inside its window: see {@link EventResult#timing()}. */
		MATCHED,
		/** Heard inside its window with another pitch, and never with the right one. */
		WRONG_PITCH,
		/** Its window closed and nothing matched. */
		MISSED,
		/** Its window closed while nothing could be judged (clocks not ready, paused): neither a success nor a miss. */
		NOT_JUDGED,
		/** An event that is not played to be judged (a dead note, a harmonic...): the timeline goes on. */
		AUTO_PASSED
	}

	/** The pitch verdict of an event, apart from its timing and its techniques. */
	public enum PitchResult {
		NONE,
		CORRECT,
		WRONG
	}

	/** One expected event: the pitch verdict, the timing verdict and the technique results, kept apart. */
	public static final class EventResult {
		EventResult() {
		}

		public int eventIndex() {
			throw new UnsupportedOperationException();
		}

		public Status status() {
			throw new UnsupportedOperationException();
		}

		public PitchResult pitch() {
			throw new UnsupportedOperationException();
		}

		/** ON_TIME, EARLY or LATE for the onset of the (right or wrong) attempt, MISSED for an event missed; null when nothing was judged. */
		public TimingVerdict timing() {
			throw new UnsupportedOperationException();
		}

		/** Signed offset of the compensated onset from the target time (negative: early). */
		public long offsetNanos() {
			throw new UnsupportedOperationException();
		}

		/** The pitches a wrong chord lacked. */
		public int[] missingPitches() {
			throw new UnsupportedOperationException();
		}

		public List<TechniqueResult> techniques() {
			throw new UnsupportedOperationException();
		}

		/** How many onsets with pitch evidence fell in the window of the event. */
		public int attempts() {
			throw new UnsupportedOperationException();
		}
	}

	/** An onset with pitch evidence that matched no event. */
	public static final class Extra {
		Extra() {
		}

		/** The compensated time of its transient, on the wall clock. */
		public long wallNanos() {
			throw new UnsupportedOperationException();
		}
	}

	public static final class Counts {
		Counts() {
		}

		public int onTime() { throw new UnsupportedOperationException(); }
		public int early() { throw new UnsupportedOperationException(); }
		public int late() { throw new UnsupportedOperationException(); }
		public int wrongPitch() { throw new UnsupportedOperationException(); }
		public int missed() { throw new UnsupportedOperationException(); }
		public int extra() { throw new UnsupportedOperationException(); }
		public int notJudged() { throw new UnsupportedOperationException(); }
		public int autoPassed() { throw new UnsupportedOperationException(); }
	}

	public static final class Snapshot {
		Snapshot() {
		}

		public State state() { throw new UnsupportedOperationException(); }
		public List<EventResult> results() { throw new UnsupportedOperationException(); }
		public List<Extra> extras() { throw new UnsupportedOperationException(); }
		public Counts counts() { throw new UnsupportedOperationException(); }
		public TimingContext.RhythmPrecision precision() { throw new UnsupportedOperationException(); }
		public Set<TimingContext.PrecisionReason> precisionReasons() { throw new UnsupportedOperationException(); }
		/** Index of the first event not resolved yet, or -1 when all are. */
		public int openEvent() { throw new UnsupportedOperationException(); }
	}

	/**
	 * @param sequence the events, in playing order
	 * @param timeline their target times for the chosen percentage
	 * @param firstTick the absolute tick of the player at the start of the timeline
	 * @param playerClock the player's clock
	 * @param frameDating the wall time of a frame (the capture clock), {@code CaptureClock.NOT_READY} when it cannot be dated
	 * @param context the timing context now: tolerance, latency compensation, uncertainty and whether both clocks are ready
	 * @param articulationLatencyNanos how long after the transient the onset of a frame is reported
	 */
	public FollowSession(PracticeSequence sequence, FollowTimeline timeline, long firstTick, TempoClock playerClock,
			ToLongFunction<AnalysisFrame> frameDating, Supplier<TimingContext> context, NoteInterpreter interpreter,
			TolerancePolicy tolerance, ChordMatcher chordMatcher, long stabilityNanos, long articulationLatencyNanos) {
		throw new UnsupportedOperationException();
	}

	public void setTechniqueJudge(TechniqueJudge judge) {
		throw new UnsupportedOperationException();
	}

	public void setAssessment(InputAssessment assessment) {
		throw new UnsupportedOperationException();
	}

	/** The learner accepted the track (as in the wait mode). */
	public void setTrackAccepted(boolean accepted) {
		throw new UnsupportedOperationException();
	}

	/** PREPARING to COUNT_IN, when the track is accepted and the input usable; otherwise nothing happens. */
	public State start() {
		throw new UnsupportedOperationException();
	}

	public void pause(PracticeSession.PauseReason reason) {
		throw new UnsupportedOperationException();
	}

	public void resume(PracticeSession.PauseReason reason) {
		throw new UnsupportedOperationException();
	}

	public void stop() {
		throw new UnsupportedOperationException();
	}

	public Snapshot onFrame(AnalysisFrame frame) {
		throw new UnsupportedOperationException();
	}

	public Snapshot snapshot() {
		throw new UnsupportedOperationException();
	}
}
