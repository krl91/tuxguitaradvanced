package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayList;
import java.util.List;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/**
 * One calibration of the rhythm of a route (spec 002 T097, FR-054, FR-059): the clicks are played at a steady pace, the learner plays one note on each,
 * and the offsets of the notes to their clicks give the delay ({@link CalibrationTaps}). It runs on the frames of the analysis, with no thread and no
 * sleep of its own: a click is played when the time of a frame reaches it, and the time of a click is the one the {@link ClickPlayer} says it was
 * handed to the output.
 * <p>
 * It is announced before it starts, with its approximate duration ({@link #approximateSeconds()}), and {@link #cancel()} stops at once: no further click,
 * a click that rings is cut, the frames that follow are ignored and nothing is kept. Only an attack with a pitch that begins with it is a note: the
 * click itself, which the microphone may hear, has none, and a note that was already ringing is not the attack of the click heard over it.
 * <p>
 * Frames come from the analysis thread and the window reads the state: the methods are synchronized.
 * <p>
 * Limit: a note played less than about 65 ms after the click that the microphone hears again cannot be told from it by the attack alone (the
 * attack is reported that long after its transient); the learner who plays on the click with a speaker next to the microphone may be measured on
 * the click. A wired input or headphones do not have this.
 */
public final class CalibrationRun {

	public enum State {
		/** Built, not started: the learner has been told what will happen. */
		ANNOUNCED,
		RUNNING,
		/** All the clicks were played and the notes evaluated: see {@link #result()}. */
		FINISHED,
		CANCELLED,
		/** A click could not be played. */
		FAILED
	}

	/** Time before the first click, to get ready. */
	public static final long LEAD_NANOS = 1_500_000_000L;
	public static final long PERIOD_NANOS = 1_000_000_000L;
	/** A note belongs to a click when it begins from this long before it... */
	public static final long EARLY_WINDOW_NANOS = 250_000_000L;
	/** ...to this long after it (the delay of a route, a Bluetooth one included, and the learner's reaction). */
	public static final long LATE_WINDOW_NANOS = 700_000_000L;
	/**
	 * An attack must show a pitch within this long of the frame that reports it to be a note. The attack is reported well after its transient, so the
	 * pitch of a real note is already there: a pitch that comes later is another sound's.
	 */
	private static final long PITCH_PATIENCE_NANOS = 25_000_000L;
	/** The pitched sound must have begun with the attack: a note that was already ringing is not the attack's, a click heard over it is not a note. */
	private static final long PITCH_BEFORE_ATTACK_NANOS = 30_000_000L;
	private static final double SAME_PITCH_SEMITONES = 0.5;
	private static final double MIN_CONFIDENCE = 0.8;
	private static final double MIN_HZ = 70.0;
	private static final double MAX_HZ = 1400.0;

	private static final class Candidate {
		final long transientWall;
		final long deadline;

		Candidate(long transientWall, long deadline) {
			this.transientWall = transientWall;
			this.deadline = deadline;
		}
	}

	private final ClickPlayer clicks;
	private final int clickCount;
	private final long periodNanos;
	private final long articulationLatencyNanos;
	private final long[] clickWall;
	private final double[] tapOffsetMillis;
	private final List<Candidate> candidates = new ArrayList<Candidate>();

	private volatile State state = State.ANNOUNCED;
	private long pitchRunStart = Long.MIN_VALUE;
	private double pitchRunMidi = Double.NaN;
	private long firstClickAt = Long.MIN_VALUE;
	private volatile int played;
	private volatile CalibrationTaps.Result result;

	/** @param articulationLatencyNanos how long after the transient the onset of a frame is reported */
	public CalibrationRun(ClickPlayer clicks, long articulationLatencyNanos) {
		this(clicks, CalibrationTaps.CLICKS, PERIOD_NANOS, articulationLatencyNanos);
	}

	CalibrationRun(ClickPlayer clicks, int clickCount, long periodNanos, long articulationLatencyNanos) {
		this.clicks = clicks;
		this.clickCount = clickCount;
		this.periodNanos = periodNanos;
		this.articulationLatencyNanos = articulationLatencyNanos;
		this.clickWall = new long[clickCount];
		this.tapOffsetMillis = new double[clickCount];
		java.util.Arrays.fill(this.tapOffsetMillis, Double.NaN);
	}

	public State state() {
		return this.state;
	}

	public int clickCount() {
		return this.clickCount;
	}

	/** How many clicks were played so far. */
	public int clicksPlayed() {
		return this.played;
	}

	/** How many clicks have a note so far. */
	public synchronized int notesHeard() {
		int heard = 0;
		for (double offset : this.tapOffsetMillis) {
			heard += (Double.isNaN(offset) ? 0 : 1);
		}
		return heard;
	}

	/** The duration to announce to the learner, rounded up to a whole second: the lead, the clicks and the time to hear the last note. */
	public int approximateSeconds() {
		return secondsOf(this.clickCount, this.periodNanos);
	}

	/** The duration of a calibration with the standard clicks, to announce it before there is a run. */
	public static int announcedSeconds() {
		return secondsOf(CalibrationTaps.CLICKS, PERIOD_NANOS);
	}

	private static int secondsOf(int clickCount, long periodNanos) {
		long nanos = LEAD_NANOS + (clickCount - 1) * periodNanos + LATE_WINDOW_NANOS + 200_000_000L;
		return (int) ((nanos + 999_999_999L) / 1_000_000_000L);
	}

	/** The evaluation, once {@link State#FINISHED}; null before, and after a cancel or a failure. */
	public CalibrationTaps.Result result() {
		return this.result;
	}

	/** Starts at the next frame. */
	public synchronized void start() {
		if (this.state == State.ANNOUNCED) {
			this.state = State.RUNNING;
		}
	}

	/** Stops now, whatever the state of the run; nothing is kept. Calling it again does nothing. */
	public synchronized void cancel() {
		if (this.state == State.ANNOUNCED || this.state == State.RUNNING) {
			this.state = State.CANCELLED;
			this.candidates.clear();
			this.clicks.silence();
		}
	}

	/**
	 * @param frame a frame of the analysis
	 * @param frameWall its wall time (the scale of {@code System.nanoTime()}); {@link Long#MIN_VALUE} when it cannot be dated
	 */
	public synchronized void onFrame(AnalysisFrame frame, long frameWall) {
		if (this.state != State.RUNNING || frameWall == Long.MIN_VALUE) {
			return;
		}
		if (this.firstClickAt == Long.MIN_VALUE) {
			this.firstClickAt = frameWall + LEAD_NANOS;
		}
		while (this.played < this.clickCount && frameWall >= this.firstClickAt + this.played * this.periodNanos) {
			long handed = this.clicks.click();
			if (handed == Long.MIN_VALUE) {
				this.state = State.FAILED;
				this.clicks.silence();
				return;
			}
			this.clickWall[this.played] = handed;
			this.played++;
		}
		if (frame.isOnset()) {
			this.candidates.add(new Candidate(frameWall - this.articulationLatencyNanos, frameWall + PITCH_PATIENCE_NANOS));
		}
		boolean pitched = frame.getConfidence() >= MIN_CONFIDENCE && frame.getF0Hz() >= MIN_HZ && frame.getF0Hz() <= MAX_HZ;
		if (pitched) {
			double midi = 69.0 + 12.0 * Math.log(frame.getF0Hz() / 440.0) / Math.log(2.0);
			if (this.pitchRunStart == Long.MIN_VALUE || Math.abs(midi - this.pitchRunMidi) >= SAME_PITCH_SEMITONES) {
				this.pitchRunStart = frameWall;
			}
			this.pitchRunMidi = midi;
		} else {
			this.pitchRunStart = Long.MIN_VALUE;
		}
		for (Candidate candidate : new ArrayList<Candidate>(this.candidates)) {
			if (pitched && this.pitchRunStart >= candidate.transientWall - PITCH_BEFORE_ATTACK_NANOS) {
				this.candidates.remove(candidate);
				assign(candidate.transientWall);
			} else if (frameWall >= candidate.deadline) {
				this.candidates.remove(candidate);
			}
		}
		if (this.played == this.clickCount && frameWall > this.clickWall[this.clickCount - 1] + LATE_WINDOW_NANOS + this.articulationLatencyNanos) {
			CalibrationTaps taps = new CalibrationTaps();
			for (double offset : this.tapOffsetMillis) {
				if (!Double.isNaN(offset)) {
					taps.add(offset);
				}
			}
			this.result = taps.evaluate();
			this.state = State.FINISHED;
			// nothing more will sound: the clicks give back what they hold
			this.clicks.silence();
		}
	}

	/** The note belongs to the click it is closest to among those it can follow; a click keeps its first note. */
	private void assign(long transientWall) {
		int best = -1;
		long bestDistance = Long.MAX_VALUE;
		for (int k = 0; k < this.played; k++) {
			long offset = transientWall - this.clickWall[k];
			if (offset >= -EARLY_WINDOW_NANOS && offset <= LATE_WINDOW_NANOS && Math.abs(offset) < bestDistance) {
				best = k;
				bestDistance = Math.abs(offset);
			}
		}
		if (best >= 0 && Double.isNaN(this.tapOffsetMillis[best])) {
			this.tapOffsetMillis[best] = (transientWall - this.clickWall[best]) / 1e6;
		}
	}
}
