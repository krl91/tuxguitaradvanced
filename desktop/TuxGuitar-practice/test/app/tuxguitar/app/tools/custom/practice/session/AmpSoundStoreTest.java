package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;

/**
 * Spec 022 (T043, FR-008, FR-009, FR-019, contracts/amp-window.md "AmpSoundStore", owner's answer of 2026-11-02): the ready-made and own sounds, their names,
 * restore, and the sound of each score; a bad value never gives an error. The settings part is covered by {@link AmpSoundSettingsTest}.
 */
public class AmpSoundStoreTest {

	private static final ChainSettings MINE = ReadyMadeChains.CRUNCH.withAmp(ReadyMadeChains.CRUNCH.amp().withGain(9));
	private static final ChainSettings OTHER = ReadyMadeChains.CLEAN.withAmp(ReadyMadeChains.CLEAN.amp().withTreble(2));

	private static List<String> names(AmpSoundStore store) {
		List<String> names = new ArrayList<String>();
		for (Sound sound : store.sounds()) {
			names.add(sound.name());
		}
		return names;
	}

	@Test
	public void aFreshStoreGivesCleanAndTheFourReadyMadeSounds() {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo"), names(store));
		for (Sound sound : store.sounds()) {
			assertTrue(sound.readyMade());
			assertFalse(sound.changed());
			assertEquals(ReadyMadeChains.named(sound.name()), sound.chain());
		}
		assertEquals("Clean", store.current().name());
		assertFalse(store.currentChanged());
	}

	@Test
	public void anOwnSoundIsKeptListedAfterTheReadyMadeOnesByNameAndReplacedUnderItsOwnName() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		store.saveAs("  Zeppelin ", MINE);
		store.saveAs("blues", OTHER);
		AmpSoundStore again = new AmpSoundStore(memory);
		assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo", "blues", "Zeppelin"), names(again), "trimmed, by name regardless of case");
		assertEquals(MINE, again.sound("zeppelin").chain(), "found regardless of case");
		assertFalse(again.sound("Zeppelin").readyMade());
		again.saveAs("ZEPPELIN", OTHER);
		assertEquals(6, again.sounds().size(), "the same name regardless of case replaces it");
		assertEquals(OTHER, again.sound("Zeppelin").chain());
	}

	@Test
	public void namesAreOneToFortyCharactersAndNeverAReadyMadeName() {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		assertEquals(AmpSoundStore.NameProblem.EMPTY, store.nameProblem("   ", null));
		assertEquals(AmpSoundStore.NameProblem.TOO_LONG, store.nameProblem(repeat('a', 41), null));
		assertNull(store.nameProblem(repeat('a', 40), null));
		assertEquals(AmpSoundStore.NameProblem.READY_MADE, store.nameProblem(" lead ", null));
		assertThrows(IllegalArgumentException.class, () -> store.saveAs("Clean echo", MINE));
		assertThrows(IllegalArgumentException.class, () -> store.saveAs("", MINE));
		assertEquals(4, store.sounds().size());
	}

	private static String repeat(char c, int count) {
		char[] chars = new char[count];
		Arrays.fill(chars, c);
		return new String(chars);
	}

	@Test
	public void renameAndDeleteApplyToOwnSoundsOnly() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		store.saveAs("One", MINE);
		store.saveAs("Two", OTHER);
		store.rename("One", "Three");
		assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo", "Three", "Two"), names(new AmpSoundStore(memory)));
		assertThrows(IllegalArgumentException.class, () -> store.rename("Three", "two"), "taken by another sound");
		assertThrows(IllegalArgumentException.class, () -> store.rename("Lead", "Mine"), "a ready-made sound keeps its name");
		assertThrows(IllegalArgumentException.class, () -> store.delete("Crunch"));
		store.rename("Three", "three");
		assertEquals("three", store.sound("THREE").name(), "a change of case is allowed");
		store.delete("three");
		assertEquals(Arrays.asList("Clean", "Crunch", "Lead", "Clean echo", "Two"), names(new AmpSoundStore(memory)));
		assertEquals(OTHER, new AmpSoundStore(memory).sound("Two").chain(), "the others keep their chains");
	}

	@Test
	public void aChangedReadyMadeSoundStaysChangedUntilItIsRestored() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		store.setCurrent("Lead");
		ChainSettings changed = ReadyMadeChains.LEAD.withAmp(ReadyMadeChains.LEAD.amp().withGain(2));
		store.saveCurrentChain(changed);
		store.setCurrent("Clean");
		AmpSoundStore again = new AmpSoundStore(memory);
		assertTrue(again.sound("Lead").changed(), "kept on the sound after another was picked and a restart");
		assertEquals(changed, again.sound("Lead").chain());
		again.setCurrent("Lead");
		assertEquals(changed, again.currentChain(), "picking it again gives its changes");
		again.restore("Lead");
		assertFalse(again.sound("Lead").changed());
		assertEquals(ReadyMadeChains.LEAD, again.sound("Lead").chain());
		assertEquals(ReadyMadeChains.LEAD, again.currentChain(), "the current chain is restored too");
		assertFalse(new AmpSoundStore(memory).sound("Lead").changed());
	}

	@Test
	public void anOwnSoundsChangesStayInTheCurrentChainUntilSavedAndArePickedOver() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		store.saveAs("Mine", MINE);
		store.setCurrent("Mine");
		assertEquals(MINE, store.currentChain(), "picking a sound sets the current chain to its chain");
		ChainSettings edited = MINE.withAmp(MINE.amp().withVolume(3));
		store.saveCurrentChain(edited);
		AmpSoundStore again = new AmpSoundStore(memory);
		assertEquals("Mine", again.current().name());
		assertTrue(again.currentChanged(), "kept at closing, shown as changed in a new store");
		assertEquals(MINE, again.sound("Mine").chain(), "the sound itself is unchanged");
		again.setCurrent("Crunch");
		again.setCurrent("Mine");
		assertEquals(MINE, again.currentChain(), "picking another sound dropped the changes");
		assertFalse(again.currentChanged());
	}

	@Test
	public void eachScoreGetsItsRememberedSoundElseTheLastOne() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		Optional<ScoreKey> a = ScoreKey.of("Song A", "Band", "a.gp");
		Optional<ScoreKey> b = ScoreKey.of("Song B", "Band", "b.gp");
		store.remember(a, "Lead");
		store.remember(b, "Clean");
		AmpSoundStore again = new AmpSoundStore(memory);
		assertEquals("Lead", again.soundFor(a).name());
		assertEquals("Clean", again.soundFor(b).name());
		assertEquals("Clean", again.soundFor(ScoreKey.of("Never", "Played", "n.gp")).name(), "an unknown score gets the last sound used");
		again.remember(Optional.<ScoreKey>empty(), "Crunch");
		assertEquals("Crunch", again.soundFor(ScoreKey.of("Never", "Played", "n.gp")).name(), "the last sound used");
		assertEquals("Crunch", again.soundFor(Optional.<ScoreKey>empty()).name());
		for (String key : memory.values.keySet()) {
			assertFalse(key.toLowerCase().contains("song"), "no title in the settings: " + key);
		}
	}

	@Test
	public void aDeletedRememberedSoundGivesTheLastOneAndNothingFails() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		Optional<ScoreKey> a = ScoreKey.of("Song A", "Band", "a.gp");
		store.saveAs("Mine", MINE);
		store.remember(a, "Mine");
		store.remember(ScoreKey.of("Song B", "Band", "b.gp"), "Lead");
		store.delete("Mine");
		assertEquals("Lead", new AmpSoundStore(memory).soundFor(a).name());
	}

	@Test
	public void aMalformedStoredValueFallsBackAndNeverFails() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		memory.values.put("ready.Lead.chain", "amp:louder|garbage");
		memory.values.put("sound.0.name", "Broken");
		memory.values.put("sound.0.chain", "not a chain");
		memory.values.put("sound.current", "Nowhere");
		memory.values.put("sound.last", "Gone too");
		AmpSoundStore store = new AmpSoundStore(memory);
		assertEquals(ReadyMadeChains.LEAD, store.sound("Lead").chain(), "the ready-made sound of that name");
		assertFalse(store.sound("Lead").changed());
		assertEquals(ReadyMadeChains.CLEAN, store.sound("Broken").chain(), "else Clean");
		assertEquals("Clean", store.current().name(), "an unknown current sound gives Clean");
		assertEquals("Clean", store.soundFor(ScoreKey.of("X", "Y", "z")).name());
	}
}
