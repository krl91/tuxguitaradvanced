package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.ProfileLibrary;
import app.tuxguitar.audio.engine.calibration.ProfilePersistence;
import app.tuxguitar.audio.engine.calibration.ProfileState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;

/**
 * Spec 013 (T041): the dot next to the device chooser, its text, its warning and the two buttons, on a headless toolkit. Every state has its text (grey,
 * orange, green, red); a different noise reduction or gain keeps the colour and shows the warning while the profile stays in use; another sample rate gives
 * orange with its reason; Forget is enabled only for a device with a profile, asks first, and deletes only that device's profile.
 */
public class ProfileDotTest {

	private static final int RATE = 48000;
	private static final DeviceKey MIC = DeviceKey.of("Micro MacBook Pro", null, 0);
	private static final DeviceKey INTERFACE = DeviceKey.of("USB Interface", 0, 0);
	private static final CalibrationWindow.Texts TEXTS = (key, arguments) -> key + (arguments.length == 0 ? "" : Arrays.toString(arguments));

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private final class Host implements ProfileDot.Host {
		DeviceKey selected = MIC;
		int rate = RATE;
		FrontEndState frontEnd = new FrontEndState(1.0, false);
		boolean confirms = true;
		int confirmations;
		int calibrations;

		public DeviceKey selectedKey() {
			return this.selected;
		}

		public int sampleRate() {
			return this.rate;
		}

		public FrontEndState frontEnd() {
			return this.frontEnd;
		}

		public void confirmForget(Runnable yes) {
			this.confirmations++;
			if (this.confirms) {
				yes.run();
			}
		}

		public void calibrate() {
			this.calibrations++;
		}
	}

	private final SwtContractToolkit toolkit = new SwtContractToolkit();
	private final ProfileLibrary library = new ProfileLibrary(new Memory());
	private final Host host = new Host();
	private ProfileDot dot;

	private RecognitionProfile profile(DeviceKey key, int rate, RecognitionRate found, FrontEndState frontEnd, RecognitionProfile.Origin origin) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, RecognitionParameters.NOTE_ACCEPT.getMinimum());
		return new RecognitionProfile(key, rate, 1L, values, found, new RecognitionRate(5, 10, 1, 3), frontEnd, origin);
	}

	private void save(DeviceKey key, int rate, RecognitionRate found, FrontEndState frontEnd) {
		assertTrue(this.library.save(profile(key, rate, found, frontEnd, RecognitionProfile.Origin.SEARCHED)));
	}

	private ProfileDot dot() {
		if (this.dot == null) {
			this.dot = new ProfileDot(this.toolkit.factory(), this.toolkit.factory().createPanel(this.toolkit.factory().createWindow(), false), TEXTS, this.library,
					this.host);
		}
		this.dot.refresh();
		return this.dot;
	}

	private static String text(Object widget) {
		Object text = SwtContractToolkit.state(widget).properties.get("Text");
		return text == null ? "" : text.toString();
	}

	private static boolean enabled(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Enabled") != Boolean.FALSE;
	}

	private static boolean visible(Object widget) {
		return SwtContractToolkit.state(widget).properties.get("Visible") != Boolean.FALSE;
	}

	@Test
	public void noInputGivesTheGreyDotItsTextAndDisablesBothButtons() {
		this.host.selected = null;
		ProfileDot d = dot();
		assertEquals(ProfileState.NONE, d.state());
		assertEquals("practice.profile.dot.none", text(d.textLabel()));
		assertFalse(enabled(d.calibrateButton()));
		assertFalse(enabled(d.forgetButton()));
		assertFalse(visible(d.warningLabel()));
	}

	@Test
	public void aDeviceNeverCalibratedIsOrangeWithItsTextAndCanBeCalibratedButNotForgotten() {
		ProfileDot d = dot();
		assertEquals(ProfileState.ORANGE, d.state());
		assertEquals("practice.profile.dot.orange", text(d.textLabel()));
		assertTrue(enabled(d.calibrateButton()));
		assertFalse(enabled(d.forgetButton()));
	}

	@Test
	public void aCalibratedDeviceAtSixtyPercentOrMoreIsGreenWithItsRate() {
		save(MIC, RATE, new RecognitionRate(6, 10, 0, 0), new FrontEndState(1.0, false));
		ProfileDot d = dot();
		assertEquals(ProfileState.GREEN, d.state());
		assertEquals("practice.profile.dot.green[60]", text(d.textLabel()));
		assertTrue(enabled(d.forgetButton()));
	}

	@Test
	public void aCalibratedDeviceUnderSixtyPercentIsRedWithItsRate() {
		save(MIC, RATE, new RecognitionRate(5, 10, 0, 0), new FrontEndState(1.0, false));
		ProfileDot d = dot();
		assertEquals(ProfileState.RED, d.state());
		assertEquals("practice.profile.dot.red[50]", text(d.textLabel()));
	}

	@Test
	public void theDotFollowsTheSelectedDeviceAtEachRefresh() {
		save(MIC, RATE, new RecognitionRate(9, 10, 0, 0), new FrontEndState(1.0, false));
		assertEquals(ProfileState.GREEN, dot().state());
		this.host.selected = INTERFACE;
		assertEquals(ProfileState.ORANGE, dot().state());
		this.host.selected = null;
		assertEquals(ProfileState.NONE, dot().state());
		this.host.selected = MIC;
		assertEquals(ProfileState.GREEN, dot().state());
	}

	@Test
	public void aDifferentNoiseReductionKeepsTheColourShowsTheWarningAndTheWarningGoesWhenItIsBackAsRecorded() {
		save(MIC, RATE, new RecognitionRate(9, 10, 0, 0), new FrontEndState(1.0, true));
		this.host.frontEnd = new FrontEndState(1.0, false);
		ProfileDot d = dot();
		assertEquals(ProfileState.GREEN, d.state(), "the colour is kept");
		assertTrue(visible(d.warningLabel()));
		assertEquals("practice.profile.dot.warning.noise[practice.profile.dot.on]", text(d.warningLabel()));
		this.host.frontEnd = new FrontEndState(1.0, true);
		d = dot();
		assertFalse(visible(d.warningLabel()), "back as at the recording: no warning");
	}

	@Test
	public void aDifferentGainKeepsTheColourAndShowsItsOwnWarning() {
		save(MIC, RATE, new RecognitionRate(5, 10, 0, 0), new FrontEndState(2.0, false));
		this.host.frontEnd = new FrontEndState(1.0, false);
		ProfileDot d = dot();
		assertEquals(ProfileState.RED, d.state());
		assertTrue(visible(d.warningLabel()));
		assertEquals("practice.profile.dot.warning.gain", text(d.warningLabel()));
	}

	@Test
	public void anotherSampleRateGivesOrangeWithItsReasonAndNoWarning() {
		save(MIC, 44100, new RecognitionRate(9, 10, 0, 0), new FrontEndState(1.0, false));
		ProfileDot d = dot();
		assertEquals(ProfileState.ORANGE, d.state());
		assertEquals("practice.profile.dot.other-rate[44100, 48000]", text(d.textLabel()));
		assertTrue(enabled(d.forgetButton()), "the profile exists, it can be forgotten");
	}

	@Test
	public void aDefaultChosenProfileIsOrangeAndCanBeForgotten() {
		assertTrue(this.library.save(profile(MIC, RATE, new RecognitionRate(0, 0, 0, 0), new FrontEndState(1.0, false), RecognitionProfile.Origin.DEFAULT_CHOSEN)));
		ProfileDot d = dot();
		assertEquals(ProfileState.ORANGE, d.state());
		assertEquals("practice.profile.dot.orange", text(d.textLabel()));
		assertTrue(enabled(d.forgetButton()));
	}

	@Test
	public void forgetAsksFirstThenDeletesOnlyThatDevicesProfile() {
		save(MIC, RATE, new RecognitionRate(9, 10, 0, 0), new FrontEndState(1.0, false));
		save(INTERFACE, RATE, new RecognitionRate(8, 10, 0, 0), new FrontEndState(1.0, false));
		ProfileDot d = dot();
		SwtContractToolkit.userSelects(d.forgetButton());
		assertEquals(1, this.host.confirmations);
		assertNull(this.library.get(MIC));
		assertNotNull(this.library.get(INTERFACE), "no other device changes");
		assertEquals(ProfileState.ORANGE, d.state());
		assertFalse(enabled(d.forgetButton()));
	}

	@Test
	public void aRefusedConfirmationKeepsTheProfile() {
		save(MIC, RATE, new RecognitionRate(9, 10, 0, 0), new FrontEndState(1.0, false));
		this.host.confirms = false;
		ProfileDot d = dot();
		SwtContractToolkit.userSelects(d.forgetButton());
		assertEquals(1, this.host.confirmations);
		assertNotNull(this.library.get(MIC));
		assertEquals(ProfileState.GREEN, d.state());
	}

	@Test
	public void calibrateOpensTheCalibration() {
		ProfileDot d = dot();
		SwtContractToolkit.userSelects(d.calibrateButton());
		assertEquals(1, this.host.calibrations);
	}

	@Test
	public void theDotHasAColourPerState() {
		ProfileDot d = dot();
		Object orange = SwtContractToolkit.state(d.dotLabel()).properties.get("FgColor");
		save(MIC, RATE, new RecognitionRate(9, 10, 0, 0), new FrontEndState(1.0, false));
		d = dot();
		Object green = SwtContractToolkit.state(d.dotLabel()).properties.get("FgColor");
		save(MIC, RATE, new RecognitionRate(1, 10, 0, 0), new FrontEndState(1.0, false));
		d = dot();
		Object red = SwtContractToolkit.state(d.dotLabel()).properties.get("FgColor");
		this.host.selected = null;
		d = dot();
		Object grey = SwtContractToolkit.state(d.dotLabel()).properties.get("FgColor");
		java.util.Set<Object> colours = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
		colours.addAll(Arrays.asList(orange, green, red, grey));
		assertEquals(4, colours.size(), "four different colours");
	}
}
