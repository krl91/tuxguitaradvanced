package app.tuxguitar.audio.engine.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor.Kind;

public class InputDeviceCatalogTest {

	@Test
	public void positivelyClassifiesOnlyRecognizableBuiltInMicrophoneNames() {
		assertEquals(Kind.BUILT_IN_MICROPHONE, InputDeviceCatalog.classify("Micro MacBook Pro"));
		assertEquals(Kind.BUILT_IN_MICROPHONE, InputDeviceCatalog.classify("Built-in Microphone"));
		assertEquals(Kind.UNKNOWN, InputDeviceCatalog.classify("iPhone Microphone"));
		assertEquals(Kind.UNKNOWN, InputDeviceCatalog.classify("USB Audio CODEC"));
		assertEquals(Kind.UNKNOWN, InputDeviceCatalog.classify("Microsoft Teams Audio"));
	}

	/**
	 * Owner's product decision of 2026-10-07: an input is accepted, listed, refreshed and selectable whatever its name or its transport
	 * (built-in, USB, Bluetooth...) as long as Java Sound exposes a usable capture format. The name only gives a label: nothing is refused or
	 * hidden because a name looks like a Bluetooth device, and no latency is assumed from it.
	 */
	@Test
	public void aBluetoothInputIsLabelledLikeAnyOtherAndNeverRefused() {
		for( String name : new String[] { "AirPods Pro", "Bluetooth Headset", "Galaxy Buds2 (Bluetooth)", "JBL Tune 510BT", "Hands-Free AG Audio" } ) {
			assertEquals(Kind.UNKNOWN, InputDeviceCatalog.classify(name), name);
		}
	}
}
