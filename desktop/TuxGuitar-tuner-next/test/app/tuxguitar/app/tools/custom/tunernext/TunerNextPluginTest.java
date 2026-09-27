package app.tuxguitar.app.tools.custom.tunernext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

import app.tuxguitar.util.TGContext;

/** T005: the evolved tuner keeps the legacy tuner's identity (research R19). */
public class TunerNextPluginTest {

	@Test
	public void keepsTheLegacyModuleIdAndAction() throws Exception {
		TunerNextPlugin plugin = new TunerNextPlugin();
		assertEquals("tuxguitar-tuner", plugin.getModuleId());
		assertEquals("tuner.tuner", plugin.getItemName());
	}

	/** Regression: reopening after closing reused the old window and its disposed string buttons. */
	@Test
	public void aClosedWindowIsNeverReused() {
		TunerNextPlugin plugin = new TunerNextPlugin();
		TGContext context = new TGContext();
		TunerWindow first = plugin.windowFor(context);
		assertFalse(first.isOpen());
		assertNotSame(first, plugin.windowFor(context));
	}
}
