package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;

/** Persistence, repair on load, restore and notification of the recognition settings (T102). */
public class RecognitionSettingsStoreTest {

	/** An in-memory configuration file. */
	static final class MemoryPersistence implements RecognitionSettingsStore.Persistence {
		Map<String, String> values = new HashMap<String, String>();
		int writes;
		boolean failRead;
		boolean failWrite;

		public Map<String, String> read() {
			if( this.failRead ) {
				throw new IllegalStateException("unreadable");
			}
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.writes ++;
			if( this.failWrite ) {
				throw new IllegalStateException("read-only");
			}
			this.values = new HashMap<String, String>(values);
		}
	}

	@Test
	public void aNewInstallationStartsWithTheRecommendedValuesAndWritesNothing() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		assertEquals(RecognitionSettings.defaults(), store.get());
		assertEquals(0, file.writes);
	}

	@Test
	public void valuesSurviveARestart() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		RecognitionSettings changed = store.get().with(RecognitionParameters.NOTE_ACCEPT, 20).with(RecognitionParameters.TUNER_HOLD, false)
				.with(RecognitionParameters.PASSAGE_MEASURES, "0").with(RecognitionParameters.CHORD_EVIDENCE, 0.5);
		store.update(changed);

		RecognitionSettingsStore restarted = new RecognitionSettingsStore(file);
		assertEquals(changed, restarted.get());
		assertTrue(restarted.getLoaded().getRepairs().isEmpty());
		assertEquals("1", file.values.get(RecognitionParameters.VERSION_KEY));
	}

	@Test
	public void aDamagedFileIsRepairedOnLoadAndRewrittenCleanOnTheNextSave() {
		MemoryPersistence file = new MemoryPersistence();
		file.values.put("recognition.note.stability", "-Infinity");
		file.values.put("recognition.chord.window", "9999");
		file.values.put("recognition.passage.retries", "two");
		file.values.put("recognition.obsolete.key", "1");
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);

		assertEquals(107.0, store.get().get(RecognitionParameters.NOTE_STABILITY));
		assertEquals(1500.0, store.get().get(RecognitionParameters.CHORD_WINDOW));
		assertEquals(2, store.get().getPassageRetries());
		assertEquals(List.of("recognition.obsolete.key"), store.getLoaded().getUnknownKeys());

		store.update(store.get().with(RecognitionParameters.TUNER_IN_TUNE, 6));
		assertFalse(file.values.containsKey("recognition.obsolete.key"));
		assertEquals("1500", file.values.get("recognition.chord.window"));
		assertEquals("107", file.values.get("recognition.note.stability"));
	}

	@Test
	public void anUnreadableFileGivesTheRecommendedValues() {
		MemoryPersistence file = new MemoryPersistence();
		file.failRead = true;
		assertEquals(RecognitionSettings.defaults(), new RecognitionSettingsStore(file).get());
	}

	@Test
	public void aWriteFailureKeepsTheNewValuesForThisRun() {
		MemoryPersistence file = new MemoryPersistence();
		file.failWrite = true;
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		RecognitionSettings changed = store.get().with(RecognitionParameters.NOTE_ACCEPT, 25);
		store.update(changed);
		assertEquals(changed, store.get());
	}

	@Test
	public void listenersHearRealChangesOnlyOnceWithBothVersions() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new MemoryPersistence());
		List<RecognitionSettings[]> heard = new ArrayList<RecognitionSettings[]>();
		RecognitionSettingsStore.Listener listener = (previous, current) -> heard.add(new RecognitionSettings[] { previous, current });
		store.addListener(listener);
		store.addListener(listener);

		store.update(store.get());
		assertTrue(heard.isEmpty());
		RecognitionSettings changed = store.get().with(RecognitionParameters.CHORD_WINDOW, 800);
		store.update(changed);
		assertEquals(1, heard.size());
		assertEquals(RecognitionSettings.defaults(), heard.get(0)[0]);
		assertSame(changed, heard.get(0)[1]);

		store.removeListener(listener);
		store.restoreDefaults();
		assertEquals(1, heard.size());
		assertThrows(IllegalArgumentException.class, () -> store.update(null));
	}

	@Test
	public void restoreAllAndRestoreOneSection() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		store.update(store.get().with(RecognitionParameters.TUNER_IN_TUNE, 12).with(RecognitionParameters.PASSAGE_RETRIES, 5));

		store.restoreDefaults(Section.PASSAGES);
		assertEquals(12.0, store.get().get(RecognitionParameters.TUNER_IN_TUNE));
		assertEquals(2, store.get().getPassageRetries());

		store.restoreDefaults();
		assertEquals(RecognitionSettings.defaults(), store.get());
		assertEquals(RecognitionSettings.defaults(), new RecognitionSettingsStore(file).get());
	}

	@Test
	public void repeatedUpdatesFromManyOpeningsOfTheConfigurationStayConsistent() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		for( int i = 0 ; i < 200 ; i ++ ) {
			// each Configuration opening starts from the stored values and confirms or cancels
			RecognitionSettings pending = store.get().with(RecognitionParameters.CHORD_WINDOW, 300 + (i % 13) * 100);
			if( i % 3 != 0 ) {
				store.update(pending);
			}
			assertNotNull(store.get());
		}
		assertEquals(store.get(), new RecognitionSettingsStore(file).get());
	}

	@Test
	public void onlyRealEnginesAreOffered() {
		assertEquals(1, PitchEngineCatalog.getEngines().size());
		assertEquals(PitchEngineCatalog.FASTYIN, PitchEngineCatalog.getRecommended().getId());
		assertEquals(PitchEngineCatalog.getRecommended(), PitchEngineCatalog.resolve(PitchEngineCatalog.AUTOMATIC));
		assertEquals(PitchEngineCatalog.getRecommended(), PitchEngineCatalog.resolve("neural-net"));
		assertEquals(List.of(PitchEngineCatalog.AUTOMATIC), RecognitionParameters.ENGINE_PITCH.getOptions());
		assertNotNull(PitchEngineCatalog.getRecommended().create(48000));
		assertNotNull(PitchEngineCatalog.getRecommended().create(44100));
	}
}
