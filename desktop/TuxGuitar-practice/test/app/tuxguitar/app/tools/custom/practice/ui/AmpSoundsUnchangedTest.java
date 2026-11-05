package app.tuxguitar.app.tools.custom.practice.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.app.tools.custom.practice.session.AmpSoundSettings;
import app.tuxguitar.app.tools.custom.practice.session.AmpSoundStore;
import app.tuxguitar.app.tools.custom.practice.session.ArcadeChoices;
import app.tuxguitar.app.tools.custom.practice.session.Sound;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;

/**
 * The learner's amp settings and sounds are kept across the new layout of the amp window (spec 025 T024, FR-012): settings written as spec 022 writes them
 * (the switch on, an output, the guitar volume, the kept bounds, the chain in use, an own sound current) load with the same values and chains.
 */
public class AmpSoundsUnchangedTest {

	private static final class Memory implements ArcadeChoices.Persistence {
		final Map<String, String> values = new HashMap<String, String>();

		public String get(String key) {
			return this.values.get(key);
		}

		public void set(String key, String value) {
			this.values.put(key, value);
		}
	}

	@Test
	public void settingsWrittenBeforeLoadTheSame() {
		Memory memory = new Memory();
		// the keys and the text of spec 022's store, as a learner's configuration has them
		memory.values.put("enabled", "true");
		memory.values.put("output", "Headphones");
		memory.values.put("guitar.volume", "0.55");
		memory.values.put("window.bounds", "150,160,500,420");
		memory.values.put("sound.0.name", "blues");
		memory.values.put("sound.0.chain", ReadyMadeChains.LEAD.toText());
		memory.values.put("sound.current", "blues");
		memory.values.put("chain.current", ReadyMadeChains.LEAD.toText());

		AmpSoundStore store = new AmpSoundStore(memory);
		AmpSoundSettings settings = store.settings();
		assertTrue(settings.enabled());
		assertEquals("Headphones", settings.output());
		assertEquals(0.55, settings.guitarVolume(), 1e-9);
		assertArrayEquals(new int[] { 150, 160, 500, 420 }, settings.windowBounds());
		assertEquals("blues", store.current().name());
		assertEquals(ReadyMadeChains.LEAD, store.currentChain());
		List<String> names = new ArrayList<String>();
		for (Sound sound : store.sounds()) {
			names.add(sound.name());
		}
		assertEquals(List.of("Clean", "Crunch", "Lead", "Clean echo", "blues"), names);
		assertEquals(ReadyMadeChains.LEAD, store.sound("blues").chain());
	}
}
