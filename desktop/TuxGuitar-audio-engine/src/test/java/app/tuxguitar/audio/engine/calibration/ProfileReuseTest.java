package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.input.AudioInputDescriptor;
import app.tuxguitar.audio.engine.input.CurrentInputSelection;
import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;
import app.tuxguitar.util.TGContext;

/**
 * Spec 013 (T040): what a saved profile does to the settings the readers see, after a restart (SC-002), for every reader alike (the tuner window, Follow,
 * Arcade, Wait: they all read {@code RecognitionSettingsStore.get()}), while the tuner's own settings are never the profile's (FR-010).
 */
public class ProfileReuseTest {

	private static final class Memory implements ProfilePersistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static final class StoreMemory implements RecognitionSettingsStore.Persistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static AudioInputDescriptor input(int mixerIndex, String name) {
		return new AudioInputDescriptor(mixerIndex + ":" + name + ":mono", name, null, 1, name, AudioInputDescriptor.Kind.UNKNOWN, false);
	}

	private static RecognitionProfile everyProfileParameterChanged(DeviceKey key) {
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
			values.put(parameter, parameter.getMinimum() == parameter.getDefault() ? parameter.getMaximum() : parameter.getMinimum());
		}
		return new RecognitionProfile(key, 48000, 1L, values, new RecognitionRate(9, 10, 2, 3), new RecognitionRate(7, 10, 1, 3), new FrontEndState(1.0, false),
				RecognitionProfile.Origin.SEARCHED);
	}

	@Test
	public void everyReaderSeesTheProfileValuesButTheTunersOwnSettingsStayTheDefaults() {
		List<AudioInputDescriptor> catalog = Arrays.asList(input(0, "Micro MacBook Pro"));
		Memory file = new Memory();
		new ProfileLibrary(file).save(everyProfileParameterChanged(DeviceKey.of(catalog.get(0), catalog)));
		TGContext context = new TGContext();
		CurrentInputSelection selection = CurrentInputSelection.getInstance(context);
		selection.select(catalog.get(0), null);
		RecognitionSettingsStore store = new RecognitionSettingsStore(new StoreMemory());
		store.update(store.getDefault().with(RecognitionParameters.TUNER_HOLD, true).with(RecognitionParameters.TUNER_IN_TUNE, 12.0)
				.with(RecognitionParameters.TUNER_CONFIDENCE, 0.9));
		new ProfileActivator(selection, new ProfileLibrary(file), store, () -> catalog).install();
		for (RecognitionParameter.Number parameter : ProfileParameters.LIST) {
			assertNotEquals(store.getDefault().get(parameter), store.get().get(parameter), parameter.getKey() + " is the profile's");
		}
		assertEquals(true, store.get().get(RecognitionParameters.TUNER_HOLD), "the tuner's hold is the default's");
		assertEquals(12.0, store.get().get(RecognitionParameters.TUNER_IN_TUNE), "and its tolerance");
		assertEquals(0.9, store.get().get(RecognitionParameters.TUNER_CONFIDENCE), "and its confidence");
		assertEquals(store.getDefault().get(RecognitionParameters.TEMPO_WINDOW), store.get().get(RecognitionParameters.TEMPO_WINDOW), "the timing window is the learner's");
		assertEquals(store.getDefault().get(RecognitionParameters.ENGINE_PITCH), store.get().get(RecognitionParameters.ENGINE_PITCH));
	}

	@Test
	public void aProfileSavedBeforeARestartIsTheSameAfterItAndAnotherDeviceStaysOnTheDefault() {
		List<AudioInputDescriptor> catalog = Arrays.asList(input(0, "Micro MacBook Pro"), input(1, "Other interface"));
		Memory file = new Memory();
		RecognitionProfile saved = everyProfileParameterChanged(DeviceKey.of(catalog.get(0), catalog));
		new ProfileLibrary(file).save(saved);

		// the restart: a new library over the same file, a new store, a new selection, a new activator
		ProfileLibrary reloaded = new ProfileLibrary(file);
		assertEquals(saved, reloaded.get(DeviceKey.of(catalog.get(0), catalog)), "identical to what was saved");
		TGContext context = new TGContext();
		CurrentInputSelection selection = CurrentInputSelection.getInstance(context);
		RecognitionSettingsStore store = new RecognitionSettingsStore(new StoreMemory());
		ProfileActivator activator = new ProfileActivator(selection, reloaded, store, () -> catalog);
		activator.install();
		selection.select(catalog.get(0), null);
		activator.refresh();
		assertEquals(saved.applyTo(store.getDefault()), store.get(), "selecting the device loads its profile again");
		selection.select(catalog.get(1), null);
		activator.refresh();
		assertEquals(store.getDefault(), store.get(), "a device that was never calibrated uses the default configuration, not the other's profile");
	}

	@Test
	public void editingTheDefaultConfigurationKeepsTheProfileOfACalibratedDevice() {
		List<AudioInputDescriptor> catalog = Arrays.asList(input(0, "Micro MacBook Pro"));
		Memory file = new Memory();
		RecognitionProfile saved = everyProfileParameterChanged(DeviceKey.of(catalog.get(0), catalog));
		new ProfileLibrary(file).save(saved);
		TGContext context = new TGContext();
		CurrentInputSelection selection = CurrentInputSelection.getInstance(context);
		selection.select(catalog.get(0), null);
		RecognitionSettingsStore store = new RecognitionSettingsStore(new StoreMemory());
		new ProfileActivator(selection, new ProfileLibrary(file), store, () -> catalog).install();
		store.update(store.getDefault().with(RecognitionParameters.NOTE_ACCEPT, 33.0).with(RecognitionParameters.TEMPO_WINDOW, 150.0));
		assertEquals(saved.getValues().get(RecognitionParameters.NOTE_ACCEPT), store.get().get(RecognitionParameters.NOTE_ACCEPT), "the profile's value stays");
		assertEquals(150.0, store.get().get(RecognitionParameters.TEMPO_WINDOW), "what the profile does not hold follows the edited default");
	}
}
