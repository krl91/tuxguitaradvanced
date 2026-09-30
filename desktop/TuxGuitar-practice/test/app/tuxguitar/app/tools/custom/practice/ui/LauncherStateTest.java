package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LauncherStateTest {

	private static final String ITEM = "transport.practice";

	@Test
	public void anOpenWindowIsActiveAndCheckedWhateverElse() {
		for( boolean song : new boolean[] { true, false } ) {
			for( boolean track : new boolean[] { true, false } ) {
				LauncherState state = LauncherState.of(true, song, track);
				assertEquals(LauncherState.Kind.ACTIVE, state.getKind());
				assertTrue(state.isChecked());
				assertTrue(state.isEnabled());
				assertEquals(ITEM, state.tooltipKey(ITEM));
			}
		}
	}

	@Test
	public void withoutASongThereIsNothingToPractiseAndTheButtonSaysWhy() {
		LauncherState state = LauncherState.of(false, false, false);
		assertEquals(LauncherState.Kind.UNAVAILABLE, state.getKind());
		assertEquals(LauncherState.Reason.NO_SONG, state.getReason());
		assertFalse(state.isEnabled());
		assertFalse(state.isChecked());
		assertEquals(ITEM + ".no-song", state.tooltipKey(ITEM));
	}

	@Test
	public void aSongWithoutAPracticeTrackIsUnavailableWithItsOwnReason() {
		LauncherState state = LauncherState.of(false, true, false);
		assertEquals(LauncherState.Reason.NO_GUITAR_TRACK, state.getReason());
		assertFalse(state.isEnabled());
		assertEquals(ITEM + ".no-track", state.tooltipKey(ITEM));
	}

	@Test
	public void aSongWithATrackAndNoWindowIsInactiveButAvailable() {
		LauncherState state = LauncherState.of(false, true, true);
		assertEquals(LauncherState.Kind.INACTIVE, state.getKind());
		assertEquals(LauncherState.Reason.NONE, state.getReason());
		assertTrue(state.isEnabled());
		assertFalse(state.isChecked());
		assertEquals(ITEM, state.tooltipKey(ITEM));
	}
}
