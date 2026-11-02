package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;

/** Spec 022 (T070, FR-019): renaming a sound carries it over for every score that remembered it, not only the score in front. */
public class AmpSoundRenameTest {

	@Test
	public void everyScoreThatRememberedASoundFollowsItsNewName() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		Optional<ScoreKey> a = ScoreKey.of("Song A", "Band", "a.gp");
		Optional<ScoreKey> b = ScoreKey.of("Song B", "Band", "b.gp");
		store.saveAs("One", ReadyMadeChains.CRUNCH);
		store.remember(a, "One");
		store.remember(b, "Lead");
		store.rename("One", "Two");
		assertEquals("Two", new AmpSoundStore(memory).soundFor(a).name(), "after a restart too");
		store.rename("Two", "Three");
		assertEquals("Three", new AmpSoundStore(memory).soundFor(a).name(), "renamed twice");
		assertEquals("Lead", store.soundFor(b).name(), "the other scores keep theirs");
	}

	@Test
	public void aNewSoundUnderTheOldNameTakesTheOldNameBack() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		Optional<ScoreKey> a = ScoreKey.of("Song A", "Band", "a.gp");
		store.saveAs("One", ReadyMadeChains.CRUNCH);
		store.remember(a, "One");
		store.rename("One", "Two");
		store.saveAs("One", ReadyMadeChains.LEAD);
		assertEquals("One", store.soundFor(a).name(), "the name is used again on purpose");
	}

	@Test
	public void aRenamedThenDeletedSoundGivesTheLastOne() {
		AmpSoundSettingsTest.Memory memory = new AmpSoundSettingsTest.Memory();
		AmpSoundStore store = new AmpSoundStore(memory);
		Optional<ScoreKey> a = ScoreKey.of("Song A", "Band", "a.gp");
		store.saveAs("One", ReadyMadeChains.CRUNCH);
		store.remember(a, "One");
		store.rename("One", "Two");
		store.remember(Optional.<ScoreKey>empty(), "Crunch");
		store.delete("Two");
		assertEquals("Crunch", store.soundFor(a).name());
	}
}
