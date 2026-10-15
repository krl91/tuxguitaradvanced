package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.calibration.DeviceKey;
import app.tuxguitar.audio.engine.calibration.FrontEndState;
import app.tuxguitar.audio.engine.calibration.RecognitionProfile;
import app.tuxguitar.audio.engine.calibration.RecognitionRate;
import app.tuxguitar.audio.engine.settings.RecognitionParameter;
import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;

/** Spec 013 (T011): the timing window is saved on the default layer; a device profile in force is not copied into the default (FR-010). */
public class FollowSettingsProfileTest {

	private static final class Memory implements RecognitionSettingsStore.Persistence {
		Map<String, String> values = new HashMap<String, String>();

		public Map<String, String> read() {
			return new HashMap<String, String>(this.values);
		}

		public void write(Map<String, String> values) {
			this.values = new HashMap<String, String>(values);
		}
	}

	@Test
	public void savingTheWindowWithAProfileActiveDoesNotCopyTheProfileIntoTheDefault() {
		Memory file = new Memory();
		RecognitionSettingsStore store = new RecognitionSettingsStore(file);
		double defaultAccept = store.getDefault().get(RecognitionParameters.NOTE_ACCEPT);
		RecognitionParameter.Number accept = RecognitionParameters.NOTE_ACCEPT;
		double other = accept.getMinimum() == accept.getDefault() ? accept.getMaximum() : accept.getMinimum();
		Map<RecognitionParameter.Number, Double> values = new LinkedHashMap<RecognitionParameter.Number, Double>();
		values.put(accept, other);
		store.setActiveProfile(new RecognitionProfile(DeviceKey.of("Micro MacBook Pro", null, 0), 48000, 0L, values, new RecognitionRate(9, 10, 2, 3),
				new RecognitionRate(7, 10, 1, 3), new FrontEndState(1.0, false), RecognitionProfile.Origin.SEARCHED));
		FollowSettings settings = new FollowSettings(store);
		settings.setWindowMillis(150);
		assertEquals(150.0, store.getDefault().get(RecognitionParameters.TEMPO_WINDOW), "the window is saved");
		assertEquals(defaultAccept, store.getDefault().get(RecognitionParameters.NOTE_ACCEPT), "the profile's value is not in the default");
		assertEquals(accept.format(defaultAccept), file.values.get(accept.getKey()), "nor in the file");
		assertEquals(other, store.get().get(accept), "the profile is still in force");
		assertEquals(150, settings.getWindowMillis());
	}
}
