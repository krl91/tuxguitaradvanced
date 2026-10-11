package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** The default input of the learner (owner, 2026-10-11): remembered, taken back when it comes back, never forced while it is away. */
public class InputDefaultPolicyTest {

	@Test
	public void theRememberedInputIsSelectedWhenTheListIsFirstReadAndItIsThere() {
		assertEquals("usb", InputDefaultPolicy.choose(Arrays.asList("mic", "usb"), "mic", "usb", false));
		assertEquals("usb", InputDefaultPolicy.choose(Arrays.asList("mic", "usb"), null, "usb", false));
	}

	@Test
	public void nothingChangesWhenTheRememberedInputIsAlreadyTheCurrentOneOrNothingIsRemembered() {
		assertNull(InputDefaultPolicy.choose(Arrays.asList("mic", "usb"), "usb", "usb", false));
		assertNull(InputDefaultPolicy.choose(Arrays.asList("mic", "usb"), "mic", null, false));
		assertNull(InputDefaultPolicy.choose(Collections.<String>emptyList(), null, "usb", false));
	}

	@Test
	public void whenTheRememberedInputIsAwayTheOthersAreLeftAsTheyAre() {
		assertNull(InputDefaultPolicy.choose(Arrays.asList("mic"), "mic", "usb", false));
		assertNull(InputDefaultPolicy.choose(Arrays.asList("mic"), null, "usb", true));
	}

	@Test
	public void aChoiceOfTheLearnerIsKeptWhileTheRememberedInputWasThereAlready() {
		assertNull(InputDefaultPolicy.choose(Arrays.asList("mic", "usb"), "mic", "usb", true));
	}

	@Test
	public void theRememberedInputIsSelectedAgainWhenItAppearsAfterAbsence() {
		// absent at the previous reading (wasThere false), present now, another input selected meanwhile
		assertEquals("usb", InputDefaultPolicy.choose(Arrays.asList("mic", "usb"), "mic", "usb", false));
	}
}
