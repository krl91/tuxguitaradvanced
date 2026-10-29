package app.tuxguitar.audio.engine.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.ProfileParameters;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;

/**
 * Spec 016 (T026): the time to play a note in Follow is the learner's choice, like the timing window: it is not a parameter of a device profile (spec 013),
 * so a profile can neither carry it nor change it.
 */
public class FollowPlayTimeProfileTest {

	private static final class MemoryPersistence implements RecognitionSettingsStore.Persistence {
		private Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	private static RecognitionProfile profile(Map<RecognitionParameter.Number, Double> values) {
		return new RecognitionProfile(DeviceKey.of("Rocksmith USB Guitar Adapter", null, 0), 48000, 0L, values, new RecognitionRate(9, 10, 2, 3),
				new RecognitionRate(7, 10, 1, 3), new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED);
	}

	@Test
	public void theTimeToPlayIsNotAParameterOfADeviceProfile() {
		assertFalse(ProfileParameters.LIST.contains(RecognitionParameters.FOLLOW_PLAY_TIME));
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(RecognitionParameters.FOLLOW_PLAY_TIME, 500.0);
		assertThrows(IllegalArgumentException.class, () -> profile(values), "a profile cannot carry it");
	}

	@Test
	public void afterADeviceProfileIsAppliedTheTimeToPlayStaysTheLearners() {
		RecognitionSettingsStore store = new RecognitionSettingsStore(new MemoryPersistence());
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		RecognitionParameter.Number accept = RecognitionParameters.NOTE_ACCEPT;
		values.put(accept, accept.getMinimum() == accept.getDefault() ? accept.getMaximum() : accept.getMinimum());
		store.setActiveProfile(profile(values));
		assertEquals(300.0, store.get().get(RecognitionParameters.FOLLOW_PLAY_TIME), "the default, 300 ms");
		store.setActiveProfile(null);
		store.update(store.getDefault().with(RecognitionParameters.FOLLOW_PLAY_TIME, 450.0));
		store.setActiveProfile(profile(values));
		assertEquals(450.0, store.get().get(RecognitionParameters.FOLLOW_PLAY_TIME), "the value the learner set");
	}

	@Test
	public void theTimeToPlayGoesFromOneHundredToSixHundredMillisecondsWithThePassages() {
		RecognitionParameter.Number playTime = RecognitionParameters.FOLLOW_PLAY_TIME;
		assertEquals(300.0, playTime.getDefault());
		assertEquals(100.0, playTime.getMinimum());
		assertEquals(600.0, playTime.getMaximum());
		assertEquals(RecognitionParameter.Section.PASSAGES, playTime.getSection(), "the owner's choice of 2026-10-29: the notes have no room left");
	}
}
