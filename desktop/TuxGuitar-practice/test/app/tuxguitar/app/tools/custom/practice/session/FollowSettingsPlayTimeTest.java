package app.tuxguitar.app.tools.custom.practice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.tuxguitar.audio.engine.settings.RecognitionParameters;
import app.tuxguitar.audio.engine.settings.RecognitionSettingsStore;

/**
 * Spec 016 (T026, FR-011): the time to play a note in Follow, 300 ms until the learner sets it, from 100 to 600 ms, saved at once in the default layer of the
 * recognition settings (as the timing window), and the window of Arcade untouched by it.
 */
public class FollowSettingsPlayTimeTest {

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
	public void absentItIsThreeHundredMilliseconds() {
		assertEquals(300, new FollowSettings(new RecognitionSettingsStore(new Memory())).getPlayTimeMillis());
		assertEquals(300, new FollowSettings().getPlayTimeMillis(), "without a store too");
	}

	@Test
	public void aValueIsSavedAtOnceInTheDefaultLayerAndReadBack() {
		Memory file = new Memory();
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(file));
		settings.setPlayTimeMillis(450);
		assertEquals(450, settings.getPlayTimeMillis());
		RecognitionSettingsStore saved = new RecognitionSettingsStore(file);
		assertEquals(450.0, saved.getDefault().get(RecognitionParameters.FOLLOW_PLAY_TIME), "saved at once");
		assertEquals(450, new FollowSettings(saved).getPlayTimeMillis(), "the next launch reads it");
	}

	@Test
	public void aValueOutOfRangeIsBroughtToOneHundredOrSixHundred() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setPlayTimeMillis(20);
		assertEquals(100, settings.getPlayTimeMillis());
		settings.setPlayTimeMillis(5000);
		assertEquals(600, settings.getPlayTimeMillis());
		FollowSettings own = new FollowSettings();
		own.setPlayTimeMillis(-1);
		assertEquals(100, own.getPlayTimeMillis());
	}

	@Test
	public void theTimeToPlayAndTheWindowOfArcadeAreTwoSettings() {
		FollowSettings settings = new FollowSettings(new RecognitionSettingsStore(new Memory()));
		settings.setWindowMillis(150);
		settings.setPlayTimeMillis(200);
		assertEquals(150, settings.getWindowMillis());
		assertEquals(200, settings.getPlayTimeMillis());
	}
}
