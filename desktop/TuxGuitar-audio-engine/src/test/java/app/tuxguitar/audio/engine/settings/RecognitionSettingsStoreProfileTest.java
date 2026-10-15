package app.tuxguitar.audio.engine.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter.Section;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStoreTest.MemoryPersistence;

/** Spec 013 (T009): the default layer and the effective settings (contract profile-library 1 to 4, FR-010, FR-011). */
public class RecognitionSettingsStoreProfileTest {

	private static RecognitionProfile profile(double noteAccept, double chordWindow) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.NOTE_ACCEPT, noteAccept);
		values.put(RecognitionParameters.CHORD_WINDOW, chordWindow);
		return new RecognitionProfile(DeviceKey.of("Micro MacBook Pro", null, 0), 48000, 0L, values, new RecognitionRate(9, 10, 2, 3),
				new RecognitionRate(7, 10, 1, 3), new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
	}

	private static double other(RecognitionParameter.Number parameter) {
		return parameter.getMinimum() == parameter.getDefault() ? parameter.getMaximum() : parameter.getMinimum();
	}

	@Test
	public void withNoProfileTheEffectiveSettingsAreTheDefaultOnesForEveryParameter() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new MemoryPersistence());
		for (RecognitionParameter parameter : RecognitionParameters.ALL) {
			assertEquals(store.getDefault().getValue(parameter), store.get().getValue(parameter), parameter.getKey());
		}
		assertEquals(store.getDefault(), store.get());
	}

	@Test
	public void aProfileChangesOnlyItsOwnParameters() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new MemoryPersistence());
		double accept = other(RecognitionParameters.NOTE_ACCEPT);
		double window = other(RecognitionParameters.CHORD_WINDOW);
		store.setActiveProfile(profile(accept, window));
		for (RecognitionParameter parameter : RecognitionParameters.ALL) {
			if (parameter == RecognitionParameters.NOTE_ACCEPT) {
				assertEquals(accept, store.get().get(RecognitionParameters.NOTE_ACCEPT));
			} else if (parameter == RecognitionParameters.CHORD_WINDOW) {
				assertEquals(window, store.get().get(RecognitionParameters.CHORD_WINDOW));
			} else {
				assertEquals(store.getDefault().getValue(parameter), store.get().getValue(parameter), parameter.getKey() + " is the default's");
			}
		}
		assertNotEquals(store.getDefault(), store.get());
		store.setActiveProfile(null);
		assertEquals(store.getDefault(), store.get(), "no profile again");
	}

	@Test
	public void writingTheDefaultWhileAProfileIsActiveNeverCopiesTheProfileIntoTheDefaultOrTheFile() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		double defaultAccept = store.getDefault().get(RecognitionParameters.NOTE_ACCEPT);
		store.setActiveProfile(profile(other(RecognitionParameters.NOTE_ACCEPT), other(RecognitionParameters.CHORD_WINDOW)));
		store.update(store.getDefault().with(RecognitionParameters.TEMPO_WINDOW, 150.0));
		assertEquals(defaultAccept, store.getDefault().get(RecognitionParameters.NOTE_ACCEPT), "the default's profile parameters are as they were");
		assertEquals(150.0, store.getDefault().get(RecognitionParameters.TEMPO_WINDOW));
		assertEquals(150.0, store.get().get(RecognitionParameters.TEMPO_WINDOW), "the effective settings follow the default for what the profile does not hold");
		assertEquals(other(RecognitionParameters.NOTE_ACCEPT), store.get().get(RecognitionParameters.NOTE_ACCEPT), "and keep the profile's own value");
		assertEquals(RecognitionParameters.NOTE_ACCEPT.format(defaultAccept), file.values.get(RecognitionParameters.NOTE_ACCEPT.getKey()), "the file holds the default layer");
	}

	@Test
	public void settingTheProfileTellsTheListenersOnceWhenTheEffectiveSettingsChangeAndWritesNothing() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		List<RecognitionSettings[]> changes = new ArrayList<RecognitionSettings[]>();
		store.addListener((previous, current) -> changes.add(new RecognitionSettings[] { previous, current }));
		RecognitionSettings before = store.get();
		store.setActiveProfile(profile(other(RecognitionParameters.NOTE_ACCEPT), other(RecognitionParameters.CHORD_WINDOW)));
		assertEquals(1, changes.size());
		assertEquals(before, changes.get(0)[0]);
		assertEquals(store.get(), changes.get(0)[1]);
		assertEquals(0, file.writes, "a profile is not written to the recognition file");
		store.setActiveProfile(profile(other(RecognitionParameters.NOTE_ACCEPT), other(RecognitionParameters.CHORD_WINDOW)));
		assertEquals(1, changes.size(), "the same effective settings: nobody is told");
		store.setActiveProfile(null);
		assertEquals(2, changes.size());
		assertEquals(before, changes.get(1)[1]);
	}

	@Test
	public void restoringTheDefaultsActsOnTheDefaultLayerOnly() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		double accept = other(RecognitionParameters.NOTE_ACCEPT);
		store.update(store.getDefault().with(RecognitionParameters.CHORD_EVIDENCE, other(RecognitionParameters.CHORD_EVIDENCE)));
		store.setActiveProfile(profile(accept, other(RecognitionParameters.CHORD_WINDOW)));
		store.restoreDefaults(Section.CHORDS);
		assertEquals(RecognitionParameters.CHORD_EVIDENCE.getDefault(), store.getDefault().get(RecognitionParameters.CHORD_EVIDENCE));
		assertEquals(accept, store.get().get(RecognitionParameters.NOTE_ACCEPT), "the profile is still in force");
		assertEquals(RecognitionParameters.NOTE_ACCEPT.format(RecognitionParameters.NOTE_ACCEPT.getDefault()), file.values.get(RecognitionParameters.NOTE_ACCEPT.getKey()));
		store.restoreDefaults();
		assertEquals(RecognitionSettings.defaults(), store.getDefault());
		assertEquals(accept, store.get().get(RecognitionParameters.NOTE_ACCEPT));
	}

	@Test
	public void aStoreWithNoProfileBehavesAsBefore() {
		MemoryPersistence file = new MemoryPersistence();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		List<Integer> told = new ArrayList<Integer>();
		store.addListener((previous, current) -> told.add(1));
		store.update(store.get().with(RecognitionParameters.TEMPO_WINDOW, 90.0));
		assertEquals(1, told.size());
		assertEquals(1, file.writes);
		assertEquals(90.0, store.get().get(RecognitionParameters.TEMPO_WINDOW));
		assertNull(file.values.get("recognition.profile"), "nothing about profiles in the recognition file");
	}
}
