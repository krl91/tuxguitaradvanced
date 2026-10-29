package app.tuxguitar.app.tools.custom.practice.session;

import java.util.List;

import app.tuxguitar.app.tools.custom.practice.model.PracticeEvent;
import app.tuxguitar.audio.engine.analysis.HopListener;
import app.tuxguitar.audio.engine.calibration.HopRecorder;

/**
 * What a Follow run keeps for its record (spec 016 FR-003, contracts/run-record.md), without any file: the settings of the run when it starts, the
 * sound the analysis sees when the learner asked for it (an {@link HopRecorder} of at most {@link #MAX_MILLIS}), the time of its first sample, and at
 * the end the text of the record. The window writes the files; a run that ended before its count-in did gives nothing.
 */
public final class FollowRunRecorder {

	/** The longest sound kept: beyond it the record says {@code audio_truncated=true}. */
	public static final int MAX_MILLIS = 10 * 60 * 1000;
	/** Kept beyond the length of the timeline: the count-in, and a player slower than its clock. */
	static final int MARGIN_MILLIS = 60 * 1000;

	/** Where the hops come from: the audio engine in the application. */
	public interface Hops {
		void add(HopListener listener);

		void remove(HopListener listener);
	}

	/** What the record says of a run before it starts. */
	public static final class Start {
		final String score;
		final int track;
		final int percent;
		final int windowMillis;
		final int playTimeMillis;
		final FollowLeniencyPosition position;
		final AudioRoute route;
		final LatencyCompensation calibration;

		/**
		 * @param windowMillis the timing window the run judges with
		 * @param route the route of the run, or null when it is not known
		 */
		public Start(String score, int track, int percent, int windowMillis, AudioRoute route, LatencyCompensation calibration) {
			this(score, track, percent, windowMillis, -1, null, route, calibration);
		}

		/**
		 * A run of Follow judged with a time to play and a pitch permissiveness (spec 016 FR-007).
		 *
		 * @param playTimeMillis the time to play, or a negative number when the run judges with the timing window
		 * @param position the pitch permissiveness, or null when the run has none
		 */
		public Start(String score, int track, int percent, int windowMillis, int playTimeMillis, FollowLeniencyPosition position, AudioRoute route,
				LatencyCompensation calibration) {
			this.score = score;
			this.track = track;
			this.percent = percent;
			this.windowMillis = windowMillis;
			this.playTimeMillis = playTimeMillis;
			this.position = position;
			this.route = route;
			this.calibration = calibration;
		}
	}

	/** The files of a run, not written yet. */
	public static final class Take {
		private final String record;
		private final float[] samples;
		private final int sampleRate;

		Take(String record, float[] samples, int sampleRate) {
			this.record = record;
			this.samples = samples;
			this.sampleRate = sampleRate;
		}

		public String record() {
			return this.record;
		}

		/** The sound, or null when it was not asked for. */
		public float[] samples() {
			return this.samples;
		}

		public int sampleRate() {
			return this.sampleRate;
		}
	}

	private final Hops hops;
	private final HopRecorder recorder;
	private final Start start;
	private volatile long firstSampleWall = Long.MIN_VALUE;
	private boolean released;

	/**
	 * @param audio true to keep the sound too
	 * @param timelineNanos the length of the run's timeline: the sound is kept for it and a margin, at most {@link #MAX_MILLIS}
	 */
	public FollowRunRecorder(Hops hops, boolean audio, long timelineNanos, Start start) {
		this.hops = hops;
		this.start = start;
		this.recorder = (audio ? new HopRecorder(capacityMillis(timelineNanos)) : null);
	}

	static int capacityMillis(long timelineNanos) {
		return (int) Math.min(MAX_MILLIS, Math.max(0L, timelineNanos / 1_000_000L) + MARGIN_MILLIS);
	}

	/** When the count-in begins (the window, before the run starts): the sound is kept from the next hop. */
	public void begin() {
		if (this.recorder != null) {
			this.hops.add(this.recorder);
			this.recorder.begin(null);
		}
	}

	/**
	 * Every frame of the run, with its wall time (the analysis thread; allocates nothing). The first frame that follows recorded sound dates the first
	 * sample: the frame is dated at the end of the capture it read, so the first sample lies the recorded length before it (within one hop).
	 */
	public void frame(long wallNanos) {
		HopRecorder current = this.recorder;
		if (current == null || this.firstSampleWall != Long.MIN_VALUE || wallNanos == Long.MIN_VALUE) {
			return;
		}
		int length = current.getLength();
		int rate = current.getSampleRate();
		if (length > 0 && rate > 0) {
			this.firstSampleWall = wallNanos - length * 1_000_000_000L / rate;
		}
	}

	/** Stops keeping the sound and lets the hops go; calling it again does nothing. */
	public synchronized void release() {
		if (!this.released && this.recorder != null) {
			this.recorder.end();
			this.hops.remove(this.recorder);
		}
		this.released = true;
	}

	/** True for a run whose count-in ended: only those have a record. */
	static boolean followed(FollowSession.State state) {
		return state != FollowSession.State.PREPARING && state != FollowSession.State.COUNT_IN;
	}

	/**
	 * At the end of the run, on the session's thread before the session stops ({@link FollowRun#setEndHook}).
	 *
	 * @param stem the name of the files without their extension: the record names its sound with it
	 * @return the files to write, or null for a run that ended before its count-in did
	 */
	public Take ended(FollowSession session, List<PracticeEvent> events, FollowTimeline timeline, String stem) {
		release();
		FollowSession.Snapshot snapshot = session.snapshot();
		if (!followed(snapshot.state())) {
			if (this.recorder != null) {
				this.recorder.discard();
			}
			return null;
		}
		float[] samples = null;
		int rate = 0;
		boolean truncated = false;
		long origin = session.timelineOriginWall();
		double firstSample = 0.0;
		if (this.recorder != null) {
			samples = this.recorder.getSamples();
			rate = this.recorder.getSampleRate();
			truncated = this.recorder.isFull();
			if (this.firstSampleWall != Long.MIN_VALUE) {
				firstSample = (this.firstSampleWall - origin) / 1e6;
			}
			this.recorder.discard();
		}
		AudioRoute route = this.start.route;
		LatencyCompensation calibration = this.start.calibration;
		FollowRunRecord.Header header = new FollowRunRecord.Header(this.start.score, this.start.track, this.start.percent, this.start.windowMillis, this.start.playTimeMillis, this.start.position != null ? this.start.position.name() : "",
				route != null ? route.input() : "", route != null ? route.output() : "", route != null ? route.sampleRate() + " Hz " + route.profile() : "",
				calibration.source().name(), calibration.offsetNanos() / 1e6, samples != null ? stem + ".wav" : null, rate, firstSample, truncated);
		String text = FollowRunRecord.text(header, events, snapshot.results(), i -> timeline.targetNanos(i) / 1e6, session::heard, origin);
		return new Take(text, samples, rate);
	}
}
