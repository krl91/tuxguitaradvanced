package app.tuxguitar.app.tools.custom.practice.arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.AudioInputDescriptor.Kind;

/** The headphones advice of Arcade (spec 003 T026, FR-013): given for every input that could hear the speakers, nothing is decided from a device name. */
public class ArcadeAdviceTest {

	@Test
	public void aMicrophoneOfAnyKindOrAnUnknownInputGetsTheAdviceAndAnInstrumentInputDoesNot() {
		assertTrue(ArcadeAdvice.headphonesAdvised(Kind.BUILT_IN_MICROPHONE));
		assertTrue(ArcadeAdvice.headphonesAdvised(Kind.MICROPHONE));
		assertTrue(ArcadeAdvice.headphonesAdvised(Kind.UNKNOWN));
		assertFalse(ArcadeAdvice.headphonesAdvised(Kind.INSTRUMENT));
	}

	@Test
	public void noInputNoAdviceAndTheNameOfTheDeviceNeverMatters() {
		assertFalse(ArcadeAdvice.headphonesAdvised((AudioInputDescriptor) null));
		AudioInputDescriptor bluetooth = new AudioInputDescriptor("bt", "Bluetooth Headset", 1, 1, "Bluetooth Headset", Kind.UNKNOWN, false);
		AudioInputDescriptor interfaceInput = new AudioInputDescriptor("usb", "Bluetooth-named interface", 1, 2, "Interface", Kind.INSTRUMENT, false);
		assertTrue(ArcadeAdvice.headphonesAdvised(bluetooth));
		assertFalse(ArcadeAdvice.headphonesAdvised(interfaceInput), "the kind decides, not a name that looks like something");
		assertEquals(Kind.INSTRUMENT, interfaceInput.getKind());
	}
}
