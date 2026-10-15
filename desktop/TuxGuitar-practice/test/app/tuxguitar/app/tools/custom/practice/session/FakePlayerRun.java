package app.tuxguitar.app.tools.custom.practice.session;

import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.HOP;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LATENCY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.LEAD;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.STABILITY;
import static app.tuxguitar.app.tools.custom.practice.session.FollowHarness.T0;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.tuxguitar.app.tools.custom.practice.model.PracticeSequence;
import app.tuxguitar.app.tools.custom.practice.model.SongFixture;
import app.tuxguitar.app.tools.custom.practice.session.FollowHarness.Note;
import app.tuxguitar.app.tools.custom.practice.session.TimingTolerance.Preset;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeMidiPlayer;
import app.tuxguitar.app.tools.custom.practice.testsupport.FakeTempoClock;
import app.tuxguitar.audio.engine.analysis.AnalysisFrame;
import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.pitch.NoteInterpreter;
import app.tuxguitar.audio.engine.pitch.TolerancePolicy;
import app.tuxguitar.song.models.TGSong;

/**
 * A whole tempo-following run on the fakes (spec 002 T137): the {@link FollowPlayback} starts the {@link FakeMidiPlayer}, whose count-down and
 * {@link FakeTempoClock} give the position of the player to the {@link FollowSession}, and the frames come from scripted notes or from analysed audio.
 * The only time is the one the test advances: no thread, no sleep.
 */
final class FakePlayerRun {

	static final long BASE_TICK = 960L;
	static final int TRACK = 1;

	final SongFixture fixture;
	final long lead;
	final PracticeSequence sequence;
	final FollowTimeline timeline;
	final FakeTempoClock clock;
	final FakeMidiPlayer player;
	final FollowPlayback playback;
	final FollowSession session;
	final List<FollowPlayback.EndReason> ends = new ArrayList<FollowPlayback.EndReason>();
	final List<Note> notes = new ArrayList<Note>();
	final Set<Note> flagged = new HashSet<Note>();

	boolean captureReady = true;
	LatencyCompensation compensation = LatencyCompensation.calibrated(0.0);
	double spreadMillis = 5.0;
	/** The dating of a frame throws: the session fails. */
	volatile boolean failDating;
	long now = T0;
	FollowSession.Snapshot last;

	FakePlayerRun(SongFixture fixture, int percent) {
		this(fixture, percent, LATENCY);
	}

	FakePlayerRun(SongFixture fixture, int percent, long articulationLatency) {
		this(fixture, percent, articulationLatency, LEAD);
	}

	/** @param leadNanos the count-down: the first measure of the score begins this long after the first frame */
	FakePlayerRun(SongFixture fixture, int percent, long articulationLatency, long leadNanos) {
		this(fixture, fixture.song, fixture.sequence(), percent, articulationLatency, leadNanos, new TimingTolerance(Preset.NORMAL));
	}

	/** A run judged with the given windows (spec 012: the width the learner chose instead of the old Normal preset). */
	FakePlayerRun(SongFixture fixture, int percent, TimingTolerance tolerance) {
		this(fixture, fixture.song, fixture.sequence(), percent, 0L, LEAD, tolerance);
	}

	/** A run on a song that is not a fixture (a score read from a file): the sequence is the one of its track. */
	FakePlayerRun(TGSong song, PracticeSequence sequence, int percent, long articulationLatency, long leadNanos) {
		this(null, song, sequence, percent, articulationLatency, leadNanos, new TimingTolerance(Preset.NORMAL));
	}

	private FakePlayerRun(SongFixture fixture, TGSong song, PracticeSequence sequence, int percent, long articulationLatency, long leadNanos, TimingTolerance tolerance) {
		this.lead = leadNanos;
		this.fixture = fixture;
		this.sequence = sequence;
		this.timeline = FollowTimeline.build(song, this.sequence, percent);
		long length = 0;
		while (this.timeline.nanosAtTick(length) < this.timeline.durationNanos()) {
			length += 960;
		}
		this.clock = new FakeTempoClock(ticks -> {
			long whole = (long) Math.floor(ticks);
			double part = ticks - whole;
			return this.timeline.nanosAtTick(whole) * (1.0 - part) + this.timeline.nanosAtTick(whole + 1) * part;
		}, BASE_TICK, length);
		this.clock.setNow(T0);
		this.player = new FakeMidiPlayer(this.clock).withInitialState(100, false, true).countDownNanos(leadNanos);
		this.playback = new FollowPlayback(this.player, TRACK, percent, BASE_TICK + this.timeline.playStartTicks(0), this.ends::add);
		this.session = new FollowSession(this.sequence, this.timeline, BASE_TICK, this.clock, frame -> {
					if (this.failDating) {
						throw new IllegalStateException("the session fails");
					}
					return T0 + frame.getTimestampNanos();
				},
				() -> new TimingContext(tolerance, this.compensation, new ClockUncertainty(this.spreadMillis), this.captureReady, this.clock.isReady() && !this.clock.isStalled(this.clock.nowNanos())),
				new NoteInterpreter(0.8), new TolerancePolicy(5.0, 25.0), new ChordMatcher(ChordMatcher.Parameters.defaults()), STABILITY, articulationLatency);
		this.session.setAssessment(new InputAssessment(InputAssessment.Status.SUITABLE, InputAssessment.Reason.NONE));
		this.session.setTrackAccepted(true);
	}

	/** The session is started and the player plays; the count-down is the first {@link FollowHarness#LEAD} of the run. */
	FollowPlayback.Result start() {
		this.session.start();
		return this.playback.start();
	}

	long target(int eventIndex) {
		return T0 + this.lead + this.timeline.targetNanos(eventIndex);
	}

	Note play(long wallStart, int midi, long duration) {
		Note note = new Note(wallStart, duration, seconds -> midi);
		this.notes.add(note);
		return note;
	}

	Note playMoving(long wallStart, long duration, java.util.function.DoubleUnaryOperator midiAt) {
		Note note = new Note(wallStart, duration, midiAt);
		this.notes.add(note);
		return note;
	}

	Note playEvent(int eventIndex, long offsetMillis) {
		return play(target(eventIndex) + offsetMillis * FollowHarness.MS, this.sequence.getEvents().get(eventIndex).getExpectedMidi(), 600 * FollowHarness.MS);
	}

	/** Moves the player and the clock to the wall time, one frame every {@link FollowHarness#HOP}. */
	FollowSession.Snapshot runUntil(long wallEnd) {
		while (this.now < wallEnd) {
			advanceTo(this.now);
			this.player.pollClock();
			this.last = this.session.onFrame(FollowHarness.frameOf(this.notes, this.flagged, this.now));
			this.now += HOP;
		}
		return this.last;
	}

	FollowSession.Snapshot runAll() {
		return runUntil(T0 + this.lead + this.timeline.durationNanos() + 1_500 * FollowHarness.MS);
	}

	/** Frames from the analysis of audio (stream times), the player moving to each one. */
	FollowSession.Snapshot feed(List<AnalysisFrame> frames) {
		for (AnalysisFrame frame : frames) {
			advanceTo(T0 + frame.getTimestampNanos());
			this.player.pollClock();
			this.last = this.session.onFrame(frame);
		}
		return this.last;
	}

	void advanceTo(long wall) {
		if (wall > this.clock.nowNanos()) {
			this.player.advance(wall - this.clock.nowNanos());
		}
	}
}
