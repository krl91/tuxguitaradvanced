package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import app.tuxguitar.app.tools.custom.practice.model.PitchPath;
import app.tuxguitar.app.tools.custom.practice.model.PlayingTechnique;
import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueResult;
import app.tuxguitar.app.tools.custom.practice.model.TechniqueTarget;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Window;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.analysis.SpectrumFrame;
import app.tuxguitar.audio.engine.input.CaptureClock;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.PitchReading;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.audio.engine.technique.BendDetector;
import app.tuxguitar.audio.engine.technique.NoteSegment;

/**
 * The tempo-following practice mode (spec 002 T095, contracts/tempo-following.md). The score advances with the player's clock; every expected event has its
 * own window around its target time ({@link TimingTolerance}) and is judged on it: <b>nothing waits</b> for a wrong or missed note, and a mistake cannot move
 * the following events, because each one is judged against its own absolute window, never against the previous result.
 * <p>
 * The three verdicts stay apart: the <b>pitch</b> (right or wrong, a chord by the {@link ChordMatcher}), the <b>timing</b> (ON_TIME, EARLY, LATE, MISSED,
 * and EXTRA for an onset that matched nothing) and the <b>techniques</b> (judged after the event by the {@link TechniqueJudge}, which never changes the
 * other two: a bend that is not made does not block anything). The time of an onset is that of its <b>transient</b> (the frame that reports it, less
 * the latency of the detector, less the latency compensation of the route), never the moment the pitch was decided.
 * <p>
 * <b>No frame is judged before both clocks are ready</b> ({@link TimingContext#mayJudge()}), and an event whose window closes while nothing can be judged
 * (clocks not ready, paused) is NOT_JUDGED, neither matched nor missed. The wait-for-the-right-note mode is not touched: {@code PracticeSession} knows nothing of this class.
 * <p>
 * Single threaded: one thread feeds the frames and reads the snapshots.
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

	/** One expected event: the pitch verdict, the timing verdict and the technique results, kept apart. Immutable. */
	public static final class EventResult {
		private final int eventIndex;
		private final Status status;
		private final PitchResult pitch;
		private final TimingVerdict timing;
		private final long offsetNanos;
		private final int[] missing;
		private final List<TechniqueResult> techniques;
		private final int attempts;

		EventResult(int eventIndex, Status status, PitchResult pitch, TimingVerdict timing, long offsetNanos, int[] missing, List<TechniqueResult> techniques, int attempts) {
			this.eventIndex = eventIndex;
			this.status = status;
			this.pitch = pitch;
			this.timing = timing;
			this.offsetNanos = offsetNanos;
			this.missing = missing;
			this.techniques = techniques;
			this.attempts = attempts;
		}

		EventResult with(Status status, PitchResult pitch, TimingVerdict timing, long offsetNanos, int[] missing) {
			return new EventResult(this.eventIndex, status, pitch, timing, offsetNanos, missing, this.techniques, this.attempts);
		}

		EventResult withAttempts(int attempts) {
			return new EventResult(this.eventIndex, this.status, this.pitch, this.timing, this.offsetNanos, this.missing, this.techniques, attempts);
		}

		EventResult withTechniques(List<TechniqueResult> techniques) {
			return new EventResult(this.eventIndex, this.status, this.pitch, this.timing, this.offsetNanos, this.missing, Collections.unmodifiableList(new ArrayList<TechniqueResult>(techniques)), this.attempts);
		}

		public int eventIndex() {
			return this.eventIndex;
		}

		public Status status() {
			return this.status;
		}

		public PitchResult pitch() {
			return this.pitch;
		}

		/** ON_TIME, EARLY or LATE for the onset of the (right or wrong) attempt, MISSED for an event missed; null when nothing was judged. */
		public TimingVerdict timing() {
			return this.timing;
		}

		/** Signed offset of the compensated onset from the target time (negative: early). */
		public long offsetNanos() {
			return this.offsetNanos;
		}

		/** The pitches a wrong chord lacked. */
		public int[] missingPitches() {
			return this.missing.clone();
		}

		public List<TechniqueResult> techniques() {
			return this.techniques;
		}

		/** How many onsets with pitch evidence fell in the window of the event. */
		public int attempts() {
			return this.attempts;
		}
	}

	/** An onset with pitch evidence that matched no event. */
	public static final class Extra {
		private final long wallNanos;

		Extra(long wallNanos) {
			this.wallNanos = wallNanos;
		}

		/** The compensated time of its transient, on the wall clock. */
		public long wallNanos() {
			return this.wallNanos;
		}
	}

	public static final class Counts {
		private final int onTime;
		private final int early;
		private final int late;
		private final int wrongPitch;
		private final int missed;
		private final int extra;
		private final int notJudged;
		private final int autoPassed;

		Counts(int onTime, int early, int late, int wrongPitch, int missed, int extra, int notJudged, int autoPassed) {
			this.onTime = onTime;
			this.early = early;
			this.late = late;
			this.wrongPitch = wrongPitch;
			this.missed = missed;
			this.extra = extra;
			this.notJudged = notJudged;
			this.autoPassed = autoPassed;
		}

		public int onTime() { return this.onTime; }
		public int early() { return this.early; }
		public int late() { return this.late; }
		public int wrongPitch() { return this.wrongPitch; }
		public int missed() { return this.missed; }
		public int extra() { return this.extra; }
		public int notJudged() { return this.notJudged; }
		public int autoPassed() { return this.autoPassed; }
	}

	public static final class Snapshot {
		private final State state;
		private final List<EventResult> results;
		private final List<Extra> extras;
		private final Counts counts;
		private final TimingContext.RhythmPrecision precision;
		private final Set<TimingContext.PrecisionReason> precisionReasons;
		private final int openEvent;

		Snapshot(State state, List<EventResult> results, List<Extra> extras, Counts counts, TimingContext.RhythmPrecision precision,
				Set<TimingContext.PrecisionReason> precisionReasons, int openEvent) {
			this.state = state;
			this.results = results;
			this.extras = extras;
			this.counts = counts;
			this.precision = precision;
			this.precisionReasons = precisionReasons;
			this.openEvent = openEvent;
		}

		public State state() { return this.state; }
		public List<EventResult> results() { return this.results; }
		public List<Extra> extras() { return this.extras; }
		public Counts counts() { return this.counts; }
		public TimingContext.RhythmPrecision precision() { return this.precision; }
		public Set<TimingContext.PrecisionReason> precisionReasons() { return this.precisionReasons; }
		/** Index of the first event not resolved yet, or -1 when all are. */
		public int openEvent() { return this.openEvent; }
	}

	// ------------------------------------------------------------------------------------------------------------- internals

	/** The pitch evidence of one frame, kept for the onsets that began a little before it was reported. */
	private static final class Seen {
		final long wall;
		final PitchReading reading;
		final SpectrumFrame spectrum;
		final boolean usable;
		final double[] hop;

		Seen(long wall, PitchReading reading, SpectrumFrame spectrum, boolean usable, double[] hop) {
			this.wall = wall;
			this.reading = reading;
			this.spectrum = spectrum;
			this.usable = usable;
			this.hop = hop;
		}
	}

	/** An onset waiting for enough pitch evidence to be matched, called wrong or called extra. */
	private static final class PendingOnset {
		final long transient_;
		final long adjusted;
		/** The events whose window holds the onset (any status), in order. */
		final List<Integer> inWindow = new ArrayList<Integer>();
		/** For each open single-note candidate, since when its pitch has matched without a break. */
		final long[] matchSince;
		final int[] candidates;
		final int[] chordRun;
		final long[] lastMatch;
		long gapSince = -1L;
		int heldMidi = Integer.MIN_VALUE;
		long heldSince;
		boolean resolved;

		PendingOnset(long transient_, long adjusted, int[] candidates) {
			this.transient_ = transient_;
			this.adjusted = adjusted;
			this.candidates = candidates;
			this.matchSince = new long[candidates.length];
			this.lastMatch = new long[candidates.length];
			Arrays.fill(this.matchSince, -1L);
			this.chordRun = new int[candidates.length];
		}
	}

	/** The hops of a matched note, until its techniques can be judged. */
	private static final class Collector {
		final int eventIndex;
		final long transient_;
		final long endWall;
		final List<TechniqueTarget> targets;
		final List<double[]> hops = new ArrayList<double[]>();

		Collector(int eventIndex, long transient_, long endWall, List<TechniqueTarget> targets) {
			this.eventIndex = eventIndex;
			this.transient_ = transient_;
			this.endWall = endWall;
			this.targets = targets;
		}
	}

	/** What an event keeps while it is open: the best wrong attempt, to be told at the closing. */
	private static final class OpenEvent {
		boolean wrongSeen;
		TimingVerdict wrongTiming;
		long wrongOffset;
		int[] wrongMissing = new int[0];
		/** For a legato arrival: since when the pitch has matched without a break. */
		long legatoSince = -1L;
		int legatoMidi = Integer.MIN_VALUE;
	}

	private static final long EVIDENCE_HORIZON_NANOS = 400_000_000L;
	/** Without any pitch for this long the sound of an attack is over. */
	private static final long SILENCE_NANOS = 40_000_000L;
	/** A note that ended before the stability time is accepted when its pitch was held this long. */
	private static final long SHORT_NOTE_NANOS = 40_000_000L;
	private static final long SAME_SOUND_NANOS = 100_000_000L;
	private static final long TAIL_NANOS = 100_000_000L;
	private static final long HISTORY_NANOS = 1_200_000_000L;
	private static final long TECHNIQUE_WINDOW_NANOS = 1_500_000_000L;
	private static final long BEND_WINDOW_MIN_NANOS = 1_000_000_000L;
	private static final long BEND_WINDOW_MAX_NANOS = 2_500_000_000L;
	private static final double BEND_MARGIN_SEMITONES = 0.5;
	private static final BendDetector.Parameters BEND = BendDetector.Parameters.defaults();

	private final PracticeSequence sequence;
	private final List<PracticeEvent> events;
	private final FollowTimeline timeline;
	private final long firstTick;
	private final TempoClock playerClock;
	private final ToLongFunction<AnalysisFrame> frameDating;
	private final Supplier<TimingContext> contextSupplier;
	private final NoteInterpreter interpreter;
	private final TolerancePolicy tolerance;
	private final ChordMatcher chordMatcher;
	private final long stabilityNanos;
	private final long articulationLatencyNanos;
	private final long[] gapBefore;
	private final long[] gapAfter;
	private final boolean[] legatoArrival;
	/** For a matched event: the time of the sound it was matched on (an attack flagged at the same time, by another path, is that same sound). */
	private final long[] soundAt;

	private TechniqueJudge judge;
	private Supplier<PolyphonicAcceptance> polyphonicFactory;
	private PolyphonicAcceptance[] acceptances;
	private InputAssessment assessment = InputAssessment.UNCHECKED;
	private boolean trackAccepted;
	private State state = State.PREPARING;
	private State stateBeforePause = State.FOLLOWING;
	private boolean judging;
	/** Since when (compensated wall time) the frames are judged without a break: a window that began before was not seen whole. */
	private long judgingSince;
	private final EventResult[] results;
	private final OpenEvent[] open;
	private final List<Extra> extras = new ArrayList<Extra>();
	private final List<PendingOnset> pending = new ArrayList<PendingOnset>();
	private final List<Collector> collectors = new ArrayList<Collector>();
	private final Deque<Seen> history = new ArrayDeque<Seen>();
	private int scanFrom;
	private TimingContext lastContext;

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
		this.sequence = sequence;
		this.events = sequence.getEvents();
		this.timeline = timeline;
		this.firstTick = firstTick;
		this.playerClock = playerClock;
		this.frameDating = frameDating;
		this.contextSupplier = context;
		this.interpreter = interpreter;
		this.tolerance = tolerance;
		this.chordMatcher = chordMatcher;
		this.stabilityNanos = stabilityNanos;
		this.articulationLatencyNanos = articulationLatencyNanos;
		int n = this.events.size();
		this.gapBefore = new long[n];
		this.gapAfter = new long[n];
		this.legatoArrival = new boolean[n];
		this.soundAt = new long[n];
		Arrays.fill(this.soundAt, Long.MIN_VALUE);
		this.results = new EventResult[n];
		this.acceptances = new PolyphonicAcceptance[n];
		this.open = new OpenEvent[n];
		for (int i = 0; i < n; i++) {
			this.gapBefore[i] = timeline.gapBeforeNanos(i);
			this.gapAfter[i] = timeline.gapAfterNanos(i);
			PracticeEvent event = this.events.get(i);
			Status status = Status.PENDING;
			if (event.getKind() == PracticeEvent.Kind.AUTO_PASS) {
				status = Status.AUTO_PASSED;
			} else if (event.getKind() == PracticeEvent.Kind.MULTI_NOTE) {
				// several notes with a pitch-changing effect: nothing can be checked, the wait mode asks for Skip, this mode goes on
				status = Status.NOT_JUDGED;
			}
			this.results[i] = new EventResult(i, status, PitchResult.NONE, null, 0L, new int[0], Collections.<TechniqueResult>emptyList(), 0);
			this.open[i] = new OpenEvent();
			// the note a hammer-on, a pull-off or a slide arrives on has no attack of its own
			if (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE) {
				for (TechniqueTarget target : sequence.targetsOf(i)) {
					if (target.getTechnique() == PlayingTechnique.HAMMER_ON || target.getTechnique() == PlayingTechnique.PULL_OFF) {
						this.legatoArrival[i] = true;
					}
				}
				if (i > 0 && this.events.get(i - 1).getKind() == PracticeEvent.Kind.EXPECTED_NOTE
						&& this.events.get(i - 1).getTechniques().contains(PracticeEvent.Technique.SLIDE)) {
					this.legatoArrival[i] = true;
				}
			}
		}
		this.lastContext = this.contextSupplier.get();
	}

	public void setTechniqueJudge(TechniqueJudge judge) {
		this.judge = judge;
	}

	/**
	 * Hears a note or a chord from the attack evidence of the frames, as the wait mode does (spec 004), for what a single pitch cannot give: a
	 * note struck while the previous chord rings. One acceptance per open event; null goes back to the single pitch and the chord spectrum alone.
	 */
	public void setPolyphonicAcceptance(Supplier<PolyphonicAcceptance> factory) {
		this.polyphonicFactory = factory;
	}

	public void setAssessment(InputAssessment assessment) {
		this.assessment = assessment;
	}

	/** The learner accepted the track (as in the wait mode). */
	public void setTrackAccepted(boolean accepted) {
		this.trackAccepted = accepted;
	}

	/** PREPARING to COUNT_IN, when the track is accepted and the input usable; otherwise nothing happens. */
	public State start() {
		if (this.state == State.PREPARING) {
			InputAssessment.Status status = this.assessment.getStatus();
			boolean usable = (status == InputAssessment.Status.SUITABLE || status == InputAssessment.Status.SUBOPTIMAL);
			if (this.trackAccepted && usable && !this.sequence.isEmpty()) {
				this.state = State.COUNT_IN;
			}
		}
		return this.state;
	}

	public void pause(PracticeSession.PauseReason reason) {
		if (this.state == State.FOLLOWING || this.state == State.COUNT_IN) {
			this.stateBeforePause = this.state;
			this.state = State.PAUSED;
			this.judging = false;
			this.pending.clear();
			this.history.clear();
		}
	}

	public void resume(PracticeSession.PauseReason reason) {
		if (this.state == State.PAUSED) {
			this.state = this.stateBeforePause;
		}
	}

	public void stop() {
		if (this.state != State.STOPPED) {
			finalizeCollectors(Long.MAX_VALUE);
			this.state = State.STOPPED;
			this.pending.clear();
		}
	}

	public Snapshot snapshot() {
		return snapshot(this.contextSupplier.get());
	}

	private Snapshot snapshot(TimingContext context) {
		int onTime = 0, early = 0, late = 0, wrong = 0, missed = 0, notJudged = 0, auto = 0, openIndex = -1;
		for (int i = 0; i < this.results.length; i++) {
			EventResult r = this.results[i];
			switch (r.status()) {
				case MATCHED:
					if (r.timing() == TimingVerdict.ON_TIME) { onTime++; } else if (r.timing() == TimingVerdict.EARLY) { early++; } else if (r.timing() == TimingVerdict.LATE) { late++; }
					break;
				case WRONG_PITCH: wrong++; break;
				case MISSED: missed++; break;
				case NOT_JUDGED: notJudged++; break;
				case AUTO_PASSED: auto++; break;
				default:
					if (openIndex < 0) { openIndex = i; }
					break;
			}
		}
		return new Snapshot(this.state, Collections.unmodifiableList(Arrays.asList(this.results.clone())), Collections.unmodifiableList(new ArrayList<Extra>(this.extras)),
				new Counts(onTime, early, late, wrong, missed, this.extras.size(), notJudged, auto), context.precision(), context.precisionReasons(), openIndex);
	}

	// ------------------------------------------------------------------------------------------------------------- frames

	public Snapshot onFrame(AnalysisFrame frame) {
		TimingContext context = this.contextSupplier.get();
		this.lastContext = context;
		if (this.state == State.PREPARING || this.state == State.COMPLETE || this.state == State.STOPPED || this.state == State.PAUSED) {
			if (this.state == State.PAUSED) {
				this.judging = false;
			}
			return snapshot(context);
		}
		long wall = this.frameDating.applyAsLong(frame);
		// the gate: nothing at all is judged, dated or counted before both clocks are ready
		if (wall == CaptureClock.NOT_READY || !context.mayJudge()) {
			this.judging = false;
			this.pending.clear();
			this.history.clear();
			return snapshot(context);
		}
		if (!this.judging) {
			// judging begins (or begins again): what closed in the meantime was not judged
			this.judging = true;
			this.pending.clear();
			this.history.clear();
			this.judgingSince = context.compensation().adjust(wall);
			for (int i = 0; i < this.results.length; i++) {
				if (this.results[i].status() == Status.PENDING && window(i, context).outerTo() < this.judgingSince) {
					this.results[i] = this.results[i].with(Status.NOT_JUDGED, PitchResult.NONE, null, 0L, new int[0]);
				}
			}
		}
		if (this.state == State.COUNT_IN) {
			int first = firstOpen();
			if (first < 0) {
				this.state = State.COMPLETE;
				return snapshot(context);
			}
			if (wall < window(first, context).outerFrom()) {
				return snapshot(context);
			}
			this.state = State.FOLLOWING;
		}
		process(frame, wall, context);
		if (firstOpen() < 0 && this.pending.isEmpty() && wall > lastWindowEnd(context) + this.articulationLatencyNanos + this.stabilityNanos + TAIL_NANOS) {
			finalizeCollectors(Long.MAX_VALUE);
			this.state = State.COMPLETE;
		}
		return snapshot(context);
	}

	private void process(AnalysisFrame frame, long wall, TimingContext context) {
		boolean usable = frame.getLevelState() == AnalysisFrame.LevelState.SUITABLE || frame.getLevelState() == AnalysisFrame.LevelState.TOO_HIGH;
		PitchReading reading = this.interpreter.interpret(frame.getF0Hz(), frame.getConfidence(), this.assessment);
		double f0 = frame.getF0Hz();
		double midi = (Double.isFinite(f0) && f0 > 0.0 && frame.getConfidence() >= 0.8 ? 69.0 + 12.0 * Math.log(f0 / 440.0) / Math.log(2.0) : Double.NaN);
		double[] hop = new double[] { wall, midi, frame.getRmsDbfs(), frame.isOnset() ? 1.0 : 0.0 };
		Seen seen = new Seen(wall, reading, frame.getSpectrum(), usable, hop);
		this.history.addLast(seen);
		while (!this.history.isEmpty() && wall - this.history.peekFirst().wall > HISTORY_NANOS) {
			this.history.removeFirst();
		}
		for (Collector collector : this.collectors) {
			collector.hops.add(hop);
		}
		if (frame.isOnset()) {
			registerOnset(wall - this.articulationLatencyNanos, context);
		}
		for (PendingOnset onset : new ArrayList<PendingOnset>(this.pending)) {
			evaluate(onset, seen, context);
		}
		this.pending.removeIf(o -> o.resolved);
		polyphonicAcceptance(frame, wall, context);
		legatoArrivals(seen, context);
		closeWindows(wall, context);
		finalizeCollectors(wall);
		while (this.scanFrom < this.results.length && this.results[this.scanFrom].status() != Status.PENDING) {
			this.scanFrom++;
		}
	}

	// ------------------------------------------------------------------------------------------------------------- windows

	/**
	 * The end of the window of the last event. The session is complete only a little after it: an attack is reported some frames after its
	 * transient and needs the stability time, so a late attack of the last event (a match or an extra note) is still to come.
	 */
	private long lastWindowEnd(TimingContext context) {
		int last = this.results.length - 1;
		return last < 0 ? Long.MIN_VALUE / 2 : window(last, context).outerTo();
	}

	private int firstOpen() {
		for (int i = 0; i < this.results.length; i++) {
			if (this.results[i].status() == Status.PENDING) {
				return i;
			}
		}
		return -1;
	}

	private long targetWall(int i) {
		return this.playerClock.wallNanosAt(this.firstTick + this.timeline.targetTicks(i));
	}

	private Window window(int i, TimingContext context) {
		return context.tolerance().windowFor(targetWall(i), this.timeline.beatNanos(i), this.gapBefore[i], this.gapAfter[i]);
	}

	/** The first event worth looking at: the matched ones just before can still be in window for a second attack. */
	private int scanStart() {
		return Math.max(0, this.scanFrom - 8);
	}

	// ------------------------------------------------------------------------------------------------------------- onsets

	private void registerOnset(long transient_, TimingContext context) {
		long adjusted = context.compensation().adjust(transient_);
		List<Integer> inWindow = new ArrayList<Integer>();
		List<Integer> candidates = new ArrayList<Integer>();
		for (int i = scanStart(); i < this.results.length; i++) {
			PracticeEvent event = this.events.get(i);
			if (event.getKind() != PracticeEvent.Kind.EXPECTED_NOTE && event.getKind() != PracticeEvent.Kind.EXPECTED_CHORD) {
				continue;
			}
			Window w = window(i, context);
			if (w.outerFrom() > adjusted + 1_000_000_000L) {
				break;
			}
			if (w.classify(adjusted) != null) {
				inWindow.add(i);
				if (isOpenForOnset(i)) {
					candidates.add(i);
				}
			}
		}
		PendingOnset onset = new PendingOnset(transient_, adjusted, candidates.stream().mapToInt(Integer::intValue).toArray());
		onset.inWindow.addAll(inWindow);
		this.pending.add(onset);
		// the evidence began at the transient, some frames before the onset was reported
		for (Seen past : this.history) {
			if (past.wall >= transient_) {
				evaluateFrame(onset, past, context);
				if (onset.resolved) {
					break;
				}
			}
		}
	}

	private void evaluate(PendingOnset onset, Seen seen, TimingContext context) {
		if (onset.resolved) {
			return;
		}
		if (seen.wall > onset.transient_ + latestSeenFor(onset)) {
			// replayed frames were evaluated at registration: only the newer ones are new here
		}
		if (seen.wall <= lastEvaluated(onset)) {
			return;
		}
		evaluateFrame(onset, seen, context);
	}

	private long latestSeenFor(PendingOnset onset) {
		return 0L;
	}

	private final java.util.IdentityHashMap<PendingOnset, Long> evaluatedUpTo = new java.util.IdentityHashMap<PendingOnset, Long>();

	private long lastEvaluated(PendingOnset onset) {
		Long at = this.evaluatedUpTo.get(onset);
		return at == null ? Long.MIN_VALUE : at;
	}

	private void evaluateFrame(PendingOnset onset, Seen seen, TimingContext context) {
		this.evaluatedUpTo.put(onset, seen.wall);
		long age = seen.wall - onset.transient_;
		if (hasPendingChord(onset)) {
			// a chord has no single pitch: its evidence is the spectrum
			onset.gapSince = -1L;
		} else if (!seen.reading.hasPitch()) {
			if (onset.gapSince < 0) {
				onset.gapSince = seen.wall;
			}
			if (seen.wall - onset.gapSince >= SILENCE_NANOS) {
				// the sound of this attack is over: a short note (a grace note) that held its pitch long enough is a note
				for (int c = 0; c < onset.candidates.length; c++) {
					int index = onset.candidates[c];
					if (onset.matchSince[c] >= 0 && isOpenForOnset(index) && this.events.get(index).getKind() == PracticeEvent.Kind.EXPECTED_NOTE
							&& onset.lastMatch[c] - onset.matchSince[c] >= SHORT_NOTE_NANOS) {
						match(index, onset, context);
						return;
					}
				}
				resolve(onset);
				return;
			}
		} else {
			onset.gapSince = -1L;
		}
		// single-note and chord candidates
		for (int c = 0; c < onset.candidates.length; c++) {
			int index = onset.candidates[c];
			if (!isOpenForOnset(index)) {
				continue;
			}
			PracticeEvent event = this.events.get(index);
			if (event.getKind() == PracticeEvent.Kind.EXPECTED_CHORD) {
				if (seen.spectrum != null && seen.usable) {
					ChordMatcher.Result r = this.chordMatcher.check(seen.spectrum, event.getRequiredPitches(), event.getToleratedPitches());
					onset.chordRun[c] = (r.isComplete() ? onset.chordRun[c] + 1 : 0);
					if (onset.chordRun[c] >= this.chordMatcher.getParameters().stableHops) {
						match(index, onset, context);
						return;
					}
					if (age > this.chordMatcher.getParameters().windowNanos) {
						wrongAttempt(index, onset, context, r.getMissing());
						resolve(onset);
						return;
					}
				} else {
					onset.chordRun[c] = 0;
				}
			} else {
				boolean matches = seen.usable && matchesPitch(event, seen.reading, onset.matchSince[c] >= 0);
				if (matches) {
					if (onset.matchSince[c] < 0) {
						onset.matchSince[c] = seen.wall;
					}
					onset.lastMatch[c] = seen.wall;
					if (seen.wall - onset.matchSince[c] >= this.stabilityNanos) {
						match(index, onset, context);
						return;
					}
				} else {
					if (onset.matchSince[c] >= 0 && onset.lastMatch[c] - onset.matchSince[c] >= SHORT_NOTE_NANOS) {
						// the pitch changed after being held long enough: that was a short note (a grace note), the next sound is another one
						match(index, onset, context);
						return;
					}
					onset.matchSince[c] = -1L;
				}
			}
		}
		// a stable pitch that matches none of the open candidates: a wrong note, or an extra one
		if (seen.usable && seen.reading.hasPitch() && seen.reading.isReliable()) {
			if (seen.reading.getMidi() != onset.heldMidi) {
				onset.heldMidi = seen.reading.getMidi();
				onset.heldSince = seen.wall;
			}
			boolean anyMatching = false;
			for (int c = 0; c < onset.candidates.length; c++) {
				anyMatching |= (onset.matchSince[c] >= 0 && isOpenForOnset(onset.candidates[c]));
			}
			boolean chordPending = false;
			for (int index : onset.candidates) {
				chordPending |= (isOpenForOnset(index) && this.events.get(index).getKind() == PracticeEvent.Kind.EXPECTED_CHORD);
			}
			if (!anyMatching && !chordPending && seen.wall - onset.heldSince >= this.stabilityNanos) {
				resolveWrongOrExtra(onset, context);
				return;
			}
		} else {
			onset.heldMidi = Integer.MIN_VALUE;
		}
		if (age >= EVIDENCE_HORIZON_NANOS && !hasPendingChord(onset)) {
			// no pitch evidence: a click, noise: neither a note nor an extra note
			resolve(onset);
		}
	}

	/**
	 * An attack is reported {@code articulationLatency} after its transient, while the window of an event closes at its own end: a note played at the
	 * very end of a window is reported after the window closed. The MISSED of an event stays open to such an attack (and only to it: the pitch alone
	 * decides nothing), which turns it into MATCHED or WRONG_PITCH.
	 */
	private boolean isOpenForOnset(int index) {
		Status status = this.results[index].status();
		return status == Status.PENDING || (status == Status.MISSED && !this.open[index].wrongSeen);
	}

	private boolean hasPendingChord(PendingOnset onset) {
		for (int index : onset.candidates) {
			if (isOpenForOnset(index) && this.events.get(index).getKind() == PracticeEvent.Kind.EXPECTED_CHORD) {
				return true;
			}
		}
		return false;
	}

	private void resolve(PendingOnset onset) {
		onset.resolved = true;
		this.evaluatedUpTo.remove(onset);
	}

	private void resolveWrongOrExtra(PendingOnset onset, TimingContext context) {
		int nearest = -1;
		long best = Long.MAX_VALUE;
		for (int index : onset.candidates) {
			if (isOpenForOnset(index)) {
				long distance = Math.abs(window(index, context).offsetNanos(onset.adjusted));
				if (distance < best) {
					best = distance;
					nearest = index;
				}
			}
		}
		if (nearest >= 0) {
			wrongAttempt(nearest, onset, context, new int[0]);
		} else {
			// no open event holds it: an event already matched, or none at all
			for (int index : onset.inWindow) {
				this.results[index] = this.results[index].withAttempts(this.results[index].attempts() + 1);
			}
			for (int index : onset.inWindow) {
				if (this.soundAt[index] != Long.MIN_VALUE && Math.abs(this.soundAt[index] - onset.transient_) <= SAME_SOUND_NANOS) {
					// the attack of a sound an event was already matched on, by the attack evidence or from the pitch of a step
					resolve(onset);
					return;
				}
			}
			TimingOutcome outcome = context.judgeUnmatchedOnset(onset.transient_);
			if (outcome.verdict() == TimingVerdict.EXTRA) {
				this.extras.add(new Extra(onset.adjusted));
			}
		}
		resolve(onset);
	}

	private void wrongAttempt(int index, PendingOnset onset, TimingContext context, int[] missing) {
		TimingOutcome outcome = context.judgeOnset(window(index, context), onset.transient_);
		OpenEvent open = this.open[index];
		boolean closed = this.results[index].status() == Status.MISSED;
		if (!open.wrongSeen) {
			open.wrongSeen = true;
			open.wrongTiming = outcome.verdict();
			open.wrongOffset = outcome.offsetNanos();
			open.wrongMissing = missing;
		}
		this.results[index] = this.results[index].withAttempts(this.results[index].attempts() + 1);
		if (closed) {
			this.results[index] = this.results[index].with(Status.WRONG_PITCH, PitchResult.WRONG, open.wrongTiming, open.wrongOffset, open.wrongMissing);
		}
	}

	private void match(int index, PendingOnset onset, TimingContext context) {
		Window w = window(index, context);
		TimingOutcome outcome = context.judgeOnset(w, onset.transient_);
		for (int other : onset.inWindow) {
			if (other != index) {
				this.results[other] = this.results[other].withAttempts(this.results[other].attempts() + 1);
			}
		}
		finishMatch(index, outcome, onset.transient_);
		resolve(onset);
	}

	private void finishMatch(int index, TimingOutcome outcome, long transient_) {
		this.soundAt[index] = transient_;
		// the notes before this sound end here, whether this one has techniques to judge or not
		cutCollectors(transient_);
		EventResult previous = this.results[index];
		this.results[index] = previous.with(Status.MATCHED, PitchResult.CORRECT, outcome.verdict(), outcome.offsetNanos(), new int[0]).withAttempts(previous.attempts() + 1);
		startTechniques(index, transient_);
	}

	// ------------------------------------------------------------------------------------------------------------- attack evidence

	/** How long before its window an event starts watching the attack evidence: the quiet level before the attack is needed to see it grow. */
	private static final long EVIDENCE_LEAD_NANOS = 400_000_000L;

	private void polyphonicAcceptance(AnalysisFrame frame, long wall, TimingContext context) {
		if (this.polyphonicFactory == null || frame.getAttackEvidence() == null) {
			return;
		}
		boolean clipping = frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING;
		for (int i = scanStart(); i < this.results.length; i++) {
			PracticeEvent event = this.events.get(i);
			if (this.results[i].status() != Status.PENDING
					|| (event.getKind() != PracticeEvent.Kind.EXPECTED_NOTE && event.getKind() != PracticeEvent.Kind.EXPECTED_CHORD)) {
				continue;
			}
			Window w = window(i, context);
			if (w.outerFrom() - EVIDENCE_LEAD_NANOS > context.compensation().adjust(wall)) {
				break;
			}
			// a written movement (a bend, a release) cannot be told from its path by the spectrum: it is heard from its pitch
			if (event.getBendPath() != null || this.legatoArrival[i]) {
				continue;
			}
			PolyphonicAcceptance acceptance = this.acceptances[i];
			if (acceptance == null) {
				acceptance = this.polyphonicFactory.get();
				this.acceptances[i] = acceptance;
			}
			int[] pitches = (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ? new int[] { event.getExpectedMidi() } : event.getRequiredPitches());
			int[] frets = (event.getKind() == PracticeEvent.Kind.EXPECTED_NOTE ? new int[] { event.getFret() } : null);
			if (acceptance.offer(clipping ? null : frame.getAttackEvidence(), frame.getTimestampNanos(), pitches, frets, this.tolerance)) {
				long onsetWall = wall - (frame.getTimestampNanos() - acceptance.getOnsetNanos());
				TimingOutcome outcome = context.judgeOnset(w, onsetWall);
				if (outcome.verdict() != null) {
					finishMatch(i, outcome, onsetWall);
					this.acceptances[i] = null;
				} else {
					// an attack outside the window of this event: it is not this event's
					acceptance.rearm(frame.getTimestampNanos());
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------- legato arrivals

	/** The arrival note of a hammer-on, a pull-off or a slide has no attack: it is heard from its pitch, timed from the step. */
	private void legatoArrivals(Seen seen, TimingContext context) {
		for (int i = scanStart(); i < this.results.length; i++) {
			if (!this.legatoArrival[i] || this.results[i].status() != Status.PENDING) {
				continue;
			}
			if (i == 0 || this.results[i - 1].status() != Status.MATCHED) {
				continue;
			}
			Window w = window(i, context);
			if (w.outerFrom() > seen.wall + 1_000_000_000L) {
				break;
			}
			PracticeEvent event = this.events.get(i);
			OpenEvent open = this.open[i];
			if (seen.usable && matchesPitch(event, seen.reading, false)) {
				if (open.legatoSince < 0) {
					open.legatoSince = seen.wall;
				}
				if (seen.wall - open.legatoSince >= this.stabilityNanos) {
					long step = open.legatoSince;
					TimingOutcome outcome = context.judgeOnset(w, step + context.compensation().offsetNanos());
					if (outcome.verdict() != null) {
						finishMatch(i, outcome, step);
					}
					open.legatoSince = -1L;
				}
			} else {
				open.legatoSince = -1L;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------- pitch matching

	private boolean matchesPitch(PracticeEvent event, PitchReading reading, boolean runStarted) {
		if (!reading.hasPitch() || !reading.isReliable()) {
			return false;
		}
		PitchPath path = event.getBendPath();
		if (path != null) {
			double base = bendBase(event);
			double midi = reading.getMidi() + reading.getCents() / 100.0;
			double start = path.getPoints().get(0).getSemitones();
			if (runStarted) {
				// the run began on the written start: it goes on along the written path, a bend may leave its pitch at once
				double low = Double.POSITIVE_INFINITY;
				double high = Double.NEGATIVE_INFINITY;
				for (PitchPath.Point point : path.getPoints()) {
					low = Math.min(low, point.getSemitones());
					high = Math.max(high, point.getSemitones());
				}
				if (midi >= base + low - BEND_MARGIN_SEMITONES && midi <= base + high + BEND_MARGIN_SEMITONES) {
					return true;
				}
			}
			if (start * 100.0 >= BEND.minWrittenMoveCents) {
				double tol = Math.max(BEND.onToleranceCents, BEND.onToleranceShare * start * 100.0) / 100.0;
				if (midi >= base + start - tol && midi <= base + start + BEND_MARGIN_SEMITONES) {
					return true;
				}
			}
		}
		return this.tolerance.matches(reading, event.getExpectedMidi(), event.getFret());
	}

	private static double bendBase(PracticeEvent event) {
		for (TechniqueTarget target : event.getTechniqueTargets()) {
			if (target.getBaseMidi() != TechniqueTarget.NONE) {
				return target.getBaseMidi();
			}
		}
		return event.getExpectedMidi();
	}

	// ------------------------------------------------------------------------------------------------------------- closing

	private void closeWindows(long wall, TimingContext context) {
		for (int i = scanStart(); i < this.results.length; i++) {
			if (this.results[i].status() != Status.PENDING) {
				continue;
			}
			Window w = window(i, context);
			if (w.outerFrom() > wall + 1_000_000_000L) {
				break;
			}
			TimingOutcome closure = context.judgeClosure(w, wall);
			if (closure.verdict() != TimingVerdict.MISSED || waitingOnAnOnset(i)) {
				continue;
			}
			OpenEvent open = this.open[i];
			if (!open.wrongSeen && w.outerFrom() < this.judgingSince) {
				// the window began while nothing was judged: an attack of its beginning may have been missed, so it is not a miss
				this.results[i] = this.results[i].with(Status.NOT_JUDGED, PitchResult.NONE, null, 0L, new int[0]);
			} else if (open.wrongSeen) {
				this.results[i] = this.results[i].with(Status.WRONG_PITCH, PitchResult.WRONG, open.wrongTiming, open.wrongOffset, open.wrongMissing);
			} else {
				this.results[i] = this.results[i].with(Status.MISSED, PitchResult.NONE, TimingVerdict.MISSED, 0L, new int[0]);
			}
		}
	}

	/** An onset that fell in the window of the event and is not resolved yet could still match it. */
	private boolean waitingOnAnOnset(int index) {
		for (PendingOnset onset : this.pending) {
			for (int candidate : onset.candidates) {
				if (candidate == index && !onset.resolved) {
					return true;
				}
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------------------- techniques

	private void startTechniques(int index, long transient_) {
		if (this.judge == null) {
			return;
		}
		List<TechniqueTarget> targets = new ArrayList<TechniqueTarget>();
		boolean needsMeasure = false;
		for (TechniqueTarget target : this.sequence.targetsOf(index)) {
			if (target.getTechnique().isJudged()) {
				targets.add(target);
				needsMeasure |= !this.judge.isNeverVerdict(target.getTechnique());
			}
		}
		if (targets.isEmpty()) {
			return;
		}
		if (!needsMeasure) {
			this.results[index] = this.results[index].withTechniques(this.judge.judge(targets, null));
			return;
		}
		PracticeEvent event = this.events.get(index);
		long window = TECHNIQUE_WINDOW_NANOS;
		if (event.getBendPath() != null) {
			window = Math.max(BEND_WINDOW_MIN_NANOS, Math.min(BEND_WINDOW_MAX_NANOS, (long) (event.getBendWrittenSeconds() * 1e9)));
		}
		Collector collector = new Collector(index, transient_, transient_ + window, targets);
		for (Seen past : this.history) {
			if (past.wall >= transient_) {
				collector.hops.add(past.hop);
			}
		}
		this.collectors.add(collector);
	}

	/** Judges the techniques of the collectors whose observation window has ended, on the hops of that window only. */
	private void finalizeCollectors(long wall) {
		for (Collector collector : new ArrayList<Collector>(this.collectors)) {
			if (wall == Long.MAX_VALUE || wall >= collector.endWall) {
				collector.hops.removeIf(h -> h[0] > collector.endWall);
				this.collectors.remove(collector);
				judgeCollector(collector);
			}
		}
	}

	/**
	 * A new sound began at the wall time: the techniques of the notes before it are judged on what they sounded until then. What comes after
	 * (the next note, whatever its pitch) is not part of them: a bend that was not made is not made by the note that follows it.
	 */
	private void cutCollectors(long soundWall) {
		for (Collector collector : new ArrayList<Collector>(this.collectors)) {
			if (collector.transient_ < soundWall) {
				collector.hops.removeIf(h -> h[0] >= soundWall);
				this.collectors.remove(collector);
				judgeCollector(collector);
			}
		}
	}

	private void judgeCollector(Collector collector) {
		int n = collector.hops.size();
		NoteSegment segment = null;
		if (n >= 2) {
			double[] midi = new double[n];
			double[] level = new double[n];
			boolean[] onset = new boolean[n];
			for (int i = 0; i < n; i++) {
				double[] hop = collector.hops.get(i);
				midi[i] = hop[1];
				level[i] = hop[2];
				onset[i] = hop[3] > 0.0;
			}
			double hopSeconds = (collector.hops.get(n - 1)[0] - collector.hops.get(0)[0]) / (n - 1) / 1e9;
			if (hopSeconds > 0.0) {
				segment = new NoteSegment(midi, level, onset, hopSeconds);
			}
		}
		this.results[collector.eventIndex] = this.results[collector.eventIndex].withTechniques(this.judge.judge(collector.targets, segment));
	}

	/** The spectrum frames of an expected chord (used by the tests of the contract to know the evidence needs one). */
	static EnumSet<PracticeEvent.Kind> judgedKinds() {
		return EnumSet.of(PracticeEvent.Kind.EXPECTED_NOTE, PracticeEvent.Kind.EXPECTED_CHORD);
	}
}
