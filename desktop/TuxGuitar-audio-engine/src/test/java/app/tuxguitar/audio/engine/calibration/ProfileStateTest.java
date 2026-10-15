package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter;

/** Spec 013 (T007): the state of the dot is a pure function (FR-008, SC-003). */
public class ProfileStateTest {

	private static final DeviceKey KEY = DeviceKey.of("Micro MacBook Pro", null, 0);

	private static RecognitionProfile searched(int rate, RecognitionRate found) {
		return new RecognitionProfile(KEY, rate, 0L, new LinkedHashMap<RecognitionParameter.Number, Double>(), found, new RecognitionRate(1, 10, 0, 3),
				new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
	}

	@Test
	public void noInputIsGreyWhateverThereIs() {
		assertEquals(ProfileState.NONE, ProfileState.of(false, null, 0));
		assertEquals(ProfileState.NONE, ProfileState.of(false, searched(48000, new RecognitionRate(10, 10, 3, 3)), 48000));
	}

	@Test
	public void noProfileIsOrange() {
		assertEquals(ProfileState.ORANGE, ProfileState.of(true, null, 48000));
	}

	@Test
	public void theDefaultChosenIsOrange() {
		RecognitionProfile profile = RecognitionProfile.defaultChosen(KEY, 48000, 0L, new FrontEndState(1.0, false));
		assertEquals(ProfileState.ORANGE, ProfileState.of(true, profile, 48000));
	}

	@Test
	public void aSampleRateMismatchIsOrange() {
		RecognitionProfile profile = searched(48000, new RecognitionRate(10, 10, 3, 3));
		assertEquals(ProfileState.ORANGE, ProfileState.of(true, profile, 44100));
		assertEquals(ProfileState.GREEN, ProfileState.of(true, profile, 0), "the rate is not known yet: the profile counts");
	}

	@Test
	public void sixtyPercentAndMoreIsGreenAndLessIsRed() {
		assertEquals(ProfileState.GREEN, ProfileState.of(true, searched(48000, new RecognitionRate(8, 10, 0, 3)), 48000), "8 of 13");
		assertEquals(ProfileState.GREEN, ProfileState.of(true, searched(48000, new RecognitionRate(3, 5, 0, 0)), 48000), "exactly 60 %");
		assertEquals(ProfileState.RED, ProfileState.of(true, searched(48000, new RecognitionRate(7, 10, 0, 3)), 48000), "7 of 13");
	}

	@Test
	public void aDifferentFrontEndDoesNotChangeTheState() {
		RecognitionProfile profile = searched(48000, new RecognitionRate(10, 10, 3, 3));
		assertEquals(ProfileState.GREEN, ProfileState.of(true, profile, 48000));
		// the warning sentence is a separate question: does the front end differ from the recording
		assertEquals(true, profile.getFrontEnd().differsFrom(2.0, false));
		assertEquals(true, profile.getFrontEnd().differsFrom(1.0, true));
		assertEquals(false, profile.getFrontEnd().differsFrom(1.0, false));
	}
}
