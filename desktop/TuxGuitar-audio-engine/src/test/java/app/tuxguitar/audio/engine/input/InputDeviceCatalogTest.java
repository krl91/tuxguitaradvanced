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
}
