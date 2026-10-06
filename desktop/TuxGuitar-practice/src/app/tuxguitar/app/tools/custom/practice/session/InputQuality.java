package app.tuxguitar.app.tools.custom.practice.session;

import java.util.ArrayDeque;
import java.util.Arrays;

import app.tuxguitar.audio.engine.analysis.AnalysisFrame;

/**
 * Tells when the input is too weak for reliable fast recognition (spec 002 FR-015). It watches the level the learner actually plays at:
 * the strongest level reached in the {@link #ATTACK_WINDOW_NANOS} after each attack. When the median of the last few attacks lies below
 * {@link PracticeParameters#WEAK_FAST_DBFS}, fast recognition is not reliable (measured on real notes, research R3: notes in a row
 * 0.35 s apart are followed in full at 100 % from -25 dBFS, 95 % at -30, 80 to 90 % at -35 and 10 % at -40; added white noise up to -40 dBFS
 * changed nothing measurable, so noise is not a criterion). Pure logic over frames, no audio, deterministic.
 */
public final class InputQuality {

	/** Attacks considered, and how long an attack counts. */
	static final int ATTACKS = 3;
	static final long REMEMBER_NANOS = 8_000_000_000L;
	static final long ATTACK_WINDOW_NANOS = 200_000_000L;

	private final double weakDbfs;
	private final ArrayDeque<double[]> peaks = new ArrayDeque<double[]>();
	private long attackStartNanos = Long.MIN_VALUE;
	private double attackPeak;

	public InputQuality(double weakDbfs) {
		this.weakDbfs = weakDbfs;
	}

	/** Offers the next frame; clipping frames are ignored (that is another problem, with its own message). */
	public void offer(AnalysisFrame frame) {
		long now = frame.getTimestampNanos();
		if( frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING ) {
			return;
		}
		if( this.attackStartNanos != Long.MIN_VALUE ) {
			if( now - this.attackStartNanos <= ATTACK_WINDOW_NANOS ) {
				this.attackPeak = Math.max(this.attackPeak, frame.getRmsDbfs());
			} else {
				this.peaks.addLast(new double[] { this.attackStartNanos, this.attackPeak });
				this.attackStartNanos = Long.MIN_VALUE;
			}
		}
		if( frame.isOnset() && this.attackStartNanos == Long.MIN_VALUE ) {
			this.attackStartNanos = now;
			this.attackPeak = frame.getRmsDbfs();
		}
		while( !this.peaks.isEmpty() && now - (long) this.peaks.peekFirst()[0] > REMEMBER_NANOS ) {
			this.peaks.removeFirst();
		}
		while( this.peaks.size() > ATTACKS ) {
			this.peaks.removeFirst();
		}
	}

	/** Forgets what was seen (a start, a resume, a restart). */
	public void reset() {
		this.peaks.clear();
		this.attackStartNanos = Long.MIN_VALUE;
	}

	/** True when at least {@link #ATTACKS} recent attacks were played and their median level is below the weak limit. */
	public boolean isPoor() {
		return this.peaks.size() >= ATTACKS && median() < this.weakDbfs;
	}

	/** The median level of the recent attacks in dBFS, or NaN while there are fewer than {@link #ATTACKS}. */
	public double getPlayedLevelDbfs() {
		return this.peaks.size() >= ATTACKS ? median() : Double.NaN;
	}

	private double median() {
		double[] levels = new double[this.peaks.size()];
		int i = 0;
		for( double[] peak : this.peaks ) {
			levels[i ++] = peak[1];
		}
		Arrays.sort(levels);
		return levels[levels.length / 2];
	}
}
