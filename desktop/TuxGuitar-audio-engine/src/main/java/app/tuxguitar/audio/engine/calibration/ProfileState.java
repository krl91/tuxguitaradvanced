package app.tuxguitar.audio.engine.calibration;

/** The state shown by the dot next to the device chooser (spec 013, FR-008): a pure function of the profile and of the capture rate. */
public enum ProfileState {
	/** no input is selected */
	NONE,
	/** not calibrated, the default chosen, or a profile that does not apply at the current sample rate: the default configuration is used */
	ORANGE,
	/** calibrated, at least 60 % */
	GREEN,
	/** calibrated and kept, under 60 % */
	RED;

	public static ProfileState of(boolean inputSelected, RecognitionProfile profile, int captureSampleRate) {
		return of(inputSelected, profile, captureSampleRate, RecognitionRate.THRESHOLD_PERCENT);
	}

	/** @param thresholdPercent the rate that makes a kept profile green (spec 026, {@code recognition.calibration.recognition-rate}) */
	public static ProfileState of(boolean inputSelected, RecognitionProfile profile, int captureSampleRate, int thresholdPercent) {
		if (!inputSelected) {
			return NONE;
		}
		if (profile == null || profile.getOrigin() == RecognitionProfile.Origin.DEFAULT_CHOSEN || !profile.appliesAt(captureSampleRate)) {
			return ORANGE;
		}
		return profile.getFoundRate().meetsThreshold(thresholdPercent) ? GREEN : RED;
	}
}
