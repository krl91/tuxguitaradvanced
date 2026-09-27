package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** T005: the evolved tuner keeps the legacy tuner's identity (research R19). */
public class TunerNextPluginTest {

	@Test
	public void keepsTheLegacyModuleIdAndAction() throws Exception {
		TunerNextPlugin plugin = new TunerNextPlugin();
		assertEquals("tuxguitar-tuner", plugin.getModuleId());
		assertEquals("tuner.tuner", plugin.getItemName());
	}
}
