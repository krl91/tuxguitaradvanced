package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The setting of the stage flames (spec 009 FR-008, T004): discreet by default, off or full on request, and a missing value is refused rather than
 * silently turned into another one.
 */
public class FollowSettingsFlamesTest {

	@Test
	public void theFlamesAreDiscreetByDefault() {
		assertEquals(FlameSetting.DISCREET, new FollowSettings().getFlames());
	}

	@Test
	public void eachSettingCanBeSetAndReadBack() {
		FollowSettings settings = new FollowSettings();
		for (FlameSetting setting : FlameSetting.values()) {
			settings.setFlames(setting);
			assertEquals(setting, settings.getFlames());
		}
		assertEquals(3, FlameSetting.values().length, "off, discreet and full: nothing else");
	}

	@Test
	public void aMissingValueIsRefusedAndTheSettingIsKept() {
		FollowSettings settings = new FollowSettings();
		settings.setFlames(FlameSetting.FULL);
		assertThrows(IllegalArgumentException.class, () -> settings.setFlames(null));
		assertEquals(FlameSetting.FULL, settings.getFlames());
	}
}
