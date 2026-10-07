package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.LatencyCompensation.Source;

/**
 * The calibration belongs to a route: an input, an output and an audio format (spec 002 T097, FR-057, FR-058). Changing any part loses it; nothing
 * is decided from the name or the transport of a device, a Bluetooth one included.
 */
public class RouteCalibrationsTest {

	private static final AudioRoute BUILT_IN = new AudioRoute("built-in microphone", "built-in speakers", 48000, "1ch 16bit");

	@Test
	public void aCalibrationIsFoundForItsOwnRouteOnly() {
		RouteCalibrations calibrations = new RouteCalibrations();
		calibrations.set(BUILT_IN, LatencyCompensation.calibrated(66.0));

		assertEquals(Source.CALIBRATED, calibrations.forRoute(BUILT_IN).source());
		assertEquals(66_000_000L, calibrations.forRoute(BUILT_IN).offsetNanos());
		assertFalse(calibrations.forRoute(new AudioRoute("USB interface", "built-in speakers", 48000, "1ch 16bit")).isCalibrated(), "another input");
		assertFalse(calibrations.forRoute(new AudioRoute("built-in microphone", "headphones", 48000, "1ch 16bit")).isCalibrated(), "another output");
		assertFalse(calibrations.forRoute(new AudioRoute("built-in microphone", "built-in speakers", 44100, "1ch 16bit")).isCalibrated(), "another sample rate");
		assertFalse(calibrations.forRoute(new AudioRoute("built-in microphone", "built-in speakers", 48000, "2ch 16bit")).isCalibrated(), "another profile");
		assertFalse(calibrations.forRoute(null).isCalibrated());
	}

	@Test
	public void aRouteThatChangedAndComesBackFindsItsCalibrationAgain() {
		RouteCalibrations calibrations = new RouteCalibrations();
		AudioRoute headset = new AudioRoute("wireless headset", "wireless headset", 16000, "1ch 16bit");
		calibrations.set(BUILT_IN, LatencyCompensation.calibrated(66.0));
		calibrations.set(headset, LatencyCompensation.manual(230.0));

		assertEquals(Source.MANUAL, calibrations.forRoute(headset).source());
		assertEquals(Source.CALIBRATED, calibrations.forRoute(BUILT_IN).source());
		assertEquals(2, calibrations.size());
	}

	@Test
	public void aWirelessRouteIsTreatedLikeAnyOtherAndMayBeCalibratedByHandOrByTaps() {
		RouteCalibrations calibrations = new RouteCalibrations();
		AudioRoute bluetooth = new AudioRoute("Bluetooth Headset", "Bluetooth Headset", 16000, "1ch 16bit");

		calibrations.set(bluetooth, LatencyCompensation.calibrated(240.0));

		assertEquals(240_000_000L, calibrations.forRoute(bluetooth).offsetNanos(), "no latency is deduced from a name, and none is refused");
	}

	@Test
	public void settingNoneOrForgettingRemovesTheCalibration() {
		RouteCalibrations calibrations = new RouteCalibrations();
		calibrations.set(BUILT_IN, LatencyCompensation.calibrated(66.0));
		calibrations.set(BUILT_IN, LatencyCompensation.none());
		assertFalse(calibrations.forRoute(BUILT_IN).isCalibrated());

		calibrations.set(BUILT_IN, LatencyCompensation.manual(10.0));
		calibrations.forget(BUILT_IN);
		assertFalse(calibrations.forRoute(BUILT_IN).isCalibrated());
		assertEquals(0, calibrations.size());
	}

	@Test
	public void theCalibrationsSurviveARunOfTheApplicationAndUnreadableOnesAreIgnored() {
		final Map<String, String> disk = new HashMap<String, String>();
		RouteCalibrations.Persistence persistence = new RouteCalibrations.Persistence() {
			public Map<String, String> load() {
				return new HashMap<String, String>(disk);
			}

			public void save(Map<String, String> entries) {
				disk.clear();
				disk.putAll(entries);
			}
		};
		RouteCalibrations first = new RouteCalibrations(persistence);
		first.set(BUILT_IN, LatencyCompensation.calibrated(66.5));
		first.set(new AudioRoute("a|b", "c%d", 44100, "p"), LatencyCompensation.manual(-12.0));
		disk.put("garbage", "NOT A COMPENSATION");
		disk.put(new AudioRoute("x", "y", 1, "z").key(), "CALIBRATED:abc");
		disk.put(new AudioRoute("n", "n", 1, "n").key(), "NONE:0.0");

		RouteCalibrations second = new RouteCalibrations(persistence);

		assertEquals(66_500_000L, second.forRoute(BUILT_IN).offsetNanos());
		assertEquals(-12_000_000L, second.forRoute(new AudioRoute("a|b", "c%d", 44100, "p")).offsetNanos(), "a separator or a percent sign in a name is kept");
		assertEquals(2, second.size(), "the garbage, the unreadable number and the 'none' entry are not calibrations");
	}

	@Test
	public void theKeyOfARouteIsReversibleAndDistinct() {
		AudioRoute route = new AudioRoute("in|put", "out%put", 48000, "p|r%o");
		assertEquals(route, AudioRoute.parse(route.key()));
		assertNotEquals(new AudioRoute("a|b", "c", 1, "d").key(), new AudioRoute("a", "b|c", 1, "d").key());
		assertTrue(route.toString().contains("48000"));
	}
}
