package app.tuxguitar.audio.engine.monitor.fx;

/**
 * The last stage before the output (spec 022 FR-010, research R3): the guitar's volume (ramped over a block), a half-second fade in after
 * {@link #start}, a 20 ms fade out after {@link #stop}, a peak limiter holding the peaks at -1 dBFS, and a clamp at 0 dBFS. Mono, in place.
 */
public final class OutputStage {

	public static final double CEILING = Math.pow(10, -1.0 / 20);
	private static final double FADE_IN_SECONDS = 0.5;
	private static final double FADE_OUT_SECONDS = 0.020;
	private static final double RELEASE_SECONDS = 0.050;

	private final double fadeInStep;
	private final double fadeOutStep;
	private final double release;
	private volatile double targetVolume = 0.7;
	private double volume = 0.7;
	private double fadePosition;
	private boolean fadingOut;
	private double fadeOutLevel;
	private double limiterGain = 1.0;

	public OutputStage(float sampleRate, int maxBlock) {
		this.fadeInStep = 1.0 / (FADE_IN_SECONDS * sampleRate);
		this.fadeOutStep = 1.0 / (FADE_OUT_SECONDS * sampleRate);
		this.release = 1.0 - Math.exp(-1.0 / (RELEASE_SECONDS * sampleRate));
	}

	/** From 0 to 1; reached at the end of the next block. Any thread. */
	public void setVolume(double value) {
		this.targetVolume = Math.max(0, Math.min(1, value));
	}

	/** Starts the fade in from silence. Output thread. */
	public void start() {
		this.fadePosition = 0;
		this.fadingOut = false;
		this.limiterGain = 1.0;
		this.volume = this.targetVolume;
	}

	/** Starts the 20 ms fade out. Output thread. */
	public void stop() {
		if( !this.fadingOut ) {
			this.fadeOutLevel = fadeIn();
			this.fadingOut = true;
		}
	}

	/** True once the fade in is over. */
	public boolean isFadedIn() {
		return !this.fadingOut && this.fadePosition >= 1.0;
	}

	/** True once the fade out reached silence. */
	public boolean isSilent() {
		return this.fadingOut && this.fadeOutLevel <= 0;
	}

	private double fadeIn() {
		// a raised cosine: no step at the start or at the end
		double position = Math.min(1.0, this.fadePosition);
		return 0.5 - 0.5 * Math.cos(Math.PI * position);
	}

	public void process(float[] buffer, int n) {
		double from = this.volume;
		double to = this.targetVolume;
		for( int i = 0 ; i < n ; i++ ) {
			double gain = from + (to - from) * (i + 1) / n;
			double fade;
			if( this.fadingOut ) {
				this.fadeOutLevel = Math.max(0, this.fadeOutLevel - this.fadeOutStep);
				fade = this.fadeOutLevel;
			} else {
				this.fadePosition += this.fadeInStep;
				fade = fadeIn();
			}
			double x = buffer[i] * gain * fade;
			double magnitude = Math.abs(x);
			if( magnitude * this.limiterGain > CEILING ) {
				this.limiterGain = CEILING / magnitude;
			} else {
				this.limiterGain += (1.0 - this.limiterGain) * this.release;
			}
			double y = x * this.limiterGain;
			if( y > 1.0 ) {
				y = 1.0;
			} else if( y < -1.0 ) {
				y = -1.0;
			}
			buffer[i] = (float) y;
		}
		this.volume = to;
	}
}
