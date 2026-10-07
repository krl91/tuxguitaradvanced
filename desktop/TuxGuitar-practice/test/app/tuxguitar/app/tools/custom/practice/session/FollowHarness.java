package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Preset;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;

/**
 * Drives a {@link FollowSession} with fake clocks and scripted frames (spec 002 T089, T090): no sleep, no device, no real time. Everything is on a grid
 * of 5 ms so that the times of the tests are exact. The wall clock of the stream starts at {@link #T0}; the timeline starts {@link #LEAD} later (the
 * count-in); a note is a transient time, a duration and a pitch (or a pitch that moves).
 */
final class FollowHarness {

	static final long MS = 1_000_000L;
	static final long T0 = 10_000_000_000L;
	static final long LEAD = 2_000 * MS;
	static final long HOP = 5 * MS;
	/** The onset flag of a frame comes 13 hops after the transient, as the detector reports it. */
	static final long LATENCY = 13 * HOP;
	static final long FIRST_TICK = 960;
	static final long STABILITY = 100 * MS;

	/** A scripted note: heard from {@code start} for {@code duration}, its pitch a function of the seconds since the start. */
	static final class Note {
		final long start;
		final long duration;
		final DoubleUnaryOperator midiAt;
		boolean flagged;

		Note(long start, long duration, DoubleUnaryOperator midiAt) {
			this.start = start;
			this.duration = duration;
			this.midiAt = midiAt;
		}
	}

	/** The player's clock: a position in ticks mapped to the wall clock by the timeline itself. */
	static final class FakeClock implements TempoClock {
		final FollowTimeline timeline;
		boolean ready = true;

		FakeClock(FollowTimeline timeline) {
			this.timeline = timeline;
		}

		@Override
		public long nowNanos() {
			return 0;
		}

		@Override
		public double tickAt(long wallNanos) {
			throw new UnsupportedOperationException("the follow session asks for the wall time of a tick");
		}

		@Override
		public long wallNanosAt(double tick) {
			return T0 + LEAD + this.timeline.nanosAtTick(Math.round(tick) - FIRST_TICK);
		}

		@Override
		public boolean isReady() {
			return this.ready;
		}

		@Override
		public boolean isStalled(long wallNanos) {
			return false;
		}
	}

	final SongFixture fixture;
	final PracticeSequence sequence;
	final FollowTimeline timeline;
	final FakeClock clock;
	final FollowSession session;
	final List<Note> notes = new ArrayList<Note>();
	final Set<Note> flagged = new HashSet<Note>();

	boolean captureReady = true;
	boolean playerReady = true;
	double spreadMillis = 5.0;
	LatencyCompensation compensation = LatencyCompensation.calibrated(0.0);
	TimingTolerance tolerance = new TimingTolerance(Preset.NORMAL);
	/** The latency of the simulated route: every transient is seen this much later. */
	long routeLatency;
	/** The wall time of the next frame. */
	long now = T0;
	FollowSession.Snapshot last;

	FollowHarness(SongFixture fixture, int percent) {
		this(fixture, percent, LATENCY);
	}

	FollowHarness(SongFixture fixture, int percent, long articulationLatency) {
		this(fixture, percent, articulationLatency, ChordMatcher.Parameters.defaults());
	}

	FollowHarness(SongFixture fixture, int percent, long articulationLatency, ChordMatcher.Parameters chordParameters) {
		this.fixture = fixture;
		this.sequence = fixture.sequence();
		this.timeline = FollowTimeline.build(fixture.song, this.sequence, percent);
		this.clock = new FakeClock(this.timeline);
		this.session = new FollowSession(this.sequence, this.timeline, FIRST_TICK, this.clock, frame -> T0 + frame.getTimestampNanos(),
				() -> new TimingContext(this.tolerance, this.compensation, new ClockUncertainty(this.spreadMillis), this.captureReady, this.playerReady),
				new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0), new ChordMatcher(chordParameters), STABILITY, articulationLatency);
		this.session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		this.session.setTrackAccepted(true);
	}

	/** The wall time at which the event is expected. */
	long target(int eventIndex) {
		return T0 + LEAD + this.timeline.targetNanos(eventIndex);
	}

	static double hz(double midi) {
		return 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0);
	}

	/** A note of a steady pitch starting at the wall time (as the learner plays it; the route latency is added). */
	Note play(long wallStart, int midi, long duration) {
		return playMoving(wallStart, duration, seconds -> midi);
	}

	Note playMoving(long wallStart, long duration, DoubleUnaryOperator midiAt) {
		Note note = new Note(wallStart + this.routeLatency, duration, midiAt);
		this.notes.add(note);
		return note;
	}

	/** The event is played {@code offsetMillis} from its target (negative: early) with its own pitch. */
	Note playEvent(int eventIndex, long offsetMillis) {
		return play(target(eventIndex) + offsetMillis * MS, this.sequence.getEvents().get(eventIndex).getExpectedMidi(), 600 * MS);
	}

	/** Feeds the frames up to the wall time (exclusive) and returns the last snapshot. */
	FollowSession.Snapshot runUntil(long wallEnd) {
		while( this.now < wallEnd ) {
			this.last = this.session.onFrame(frameAt(this.now));
			this.now += HOP;
		}
		return this.last;
	}

	/** Runs the whole performance: up to the end of the timeline and a second more. */
	FollowSession.Snapshot runAll() {
		return runUntil(T0 + LEAD + this.timeline.durationNanos() + 1_500 * MS);
	}

	/** Feeds frames that come from real analysis of an audio signal (their timestamps are stream times: the wall time is {@link #T0} plus them). */
	FollowSession.Snapshot feed(java.util.List<AnalysisFrame> frames) {
		for( AnalysisFrame frame : frames ) {
			this.last = this.session.onFrame(frame);
		}
		return this.last;
	}

	private AnalysisFrame frameAt(long wall) {
		Note sounding = null;
		for( Note note : this.notes ) {
			if( note.start <= wall && wall < note.start + note.duration ) {
				sounding = note;
			}
		}
		boolean onset = false;
		for( Note note : this.notes ) {
			if( !this.flagged.contains(note) && wall >= note.start + LATENCY ) {
				this.flagged.add(note);
				onset = true;
			}
		}
		double f0 = (sounding != null ? hz(sounding.midiAt.applyAsDouble((wall - sounding.start) / 1e9)) : Double.NaN);
		double confidence = (sounding != null ? 0.95 : 0.0);
		return new AnalysisFrame(wall - T0, -20.0, -10.0, 0.0, AnalysisFrame.LevelState.SUITABLE, f0, confidence, onset);
	}
}
