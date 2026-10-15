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
		if (!inputSelected) {
			return NONE;
		}
		if (profile == null || profile.getOrigin() == RecognitionProfile.Origin.DEFAULT_CHOSEN || !profile.appliesAt(captureSampleRate)) {
			return ORANGE;
		}
		return profile.getFoundRate().meetsThreshold() ? GREEN : RED;
	}
}
