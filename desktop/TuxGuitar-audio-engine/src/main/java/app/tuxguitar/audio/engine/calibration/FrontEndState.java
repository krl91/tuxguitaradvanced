package app.tuxguitar.audio.engine.calibration;

/** The input gain and the noise reduction when the recording was made (spec 013): remembered and shown, never enforced. */
public final class FrontEndState {

	private static final double GAIN_TOLERANCE = 1e-3;

	private final double gainMultiplier;
	private final boolean noiseReduction;

	public FrontEndState(double gainMultiplier, boolean noiseReduction) {
		this.gainMultiplier = gainMultiplier;
		this.noiseReduction = noiseReduction;
	}

	public double getGainMultiplier() {
		return this.gainMultiplier;
	}

	public boolean isNoiseReduction() {
		return this.noiseReduction;
	}

	/** True when the gain or the noise reduction now in use is not what they were at the recording (the dot then shows its warning sentence). */
	public boolean differsFrom(double gainMultiplier, boolean noiseReduction) {
		return Math.abs(this.gainMultiplier - gainMultiplier) > GAIN_TOLERANCE * Math.max(1.0, Math.abs(this.gainMultiplier)) || this.noiseReduction != noiseReduction;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof FrontEndState && ((FrontEndState) other).noiseReduction == this.noiseReduction
				&& Double.compare(((FrontEndState) other).gainMultiplier, this.gainMultiplier) == 0;
	}

	@Override
	public int hashCode() {
		return Double.hashCode(this.gainMultiplier) * 31 + (this.noiseReduction ? 1 : 0);
	}
}
