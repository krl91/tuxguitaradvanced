package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Spec 022 (T071, research R6): a stored value that cannot be read is logged once per key, then the default is used. */
public class AmpSoundStoreLogTest {

	@Test
	public void anUnreadableValueIsLoggedOncePerKey() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		memory.values.put("chain.current", "not a chain");
		memory.values.put("ready.Lead.chain", "amp:louder");
		memory.values.put("sound.current", "Nowhere");
		List<String> lines = new ArrayList<String>();
		AmpSoundStore store = new AmpSoundStore(memory, lines::add);
		for (int i = 0; i < 3; i++) {
			store.currentChain();
			store.sounds();
			store.current();
		}
		assertEquals(3, lines.size(), lines.toString());
		assertTrue(lines.get(0).contains("chain.current") || lines.get(1).contains("chain.current") || lines.get(2).contains("chain.current"), lines.toString());
		for (String line : lines) {
			assertTrue(line.startsWith("TuxGuitar amp sound:"), line);
		}
	}

	@Test
	public void goodValuesLogNothing() {
		List<String> lines = new ArrayList<String>();
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory(), lines::add);
		store.saveAs("Mine", store.currentChain());
		store.setCurrent("Mine");
		store.sounds();
		store.current();
		assertTrue(lines.isEmpty(), lines.toString());
	}
}
