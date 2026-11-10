package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.tuxguitar.audio.engine.monitor.ChainSettings;
import app.tuxguitar.audio.engine.monitor.ReadyMadeChains;
import app.tuxguitar.audio.engine.monitor.SoundFile;

/**
 * Spec 027 (T044, FR-023, contracts/sound-file.md "Import"), no window: a sound file imported into {@link AmpSoundStore} is added; the name of an own
 * sound changes nothing until the learner chooses to replace it or keep both ("<name> 2"); a ready-made name gives "<name> (imported)"; a file of a newer
 * format, or not a sound file, is refused with its problem and changes nothing. The same rule as GuitarFX's {@code sounds.rs}.
 */
public class AmpSoundImportTest {

	private static final ChainSettings MINE = ReadyMadeChains.CRUNCH.withAmp(ReadyMadeChains.CRUNCH.amp().withGain(9));

	@TempDir
	Path folder;

	private Path file(String name, ChainSettings chain) throws Exception {
		Path file = this.folder.resolve(name.replace(' ', '-') + "." + SoundFile.EXTENSION);
		SoundFile.write(file, name, chain);
		return file;
	}

	@Test
	public void aNewNameIsAdded() throws Exception {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		AmpSoundImport.Result result = AmpSoundImport.importFile(store, file("My lead", MINE));
		assertEquals(AmpSoundImport.Outcome.ADDED, result.outcome());
		assertEquals("My lead", result.name());
		assertEquals(MINE, store.sound("my lead").chain());
		assertEquals(5, store.sounds().size());
	}

	@Test
	public void anOwnNameAsksThenReplacesOrKeepsBoth() throws Exception {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		store.saveAs("Mine", ReadyMadeChains.LEAD);
		AmpSoundImport.Result result = AmpSoundImport.importFile(store, file("MINE", MINE));
		assertEquals(AmpSoundImport.Outcome.CLASH, result.outcome());
		assertEquals("Mine", result.name(), "the own sound's name, as listed");
		assertEquals(ReadyMadeChains.LEAD, store.sound("Mine").chain(), "nothing is overwritten before the learner answers");
		assertEquals(5, store.sounds().size());
		assertEquals("MINE 2", AmpSoundImport.keepBoth(store, result.sound()), "the imported sound keeps its own name");
		assertEquals("MINE 3", AmpSoundImport.keepBoth(store, result.sound()));
		assertEquals(MINE, store.sound("Mine 2").chain());
		assertEquals("Mine", AmpSoundImport.replace(store, result.sound()));
		assertEquals(MINE, store.sound("Mine").chain());
		assertEquals(7, store.sounds().size());
	}

	@Test
	public void aReadyMadeNameIsImportedAsNameImported() throws Exception {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		AmpSoundImport.Result result = AmpSoundImport.importFile(store, file("crunch", MINE));
		assertEquals(AmpSoundImport.Outcome.ADDED, result.outcome());
		assertEquals("Crunch (imported)", result.name());
		assertEquals(ReadyMadeChains.CRUNCH, store.sound("Crunch").chain(), "the ready-made sound is unchanged");
		assertEquals(AmpSoundImport.Outcome.CLASH, AmpSoundImport.importFile(store, file("Crunch", MINE)).outcome());
	}

	@Test
	public void keepBothShortensALongNameToFit() throws Exception {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		String longName = new String(new char[AmpSoundStore.NAME_LENGTH]).replace('\0', 'x');
		store.saveAs(longName, MINE);
		AmpSoundImport.Result result = AmpSoundImport.importFile(store, file(longName, MINE));
		String kept = AmpSoundImport.keepBoth(store, result.sound());
		assertEquals(AmpSoundStore.NAME_LENGTH, kept.length());
		assertEquals(" 2", kept.substring(kept.length() - 2));
	}

	@Test
	public void aNewerOrDamagedFileIsRefusedAndChangesNothing() throws Exception {
		AmpSoundStore store = new AmpSoundStore(new AmpSoundSettingsTest.Memory());
		Path newer = this.folder.resolve("newer.guitarfx");
		Files.write(newer, "# GuitarFX sound\nformat=2\nname=Later\nchain=amp:clean,3,5,5,6,6|cab:on\n".getBytes(StandardCharsets.UTF_8));
		AmpSoundImport.Result result = AmpSoundImport.importFile(store, newer);
		assertEquals(AmpSoundImport.Outcome.REFUSED, result.outcome());
		assertEquals(SoundFile.Problem.NEWER, result.problem());
		Path damaged = this.folder.resolve("damaged.guitarfx");
		Files.write(damaged, "format=1\nname=Broken\nchain=amp:nothing\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(SoundFile.Problem.NOT_A_SOUND_FILE, AmpSoundImport.importFile(store, damaged).problem());
		assertEquals(4, store.sounds().size());
		assertNull(store.sound("Later"));
	}
}
