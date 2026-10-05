package app.tuxguitar.app.tools.custom.tunernext;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/**
 * Steadies the tuner reading: the pitch estimate of a single analysis hop jumps by several cents
 * (and sometimes by an octave while the string is plucked or fading), so the needle shown from one
 * hop shakes and, when a hop is not confident, freezes. This keeps the confident readings of the
 * last {@link #WINDOW_NANOS} and shows their median: a glitch cannot move it, a gap shorter than
 * {@link #GRACE_NANOS} does not freeze it, and a new note replaces the old one within a few hops.
 * Fed by the analysis thread, read by the UI thread: synchronized, cheap per frame.
 */
public final class TunerSmoother {

	/** Readings older than this are forgotten. */
	public static final long WINDOW_NANOS = 250_000_000L;
	/** Without any confident reading for this long there is no reading (the string is silent). */
	public static final long GRACE_NANOS = 300_000_000L;
	/** A reading this far (cents) from the median is a glitch, or a new note when it persists. */
	private static final double OUTLIER_CENTS = 60.0;
	private static final int NEW_NOTE_READINGS = 3;
	private static final int MIN_READINGS = 3;

	private static final class Reading {
		final long nanos;
		final double cents;
		final double confidence;

		Reading(long nanos, double cents, double confidence) {
			this.nanos = nanos;
			this.cents = cents;
			this.confidence = confidence;
		}
	}

	private final ArrayDeque<Reading> window = new ArrayDeque<Reading>();
	private long lastNanos = Long.MIN_VALUE;
	private int apart;

	/** @param minConfidence the certainty below which a hop's estimate is not used */
	public synchronized void offer(AnalysisFrame frame, double minConfidence) {
		long now = frame.getTimestampNanos();
		this.lastNanos = now;
		prune(now);
		double f0 = frame.getF0Hz();
		boolean usable = Double.isFinite(f0) && f0 > 0.0 && frame.getConfidence() >= minConfidence
				&& frame.getLevelState() != AnalysisFrame.LevelState.CLIPPING;
		if( !usable ) {
			return;
		}
		double cents = 1200.0 * Math.log(f0 / 440.0) / Math.log(2.0);
		if( this.window.size() >= MIN_READINGS && Math.abs(cents - median()) > OUTLIER_CENTS ) {
			// a glitch, unless the same different pitch keeps coming: then it is the next note
			this.apart ++;
			if( this.apart < NEW_NOTE_READINGS ) {
				return;
			}
			this.window.clear();
		}
		this.apart = 0;
		this.window.addLast(new Reading(now, cents, frame.getConfidence()));
	}

	/**
	 * The frame to show: the same frame with the steady pitch and certainty when there are enough
	 * recent confident readings, otherwise the frame itself.
	 */
	public synchronized AnalysisFrame smooth(AnalysisFrame frame) {
		long now = Math.max(frame.getTimestampNanos(), this.lastNanos);
		prune(now);
		if( this.window.size() < MIN_READINGS ) {
			return frame;
		}
		double best = 0.0;
		for( Reading reading : this.window ) {
			best = Math.max(best, reading.confidence);
		}
		double f0 = 440.0 * Math.pow(2.0, median() / 1200.0);
		return new AnalysisFrame(frame.getTimestampNanos(), frame.getRmsDbfs(), frame.getPeakDbfs(), frame.getClippedRatio(), levelOf(frame), f0, best, frame.isOnset());
	}

	/** Forgets everything (input, mode or target changed). */
	public synchronized void clear() {
		this.window.clear();
		this.apart = 0;
	}

	/** While readings are being kept the tone is there: a low instantaneous level is not "too low". */
	private AnalysisFrame.LevelState levelOf(AnalysisFrame frame) {
		return (frame.getLevelState() == AnalysisFrame.LevelState.TOO_LOW ? AnalysisFrame.LevelState.SUITABLE : frame.getLevelState());
	}

	private void prune(long now) {
		Iterator<Reading> iterator = this.window.iterator();
		while( iterator.hasNext() && now - iterator.next().nanos > WINDOW_NANOS ) {
			iterator.remove();
		}
	}

	private double median() {
		double[] values = new double[this.window.size()];
		int i = 0;
		for( Reading reading : this.window ) {
			values[i ++] = reading.cents;
		}
		Arrays.sort(values);
		return (values.length % 2 == 1 ? values[values.length / 2] : 0.5 * (values[values.length / 2 - 1] + values[values.length / 2]));
	}
}
