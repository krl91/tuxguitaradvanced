package app.tuxguitar.audio.engine.nativeengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The version check of the native library (spec 026, T005, contracts/native-parameters.md "Version check"): a library without the tuning layout, or
 * with another one, is refused as missing, so the run falls back to the Java engine with the existing notice (the fallback itself is spec 023's,
 * covered by {@code RecognitionFallbackTest}).
 */
public class NativeLayoutCheckTest {

	@Test
	public void aLibraryWithoutTheLayoutIsTooOld() {
		NativeRecognitionException refused = assertThrows(NativeRecognitionException.class, () -> NativeRecognitionLibrary.checkLayout(() -> {
			throw new UnsatisfiedLinkError("tuningLayout");
		}));
		assertEquals(NativeRecognitionException.Reason.LIBRARY_MISSING, refused.getReason());
		assertEquals("native library too old: no tuning layout", refused.getDetail());
	}

	@Test
	public void aLibraryWithAnotherLayoutIsRefused() {
		int other = NativeParameters.TUNING_LENGTH + 3;
		NativeRecognitionException refused = assertThrows(NativeRecognitionException.class, () -> NativeRecognitionLibrary.checkLayout(() -> other));
		assertEquals(NativeRecognitionException.Reason.LIBRARY_MISSING, refused.getReason());
		assertEquals("native library tuning layout " + other + ", expected " + NativeParameters.TUNING_LENGTH, refused.getDetail());
	}

	@Test
	public void aLibraryWithTheSameLayoutIsAccepted() throws Exception {
		NativeRecognitionLibrary.checkLayout(() -> NativeParameters.TUNING_LENGTH);
	}
}
