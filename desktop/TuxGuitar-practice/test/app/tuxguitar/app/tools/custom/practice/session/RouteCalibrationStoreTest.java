package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.player.base.MidiPlayer;
import app.tuxguitar.util.TGContext;

/** The calibrations in the configuration (spec 002 T097) and the clicks through the player (FR-059), without a device. */
public class RouteCalibrationStoreTest {

	@Test
	public void theNameOfAPropertyForARouteIsReversibleWhateverTheNamesOfTheDevices() {
		AudioRoute route = new AudioRoute("Interface — entrée 2 (Écouteurs = ?)", "Haut-parleurs \"MacBook\" #1", 44100, "2ch 16bit | a=b");

		String name = RouteCalibrationStore.encodeName(route.key());

		assertTrue(name.startsWith("route."));
		assertTrue(name.matches("[A-Za-z0-9._-]+"), "a name a property file accepts: " + name);
		assertEquals(route, AudioRoute.parse(RouteCalibrationStore.decodeName(name)));
	}

	@Test
	public void aNameThatIsNotARouteIsIgnored() {
		assertNull(RouteCalibrationStore.decodeName("something.else"));
		assertNull(RouteCalibrationStore.decodeName("route.***not base64***"));
		assertNull(RouteCalibrationStore.decodeName(null));
	}

	@Test
	public void aClickThatCannotBePlayedSaysSoInsteadOfThrowingAndSilenceIsRepeatable() {
		MidiClickPlayer clicks = new MidiClickPlayer(MidiPlayer.getInstance(new TGContext()));

		assertEquals(Long.MIN_VALUE, clicks.click(), "no song, no percussion channel, no click");
		clicks.silence();
		clicks.silence();
		assertEquals(Long.MIN_VALUE, clicks.click());
	}
}
