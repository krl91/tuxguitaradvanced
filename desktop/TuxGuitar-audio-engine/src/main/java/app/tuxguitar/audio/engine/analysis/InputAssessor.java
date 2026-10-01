package app.tuxguitar.audio.engine.analysis;

import app.tuxguitar.audio.engine.input.InputAssessment;
import app.tuxguitar.audio.engine.input.InputAssessment.Reason;
import app.tuxguitar.audio.engine.input.InputAssessment.Status;

/**
 * Quick level check of the selected input while the learner plays (FR-038). Frames with a
 * confident pitch count as guitar playing; the check ends once enough playing was heard, or
 * after a timeout. The criteria are provisional parameters, established by technical testing
 * (FR-038) and refined with the benchmark (tasks T076), not product constants.
 *
 * <p>Feed it the analysis frames of the selected input ({@link #analysisFrame}, analysis
 * thread); the result is reported once to the listener.</p>
 */
public final class InputAssessor implements AnalysisService.Listener {

	public interface Listener {
		void assessmentCompleted(InputAssessment assessment);
	}

	public static final class Parameters {
		/** A frame counts as playing when its pitch confidence reaches this value. */
		public double playingConfidence = 0.80;
		/** Playing needed to conclude. */
		public long requiredPlayingNanos = 500_000_000L;
		/** Without enough playing by then, the input has no meaningful guitar signal. */
		public long timeoutNanos = 10_000_000_000L;
		/** Share of playing frames that are clipping above which the input is unusable. */
		public double unusableClippingShare = 0.50;
		/** Share of playing frames that are clipping (or too high) that makes it suboptimal. */
		public double suboptimalShare = 0.10;
		/** Share of playing frames that are too low that makes it suboptimal. */
		public double tooLowShare = 0.50;
		/** A whole window at or below this RMS level is treated as exact silence (all zeros). */
		public double digitalSilenceDbfs = -120.0;
	}

	private final Parameters parameters;
	private final Listener listener;

	private boolean checking;
	private long startNanos;
	private long lastPlayingNanos;
	private long playingNanos;
	private int playingFrames;
	private int playingClipping;
	private int playingTooHigh;
	private int playingTooLow;
	private int frames;
	private int clippingFrames;
	private int silentFrames;
	private double playingRmsSumDb;
	private double playingPeakMaxDb = Double.NEGATIVE_INFINITY;
	private double quietMinRmsDb = Double.POSITIVE_INFINITY;
	private LevelCheckStats lastStats;

	public InputAssessor(Parameters parameters, Listener listener) {
		this.parameters = parameters;
		this.listener = listener;
	}

	/** Starts a new check, discarding any running one. */
	public synchronized void runLevelCheck() {
		this.checking = true;
		this.startNanos = -1;
		this.lastPlayingNanos = -1;
		this.playingNanos = 0;
		this.playingFrames = 0;
		this.playingClipping = 0;
		this.playingTooHigh = 0;
		this.playingTooLow = 0;
		this.frames = 0;
		this.clippingFrames = 0;
		this.silentFrames = 0;
		this.playingRmsSumDb = 0.0;
		this.playingPeakMaxDb = Double.NEGATIVE_INFINITY;
		this.quietMinRmsDb = Double.POSITIVE_INFINITY;
	}

	/** What the latest finished level check measured (spec 002, FR-066), or null before any check. */
	public synchronized LevelCheckStats getLastStats() {
		return this.lastStats;
	}

	private LevelCheckStats currentStats() {
		boolean playing = this.playingFrames > 0;
		boolean quiet = !Double.isInfinite(this.quietMinRmsDb);
		return new LevelCheckStats(playing, playing ? this.playingRmsSumDb / this.playingFrames : Double.NaN,
				playing ? this.playingPeakMaxDb : Double.NaN, quiet, quiet ? this.quietMinRmsDb : Double.NaN);
	}

	public synchronized void cancel() {
		this.checking = false;
	}

	/** The input disappeared: the input is unusable whether or not a check was running. */
	public void deviceLost() {
		synchronized (this) {
			this.checking = false;
		}
		this.listener.assessmentCompleted(new InputAssessment(Status.UNUSABLE, Reason.DEVICE_LOST));
	}

	public synchronized boolean isChecking() {
		return this.checking;
	}

	@Override
	public void analysisFrame(AnalysisFrame frame) {
		InputAssessment result = null;
		synchronized (this) {
			if( !this.checking ) {
				return;
			}
			long now = frame.getTimestampNanos();
			if( this.startNanos < 0 ) {
				this.startNanos = now;
			}
			this.frames ++;
			if( frame.getLevelState() == AnalysisFrame.LevelState.CLIPPING ) {
				this.clippingFrames ++;
			}
			if( frame.getRmsDbfs() <= this.parameters.digitalSilenceDbfs ) {
				this.silentFrames ++;
			}
			if( !Double.isNaN(frame.getF0Hz()) && frame.getConfidence() >= this.parameters.playingConfidence ) {
				if( this.lastPlayingNanos >= 0 ) {
					this.playingNanos += Math.min(now - this.lastPlayingNanos, 50_000_000L);
				}
				this.lastPlayingNanos = now;
				this.playingFrames ++;
				this.playingRmsSumDb += frame.getRmsDbfs();
				this.playingPeakMaxDb = Math.max(this.playingPeakMaxDb, frame.getPeakDbfs());
				switch( frame.getLevelState() ) {
					case CLIPPING: this.playingClipping ++; break;
					case TOO_HIGH: this.playingTooHigh ++; break;
					case TOO_LOW: this.playingTooLow ++; break;
					default: break;
				}
			}
			if( (Double.isNaN(frame.getF0Hz()) || frame.getConfidence() < this.parameters.playingConfidence)
					&& frame.getRmsDbfs() > this.parameters.digitalSilenceDbfs ) {
				// a frame without playing: the background (its quietest level stands for the noise floor)
				this.quietMinRmsDb = Math.min(this.quietMinRmsDb, frame.getRmsDbfs());
			}
			if( this.playingNanos >= this.parameters.requiredPlayingNanos ) {
				result = classifyPlaying();
			} else if( now - this.startNanos >= this.parameters.timeoutNanos ) {
				result = classifyWithoutPlaying();
			}
			if( result != null ) {
				this.checking = false;
				this.lastStats = currentStats();
			}
		}
		if( result != null ) {
			this.listener.assessmentCompleted(result);
		}
	}

	private InputAssessment classifyPlaying() {
		double clipping = share(this.playingClipping);
		if( clipping >= this.parameters.unusableClippingShare ) {
			return new InputAssessment(Status.UNUSABLE, Reason.CLIPPING);
		}
		if( clipping >= this.parameters.suboptimalShare ) {
			return new InputAssessment(Status.SUBOPTIMAL, Reason.CLIPPING);
		}
		if( share(this.playingTooHigh) >= this.parameters.suboptimalShare ) {
			return new InputAssessment(Status.SUBOPTIMAL, Reason.TOO_HIGH);
		}
		if( share(this.playingTooLow) >= this.parameters.tooLowShare ) {
			return new InputAssessment(Status.SUBOPTIMAL, Reason.TOO_LOW);
		}
		return new InputAssessment(Status.SUITABLE, Reason.NONE);
	}

	private InputAssessment classifyWithoutPlaying() {
		if( this.frames > 0 && this.silentFrames == this.frames ) {
			return new InputAssessment(Status.UNUSABLE, Reason.ACCESS_DENIED_SUSPECTED);
		}
		if( this.frames > 0 && this.clippingFrames * 2 >= this.frames ) {
			return new InputAssessment(Status.UNUSABLE, Reason.CLIPPING);
		}
		return new InputAssessment(Status.UNUSABLE, Reason.NO_SIGNAL);
	}

	private double share(int count) {
		return (this.playingFrames == 0 ? 0 : count / (double) this.playingFrames);
	}
}
