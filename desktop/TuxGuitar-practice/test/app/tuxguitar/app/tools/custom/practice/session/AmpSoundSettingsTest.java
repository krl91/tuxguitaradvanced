package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.AmpSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;

/** Spec 022 (T020, FR-009, SC-007): the settings of the amp window are kept from one launch to the next, and a bad value never gives an error. */
public class AmpSoundSettingsTest {

	static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();

		public String get(String key) {
			return this.values.get(key);
		}

		public void set(String key, String value) {
			this.values.put(key, value);
		}
	}

	@Test
	public void aFreshStoreGivesTheDefaults() {
		AmpSoundStore store = new AmpSoundStore(new Memory());
		AmpSoundSettings settings = store.settings();
		assertFalse(settings.enabled(), "the sound is off at first");
		assertEquals("", settings.output(), "the system's default output");
		assertEquals(0.7, settings.guitarVolume(), 1e-9);
		assertNull(settings.windowBounds());
		assertEquals(ReadyMadeChains.CLEAN, store.currentChain());
	}

	@Test
	public void everyValueWrittenIsFoundAgainByANewStore() {
		Memory memory = new Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		store.save(store.settings().withEnabled(true).withOutput("Casque USB").withGuitarVolume(0.45).withWindowBounds(new int[] { 10, 20, 400, 500 }));
		store.saveCurrentChain(ReadyMadeChains.LEAD.withAmp(ReadyMadeChains.LEAD.amp().withCharacter(AmpSettings.Character.CRUNCH).withGain(3)));

		AmpSoundStore again = new AmpSoundStore(memory);
		AmpSoundSettings settings = again.settings();
		assertTrue(settings.enabled());
		assertEquals("Casque USB", settings.output());
		assertEquals(0.45, settings.guitarVolume(), 1e-9);
		assertArrayEquals(new int[] { 10, 20, 400, 500 }, settings.windowBounds());
		assertEquals(ReadyMadeChains.LEAD.withAmp(ReadyMadeChains.LEAD.amp().withCharacter(AmpSettings.Character.CRUNCH).withGain(3)), again.currentChain());
		for (String key : new String[] { "enabled", "output", "guitar.volume", "chain.current", "window.bounds" }) {
			assertTrue(memory.values.containsKey(key), "kept under " + key);
		}
	}

	@Test
	public void aMalformedStoredChainFallsBackToClean() {
		Memory memory = new Memory();
		memory.values.put("chain.current", "amp=loud;;;");
		assertEquals(ReadyMadeChains.CLEAN, new AmpSoundStore(memory).currentChain());
	}

	@Test
	public void badValuesGiveTheDefaultOrTheValueWithinRange() {
		Memory memory = new Memory();
		memory.values.put("enabled", "maybe");
		memory.values.put("guitar.volume", "3.5");
		memory.values.put("window.bounds", "1,2,three");
		AmpSoundSettings settings = new AmpSoundStore(memory).settings();
		assertFalse(settings.enabled());
		assertEquals(1.0, settings.guitarVolume(), 1e-9, "brought back into 0 to 1");
		assertNull(settings.windowBounds());
		memory.values.put("guitar.volume", "abc");
		assertEquals(0.7, new AmpSoundStore(memory).settings().guitarVolume(), 1e-9);
	}
}
